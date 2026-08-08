package com.kuronami.steadysight.client;

import com.kuronami.steadysight.Constants;
import com.kuronami.steadysight.compute.SteadySightSettings;
import com.kuronami.steadysight.compute.StrengthPreset;
import com.kuronami.steadysight.compute.VignetteStrength;
import com.kuronami.steadysight.platform.Services;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * The HUD layer, drawing the one of this mod's features that is an
 * on-screen visual (DESIGN_COMPILE.md §2 — the other two features, the
 * one-time vanilla settings push and the step-camera smoothing, live in
 * {@link VanillaComfortSettings} and the {@code mixin} package respectively,
 * and neither draws anything).
 *
 * <p>The vignette (DESIGN_COMPILE.md §2, revised 2026-07-29 after a
 * runClient verdict that movement-linked strength read as flicker — see
 * {@link VignetteStrength}'s Javadoc for the full story) is always-on and
 * depends on nothing but the active {@link SteadySightSettings} preset, so
 * drawing it needs no mutable state — no tick to sample, no history to roll
 * forward, nothing that could go stale.
 *
 * <p>DESIGN_COMPILE.md §5 (revised 2026-07-30, a runClient verdict on
 * draw method A — see {@code generate_vignette_textures.py} for the full
 * story): the vignette's four-rectangle draw method (linear per-edge
 * gradients plus a diagonal-split corner blend) is gone. A single pre-baked
 * texture, blitted once, replaced it.
 *
 * <p>An artificial rest-frame horizon line briefly lived here (DESIGN_COMPILE.md
 * §2, scope widened 2026-07-30) and was deleted after a runClient verdict
 * that it was "完全にゴミ" — a correct projection in an implementation form
 * that was never validated against DESIGN_COMPILE.md §2's "画面に表示物を足す
 * 機能は嫌われ、挙動を滑らかにする機能は受け入れられる" principle before being
 * built. See GAP_LOG for the full account; nothing about it survives here.
 *
 * <p><strong>Where this draws (identical on both loaders, deliberately)</strong>:
 * immediately after the vanilla crosshair and before the hotbar. On NeoForge
 * that is {@code registerAbove(VanillaGuiLayers.CROSSHAIR, ...)}; on Fabric it
 * is {@code HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR,
 * ...)} (fabric-rendering-v1 on 1.21.11 has this API, unlike the 1.21.1 cell,
 * which has to reach the same slot with a mixin — see that cell's {@code
 * GuiMixin} for the measurement that ruled out {@code HudRenderCallback}).
 * Both wrap vanilla's own per-element call sites inside {@code Gui#render}, so
 * this still lands in the same gap between the crosshair and the hotbar, and
 * anything drawn after this layer — hotbar, chat, XP bar — stays untouched by
 * the mask.
 *
 * <p>Keeping this class off a dedicated server's classpath is the loader
 * cell's job: it is only ever reached from a client-side registration
 * ({@code Dist.CLIENT} event subscriber on NeoForge, the {@code client}
 * entrypoint on Fabric), so a server never classloads it and never resolves
 * {@link Minecraft} / {@link GuiGraphics} through it.
 */
public final class SteadySightOverlay {

    private static final Identifier TEXTURE_SUBTLE =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/vignette_subtle.png");
    private static final Identifier TEXTURE_STANDARD =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/vignette_standard.png");
    private static final Identifier TEXTURE_STRONG =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/vignette_strong.png");

    private SteadySightOverlay() {}

    public static void render(GuiGraphics gui, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        // Unlike 26.x, `Options#hideGui` still exists on 1.21.11 (confirmed
        // against minecraft_1.21.11_client_mappings.txt) — this cell checks it
        // the same way the 1.21.1 cell does. NeoForge's GuiLayerManager
        // inserts a mod layer without the vanilla group's shouldRender
        // supplier, so a layer that does not check this itself would still
        // draw with the HUD hidden.
        if (mc.options.hideGui || mc.player == null) {
            return;
        }

        SteadySightSettings settings = Services.CONFIG.vignetteSettings();
        float strength = VignetteStrength.strength(settings);
        if (strength > 0.0f) {
            drawVignette(gui, textureFor(settings.innerRadius()), strength);
        }
    }

    /**
     * Picks the baked mask matching the active preset's {@code innerRadius}
     * (three fixed values from {@link StrengthPreset}, compared exactly —
     * safe because the float flows from that enum's literal straight
     * through {@link SteadySightSettings} with no intervening arithmetic).
     * Falls back to the standard mask for any value that doesn't match one
     * of the three exactly, rather than failing to render at all, in case
     * this and {@code StrengthPreset} ever drift out of sync.
     */
    private static Identifier textureFor(float innerRadius) {
        if (innerRadius == StrengthPreset.SUBTLE.toSettings().innerRadius()) {
            return TEXTURE_SUBTLE;
        }
        if (innerRadius == StrengthPreset.STRONG.toSettings().innerRadius()) {
            return TEXTURE_STRONG;
        }
        return TEXTURE_STANDARD;
    }

