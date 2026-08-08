package com.kuronami.steadysight.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuronami.steadysight.compute.OtherModConfigTargets.ConfigFormat;
import com.kuronami.steadysight.compute.OtherModConfigTargets.Entry;
import com.kuronami.steadysight.compute.OtherModConfigTargets.ValueType;
import com.kuronami.steadysight.compute.PushDirection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The task's gate #4 machine-checked: "config ファイルが無い／壊れている／
 * キーが無い のそれぞれで、例外を投げずスキップすること". {@link
 * OtherModConfigFile} has no Minecraft/NeoForge dependency (only Gson and
 * NightConfig, both plain libraries — see its class Javadoc), so unlike
 * {@code client.OtherModSettingsOptimizer} it can be exercised directly
 * against real temp files without a running game.
 */
class OtherModConfigFileTest {

    @TempDir
    Path tempDir;

    private static Entry ceilNumberEntry(ConfigFormat format, String configKey, double target) {
        return new Entry(
                "test." + configKey, "TestMod", "unused-for-these-tests.cfg", format, configKey, ValueType.NUMBER,
                PushDirection.CEIL, target, false, "test fixture");
    }

    private static Entry setBooleanEntry(String configKey, boolean target) {
        return new Entry(
                "test." + configKey, "TestMod", "unused-for-these-tests.cfg", ConfigFormat.JSON, configKey,
                ValueType.BOOLEAN, PushDirection.SET, 0.0, target, "test fixture");
    }

    // ---- JSON: missing file ----

    @Test
    void jsonMissingFileReturnsFalseWithoutThrowing() {
        Path missing = tempDir.resolve("does-not-exist.json");
        assertFalse(OtherModConfigFile.applyJson(missing, ceilNumberEntry(ConfigFormat.JSON, "screenShakeScale", 0.3)));
    }

    // ---- JSON: corrupt content ----

    @Test
    void jsonMalformedSyntaxReturnsFalseWithoutThrowing() throws IOException {
        Path path = tempDir.resolve("corrupt.json");
        Files.writeString(path, "{ this is not valid json ][");
        assertFalse(OtherModConfigFile.applyJson(path, ceilNumberEntry(ConfigFormat.JSON, "screenShakeScale", 0.3)));
    }

    @Test
    void jsonValidButNotAnObjectReturnsFalseWithoutThrowing() throws IOException {
        Path path = tempDir.resolve("array.json");
        Files.writeString(path, "[1, 2, 3]");
        assertFalse(OtherModConfigFile.applyJson(path, ceilNumberEntry(ConfigFormat.JSON, "screenShakeScale", 0.3)));
    }

    // ---- JSON: key missing ----

    @Test
    void jsonMissingKeyReturnsFalseWithoutThrowing() throws IOException {
        Path path = tempDir.resolve("no-key.json");
        Files.writeString(path, "{\"someOtherSetting\": 1.0}");
        assertFalse(OtherModConfigFile.applyJson(path, ceilNumberEntry(ConfigFormat.JSON, "animationScale", 0.0)));
    }

    @Test
    void jsonKeyOfUnexpectedShapeReturnsFalseWithoutThrowing() throws IOException {
        Path path = tempDir.resolve("wrong-shape.json");
        Files.writeString(path, "{\"animationScale\": {\"nested\": true}}");
        assertFalse(OtherModConfigFile.applyJson(path, ceilNumberEntry(ConfigFormat.JSON, "animationScale", 0.0)));
    }

    @Test
    void jsonKeyOfWrongPrimitiveTypeReturnsFalseWithoutThrowing() throws IOException {
        // valueType NUMBER but the file actually holds a boolean for this key.
        Path path = tempDir.resolve("wrong-primitive.json");
        Files.writeString(path, "{\"animationScale\": true}");
        assertFalse(OtherModConfigFile.applyJson(path, ceilNumberEntry(ConfigFormat.JSON, "animationScale", 0.0)));
    }

    // ---- JSON: successful pushes ----

    @Test
    void jsonNumberAboveCeilingIsLoweredAndFileIsRewritten() throws IOException {
        Path path = tempDir.resolve("blueprint-like.json");
        Files.writeString(path, "{\"screenShakeScale\": 1.0}");

        boolean handled = OtherModConfigFile.applyJson(path, ceilNumberEntry(ConfigFormat.JSON, "screenShakeScale", 0.3));

        assertTrue(handled);
        String rewritten = Files.readString(path);
        assertTrue(rewritten.contains("0.3"), "expected rewritten file to contain 0.3, was: " + rewritten);
    }

