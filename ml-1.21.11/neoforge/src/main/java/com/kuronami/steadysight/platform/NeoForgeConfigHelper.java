package com.kuronami.steadysight.platform;

import com.kuronami.steadysight.compute.SteadySightSettings;
import com.kuronami.steadysight.config.SteadySightConfig;
import com.kuronami.steadysight.platform.services.IConfigHelper;

/**
 * Delegation only. {@link SteadySightConfig} — the {@code ModConfigSpec} with
 * the keys, defaults, enum and translation keys v0.2.0 shipped — is untouched
 * by the multiloader split, so an existing {@code steady_sight-client.toml}
 * and the {@code ConfigurationScreen} built from that spec behave exactly as
 * before; this class only exposes it through the loader-neutral interface.
 */
public final class NeoForgeConfigHelper implements IConfigHelper {

    @Override
    public SteadySightSettings vignetteSettings() {
        return SteadySightConfig.snapshot();
    }

    @Override
    public boolean optimizeVanillaSettings() {
        return SteadySightConfig.optimizeVanillaSettings();
    }

    @Override
    public boolean smoothStepCamera() {
        return SteadySightConfig.smoothStepCamera();
    }

    @Override
    public boolean optimizeOtherMods() {
        return SteadySightConfig.optimizeOtherMods();
    }
}
