package com.kuronami.steadysight;

import com.kuronami.steadysight.config.SteadySightConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Steady Sight — entry point.
 *
 * <p>Client-side only, by design (see DESIGN_COMPILE.md's non-negotiables):
 * one HUD layer plus a config screen, no server-side code at all. The
 * config-screen registration below is guarded by {@code FMLEnvironment}'s
 * dist check even though the mod as a whole is declared client-only in
 * neoforge.mods.toml, because that toml declaration affects mod-list
 * filtering, not whether this constructor runs — referencing
 * {@link ConfigurationScreen} unconditionally here would still reach for a
 * client-only class.
 */
@Mod(SteadySight.MODID)
public final class SteadySight {
    public static final String MODID = "steady_sight";

    public SteadySight(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SteadySightConfig.SPEC);
        if (FMLEnvironment.dist.isClient()) {
            container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }
    }
}
