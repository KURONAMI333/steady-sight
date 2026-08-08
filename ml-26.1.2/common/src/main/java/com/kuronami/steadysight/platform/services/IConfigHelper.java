package com.kuronami.steadysight.platform.services;

import com.kuronami.steadysight.compute.SteadySightSettings;

/**
 * The four settings the loader-neutral code reads, and the only seam through
 * which it ever touches a config system.
 *
 * <p>NeoForge backs this with the {@code ModConfigSpec} the mod already
 * shipped (same keys, same defaults, same enum, same translation keys — an
 * existing {@code steady_sight-client.toml} keeps working, and the player
 * keeps NeoForge's free {@code ConfigurationScreen}). Fabric returns fixed
 * defaults: Fabric loader has no config mechanism and no automatic config
 * screen, and the product decision (PLAN_STEADYSIGHT_MATRIX §6) is that the
 * defaults are the product, so the Fabric cell ships no config file and no
 * config screen.
 */
public interface IConfigHelper {

    /** The vignette preset, already resolved to the plain compute-layer record. */
    SteadySightSettings vignetteSettings();

    boolean optimizeVanillaSettings();

    boolean smoothStepCamera();

    boolean optimizeOtherMods();
}
