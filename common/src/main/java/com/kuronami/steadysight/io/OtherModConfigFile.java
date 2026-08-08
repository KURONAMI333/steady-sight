package com.kuronami.steadysight.io;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.kuronami.steadysight.compute.OtherModConfigCompute;
import com.kuronami.steadysight.compute.OtherModConfigTargets.Entry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and, if needed, rewrites a single {@link Entry}'s key inside its
 * target mod's own JSON or TOML config file.
 *
 * <p>This is deliberately its own package, separate from both {@code
 * compute} (pure functions, no file I/O at all) and {@code client} (the FML
 * event-subscription and path-resolution adapter that calls into this
 * class). Unlike {@code client.OtherModSettingsOptimizer}, this class has no
 * Minecraft or NeoForge dependency whatsoever — only Gson and NightConfig,
 * both plain Java libraries already transitively on this mod's classpath via
 * NeoForge's own {@code ModConfigSpec} machinery (confirmed present as
 * {@code com.electronwill.night-config:core:3.8.3} /
 * {@code :toml:3.8.3} and {@code com.google.gson:gson} in
 * {@code neoforge-21.1.227-userdev.jar}'s library list; {@code
 * mod-050-emc-as-you-wish} already relies on Gson being present the same
 * way, with no explicit {@code build.gradle} dependency of its own). That
 * makes this class directly JUnit-testable against real temp files, which
 * is what turns the task's "異常系のテスト: config ファイルが無い／壊れて
 * いる／キーが無い のそれぞれで、例外を投げずスキップすること" requirement
 * into a machine-checked fact ({@link
 * com.kuronami.steadysight.io.OtherModConfigFileTest}) instead of a claim
 * that can only be exercised manually via runClient — {@code
 * client.OtherModSettingsOptimizer} itself cannot be unit-tested the same
 * way because its {@code APPLIED_MARKER} static field eagerly resolves
 * {@code FMLPaths.CONFIGDIR}, which requires a running FML environment (the
 * same reason {@code VanillaComfortSettings} is untested by JUnit and only
 * its own pure helper, {@code compute.SettingsMarker}, is).
 *
 * <p>Every method here returns {@code false} rather than throwing for any
 * failure short of a caller-side {@code NullPointerException} on a null
 * argument: a missing key, a value of the wrong shape, or unparseable file
 * content are all indistinguishable from "this mod's version doesn't match
 * what the table expects" from the caller's point of view, and all three
 * should be skipped and retried on a later launch — never allowed to
 * propagate out of a client-startup event handler.
 */
public final class OtherModConfigFile {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private OtherModConfigFile() {}

    /**
     * @return {@code true} once the file was read and a decision was made
     *     for {@code entry}'s key — whether or not that decision produced an
     *     actual write; {@code false} if the file couldn't be read, its
     *     content isn't a JSON object, the key is missing, or the key's
     *     value isn't the primitive shape {@code entry.valueType()} expects
     */
    public static boolean applyJson(Path configPath, Entry entry) {
        String raw;
        try {
            raw = Files.readString(configPath);
        } catch (IOException e) {
            return false;
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(raw).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            // Not valid JSON at all, or valid JSON that isn't an object (e.g. a bare array).
            return false;
        }

        JsonElement current = root.get(entry.configKey());
        if (current == null || !current.isJsonPrimitive()) {
            return false;
        }

        // A plain switch statement, not an expression: the wrong-primitive-shape branches below
        // need to return false from the whole method (an earlier version of this used yield
        // inside a switch expression for both the shape check and the push decision, which
        // collapsed "wrong shape" and "correct shape, no push needed" into the same false/true
        // result and silently reported a shape mismatch as a handled, no-op success — caught by
        // OtherModConfigFileTest.jsonKeyOfWrongPrimitiveTypeReturnsFalseWithoutThrowing).
        boolean changed;
        switch (entry.valueType()) {
            case BOOLEAN -> {
                if (!current.getAsJsonPrimitive().isBoolean()) {
                    return false;
                }
                boolean currentValue = current.getAsBoolean();
                changed = OtherModConfigCompute.shouldPushBoolean(currentValue, entry.booleanTarget());
                if (changed) {
                    root.addProperty(entry.configKey(), entry.booleanTarget());
                }
            }
            case NUMBER -> {
                if (!current.getAsJsonPrimitive().isNumber()) {
                    return false;
                }
                double currentValue = current.getAsDouble();
                changed = OtherModConfigCompute.shouldPushNumber(
                        currentValue, entry.direction(), entry.numericTarget());
                if (changed) {
                    root.addProperty(entry.configKey(), entry.numericTarget());
                }
            }
            default -> throw new IllegalStateException("unreachable: ValueType has only BOOLEAN and NUMBER");
        }

        if (changed) {
            try {
                Files.writeString(configPath, GSON.toJson(root));
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return {@code true} once the file was read and a decision was made
     *     for {@code entry}'s key (see {@link #applyJson} for the exact
     *     meaning); {@code false} if the file couldn't be parsed as TOML,
     *     the key is missing, or the key's value isn't a plain number
     *     (every confirmed TOML entry in {@link
     *     com.kuronami.steadysight.compute.OtherModConfigTargets} is
     *     numeric — Blueprint has no boolean rows)
     */
    public static boolean applyToml(Path configPath, Entry entry) {
        // .sync() is deliberate, not a default left in place: FileConfig.of(Path) builds an
        // AsyncFileConfig, whose save() queues a background write rather than writing before
        // returning — measured directly (a save() followed immediately by re-reading the file
        // showed the old value still on disk). This mod applies each setting exactly once and
        // then never touches the file again, so there is no reason to pay for async batching;
        // a synchronous, deterministic write here is what makes the "config was pushed" log
        // message (and the marker recording this key as handled) actually true by the time
        // either of them happens.
        try (FileConfig config =
                FileConfig.builder(configPath, TomlFormat.instance()).sync().build()) {
            config.load();
            Object current = config.get(entry.configKey());
            if (!(current instanceof Number currentNumber)) {
                return false;
            }

            double currentValue = currentNumber.doubleValue();
            if (!OtherModConfigCompute.shouldPushNumber(currentValue, entry.direction(), entry.numericTarget())) {
                return true; // read and decided: already on the comfort-friendly side
            }

            config.set(entry.configKey(), matchNumericType(currentNumber, entry.numericTarget()));
            config.save();
            return true;
        } catch (Exception e) {
            // NightConfig throws its own unchecked parsing/IO exceptions for malformed TOML,
            // an unreadable file, or an I/O failure on save; all are equally "couldn't apply
            // this launch, try again later" from this method's contract.
            return false;
        }
    }

    /**
     * Writes the target back using the same Java number type NightConfig
     * already reported for the current value, so an integer-backed TOML key
     * (Blueprint's {@code maxScreenShakers}, an {@code IntValue} under the
     * hood) doesn't turn into a float literal on disk.
     */
    private static Object matchNumericType(Number original, double target) {
        if (original instanceof Integer) {
            return (int) Math.round(target);
        }
        if (original instanceof Long) {
            return Math.round(target);
        }
        if (original instanceof Float) {
            return (float) target;
        }
        return target;
    }
}
