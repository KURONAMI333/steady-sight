package com.kuronami.steadysight.config;

import com.kuronami.steadysight.SteadySight;
import com.kuronami.steadysight.compute.SteadySightSettings;
import com.kuronami.steadysight.compute.StrengthPreset;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * The NeoForge-facing wrapper around {@link StrengthPreset} — a
 * {@code ModConfigSpec.EnumValue} needs an actual enum type to store, and
 * that type needs to implement {@link TranslatableEnum} for
 * {@code ConfigurationScreen} to show translated button labels instead of
 * raw constant names ({@code steady_sight.configuration.strength.<name>}).
 *
 * <p>{@link StrengthPreset} itself stays free of Minecraft/NeoForge types
 * (see its Javadoc) specifically so it — and the preset→settings mapping it
 * owns — can be exercised directly by JUnit. Confirmed empirically: the
 * test source set has no NeoForge/Minecraft classes on its classpath, so a
 * {@code TranslatableEnum} implementor cannot even be loaded from a test,
 * let alone unit-tested (a first attempt at putting {@code
 * TranslatableEnum} directly on the preset enum failed {@code
 * compileTestJava} with "class file for TranslatableEnum not found" the
 * moment a test touched one of its constants). This class is the one place
 * that split lives; it has no logic of its own beyond delegating.
 */
public enum Strength implements TranslatableEnum {
    OFF(StrengthPreset.OFF),
    SUBTLE(StrengthPreset.SUBTLE),
    STANDARD(StrengthPreset.STANDARD),
    STRONG(StrengthPreset.STRONG);

    private final StrengthPreset preset;

    Strength(StrengthPreset preset) {
        this.preset = preset;
    }

    public SteadySightSettings toSettings() {
        return preset.toSettings();
    }

    @Override
    public Component getTranslatedName() {
        return Component.translatable(SteadySight.MODID + ".configuration.strength." + name().toLowerCase(Locale.ROOT));
    }
}
