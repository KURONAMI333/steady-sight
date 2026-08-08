package com.kuronami.steadysight.config;

import com.kuronami.steadysight.SteadySight;
import com.kuronami.steadysight.compute.SteadySightSettings;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * CLIENT-only config (DESIGN_COMPILE.md §3 and the non-negotiables: this mod
 * has no server-visible state at all, so there is no COMMON spec to define).
 *
 * <p>Forge 1.20.1's {@link ForgeConfigSpec} is the ancestor of NeoForge's
 * {@code ModConfigSpec}; the builder API is the same shape, so the four keys,
 * their defaults, their comments and their translation keys here are
 * character-for-character the ones the NeoForge cells declare
 * (PORTING_CHECKLIST §8's four-point check). What Forge 1.20.1 lacks is the
 * automatic {@code ConfigurationScreen} that renders them — the generated
 * {@code steady_sight-client.toml} is identical, but there is no in-game
 * editor for it on this loader.
 *
 * <p>Four controls: a {@link Strength} preset for the vignette, a one-time
 * vanilla-settings push, the step-camera smoothing mixin, and a one-time
 * push into a handful of <em>other</em> mods' own comfort-related settings.
 * Non-negotiable (revised 2026-07-29): "設定項目は最小限まで削る。生の小数を
 * 並べない" — every field here is a boolean or an enum, never a raw number.
 *
 * <p>{@link #snapshot()} is the only thing the rest of the mod calls for the
 * vignette — it turns the live Forge config value into the plain
 * {@link SteadySightSettings} record the compute and render layers use, so
 * neither of those layers ever needs to import {@code ForgeConfigSpec}.
 */
public final class SteadySightConfig {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.EnumValue<Strength> STRENGTH;
    private static final ForgeConfigSpec.BooleanValue OPTIMIZE_VANILLA_SETTINGS;
    private static final ForgeConfigSpec.BooleanValue SMOOTH_STEP_CAMERA;
    private static final ForgeConfigSpec.BooleanValue OPTIMIZE_OTHER_MODS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

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

        OPTIMIZE_OTHER_MODS = builder
                .translation(SteadySight.MODID + ".configuration.optimizeOtherMods")
                .comment(
                        "Nudges a handful of camera-motion settings in other installed mods (Blueprint's screen-shake scale and shaker cap, Punchy's freeze-shake, SmoothGUI's animation scale) toward less motion, the first time this mod loads — only settings that move the camera itself, never mods that just add visual detail, and only if that mod is actually installed. This does modify another mod's own config file. Applied once only; your own changes afterward are never overwritten.")
                .define("optimizeOtherMods", true);

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

    public static boolean optimizeOtherMods() {
        return OPTIMIZE_OTHER_MODS.get();
    }
}