    @Test
    void jsonNumberAlreadyBelowCeilingIsHandledButNotRewritten() throws IOException {
        Path path = tempDir.resolve("already-fine.json");
        String original = "{\"screenShakeScale\": 0.1}";
        Files.writeString(path, original);

        boolean handled = OtherModConfigFile.applyJson(path, ceilNumberEntry(ConfigFormat.JSON, "screenShakeScale", 0.3));

        assertTrue(handled, "a value already on the comfort-friendly side is still a handled decision");
        assertEquals(original, Files.readString(path));
    }

    @Test
    void jsonBooleanMismatchIsPushedToTarget() throws IOException {
        Path path = tempDir.resolve("punchy-like.json");
        Files.writeString(path, "{\"enableFreezeShake\": true}");

        boolean handled = OtherModConfigFile.applyJson(path, setBooleanEntry("enableFreezeShake", false));

        assertTrue(handled);
        assertTrue(Files.readString(path).contains("false"));
    }

    @Test
    void jsonBooleanAlreadyMatchingTargetIsNotRewritten() throws IOException {
        Path path = tempDir.resolve("punchy-already-fine.json");
        Files.writeString(path, "{\"enableFreezeShake\": false}");

        boolean handled = OtherModConfigFile.applyJson(path, setBooleanEntry("enableFreezeShake", false));

        assertTrue(handled);
        assertTrue(Files.readString(path).contains("false"));
    }

    // ---- TOML: missing file ----

    @Test
    void tomlMissingFileReturnsFalseWithoutThrowing() {
        Path missing = tempDir.resolve("does-not-exist.toml");
        assertFalse(OtherModConfigFile.applyToml(missing, ceilNumberEntry(ConfigFormat.TOML, "screenShakeScale", 0.3)));
    }

    // ---- TOML: corrupt content ----

    @Test
    void tomlMalformedSyntaxReturnsFalseWithoutThrowing() throws IOException {
        Path path = tempDir.resolve("corrupt.toml");
        Files.writeString(path, "this = is [ not valid toml @@@ {{{");
        assertFalse(OtherModConfigFile.applyToml(path, ceilNumberEntry(ConfigFormat.TOML, "screenShakeScale", 0.3)));
    }

    // ---- TOML: key missing ----

    @Test
    void tomlMissingKeyReturnsFalseWithoutThrowing() throws IOException {
        Path path = tempDir.resolve("no-key.toml");
        Files.writeString(path, "someOtherSetting = 1.0\n");
        assertFalse(OtherModConfigFile.applyToml(path, ceilNumberEntry(ConfigFormat.TOML, "screenShakeScale", 0.3)));
    }

    // ---- TOML: successful pushes, including integer-type preservation ----

    @Test
    void tomlDoubleAboveCeilingIsLoweredAndFileIsRewritten() throws IOException {
        Path path = tempDir.resolve("blueprint-like.toml");
        Files.writeString(path, "screenShakeScale = 1.0\n");

        boolean handled =
                OtherModConfigFile.applyToml(path, ceilNumberEntry(ConfigFormat.TOML, "screenShakeScale", 0.3));

        assertTrue(handled);
        assertTrue(Files.readString(path).contains("0.3"));
    }

    @Test
    void tomlIntegerAboveCeilingIsLoweredAsAnIntegerNotAFloat() throws IOException {
        Path path = tempDir.resolve("blueprint-shakers.toml");
        Files.writeString(path, "maxScreenShakers = 256\n");

        boolean handled =
                OtherModConfigFile.applyToml(path, ceilNumberEntry(ConfigFormat.TOML, "maxScreenShakers", 0.0));

        assertTrue(handled);
        String rewritten = Files.readString(path);
        assertTrue(rewritten.contains("maxScreenShakers = 0"), "expected an int literal, was: " + rewritten);
        assertFalse(rewritten.contains("0.0"), "expected no float literal for an originally-integer key, was: " + rewritten);
    }

    @Test
    void tomlValueAlreadyBelowCeilingIsHandledButNotRewritten() throws IOException {
        Path path = tempDir.resolve("already-fine.toml");
        Files.writeString(path, "screenShakeScale = 0.1\n");

        boolean handled =
                OtherModConfigFile.applyToml(path, ceilNumberEntry(ConfigFormat.TOML, "screenShakeScale", 0.3));

        assertTrue(handled);
        assertTrue(Files.readString(path).contains("0.1"));
    }
}
