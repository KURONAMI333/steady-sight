package com.kuronami.steadysight.config;

import com.kuronami.steadysight.compute.SteadySightSettings;
import com.kuronami.steadysight.compute.StrengthPreset;

/**
 * The Forge-facing wrapper around {@link StrengthPreset} — a
 * {@code ForgeConfigSpec.EnumValue} needs an actual enum type to store.
 *
 * <p>The NeoForge cells' version of this class also implements
 * {@code TranslatableEnum} so that loader's automatic
 * {@code ConfigurationScreen} shows translated button labels.
 * <strong>Forge 1.20.1 has neither interface nor screen</strong> (verified
 * against Forge 47.2.30's sources: no {@code TranslatableEnum} anywhere), so
 * this is a plain enum with the same four constant names and the same
 * mapping. The {@code steady_sight.configuration.strength.*} translation keys
 * stay in {@code assets/} — nothing on this loader reads them, which keeps the
 * language files byte-identical across every cell.
 *
 * <p>{@link StrengthPreset} itself stays free of Minecraft/Forge types (see
 * its Javadoc) specifically so it — and the preset→settings mapping it owns —
 * can be exercised directly by JUnit.
 */
public enum Strength {
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
}
