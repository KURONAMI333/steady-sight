package com.kuronami.steadysight.compute;

/**
 * Whether a single tick's vertical movement was a "step up" (Minecraft's
 * automatic-step-up collision resolution silently snapping the player's Y
 * position by up to the step-height attribute, default 0.6 blocks — stairs,
 * slabs, and other one-block ledges) or a minecart's full-block track jump
 * (SPIKE_PASSIVE_MOTION_2026-07.md §6, GAP_LOG G70: {@code
 * AbstractMinecart#moveAlongTrack} re-snaps the cart to its current rail
 * block's integer Y every tick, so crossing onto an ascending/descending
 * rail segment moves it exactly one block in a single tick, in either
 * direction), and how much of either snap's camera offset should still be
 * showing at some point after it happened.
 *
 * <p>DESIGN_COMPILE.md's non-negotiable for this feature (2026-07-30 scope
 * addition): "カメラのYオフセットだけを補間する（プレイヤーの実座標は触ら
 * ない）" — this class only ever computes a number of blocks to visually
 * offset the render camera by. It knows nothing about the player's actual
 * position, physics, or collision, and nothing in this package or the
 * adapter that calls it ever writes back to those.
 *
 * <p>Pure functions, no Minecraft types (same reason as every other class in
 * this package: JUnit-testable without the game). Unlike {@link
 * VignetteStrength} and the deleted horizon-line math, this feature is
 * explicitly allowed its own time constant (DESIGN_COMPILE.md: "補間の減衰
 * は、この機能に限っては時間定数を持ってよい") — smoothing a single
 * discontinuous event back to zero over a few ticks is the entire point,
 * whereas the vignette's "no self-owned time constant" rule existed because
 * that feature was supposed to track the *current* input state continuously,
 * not recover from one specific past event.
 */
public final class StepSmoothing {

    private StepSmoothing() {}

    /**
     * Below this many blocks, a Y delta is ground jitter (slope collision
     * roundoff, boat/piston nudges, etc.), not a deliberate step.
     */
    public static final float MIN_STEP_DELTA = 0.05f;

    /**
     * Above this many blocks, a same-tick Y increase is not an ordinary
     * step — vanilla's default step-height attribute is 0.6; this leaves a
     * small margin above that and still safely excludes jump arcs, which
     * gain around 0.42 blocks/tick of upward velocity and take several
     * ticks (with {@code onGround() == false} throughout) to register any
     * net Y change at all, let alone one this large in a single tick.
     */
    public static final float MAX_STEP_DELTA = 0.65f;

    /**
     * Whether this tick's Y increase looks like an ordinary automatic
     * step-up rather than a jump, a fall landing, or some other movement.
     *
     * <p>Requiring the player to have been on the ground both immediately
     * before <em>and</em> after this tick is what tells a step apart from a
     * jump: a jump reads {@code onGround() == false} for several ticks after
     * takeoff (gravity takes a moment to bring the entity back down), while
     * vanilla's step-up collision resolution never leaves the ground at all
     * — the whole mechanism is a same-tick position snap specifically so the
     * entity doesn't have to jump. A fall landing is excluded by the
     * "grounded before" half of the check (a fall starts airborne) and, even
     * on the rare landing that nets a small positive delta after bouncing
     * off collision geometry, the delta is measured across the <em>whole</em>
     * fall, so it is essentially never confined to {@link #MAX_STEP_DELTA}
     * of a single ledge.
     *
     * @param deltaY difference between this tick's Y and the previous tick's Y
     *     (positive = moved up)
     * @param onGroundBefore whether the entity was on the ground at the end of
     *     the previous tick
     * @param onGroundAfter whether the entity is on the ground at the end of
     *     this tick
     */
    public static boolean isStepUp(double deltaY, boolean onGroundBefore, boolean onGroundAfter) {
        if (!onGroundBefore || !onGroundAfter) {
            return false;
        }
        return deltaY >= MIN_STEP_DELTA && deltaY <= MAX_STEP_DELTA;
    }

    /**
     * A rail-track Y snap is exactly one block in either direction (see this
     * class's Javadoc for why) — this window is centered on {@code 1.0} with
     * a little slack for float accumulation over a long ride, not tuned
     * against a physical attribute the way {@link #MAX_STEP_DELTA} is.
     */
    public static final float MIN_VEHICLE_JUMP_DELTA = 0.9f;

