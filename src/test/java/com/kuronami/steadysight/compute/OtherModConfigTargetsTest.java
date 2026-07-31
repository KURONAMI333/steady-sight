package com.kuronami.steadysight.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuronami.steadysight.compute.OtherModConfigTargets.Entry;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Sanity checks on the confirmed table itself, independent of the push
 * logic — the kind of mistake a future addition to {@link
 * OtherModConfigTargets#ALL} could silently make without a test catching it.
 */
class OtherModConfigTargetsTest {

    @Test
    void everyMarkerKeyIsUnique() {
        Set<String> seen = new HashSet<>();
        for (Entry entry : OtherModConfigTargets.ALL) {
            assertTrue(seen.add(entry.markerKey()), "duplicate markerKey: " + entry.markerKey());
        }
    }

    @Test
    void allKeysMatchesTheTableInOrder() {
        assertEquals(OtherModConfigTargets.ALL.size(), OtherModConfigTargets.ALL_KEYS.size());
        for (int i = 0; i < OtherModConfigTargets.ALL.size(); i++) {
            assertEquals(OtherModConfigTargets.ALL.get(i).markerKey(), OtherModConfigTargets.ALL_KEYS.get(i));
        }
    }

    @Test
    void noEntryHasABlankConfigKeyOrPath() {
        for (Entry entry : OtherModConfigTargets.ALL) {
            assertFalse(entry.configKey().isBlank(), entry.markerKey() + " has a blank configKey");
            assertFalse(entry.configRelativePath().isBlank(), entry.markerKey() + " has a blank configRelativePath");
        }
    }
}
