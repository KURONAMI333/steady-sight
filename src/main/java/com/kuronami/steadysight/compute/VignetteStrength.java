package com.kuronami.steadysight.compute;

/**
 * The one thing this mod computes: how strong the comfort vignette should be
 * right now.
 *
 * <p>Pure function of the active settings alone, no Minecraft types
 * (DESIGN_COMPILE.md §6 — this is what makes it JUnit-testable without the
 * game). DESIGN_COMPILE.md §2 (revised 2026-07-29): the vignette used to
 * take tick-sampled movement impulse and camera-rotation rate as inputs,
 * scaling strength up while moving/turning and down while still. A
 * runClient verdict was that this read as flicker — "遷移が瞬時" (no
 * self-owned time constant meant every walk/stop toggled strength in a
 * single 50ms tick) compounded by "借りた前提がマイクラで成立していない"
 * (VR comfort vignettes dim <em>during</em> movement because movement is the
 * exception there; Minecraft players are moving almost continuously, so
 * "only while moving" was really "on almost always, with jarring blackouts
 * whenever the player stopped"). The fix was not a smoother transition —
 * that would have kept the same broken premise, just harder to notice — it
 * was removing the input dependency entirely. Strength is now a constant
 * for a given preset, which is also the more mechanically correct read of
 * the underlying research: peripheral-vision restriction and rest-frame
 * effects both work as (or better as) a constant presence, not something
 * that appears and disappears with the player's own movement.
 */
public final class VignetteStrength {

    private VignetteStrength() {}

    public static float strength(SteadySightSettings settings) {
        if (!settings.enabled()) {
            return 0.0f;
        }
        return clamp01(settings.maxOpacity());
    }

    /** Clamps to [0,1]. NaN (and anything else that isn't {@code > 0}) collapses to 0, never passes through. */
    private static float clamp01(float v) {
        if (!(v > 0.0f)) {
            return 0.0f;
        }
        if (v > 1.0f) {
            return 1.0f;
        }
        return v;
    }
}
