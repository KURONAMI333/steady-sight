package com.kuronami.steadysight.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * DESIGN_COMPILE.md's non-negotiables (revised 2026-07-29): the preset is
 * the entire product surface now — no numeric sliders — so its OFF
 * behavior and its ordering are pinned directly here rather than left to
 * the numbers in {@link StrengthPreset} being "obviously" right.
 */
class StrengthPresetTest {

    @Test
    void offIsDisabled() {
        SteadySightSettings settings = StrengthPreset.OFF.toSettings();
        assertFalse(settings.enabled());
    }

    @Test
    void everyOtherPresetIsEnabled() {
        assertTrue(StrengthPreset.SUBTLE.toSettings().enabled());
        assertTrue(StrengthPreset.STANDARD.toSettings().enabled());
        assertTrue(StrengthPreset.STRONG.toSettings().enabled());
    }

    @Test
    void offProducesZeroStrength() {
        SteadySightSettings off = StrengthPreset.OFF.toSettings();
        assertEquals(0.0f, VignetteStrength.strength(off));
    }

    @Test
    void presetsAreOrderedSubtleThenStandardThenStrong() {
        float subtle = VignetteStrength.strength(StrengthPreset.SUBTLE.toSettings());
        float standard = VignetteStrength.strength(StrengthPreset.STANDARD.toSettings());
        float strong = VignetteStrength.strength(StrengthPreset.STRONG.toSettings());

        assertTrue(subtle < standard, "SUBTLE (" + subtle + ") should be weaker than STANDARD (" + standard + ")");
        assertTrue(standard < strong, "STANDARD (" + standard + ") should be weaker than STRONG (" + strong + ")");
    }
}
