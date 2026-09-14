package com.kuronami.steadysight.platform;

import com.kuronami.steadysight.compute.SteadySightSettings;
import com.kuronami.steadysight.compute.StrengthPreset;
import com.kuronami.steadysight.platform.services.IConfigHelper;

/**
 * Fixed defaults, no config file and no config screen (the established design decision,
 * PLAN_STEADYSIGHT_MATRIX §6: "設定画面とか要らんだろ　俺がいい感じで調整してる
 * しな" — the defaults are the product, and Fabric loader has neither a config
 * mechanism nor an automatic screen to expose them through).
 *
 * <p>The values are <em>read</em> from the compute layer rather than written
 * out here, which is what keeps them mechanically identical to the NeoForge
 * cell's defaults: both sides resolve the same
 * {@link StrengthPreset#STANDARD} constant, so a future change to the preset
 * cannot silently desynchronise the two loaders. The three booleans match
 * {@code SteadySightConfig}'s {@code define(..., true)} defaults, which is the
 * one place a number could drift — there is nothing on the compute side to
 * read them from, and inventing a holder for three literal {@code true}s would
 * add a layer without adding a guarantee.
 */
public final class FabricConfigHelper implements IConfigHelper {

    @Override
    public SteadySightSettings vignetteSettings() {
        return StrengthPreset.STANDARD.toSettings();
    }

    @Override
    public boolean optimizeVanillaSettings() {
        return true;
    }

    @Override
    public boolean smoothStepCamera() {
        return true;
    }

    @Override
    public boolean optimizeOtherMods() {
        return true;
    }
}
