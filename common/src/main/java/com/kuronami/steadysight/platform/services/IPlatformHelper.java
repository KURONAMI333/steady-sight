package com.kuronami.steadysight.platform.services;

import java.nio.file.Path;

/**
 * The instance's config directory. Both one-time pushes
 * ({@code VanillaComfortSettings}, {@code OtherModSettingsOptimizer}) resolve
 * their marker files and the other mods' config files against it, and the two
 * loaders name it differently ({@code FMLPaths.CONFIGDIR} vs.
 * {@code FabricLoader#getConfigDir}) while pointing at the same
 * {@code <instance>/config} folder.
 */
public interface IPlatformHelper {

    Path getConfigDir();
}
