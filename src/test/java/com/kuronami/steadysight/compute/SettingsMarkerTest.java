package com.kuronami.steadysight.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link SettingsMarker}'s three recognized marker shapes (no marker /
 * legacy sentinel / newline key list) plus the fail-safe fallback for
 * unreadable content (GAP_LOG G78, added 2026-07-31 after an independent
 * review flagged that {@code client.VanillaComfortSettings}'s original
 * {@code IOException} handling failed open — treating an unreadable marker
 * as "nothing applied," which would have re-pushed all six settings and
 * overwritten anything the player had deliberately changed back).
 */
class SettingsMarkerTest {

    private static final List<String> ALL_KEYS =
            List.of("bobView", "autoJump", "screenEffectScale", "fovEffectScale", "damageTiltStrength", "fov");

    private static final Set<String> LEGACY_V1_KEYS =
            Set.of("bobView", "autoJump", "screenEffectScale", "fovEffectScale", "damageTiltStrength");

    @Test
    void noMarkerMeansNothingIsAppliedYet() {
        assertEquals(Set.of(), SettingsMarker.appliedKeys(null, ALL_KEYS, LEGACY_V1_KEYS));
        assertEquals(ALL_KEYS, SettingsMarker.pendingKeys(null, ALL_KEYS, LEGACY_V1_KEYS));
    }

    @Test
    void legacySentinelMeansTheFiveOriginalKeysAreDoneButNotFov() {
        Set<String> applied = SettingsMarker.appliedKeys(SettingsMarker.LEGACY_MARKER_CONTENT, ALL_KEYS, LEGACY_V1_KEYS);
        assertEquals(LEGACY_V1_KEYS, applied);

        List<String> pending = SettingsMarker.pendingKeys(SettingsMarker.LEGACY_MARKER_CONTENT, ALL_KEYS, LEGACY_V1_KEYS);
        assertEquals(List.of("fov"), pending);
    }

    @Test
    void newlineKeyListIsParsedAndOnlyTheMissingKeysArePending() {
        String content = "bobView\nautoJump\nscreenEffectScale";
        Set<String> applied = SettingsMarker.appliedKeys(content, ALL_KEYS, LEGACY_V1_KEYS);
        assertEquals(Set.of("bobView", "autoJump", "screenEffectScale"), applied);

        List<String> pending = SettingsMarker.pendingKeys(content, ALL_KEYS, LEGACY_V1_KEYS);
        assertEquals(List.of("fovEffectScale", "damageTiltStrength", "fov"), pending);
    }

    @Test
    void blankLinesInTheKeyListAreIgnored() {
        String content = "bobView\n\n autoJump \n\n";
        Set<String> applied = SettingsMarker.appliedKeys(content, ALL_KEYS, LEGACY_V1_KEYS);
        assertEquals(Set.of("bobView", "autoJump"), applied);
    }

    @Test
    void allKeysAlreadyPresentMeansNothingIsPending() {
        String content = String.join("\n", ALL_KEYS);
        assertTrue(SettingsMarker.pendingKeys(content, ALL_KEYS, LEGACY_V1_KEYS).isEmpty());
    }

    @Test
    void unrecognizedContentFailsSafeToEverythingAlreadyApplied() {
        // A marker file exists (this isn't the null/no-marker case) but its
        // content matches none of ALL_KEYS and isn't the legacy sentinel —
        // e.g. corrupted by a torn write, or from some future/foreign
        // format. The safe assumption is "already handled," not "start
        // over": re-pushing every setting would overwrite anything the
        // player deliberately reverted, which is strictly worse than
        // skipping a push for one launch.
        String garbled = "xyz not a real key";
        Set<String> applied = SettingsMarker.appliedKeys(garbled, ALL_KEYS, LEGACY_V1_KEYS);
        assertEquals(Set.copyOf(ALL_KEYS), applied);
        assertTrue(SettingsMarker.pendingKeys(garbled, ALL_KEYS, LEGACY_V1_KEYS).isEmpty());
    }

    @Test
    void emptyButExistingMarkerFailsSafeToEverythingAlreadyApplied() {
        // Distinguishes "the marker file exists but is empty" (still a sign
        // some earlier version of this mod ran and something went wrong
        // writing it) from "the marker file doesn't exist at all" (the
        // null case, a genuinely fresh install) — the two must not collapse
        // to the same "nothing applied" outcome.
        Set<String> applied = SettingsMarker.appliedKeys("", ALL_KEYS, LEGACY_V1_KEYS);
        assertEquals(Set.copyOf(ALL_KEYS), applied);
        assertTrue(SettingsMarker.pendingKeys("", ALL_KEYS, LEGACY_V1_KEYS).isEmpty());
    }

    @Test
    void partialOverlapWithKnownKeysIsTrustedAsIs() {
        // As long as the content shares at least one recognizable key, it's
        // treated as a genuine (if partial) applied-keys list rather than
        // triggering the fail-safe fallback — an unrelated stray line
        // shouldn't make an otherwise-valid marker look unreadable.
        String content = "bobView\nsomeFutureKeyThisVersionDoesNotKnowAbout";
        Set<String> applied = SettingsMarker.appliedKeys(content, ALL_KEYS, LEGACY_V1_KEYS);
        assertEquals(Set.of("bobView", "someFutureKeyThisVersionDoesNotKnowAbout"), applied);
    }
}
