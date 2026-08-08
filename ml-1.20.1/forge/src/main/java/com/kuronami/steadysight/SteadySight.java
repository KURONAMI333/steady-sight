package com.kuronami.steadysight;

import com.kuronami.steadysight.config.SteadySightConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Steady Sight — entry point (Forge 1.20.1).
 *
 * <p>Client-side only, by design (see DESIGN_COMPILE.md's non-negotiables):
 * one HUD overlay, no server-side code at all.
 *
 * <p><strong>Two differences from the NeoForge cells</strong>, both forced by
 * the loader and both recorded as feature differences rather than worked
 * around (PORTING_CHECKLIST §9):
 *
 * <ul>
 *   <li>Forge 1.20.1's {@code @Mod} annotation takes only the mod id, and the
 *       mod class is constructed with no arguments — there is no
 *       {@code ModContainer} handed in, so the config is registered through
 *       {@link ModLoadingContext#get()} instead.</li>
 *   <li>There is no {@code ConfigurationScreen} equivalent on Forge 1.20.1
 *       (NeoForge added the automatic screen; Forge never had one), and
 *       hand-writing a {@code Screen} for it is out of scope by decision. The
 *       config <em>file</em> is still generated and read — only the in-game
 *       editor is absent.</li>
 * </ul>
 */
@Mod(SteadySight.MODID)
public final class SteadySight {
    public static final String MODID = Constants.MOD_ID;

    public SteadySight() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SteadySightConfig.SPEC);
    }
}
