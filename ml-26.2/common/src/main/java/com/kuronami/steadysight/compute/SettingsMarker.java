package com.kuronami.steadysight.compute;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure logic behind {@code client.VanillaComfortSettings}'s one-time-per-key
 * vanilla settings push (GAP_LOG G77-G78, added 2026-07-31 alongside the FOV
 * entry). No Minecraft or NeoForge types here on purpose (DESIGN_COMPILE.md
 * §6's compute/render split) — this is the part of the marker-file
 * versioning problem that doesn't need the game running to verify, and per
 * an independent review of this mod's own code (2026-07-31) it was exactly
 * the part left unverified: the file I/O around it is the only part that
 * genuinely can't be JUnit-tested (client types, same constraint noted for
 * {@code config.Strength} in G25/G26).
 *
 * <p>The marker file historically (v0.1) held one unstructured sentinel —
 * {@value #LEGACY_MARKER_CONTENT} — meaning "the five original settings are
 * done, nothing more specific than that." Adding FOV as a sixth setting
 * needed the marker to say <em>which</em> keys are done, not just whether
 * the file exists, so existing (v0.1) players get FOV pushed exactly once
 * without their other five settings — which they may have deliberately
 * changed back since — being re-touched.
 */
public final class SettingsMarker {

    /** The entire content of a v0.1 marker file — presence-only, no key list. */
    public static final String LEGACY_MARKER_CONTENT = "applied";

    private SettingsMarker() {}

    /**
     * The keys from {@code allKeys} that still need to be applied, in
     * {@code allKeys}' order.
     *
     * @param markerContent raw marker file content, or {@code null} if no
     *     marker file exists yet (a brand-new install: nothing applied).
     * @param allKeys every setting key this mod ever pushes, in application
     *     order.
     * @param legacyKeys the keys a pre-existing {@value #LEGACY_MARKER_CONTENT}
     *     marker is known to already have applied.
     */
    public static List<String> pendingKeys(String markerContent, List<String> allKeys, Set<String> legacyKeys) {
        Set<String> applied = appliedKeys(markerContent, allKeys, legacyKeys);
        return allKeys.stream().filter(key -> !applied.contains(key)).toList();
    }

    /**
     * The keys {@code markerContent} records as already applied.
     *
     * <p>Three recognized shapes, in priority order: {@code null} (no marker
     * file — nothing applied yet); the exact legacy sentinel (the fixed
     * {@code legacyKeys} set); anything else is parsed as a newline-separated
     * key list. A parsed list that shares <em>no</em> key at all with
     * {@code allKeys} is treated the same as an unreadable/corrupted marker
     * (see the class Javadoc) rather than as "nothing applied" — a marker
     * file existing at all means some earlier version of this mod already
     * ran here, and the safe assumption when that history can't be read is
     * "already handled," not "start over": re-applying settings the player
     * may have deliberately reverted is the exact failure mode this
     * mechanism exists to prevent, and it is a strictly worse outcome than
     * skipping a genuinely-new setting for one extra launch.
     */
    public static Set<String> appliedKeys(String markerContent, List<String> allKeys, Set<String> legacyKeys) {
        if (markerContent == null) {
            return Set.of();
        }
        String trimmed = markerContent.strip();
        if (trimmed.equals(LEGACY_MARKER_CONTENT)) {
            return Set.copyOf(legacyKeys);
        }

        Set<String> parsed = new LinkedHashSet<>();
        for (String line : trimmed.split("\n")) {
            String key = line.strip();
            if (!key.isEmpty()) {
                parsed.add(key);
            }
        }

        boolean sharesAnyKnownKey = allKeys.stream().anyMatch(parsed::contains);
        if (!sharesAnyKnownKey) {
            return Set.copyOf(allKeys);
        }
        return parsed;
    }
}
