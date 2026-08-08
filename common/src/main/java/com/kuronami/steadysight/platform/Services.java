package com.kuronami.steadysight.platform;

import com.kuronami.steadysight.platform.services.IConfigHelper;
import com.kuronami.steadysight.platform.services.IPlatformHelper;
import java.util.ServiceLoader;

/**
 * Java's {@link ServiceLoader} is how the loader-neutral code reaches the two
 * things that genuinely differ per loader: where the config directory is, and
 * where the four setting values come from. Each loader cell declares its
 * implementation in {@code META-INF/services/<interface FQCN>}.
 *
 * <p>Both fields are resolved once, at class-initialization time, and never
 * from a render or tick path — {@code SteadySightOverlay#render} runs every
 * frame and {@code CameraMixin} runs inside {@code Camera#setup}, neither of
 * which should be doing service discovery.
 */
public final class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    public static final IConfigHelper CONFIG = load(IConfigHelper.class);

    private Services() {}

    private static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Steady Sight: no implementation of " + clazz.getName() + " found on this loader"));
    }
}
