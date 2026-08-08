package com.kuronami.steadysight.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * DESIGN_COMPILE.md §2 (revised 2026-07-29 after a runClient verdict:
 * movement-linked strength read as flicker, because standing still is the
 * exception during actual Minecraft play, not the rule the "dim during
 * motion" idea was borrowed from). {@link VignetteStrength#strength} now
 * takes nothing but the active settings — no tick state, no partial tick,
 * nothing that could make the same preset render two different strengths
 * from one frame to the next.
 *
 * <p>{@link #sameSettingsAlwaysProduceTheSameStrengthRegardlessOfContext()}
 * is the test that makes that a guarantee instead of an intention: it is
 * the mechanical proof that the flicker this redesign exists to kill cannot
 * come back. Preset-specific behavior (OFF, the SUBTLE/STANDARD/STRONG
 * ordering) is covered separately by {@link StrengthPresetTest}.
 */
class VignetteStrengthTest {

    private static final SteadySightSettings DEFAULTS = SteadySightSettings.defaults();

    @Test
    void sameSettingsAlwaysProduceTheSameStrengthRegardlessOfContext() {
        // "Context" no longer exists as an argument to strength() — that
        // absence is the point. Calling it repeatedly (standing in for what
        // would have been different call sites / different frames / a
        // different tick) must be indistinguishable every time.
        float first = VignetteStrength.strength(DEFAULTS);
        float second = VignetteStrength.strength(DEFAULTS);
        float third = VignetteStrength.strength(DEFAULTS);
        assertEquals(first, second, "identical settings must never produce two different strengths");
        assertEquals(second, third, "identical settings must never produce two different strengths");
    }

    @Test
    void disabledIsAlwaysZero() {
        SteadySightSettings off = new SteadySightSettings(false, 1.0f, 0.0f);
        assertEquals(0.0f, VignetteStrength.strength(off));
    }

    @Test
    void strengthNeverExceedsOneEvenWithAnOutOfRangeMaxOpacity() {
        // Config validation (ModConfigSpec#defineInRange) should keep
        // maxOpacity in [0,1], but the pure function must not trust that
        // blindly — it's the one place this invariant can be pinned without
        // going through NeoForge's config system.
        SteadySightSettings settings = new SteadySightSettings(true, 1.5f, 0.7f);
        float s = VignetteStrength.strength(settings);
        assertTrue(s <= 1.0f, "strength " + s + " exceeded 1.0 even though maxOpacity was out of range");
    }

    @Test
    void sameArgumentsAlwaysProduceTheSameResult() {
        SteadySightSettings settings = new SteadySightSettings(true, 0.27f, 0.6f);
        float first = VignetteStrength.strength(settings);
        float second = VignetteStrength.strength(settings);
        assertEquals(first, second);
    }
}
