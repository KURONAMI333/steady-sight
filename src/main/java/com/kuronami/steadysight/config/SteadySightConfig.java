package com.kuronami.steadysight.config;

import com.kuronami.steadysight.SteadySight;
import com.kuronami.steadysight.compute.SteadySightSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * CLIENT-only config (DESIGN_COMPILE.md §3 and the non-negotiables: this mod
 * has no server-visible state at all, so there is no COMMON spec to define).
 *
 * <p>Three controls, matching the mod's three features: a {@link Strength}
 * preset for the vignette, a one-time vanilla-settings push, and the
 * step-camera smoothing mixin. Non-negotiable (revised 2026-07-29):
 * "設定項目は最小限まで削る。生の小数を並べない" — every field here is a
 * boolean or an enum, never a raw number.
 *
 * <p>{@link #snapshot()} is the only thing the rest of the mod calls for the
 * vignette — it turns the live NeoForge config value into the plain
 * {@link SteadySightSettings} record the compute and render layers use, so
 * neither of those layers ever needs to import {@code ModConfigSpec}.
 * {@link #optimizeVanillaSettings()} and {@link #smoothStepCamera()} are read
 * directly by {@link com.kuronami.steadysight.client.VanillaComfortSettings}
 * and the {@code CameraMixin} respectively.
 */
public final class SteadySightConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.EnumValue<Strength> STRENGTH;
    private static final ModConfigSpec.BooleanValue OPTIMIZE_VANILLA_SETTINGS;
    private static final ModConfigSpec.BooleanValue SMOOTH_STEP_CAMERA;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        STRENGTH = builder
                .translation(SteadySight.MODID + ".configuration.strength")
                .comment("How strong the comfort vignette is. Standard already does the thing on its own — most players never need to change this.")
                .defineEnum("strength", Strength.STANDARD);

        OPTIMIZE_VANILLA_SETTINGS = builder
                .translation(SteadySight.MODID + ".configuration.optimizeVanillaSettings")
                .comment(
                        "Turns off view bobbing, FOV effects, screen distortion effects, damage tilt, and auto-jump — the vanilla accessibility settings most associated with comfort — the first time this mod loads. Applied once only; turning your own settings back on afterward sticks, this won't fight you.")
                .define("optimizeVanillaSettings", true);

        SMOOTH_STEP_CAMERA = builder
                .translation(SteadySight.MODID + ".configuration.smoothStepCamera")
                .comment(
                        "Smooths the vertical camera jolt when you step up a block (stairs, slabs, and other one-block ledges) instead of letting it snap. Only the camera is affected — your actual position and collision are untouched.")
                .define("smoothStepCamera", true);

        SPEC = builder.build();
    }

    private SteadySightConfig() {}

    /** Reads the live config value into the plain record the compute/render layers use. */
    public static SteadySightSettings snapshot() {
        return STRENGTH.get().toSettings();
    }

    public static boolean optimizeVanillaSettings() {
        return OPTIMIZE_VANILLA_SETTINGS.get();
    }

    public static boolean smoothStepCamera() {
        return SMOOTH_STEP_CAMERA.get();
    }
}
