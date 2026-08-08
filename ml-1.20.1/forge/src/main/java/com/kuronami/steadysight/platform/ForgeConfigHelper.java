package com.kuronami.steadysight.platform;

import com.kuronami.steadysight.compute.SteadySightSettings;
import com.kuronami.steadysight.config.SteadySightConfig;
import com.kuronami.steadysight.platform.services.IConfigHelper;

/**
 * Delegation only. {@link SteadySightConfig} — the {@code ForgeConfigSpec}
 * whose keys, defaults, comments and translation keys match the NeoForge
 * cells' {@code ModConfigSpec} one-for-one — is the whole implementation; this
 * class only exposes it through the loader-neutral interface.
 */
public final class ForgeConfigHelper implements IConfigHelper {

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
