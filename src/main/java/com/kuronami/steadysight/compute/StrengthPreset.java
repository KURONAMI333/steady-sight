package com.kuronami.steadysight.compute;

/**
 * The only vignette-strength control a player sees.
 *
 * <p>DESIGN_COMPILE.md's non-negotiables were revised 2026-07-29 after
 * six raw decimals (e.g. {@code 0.3499999940395355}) turned up laid out in
 * the config screen: "入れただけで効くこと。既定値が製品" — most players never
 * open the config screen, and the ones who do cannot tell what those numbers
 * mean. This enum replaces every numeric slider with four presets.
 *
 * <p>The vignette itself is always-on now (DESIGN_COMPILE.md §2, revised
 * 2026-07-29 after a runClient verdict that movement-linked strength
 * read as flicker — see {@link VignetteStrength}'s Javadoc for the full
 * story), so a preset is nothing but {@code maxOpacity}/{@code innerRadius}:
 * how dark the edges get, and how much of the screen they leave alone.
 * {@code maxOpacity} has been through several rounds of runClient tuning
 * (movement-linked SUBTLE was 0.20/0.65, STANDARD 0.35/0.55, STRONG 0.50/
 * 0.45 → an always-on first pass at 0.12/0.75, 0.20/0.70, 0.30/0.62 read as
 * too subtle → 0.25/0.72, 0.40/0.64, 0.55/0.54 read as correctly dark under
 * draw method A → **the same maxOpacity read as "暗すぎる、さすがにだめだ
 * わ" once draw method B's texture shipped**, because that texture's corner
 * regions were saturated at alpha 255 over a large area rather than a
 * single point — same darkness value, much more of the screen actually at
 * that darkness (see {@code scripts/generate_vignette_textures.py} and
 * GAP_LOG G38-G39 for the full story and the fix). The current values are
 * lower again to compensate for that larger-than-intended dark area, on
 * top of the corner fix itself.
 *
 * <p>No Minecraft or NeoForge types here on purpose (same reason as
 * {@link SteadySightSettings}): {@code config.Strength} is the thin
 * NeoForge-facing wrapper that a {@code ModConfigSpec.EnumValue} actually
 * stores, one constant per value here, so the config screen can show
 * translated button labels without this class needing to know what a
 * {@code Component} is.
 */
public enum StrengthPreset {
    OFF(false, 0.0f, 1.0f),
    SUBTLE(true, 0.18f, 0.72f),
    STANDARD(true, 0.28f, 0.64f),
    STRONG(true, 0.40f, 0.54f);

    private final boolean enabled;
    private final float maxOpacity;
    private final float innerRadius;

    StrengthPreset(boolean enabled, float maxOpacity, float innerRadius) {
        this.enabled = enabled;
        this.maxOpacity = maxOpacity;
        this.innerRadius = innerRadius;
    }

    public SteadySightSettings toSettings() {
        return new SteadySightSettings(enabled, maxOpacity, innerRadius);
    }
}
