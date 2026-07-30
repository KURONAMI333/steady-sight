package com.kuronami.steadysight.client;

import com.kuronami.steadysight.SteadySight;
import com.kuronami.steadysight.config.SteadySightConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * <p>This pushes five existing vanilla {@link Options} values toward their
 * comfort-friendly settings <strong>exactly once, ever</strong> — not on
 * every launch. Re-applying on every launch would fight the player the
 * moment they turn view bobbing back on for themselves; the marker file at
 * {@link #APPLIED_MARKER} is what makes the one-time push behave like a
 * sensible default and not like a mod that keeps overwriting the player's
 * own choices. {@code SteadySightConfig#optimizeVanillaSettings} gates
 * whether the (still-pending) one-time push happens at all — it does not
 * gate anything once {@link #APPLIED_MARKER} exists, because by then the
 * push already happened and there is nothing left to gate.
 */
@EventBusSubscriber(modid = SteadySight.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class VanillaComfortSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger(VanillaComfortSettings.class);

    /**
     * Presence of this file means "the one-time comfort push already
     * happened" — nothing more. It is deliberately not part of {@code
     * SteadySightConfig} (a {@code ModConfigSpec} field would show up as a
     * fourth, meaningless toggle on the config screen; DESIGN_COMPILE.md's
     * non-negotiable is exactly three visible settings).
     */
    private static final Path APPLIED_MARKER =
            FMLPaths.CONFIGDIR.get().resolve(SteadySight.MODID + "_vanilla_settings_applied.flag");

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
        if (Files.exists(APPLIED_MARKER)) {
            return;
        }
        if (!SteadySightConfig.optimizeVanillaSettings()) {
            return;
        }

        Options options = Minecraft.getInstance().options;
        options.bobView().set(false);
        options.autoJump().set(false);
        options.screenEffectScale().set(0.0);
        options.fovEffectScale().set(0.0);
        options.damageTiltStrength().set(0.0);
        options.save();

        markApplied();
    }

    private static void markApplied() {
        try {
            Files.createDirectories(APPLIED_MARKER.getParent());
            Files.writeString(APPLIED_MARKER, "applied");
        } catch (IOException e) {
            LOGGER.warn(
                    "Steady Sight: could not write the vanilla-settings-applied marker at {}. "
                            + "The comfort settings may be reapplied on the next launch.",
                    APPLIED_MARKER,
                    e);
        }
    }
}
