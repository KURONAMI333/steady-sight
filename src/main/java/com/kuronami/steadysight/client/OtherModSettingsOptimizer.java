package com.kuronami.steadysight.client;

import com.kuronami.steadysight.SteadySight;
import com.kuronami.steadysight.compute.OtherModConfigTargets;
import com.kuronami.steadysight.compute.OtherModConfigTargets.Entry;
import com.kuronami.steadysight.compute.SettingsMarker;
import com.kuronami.steadysight.config.SteadySightConfig;
import com.kuronami.steadysight.io.OtherModConfigFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task instructions HH2: pushes {@link OtherModConfigTargets#ALL} into the
 * other mods' own config files on disk, following the exact one-time-per-key
 * / never-fight-the-player-back regime {@link VanillaComfortSettings}
 * already established for vanilla {@link net.minecraft.client.Options} —
 * same marker mechanism ({@link SettingsMarker}, a second marker file so the
 * two features' key spaces never collide), same {@code
 * FMLClientSetupEvent}-plus-{@code enqueueWork} timing, same "gated by its
 * own config toggle" shape. What differs is entirely in the file-I/O layer:
 * vanilla settings live in one in-process {@code Options} object this mod
 * can write straight into, while these settings live in other mods' own
 * JSON/TOML files on disk that have to be read, parsed, and rewritten.
 *
 * <p><strong>Whether this takes effect the same launch or only the next one
 * (task instructions HH2, "判断と根拠を報告すること")</strong>: the honest
 * answer is <em>the next launch</em>, not this one. This class writes raw
 * config files from its own {@code FMLClientSetupEvent} handler, but it does
 * not control — and cannot know — when Blueprint, Punchy, or SmoothGUI read
 * their own config files into memory relative to that point. The common
 * pattern for a NeoForge or Fabric mod is to load its config during its own
 * mod construction or common-setup phase, which for client-side comfort
 * settings like these typically runs before {@code FMLClientSetupEvent}
 * fires for every mod in the instance (event phases run mod-by-mod but
 * complete one phase across all mods before the next phase starts) — so by
 * the time this handler runs and rewrites the file on disk, the target mod
 * has very likely already parsed its old value into memory for this
 * session. There is no in-process API this mod can call to push a changed
 * value into another mod's already-loaded config object (unlike {@code
 * Options}, which this mod's own process owns), so unlike the vanilla push,
 * a rewritten value here is expected to only take effect starting the
 * player's next launch. That is an acceptable outcome for a one-time,
 * set-and-forget push and is not a bug in this mechanism — it is simply
 * disclosed here rather than assumed away.
 *
 * <p>Every step below that can fail for a reason outside this mod's control
 * (missing file, malformed JSON/TOML, an unreadable file, a key that has
 * moved or been renamed between versions) fails a single {@link Entry} at a
 * time and moves on to the next one; nothing here is allowed to throw past
 * {@link #applyOnce}, because a client-startup event handler crashing would
 * be strictly worse than skipping one other mod's config for one launch.
 */
@EventBusSubscriber(modid = SteadySight.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class OtherModSettingsOptimizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OtherModSettingsOptimizer.class);

    /**
     * Separate from {@link VanillaComfortSettings#APPLIED_MARKER} on purpose
     * — the two features are gated by different config toggles
     * ({@code optimizeOtherMods} vs. {@code optimizeVanillaSettings}) and
     * push into entirely different key spaces (other mods' files vs.
     * vanilla {@code Options}); sharing one marker file would make turning
     * either toggle off after the fact impossible to reason about
     * independently.
     */
    private static final Path APPLIED_MARKER =
            FMLPaths.CONFIGDIR.get().resolve(SteadySight.MODID + "_other_mods_applied.flag");

    /** This is a brand-new marker file with no v0.1-era predecessor, so there is nothing to treat as legacy. */
    private static final Set<String> NO_LEGACY_KEYS = Set.of();

    private OtherModSettingsOptimizer() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(OtherModSettingsOptimizer::applyOnce);
    }

    private static void applyOnce() {
        if (!SteadySightConfig.optimizeOtherMods()) {
            return;
        }

        Set<String> alreadyApplied = readAppliedKeys();
        List<Entry> pending =
                OtherModConfigTargets.ALL.stream().filter(e -> !alreadyApplied.contains(e.markerKey())).toList();
        if (pending.isEmpty()) {
            return;
        }

        Set<String> newlyHandled = new LinkedHashSet<>();
        for (Entry entry : pending) {
            if (tryApply(entry)) {
                newlyHandled.add(entry.markerKey());
            }
        }
        if (newlyHandled.isEmpty()) {
            return;
        }

        Set<String> updated = new LinkedHashSet<>(alreadyApplied);
        updated.addAll(newlyHandled);
        writeAppliedKeys(updated);
    }

    /**
     * Whether this key should now be recorded as handled in the marker.
     *
     * <p>Returns {@code true} once this mod has actually opened the target
     * mod's config file and made a decision for this key — whether or not
     * that decision resulted in an actual write (the value may already have
     * been on the comfort-friendly side). Returns {@code false} — meaning
     * "try again on a later launch" — when the target mod isn't installed
     * yet (its config file doesn't exist), when the file exists but this
     * key isn't in it (a version mismatch this mod cannot resolve by
     * guessing), or when reading/parsing the file failed outright. All
     * three of those are states where a later launch might succeed (the
     * mod gets installed, updates its config format, or a transient I/O
     * problem resolves) and none of them are states this mod should
     * silently give up on forever by marking the key handled anyway.
     */
    private static boolean tryApply(Entry entry) {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(entry.configRelativePath());
        if (!Files.exists(configPath)) {
            // Target mod not installed, or hasn't generated its config yet — nothing to do.
            return false;
        }
        try {
            boolean handled =
                    switch (entry.format()) {
                        case JSON -> OtherModConfigFile.applyJson(configPath, entry);
                        case TOML -> OtherModConfigFile.applyToml(configPath, entry);
                    };
            if (handled) {
                LOGGER.info(
                        "Steady Sight: checked {}'s {} in {} for motion comfort (see the file's contents for"
                                + " whether a change was needed).",
                        entry.displayName(),
                        entry.configKey(),
                        configPath);
            }
            return handled;
        } catch (Exception e) {
            // OtherModConfigFile's own contract is to fail closed (return false) rather than throw; this
            // is a second line of defense in case that contract is ever violated by a future change — a
            // client-startup event handler crashing would be strictly worse than skipping one entry.
            LOGGER.warn(
                    "Steady Sight: could not optimize {}'s {} in {} — leaving it untouched, will retry on a later"
                            + " launch.",
                    entry.displayName(),
                    entry.configKey(),
                    configPath,
                    e);
            return false;
        }
    }

    /** @see VanillaComfortSettings#readAppliedKeys() — identical fail-safe reasoning, separate marker file. */
    private static Set<String> readAppliedKeys() {
        if (!Files.exists(APPLIED_MARKER)) {
            return Set.of();
        }
        try {
            return SettingsMarker.appliedKeys(
                    Files.readString(APPLIED_MARKER), OtherModConfigTargets.ALL_KEYS, NO_LEGACY_KEYS);
        } catch (IOException e) {
            LOGGER.warn(
                    "Steady Sight: could not read the other-mods-applied marker at {}. Treating every known"
                            + " setting as already applied; nothing will be pushed this launch.",
                    APPLIED_MARKER,
                    e);
            return Set.copyOf(OtherModConfigTargets.ALL_KEYS);
        }
    }

    private static void writeAppliedKeys(Set<String> keys) {
        try {
            Files.createDirectories(APPLIED_MARKER.getParent());
            Files.writeString(APPLIED_MARKER, String.join("\n", keys));
        } catch (IOException e) {
            LOGGER.warn(
                    "Steady Sight: could not write the other-mods-applied marker at {}. The settings just"
                            + " optimized may be re-applied on the next launch.",
                    APPLIED_MARKER,
                    e);
        }
    }
}
