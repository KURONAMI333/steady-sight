package com.kuronami.steadysight.client;

import com.kuronami.steadysight.SteadySight;
import com.kuronami.steadysight.compute.SettingsMarker;
import com.kuronami.steadysight.config.SteadySightConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DESIGN_COMPILE.md §2 (scope widened 2026-07-30): "画面の揺れOFFは最頻の推奨
 * 手当て。実装が最も軽く効果が最も確実" — SPIKE_MOTION_SICKNESS_2026-07 §4.7's
 * vanilla accessibility settings are the cheapest, most certain comfort win
 * available, and applying them is what "入れただけで効くこと" actually means
 * for a player who never opens a config screen.
 *
 * <p>This pushes existing vanilla {@link Options} values toward their
 * comfort-friendly settings <strong>exactly once per setting, ever</strong>
 * — not on every launch, and not more than once even across a mod update
 * that adds a new setting to the list. Re-applying on every launch would
 * fight the player the moment they turn view bobbing back on for
 * themselves; the marker file at {@link #APPLIED_MARKER} is what makes the
 * one-time push behave like a sensible default and not like a mod that
 * keeps overwriting the player's own choices.
 * {@code SteadySightConfig#optimizeVanillaSettings} gates whether any
 * still-pending push happens at all — it does not un-apply anything once a
 * key is marked done, because by then that push already happened and there
 * is nothing left to gate for it.
 *
 * <p><strong>Marker versioning (added 2026-07-31 alongside the FOV entry,
 * GAP_LOG G77-G78)</strong>: v0.1 shipped with five settings and a marker
 * file whose content was an unstructured sentinel — presence alone meant
 * "all five are done", and nothing about which five. Adding FOV as a sixth
 * setting could not simply check "does the marker exist" any more: an
 * existing player's marker already existing would make the naive check
 * skip FOV forever, while treating an existing marker as "nothing applied
 * yet" would re-apply (and silently overwrite) the other five settings the
 * player may have deliberately changed back — exactly the overwrite this
 * mechanism exists to prevent. The fix: the marker now stores <em>which
 * keys</em> have been applied (one per line), and {@link #applyOnce} only
 * ever pushes the keys from {@link #ALL_KEYS} that are missing from that
 * set — old keys already recorded (or already reverted by the player) are
 * never re-touched. The key-diffing itself lives in
 * {@link SettingsMarker} (MC-type-zero, JUnit-tested directly) — this
 * class is a thin file-I/O wrapper around it. A pre-existing marker whose
 * content doesn't parse as a newline list of known keys (i.e. the literal
 * v0.1 sentinel, {@value SettingsMarker#LEGACY_MARKER_CONTENT}) is treated
 * as "the five keys in {@link #LEGACY_V1_KEYS} are already applied" rather
 * than "nothing is applied" — this is what keeps a v0.1 player's marker
 * from re-pushing those five while still letting FOV (the one truly-new
 * key) through. The same per-key mechanism absorbs any future addition
 * without another marker-format migration. An unreadable marker file (an
 * {@link IOException} on read, or content that matches none of
 * {@link #ALL_KEYS} and isn't the legacy sentinel) is treated the same
 * way — "assume every known key is already applied, push nothing this
 * launch" — rather than falling back to "nothing applied", which would
 * have silently re-pushed (and overwritten) every setting on the very
 * read failure this handling exists to guard against.
 */
@EventBusSubscriber(modid = SteadySight.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class VanillaComfortSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger(VanillaComfortSettings.class);

    /**
     * Records which comfort settings have already been pushed, one key per
     * line (see {@link #ALL_KEYS}). It is deliberately not part of
     * {@code SteadySightConfig} (a {@code ModConfigSpec} field would show
     * up as a fourth, meaningless toggle on the config screen;
     * DESIGN_COMPILE.md's non-negotiable is exactly three visible
     * settings).
     */
    private static final Path APPLIED_MARKER =
            FMLPaths.CONFIGDIR.get().resolve(SteadySight.MODID + "_vanilla_settings_applied.flag");

    /** The five settings v0.1 pushed under the legacy sentinel marker. */
    private static final Set<String> LEGACY_V1_KEYS =
            Set.of("bobView", "autoJump", "screenEffectScale", "fovEffectScale", "damageTiltStrength");

    /**
     * Every comfort setting this mod ever pushes, in application order.
     * Adding a new one here is the entire migration step required next
     * time — no new marker file, no new format version.
     */
    private static final List<String> ALL_KEYS =
            List.of("bobView", "autoJump", "screenEffectScale", "fovEffectScale", "damageTiltStrength", "fov");

    private VanillaComfortSettings() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // FMLClientSetupEvent fires on a loading thread; enqueueWork defers
        // to the main thread once startup has progressed far enough that
        // Minecraft.getInstance().options is safe to touch (the same
        // deferral pattern ArsNouveau's ClientHandler uses for its own
        // FMLClientSetupEvent work).
        event.enqueueWork(VanillaComfortSettings::applyOnce);
    }

    private static void applyOnce() {
        if (!SteadySightConfig.optimizeVanillaSettings()) {
            return;
        }

        Set<String> alreadyApplied = readAppliedKeys();
        List<String> toApply = ALL_KEYS.stream().filter(key -> !alreadyApplied.contains(key)).toList();
        if (toApply.isEmpty()) {
            return;
        }

        Options options = Minecraft.getInstance().options;
        for (String key : toApply) {
            applyKey(options, key);
        }
        options.save();

        Set<String> newApplied = new LinkedHashSet<>(alreadyApplied);
        newApplied.addAll(toApply);
        writeAppliedKeys(newApplied);
    }

    /** One vanilla {@link Options} push per key name in {@link #ALL_KEYS}. */
    private static void applyKey(Options options, String key) {
        switch (key) {
            case "bobView" -> options.bobView().set(false);
            case "autoJump" -> options.autoJump().set(false);
            case "screenEffectScale" -> options.screenEffectScale().set(0.0);
            case "fovEffectScale" -> options.fovEffectScale().set(0.0);
            case "damageTiltStrength" -> options.damageTiltStrength().set(0.0);
            // 80 (vertical) ≈ 115° horizontal at 16:9 — enough to help with
            // cybersickness without the distortion that a much wider value
            // (e.g. 103, which is a HORIZONTAL-basis number from other games
            // and would mean ~133° horizontal if typed into Minecraft's
            // vertical-basis FOV slider) introduces. See GAP_LOG for the
            // full vertical/horizontal conversion table this was checked
            // against before picking 80.
            //
            // Deliberately a FLOOR (raise only), not an unconditional set:
            // unlike the other five pushes, which all move their setting to
            // an unambiguous comfort extreme (off / zero), 80 is a MIDDLE
            // value for FOV — a player already running something wider
            // (say 100) would have their FOV narrowed by an unconditional
            // set, which is the opposite of the comfort direction FOV is
            // being pushed in and also the exact "overwrite the player's
            // own choice" this whole mechanism exists to avoid (GAP_LOG
            // G78). A player below 80 (including vanilla's default 70)
            // still gets raised to 80, so "入れただけで効く" still holds for
            // the default-FOV majority.
            case "fov" -> {
                if (options.fov().get() < 80) {
                    options.fov().set(80);
                }
            }
            default -> throw new IllegalArgumentException("Steady Sight: unknown comfort setting key: " + key);
        }
    }

    /**
     * Empty set if the marker doesn't exist yet (a brand-new install:
     * nothing applied). Parsing of an existing marker's content — the
     * legacy sentinel, a newline key list, or unrecognized content — is
     * {@link SettingsMarker#appliedKeys}'s job (MC-type-zero, JUnit-tested
     * directly).
     *
     * <p>An {@link IOException} on an <em>existing</em> marker file is
     * deliberately <strong>not</strong> treated the same as "marker doesn't
     * exist" (GAP_LOG G78, an independent-review finding): the earlier
     * version of this method did exactly that and it was a real regression
     * — a transient read failure would have made every one of the six
     * settings look brand new and re-pushed (and overwritten) all of them.
     * Failing safe here means assuming every key is already applied
     * instead — the same "already handled" fallback
     * {@link SettingsMarker#appliedKeys} uses for unreadable/garbled
     * content it can read but not parse.
     */
    private static Set<String> readAppliedKeys() {
        if (!Files.exists(APPLIED_MARKER)) {
            return Set.of();
        }
        try {
            return SettingsMarker.appliedKeys(Files.readString(APPLIED_MARKER), ALL_KEYS, LEGACY_V1_KEYS);
        } catch (IOException e) {
            LOGGER.warn(
                    "Steady Sight: could not read the vanilla-settings-applied marker at {}. "
                            + "Treating every known setting as already applied; nothing will be pushed this launch.",
                    APPLIED_MARKER,
                    e);
            return Set.copyOf(ALL_KEYS);
        }
    }

    private static void writeAppliedKeys(Set<String> keys) {
        try {
            Files.createDirectories(APPLIED_MARKER.getParent());
            Files.writeString(APPLIED_MARKER, String.join("\n", keys));
        } catch (IOException e) {
            LOGGER.warn(
                    "Steady Sight: could not write the vanilla-settings-applied marker at {}. "
                            + "The comfort settings just pushed may be re-applied on the next launch.",
                    APPLIED_MARKER,
                    e);
        }
    }
}
