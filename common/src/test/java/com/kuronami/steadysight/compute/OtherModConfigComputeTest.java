package com.kuronami.steadysight.compute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link OtherModConfigCompute}'s direction table (HH1: "適用条件は「一
 * 括で切る」ではない" — each direction only pushes when the current value is
 * on the wrong side of the target, never past it).
 */
class OtherModConfigComputeTest {

    @Test
    void ceilPushesDownOnlyWhenAboveTarget() {
        assertTrue(OtherModConfigCompute.shouldPushNumber(1.0, PushDirection.CEIL, 0.3));
        assertFalse(OtherModConfigCompute.shouldPushNumber(0.3, PushDirection.CEIL, 0.3));
        assertFalse(OtherModConfigCompute.shouldPushNumber(0.1, PushDirection.CEIL, 0.3));
    }

    @Test
    void floorPushesUpOnlyWhenBelowTarget() {
        assertTrue(OtherModConfigCompute.shouldPushNumber(70.0, PushDirection.FLOOR, 80.0));
        assertFalse(OtherModConfigCompute.shouldPushNumber(80.0, PushDirection.FLOOR, 80.0));
        assertFalse(OtherModConfigCompute.shouldPushNumber(100.0, PushDirection.FLOOR, 80.0));
    }

    @Test
    void setPushesWheneverTheValueDiffersEitherDirection() {
        assertTrue(OtherModConfigCompute.shouldPushNumber(1.0, PushDirection.SET, 0.0));
        assertTrue(OtherModConfigCompute.shouldPushNumber(-1.0, PushDirection.SET, 0.0));
        assertFalse(OtherModConfigCompute.shouldPushNumber(0.0, PushDirection.SET, 0.0));
    }

    @Test
    void booleanPushOnlyFiresOnMismatch() {
        assertTrue(OtherModConfigCompute.shouldPushBoolean(true, false));
        assertFalse(OtherModConfigCompute.shouldPushBoolean(false, false));
        assertTrue(OtherModConfigCompute.shouldPushBoolean(false, true));
    }
}
