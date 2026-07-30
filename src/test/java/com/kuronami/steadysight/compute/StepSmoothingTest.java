package com.kuronami.steadysight.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StepSmoothingTest {

    // --- isStepUp: the jump/step/fall discrimination ---

    @Test
    void ordinaryStepIsDetected() {
        assertTrue(StepSmoothing.isStepUp(0.6, true, true), "a typical single-block step, grounded throughout");
    }

    @Test
    void tinyDeltaWhileGroundedIsNotAStep() {
        assertFalse(StepSmoothing.isStepUp(0.01, true, true), "sub-threshold jitter should not read as a step");
    }

    @Test
    void deltaAboveMaxStepHeightIsNotAStep() {
        assertFalse(StepSmoothing.isStepUp(0.9, true, true), "bigger than any ordinary step height");
    }

    @Test
    void jumpTakeoffIsNotAStepEvenWithPlausibleDelta() {
        // A jump's very first tick can show a positive delta similar in size to a
        // step, but the entity has already left the ground.
        assertFalse(StepSmoothing.isStepUp(0.42, true, false), "airborne after this tick means it's a jump, not a step");
    }

    @Test
    void wasAlreadyAirborneIsNotAStep() {
        // Mid-air (e.g. still ascending from an earlier jump) should never register,
        // regardless of this tick's onGround result.
        assertFalse(StepSmoothing.isStepUp(0.3, false, true), "wasn't grounded beforehand, so this isn't an ordinary walk-up-a-ledge step");
        assertFalse(StepSmoothing.isStepUp(0.3, false, false), "airborne both before and after");
    }

    @Test
    void negativeDeltaIsNeverAStep() {
        assertFalse(StepSmoothing.isStepUp(-0.3, true, true), "moving down is never a step up");
    }

    @Test
    void boundaryValuesAreInclusive() {
        assertTrue(StepSmoothing.isStepUp(StepSmoothing.MIN_STEP_DELTA, true, true));
        assertTrue(StepSmoothing.isStepUp(StepSmoothing.MAX_STEP_DELTA, true, true));
    }

    @Test
    void justOutsideBoundariesIsExcluded() {
        assertFalse(StepSmoothing.isStepUp(StepSmoothing.MIN_STEP_DELTA - 0.001f, true, true));
        assertFalse(StepSmoothing.isStepUp(StepSmoothing.MAX_STEP_DELTA + 0.001f, true, true));
    }

    // --- isVehicleTrackJump: the minecart rail-snap discrimination (GAP_LOG G70) ---

    @Test
    void ascendingTrackSnapIsDetected() {
        assertTrue(StepSmoothing.isVehicleTrackJump(1.0, true), "a typical ascending rail transition, on rails throughout");
    }

    @Test
    void descendingTrackSnapIsDetected() {
        assertTrue(StepSmoothing.isVehicleTrackJump(-1.0, true), "descending must be detected too — the snap is symmetric");
    }

    @Test
    void offRailsNeverCountsRegardlessOfMagnitude() {
        assertFalse(StepSmoothing.isVehicleTrackJump(1.0, false), "not on rails: this is ordinary falling, not a track snap");
        assertFalse(StepSmoothing.isVehicleTrackJump(-1.0, false));
    }

    @Test
    void tooSmallOnRailsIsNotAJump() {
        assertFalse(StepSmoothing.isVehicleTrackJump(0.2, true), "smaller than a real one-block track snap");
    }

    @Test
    void tooLargeOnRailsIsNotAJump() {
        assertFalse(StepSmoothing.isVehicleTrackJump(3.0, true), "far bigger than a single-block snap — e.g. multiple falling ticks accumulated");
    }

    @Test
    void vehicleAndStepWindowsDoNotOverlap() {
        // Whatever value StepCameraTracker checks isStepUp with first, the two
        // windows should never both fire for the same delta — that would make the
        // choice of which path detected it (and therefore which decay rate applies)
        // ambiguous based on evaluation order rather than a real distinction.
        assertTrue(StepSmoothing.MAX_STEP_DELTA < StepSmoothing.MIN_VEHICLE_JUMP_DELTA,
                "step and vehicle-jump magnitude windows must not overlap");
    }

    @Test
    void vehicleBoundaryValuesAreInclusive() {
        assertTrue(StepSmoothing.isVehicleTrackJump(StepSmoothing.MIN_VEHICLE_JUMP_DELTA, true));
        assertTrue(StepSmoothing.isVehicleTrackJump(StepSmoothing.MAX_VEHICLE_JUMP_DELTA, true));
        assertTrue(StepSmoothing.isVehicleTrackJump(-StepSmoothing.MIN_VEHICLE_JUMP_DELTA, true));
        assertTrue(StepSmoothing.isVehicleTrackJump(-StepSmoothing.MAX_VEHICLE_JUMP_DELTA, true));
    }

    @Test
    void vehicleJustOutsideBoundariesIsExcluded() {
        assertFalse(StepSmoothing.isVehicleTrackJump(StepSmoothing.MIN_VEHICLE_JUMP_DELTA - 0.01f, true));
        assertFalse(StepSmoothing.isVehicleTrackJump(StepSmoothing.MAX_VEHICLE_JUMP_DELTA + 0.01f, true));
    }

    // --- remainingOffset: the decay curve ---

    @Test
    void atElapsedZeroTheFullOffsetIsOwed() {
        assertEquals(0.6f, StepSmoothing.remainingOffset(0.0f, 0.6f, 0.5f), 0.0001f);
    }

    @Test
    void negativeOrZeroElapsedAlsoReturnsTheFullOffset() {
        // Guards the frame right at detection (elapsed can be exactly 0, or the
        // caller passes a stale/negative value) from ever amplifying the offset.
        assertEquals(0.6f, StepSmoothing.remainingOffset(-1.0f, 0.6f, 0.5f), 0.0001f);
    }

    @Test
    void offsetDecaysMonotonically() {
        float initial = 0.6f;
        float decay = 0.5f;
        float last = initial;
        for (float t = 0.5f; t <= 20.0f; t += 0.5f) {
            float now = StepSmoothing.remainingOffset(t, initial, decay);
            assertTrue(now <= last, "remaining offset should never increase as elapsed ticks grow: t=" + t);
            assertTrue(now >= 0.0f, "remaining offset should never go negative: t=" + t);
            last = now;
        }
    }

    @Test
    void negativeInitialOffsetDecaysTowardZeroFromBelow() {
        // GAP_LOG G70: a minecart descending a track snaps down (negative delta),
        // and StepCameraTracker feeds that signed delta straight in as
        // initialOffsetBlocks. The magnitude should shrink toward zero the same
        // way the positive (step-up) case does, without ever crossing over to
        // positive on the way there.
        float initial = -1.0f;
        float decay = 0.3f;
        float last = initial;
        for (float t = 0.5f; t <= 20.0f; t += 0.5f) {
            float now = StepSmoothing.remainingOffset(t, initial, decay);
            assertTrue(now >= last, "remaining (negative) offset's magnitude should shrink, i.e. the value itself should rise toward 0: t=" + t);
            assertTrue(now <= 0.0f, "should never overshoot past zero to positive: t=" + t);
            last = now;
        }
    }

    @Test
    void offsetEventuallySnapsToExactlyZero() {
        float remaining = StepSmoothing.remainingOffset(1000.0f, 0.6f, 0.5f);
        assertEquals(0.0f, remaining, "far enough out, the exponential tail should be snapped to exactly zero, not an infinitesimal float");
    }

    @Test
    void zeroInitialOffsetIsAlwaysZero() {
        assertEquals(0.0f, StepSmoothing.remainingOffset(5.0f, 0.0f, 0.5f));
        assertEquals(0.0f, StepSmoothing.remainingOffset(0.0f, 0.0f, 0.5f));
    }

    @Test
    void nonPositiveDecayRateLeavesOffsetUnchangedRatherThanMisbehaving() {
        assertEquals(0.6f, StepSmoothing.remainingOffset(5.0f, 0.6f, 0.0f), 0.0001f);
        assertEquals(0.6f, StepSmoothing.remainingOffset(5.0f, 0.6f, -1.0f), 0.0001f);
    }

    @Test
    void sameArgumentsAlwaysProduceTheSameResult() {
        float first = StepSmoothing.remainingOffset(2.5f, 0.5f, 0.4f);
        float second = StepSmoothing.remainingOffset(2.5f, 0.5f, 0.4f);
        assertEquals(first, second);
    }

    @Test
    void largerDecayRateShedsTheOffsetFaster() {
        float slow = StepSmoothing.remainingOffset(2.0f, 0.6f, 0.3f);
        float fast = StepSmoothing.remainingOffset(2.0f, 0.6f, 1.0f);
        assertTrue(fast < slow, "a bigger decay-per-tick should leave less offset remaining at the same elapsed time");
    }
}
