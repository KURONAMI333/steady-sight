package com.kuronami.steadysight;

/**
 * The one identifier the loader-neutral half of the mod needs. Kept as a
 * compile-time constant so the NeoForge cell can use it directly in
 * {@code @Mod(...)}, and so the two marker filenames and the asset namespace
 * it composes are byte-identical to the single-source NeoForge repo this was
 * split out of — an existing install's {@code steady_sight-client.toml} and
 * its two {@code steady_sight_*.flag} files must keep resolving.
 */
public final class Constants {

    public static final String MOD_ID = "steady_sight";

    private Constants() {}
}