    /**
     * One blit, stretched to cover the full screen. Aspect distortion is
     * accepted on purpose (DESIGN_COMPILE.md §5, revised 2026-07-30) — a
     * vignette naturally stretches to whatever screen it's on, and does not
     * need to stay a perfect circle.
     *
     * <p><strong>Blend mode</strong>: the 1.21.1 cell hand-writes vanilla's
     * multiply blend with {@code RenderSystem.blendFuncSeparate(ZERO,
     * ONE_MINUS_SRC_COLOR, ONE, ZERO)} around the blit, because on that
     * version {@code Gui#renderVignette} does the same thing and there is no
     * pipeline to borrow. 1.21.11 has one: disassembling {@code
     * RenderPipelines}'s {@code <clinit>} in {@code minecraft_1.21.11_client.jar}
     * shows {@code RenderPipelines.VIGNETTE} built with {@code new
     * BlendFunction(SourceFactor.ZERO, DestFactor.ONE_MINUS_SRC_COLOR)} — the
     * two-argument constructor, which (per {@code BlendFunction}'s own
     * bytecode) delegates to the four-argument one as {@code this(source,
     * dest, source, dest)}, i.e. the <em>same</em> factor pair is applied to
     * color and alpha. That is a genuine difference from 26.2's {@code
     * VIGNETTE} (which the ml-26.2 cell documents as {@code (ZERO,
     * ONE_MINUS_SRC_COLOR, ZERO, ONE)} — alpha left alone): on 1.21.11 the
     * alpha channel is multiplied down exactly like the color channels are,
     * not preserved. This cell issues no manual {@code RenderSystem} state
     * either way and just uses the pipeline, matching vanilla's own {@code
     * Gui#renderVignette} call shape on this version (confirmed by
     * disassembling it: it loads {@code RenderPipelines.VIGNETTE} and
     * {@code VIGNETTE_LOCATION} and calls the same 11-argument {@code blit}
     * overload used below) — whatever vanilla's own alpha behavior is on this
     * version, this cell reproduces it rather than one carried over from a
     * different version's cell.
     *
     * <p>Per-pixel the color result is {@code dst_rgb' = dst_rgb * (1 -
     * src_rgb)} with {@code src_rgb = textureRgb * tint} — a
     * brightness-scaling blend, not an alpha composite — identical to both
     * the 1.21.1 and 26.2 cells regardless of the alpha-channel difference
     * above.
     *
     * <p>This is only correct because the three masks carry their shape in
     * <em>RGB</em> with alpha pinned at 255 — the pipeline ignores source
     * alpha entirely for the color output, so an alpha-shaped mask would
     * render as nothing at all and no build or test gate would notice.
     * Measured on the shipped PNGs before this cell was written: centre
     * {@code (0,0,0,255)}, edge and corner {@code (255,255,255,255)}, alpha
     * exactly 255 at every sampled pixel of all three files. Same layout as
     * vanilla's own {@code vignette.png}.
     *
     * <p>Strength is the tint argument passed into the 11-argument {@code
     * blit(RenderPipeline, Identifier, int, int, float, float, int, int, int,
     * int, int)} overload (confirmed present on {@code GuiGraphics} at
     * 1.21.11 from the mappings file) rather than a {@code setColor} call —
     * this is the exact call shape vanilla's own {@code Gui#renderVignette}
     * uses on this version.
     *
     * <p>{@code strength == 0} would already produce zero visible change even
     * without the {@code strength > 0.0f} guard in {@link #render}: the tint
     * becomes {@code (0,0,0)}, so {@code srcRgb = textureRgb * 0 = 0} at every
     * pixel regardless of the baked shape, and {@code dst*(1-0) = dst}. The
     * guard is kept anyway to skip the draw call entirely rather than issue a
     * no-op one.
     */
    private static void drawVignette(GuiGraphics gui, Identifier texture, float strength) {
        float tint = Math.max(0.0f, Math.min(1.0f, strength));
        int width = gui.guiWidth();
        int height = gui.guiHeight();

        // textureWidth/textureHeight are deliberately passed as the
        // destination size (not the file's real 256x256), which is exactly
        // how vanilla's Gui#renderVignette calls this overload — it makes
        // the sampled U/V range exactly [0,1] regardless of the texture's
        // actual resolution, stretching the whole image across the
        // destination in one shot.
        gui.blit(RenderPipelines.VIGNETTE, texture,
                0, 0, 0.0f, 0.0f,
                width, height, width, height,
                ARGB.colorFromFloat(1.0f, tint, tint, tint));
    }
}
