package com.kuronami.steadysight.compute;

import java.util.List;

/**
 * The fixed table of other mods' config settings this mod nudges toward
 * less camera motion, per {@code _research/SURVEY_VIEWPOINT_MODS_2026-07.md}
 * (the one-time source of truth for which keys are "confirmed" versus
 * "config key unverified" — only the confirmed rows are here).
 *
 * <p><strong>Judged setting-by-setting, not mod-by-mod.</strong> The dividing
 * line comes from a player report: added visual detail on screen does not
 * itself cause motion sickness, whereas a viewpoint that shakes, or movement
 * that disagrees with what the input asked for, does. Every entry here is a
 * setting that moves the camera
 * itself (screen shake, freeze-frame shake, a GUI-slide that feeds mouse-look
 * pitch) — nothing about particles, arm animation, or added visual detail is
 * or ever should be in this table.
 *
 * <p><strong>Deliberately not in this table</strong> (survey §2's "config
 * key unverified" rows) — left as future candidates, not implemented,
 * because the task instructions are explicit that an unverified key must not
 * be guessed at: Camera Overhaul (undocumented as-shipped key layout beyond
 * the README; also not present in the environment used for testing), Dynamic Elytra FOV,
 * Handycam, Countered's Smooth Steps (the one survey entry recommending
 * <em>enabling</em> a setting rather than capping it — it overlaps with this
 * mod's own step-camera smoothing feature and applying both at once would
 * double-correct the same jolt; see GAP_LOG for the "one or the other, not
 * both" note left for whenever this is revisited), Smooth Third Person
 * Camera, Smooth Camera Movement, PMW Storms Shake Screen, and Sable:
 * Destructive.
 *
 * <p>Sodium Extra's {@code instant_sneak} is <strong>not</strong> a row here
 * either, on purpose: its shipped default ({@code false}) already is the
 * comfort-friendly value, so there is nothing to push — the survey's point
 * in flagging it was to confirm that a "leave it alone" case falls out
 * naturally from a direction-aware table (a {@link PushDirection#CEIL} entry
 * with a false-shaped target would simply never fire against an
 * already-false value), not to add a no-op row that would just be dead
 * weight in {@link #ALL}.
 */
public final class OtherModConfigTargets {

    /** The two file formats this mod knows how to read and rewrite a single top-level key in. */
    public enum ConfigFormat {
        JSON,
        TOML
    }

    /** Whether {@link Entry#numericTarget()} or {@link Entry#booleanTarget()} is the one that matters. */
    public enum ValueType {
        NUMBER,
        BOOLEAN
    }

    /**
     * One row of the table: one setting, in one other mod's config file.
     *
     * @param markerKey stable identity for {@code SettingsMarker}'s one-time-per-key
     *     bookkeeping; never reused across rows and never renamed once shipped, since
     *     renaming would make an already-applied key look brand new
     * @param displayName the mod's name, for log messages only
     * @param configRelativePath path under the {@code config/} directory (survey §5)
     * @param format which of the two config-file formats this row's file is in
     * @param configKey the top-level key inside that file (every confirmed row in the
     *     survey is root-level; none of these config files nest the relevant key under
     *     a section)
     * @param valueType which of {@code numericTarget}/{@code booleanTarget} applies
     * @param direction how the current value should move toward the target
     * @param numericTarget the comfort-friendly value, used when {@code valueType == NUMBER}
     * @param booleanTarget the comfort-friendly value, used when {@code valueType == BOOLEAN}
     * @param rationale why this value and not a more extreme one — surfaced in code only
     *     (GAP_LOG carries the durable record; this is here so the reasoning travels with
     *     the row instead of living solely in a commit message)
     */
    public record Entry(
            String markerKey,
            String displayName,
            String configRelativePath,
            ConfigFormat format,
            String configKey,
            ValueType valueType,
            PushDirection direction,
            double numericTarget,
            boolean booleanTarget,
            String rationale) {}

    /**
     * The confirmed table, in application order. Every {@code markerKey} here must be
     * unique — {@link OtherModConfigTargetsTest} pins that.
     */
    public static final List<Entry> ALL = List.of(
            new Entry(
                    "blueprint.screenShakeScale",
                    "Blueprint",
                    "blueprint-client.toml",
                    ConfigFormat.TOML,
                    "screenShakeScale",
                    ValueType.NUMBER,
                    PushDirection.CEIL,
                    0.3,
                    false,
                    "Blueprint is a shared library many other mods draw their own shake effects "
                            + "from (boss attacks, impacts). Zeroing it entirely would erase a signal that "
                            + "something happened, not just the discomfort; 0.3 keeps that legible while "
                            + "capping the amplitude."),
            new Entry(
                    "blueprint.maxScreenShakers",
                    "Blueprint",
                    "blueprint-client.toml",
                    ConfigFormat.TOML,
                    "maxScreenShakers",
                    ValueType.NUMBER,
                    PushDirection.CEIL,
                    0.0,
                    false,
                    "Caps how many independent shake sources can stack concurrently. Paired with "
                            + "screenShakeScale above so several small shakers layering at once can't "
                            + "reintroduce the amplitude that scale alone caps per-shaker."),
            new Entry(
                    "punchy.enableFreezeShake",
                    "Punchy",
                    "punchy/punchy_config.json",
                    ConfigFormat.JSON,
                    "enableFreezeShake",
                    ValueType.BOOLEAN,
                    PushDirection.SET,
                    0.0,
                    false,
                    "Punchy's arm physics, animation, and hit-flinch are untouched — none of those move "
                            + "the Camera class. Only the freeze-frame screen shake on a hit is a camera-motion "
                            + "effect, so it is the only Punchy setting in this table."),
            new Entry(
                    "smoothgui.animationScale",
                    "SmoothGUI",
                    "smoothgui.json",
                    ConfigFormat.JSON,
                    "animationScale",
                    ValueType.NUMBER,
                    PushDirection.CEIL,
                    0.0,
                    false,
                    "Only the GUI-open slide distance (which SmoothGUI adds directly onto mouse-look "
                            + "pitch) is a camera-motion effect; the background-blur toggle is a separate "
                            + "config field and is left alone."));

    /** {@code ALL}'s marker keys, in the same order — what {@code SettingsMarker} is given as {@code allKeys}. */
    public static final List<String> ALL_KEYS = ALL.stream().map(Entry::markerKey).toList();

    private OtherModConfigTargets() {}
}