    /** @see #MIN_VEHICLE_JUMP_DELTA */
    public static final float MAX_VEHICLE_JUMP_DELTA = 1.1f;

    /**
     * Whether this tick's Y change looks like a minecart's rail-track snap
     * rather than ordinary falling.
     *
     * <p>There is no ground-based test available here the way {@link
     * #isStepUp} has one — a mounted rider's {@code onGround()} does not
     * meaningfully track the vehicle's state (riding bypasses the rider's own
     * movement/collision resolution entirely; {@code Entity#rideTick} just
     * zeroes the rider's own velocity and copies the vehicle's position onto
     * it every tick — decompiled 1.21.1 {@code Entity.java}, confirmed, not
     * assumed). {@code onRails} — the caller passes {@code
     * AbstractMinecart#isOnRails()}, a public accessor for a field vanilla
     * sets immediately before deciding whether to run {@code
     * moveAlongTrack()} that particular tick — takes over that role instead:
     * it is {@code true} exactly when this tick's Y math came from the
     * track-snap code path, and {@code false} while genuinely airborne (a
     * derailed, falling cart), where a same-tick delta of around one block
     * can also occur several ticks into the fall but is <em>not</em> the
     * one-time discontinuity this feature exists to smooth — it is one
     * sample of an ordinary, continuously-accelerating fall that the normal
     * camera interpolation already renders correctly.
     *
     * <p>Both directions are in scope on purpose (unlike {@link #isStepUp},
     * which is one-directional because an ordinary downward step is not a
     * snap — gravity already moves the player down smoothly). A minecart's
     * track-snap is symmetric: entering a descending rail segment snaps the
     * cart down by exactly one block in a single tick, the same as entering
     * an ascending one snaps it up. Smoothing only the climb and leaving the
     * descent jarring would defeat the point.
     *
     * @param deltaY difference between this tick's Y and the previous tick's
     *     Y (either sign)
     * @param onRails whether the vehicle is on a rail this tick ({@code
     *     AbstractMinecart#isOnRails()})
     */
    public static boolean isVehicleTrackJump(double deltaY, boolean onRails) {
        if (!onRails) {
            return false;
        }
        double magnitude = Math.abs(deltaY);
        return magnitude >= MIN_VEHICLE_JUMP_DELTA && magnitude <= MAX_VEHICLE_JUMP_DELTA;
    }

    /**
     * How much of a step's initial camera offset is still left, given how
     * many ticks (fractional — callers interpolate with partial tick) have
     * elapsed since the step was detected.
     *
     * <p>Exponential decay: {@code remaining = initialOffsetBlocks *
     * e^(-decayPerTick * elapsedTicks)}. This never reaches exactly zero, so
     * results smaller than a thousandth of a block are snapped to exactly
     * {@code 0.0f} — a fraction of a millimeter of camera drift serves no
     * purpose and would otherwise linger forever without ever quite
     * disappearing.
     *
     * @param elapsedTicks ticks since the step was detected, as a continuous
     *     value (integer tick count plus the current frame's partial tick);
     *     must be {@code >= 0}
     * @param initialOffsetBlocks the event's delta Y at the moment it was
     *     detected (what the camera should visually "owe" at {@code
     *     elapsedTicks == 0}) — either sign works unchanged (multiplying a
     *     negative value by a positive, decaying exponential factor still
     *     shrinks its magnitude toward zero without flipping its sign),
     *     which is what a minecart's downhill track snap (GAP_LOG G70) needs
     * @param decayPerTick the decay rate; must be {@code > 0} for actual
     *     decay to happen (a non-positive value returns {@code
     *     initialOffsetBlocks} unchanged rather than dividing by zero or
     *     growing without bound)
     */
    public static float remainingOffset(float elapsedTicks, float initialOffsetBlocks, float decayPerTick) {
        if (initialOffsetBlocks == 0.0f) {
            return 0.0f;
        }
        if (!(elapsedTicks > 0.0f)) {
            return initialOffsetBlocks;
        }
        if (!(decayPerTick > 0.0f)) {
            return initialOffsetBlocks;
        }
        float remaining = (float) (initialOffsetBlocks * Math.exp(-decayPerTick * elapsedTicks));
        return Math.abs(remaining) < 0.001f ? 0.0f : remaining;
    }
}
