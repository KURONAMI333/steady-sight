package com.kuronami.steadysight.compute;

/**
 * Pure yes/no logic behind whether {@code client.OtherModSettingsOptimizer}
 * should overwrite a single setting in another installed mod's own config
 * file (HH1/HH2 of the task this shipped under). No Minecraft, NeoForge,
 * Gson, or NightConfig types here on purpose (DESIGN_COMPILE.md §6's
 * compute/render split, the same reason {@link SettingsMarker} and {@link
 * StepSmoothing} are MC-type-zero) — the only thing that needs the actual
 * file on disk is reading the current value and writing the new one, both
 * of which live in the client-package adapter.
 *
 * <p>Deliberately two entry points instead of one generic numeric one: a
 * boolean setting (Punchy's {@code enableFreezeShake}) has no "distance"
 * from its target to compare, only "is it already the target value or
 * not" — collapsing that into a 0.0/1.0 numeric comparison would work
 * arithmetically but would hide the fact that {@link PushDirection#SET} is
 * the only direction that ever makes sense for it.
 */
public final class OtherModConfigCompute {

    private OtherModConfigCompute() {}

    /**
     * Whether {@code currentValue} should be overwritten with {@code
     * targetValue}, per {@code direction}.
     *
     * @param currentValue the value currently in the target mod's config file
     * @param direction how the value should move toward {@code targetValue}
     * @param targetValue the comfort-friendly value from the settings table
     */
    public static boolean shouldPushNumber(double currentValue, PushDirection direction, double targetValue) {
        return switch (direction) {
            case CEIL -> currentValue > targetValue;
            case FLOOR -> currentValue < targetValue;
            case SET -> Double.compare(currentValue, targetValue) != 0;
        };
    }

    /** The boolean-valued equivalent of {@link #shouldPushNumber} — always {@link PushDirection#SET}. */
    public static boolean shouldPushBoolean(boolean currentValue, boolean targetValue) {
        return currentValue != targetValue;
    }
}
