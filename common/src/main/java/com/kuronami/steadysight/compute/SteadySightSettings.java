package com.kuronami.steadysight.compute;

/**
 * Immutable snapshot of the mod's config. {@link VignetteStrength} reads
 * {@code enabled}/{@code maxOpacity}; the render layer reads only {@code
 * innerRadius} directly (to size the untouched center of the screen) and
 * otherwise only sees the single {@code float} that
 * {@link VignetteStrength#strength} returns.
 *
 * <p>DESIGN_COMPILE.md §2 (revised 2026-07-29): the vignette is always-on,
 * not linked to movement or camera rotation — a runClient verdict was
 * that movement-linked strength read as flicker, because standing still is
 * the exception during actual Minecraft play, not the rule the "dim during
 * motion" idea was borrowed from (VR comfort vignettes, where movement
 * itself is the exception). There is accordingly no input-shape field left
 * here (no {@code moveThreshold}/{@code moveFullSpeed}/{@code
 * rotationFullRate}) — strength is a constant determined entirely by the
 * active preset.
 *
 * <p>This is a plain record with no Minecraft or NeoForge types in it, so
 * both the JUnit tests and the config-reload adapter can construct it
 * freely (see DESIGN_COMPILE.md §3's compute/render split).
 */
public record SteadySightSettings(boolean enabled, float maxOpacity, float innerRadius) {

    /**
     * A reasonable settings snapshot for tests that don't care about the
     * exact numbers. Kept as plain literals rather than delegating to
     * {@code config.Strength.STANDARD} so this package stays free of any
     * dependency on the config layer; the two are meant to describe the
     * same values (STANDARD is the in-game default preset) and should be
     * kept in sync by hand.
     */
    public static SteadySightSettings defaults() {
        return new SteadySightSettings(true, 0.20f, 0.70f);
    }
}
