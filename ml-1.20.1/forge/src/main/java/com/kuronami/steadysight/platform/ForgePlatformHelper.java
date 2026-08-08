package com.kuronami.steadysight.platform;

import com.kuronami.steadysight.platform.services.IPlatformHelper;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
