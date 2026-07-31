package com.kuronami.steadysight.compute;

/**
 * How a single setting in another mod's config should move toward its
 * comfort-friendly value (task instructions HH1: "適用条件は「無条件セット」
 * ではなく、FOVで採った方向つきの形を基本にする").
 *
 * <p>{@link #CEIL} and {@link #FLOOR} are one-directional pushes — they only
 * ever move a value toward the target when the current value is on the
 * wrong side of it, and never touch a value that is already at or past the
 * comfort-friendly side. This is what lets the mechanism coexist with a
 * player who already tuned a setting more aggressively than this mod would:
 * {@link #CEIL} never raises a value the player lowered further, {@link
 * #FLOOR} never lowers a value the player raised further. Both are the same
 * shape {@code client.VanillaComfortSettings} already uses for its own FOV
 * push (a floor: raise-only, never lower a wider FOV back down).
 *
 * <p>{@link #SET} is for values with no meaningful "distance" to compare —
 * Punchy's {@code enableFreezeShake} is a boolean, not a number on a
 * comfort/discomfort axis, so the only sensible target is an exact value.
 */
public enum PushDirection {
    /** Push down to the target only if the current value is above it. */
    CEIL,
    /** Push up to the target only if the current value is below it. */
    FLOOR,
    /** Push to the exact target value whenever the current value differs from it. */
    SET
}
