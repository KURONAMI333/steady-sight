package com.kuronami.steadysight.client;

import com.kuronami.steadysight.SteadySight;
import com.kuronami.steadysight.compute.SteadySightSettings;
import com.kuronami.steadysight.compute.StrengthPreset;
import com.kuronami.steadysight.compute.VignetteStrength;
import com.kuronami.steadysight.config.SteadySightConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

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
 * <p>{@code value = Dist.CLIENT} on the class annotation is load-bearing:
 * it keeps this class (and everything it references — {@link Minecraft},
 * {@link GuiGraphics}, ...) off a dedicated server's classpath entirely,
 * which is how a "client-side only" NeoForge mod avoids ever touching
 * client-only types on the other dist.
 */
@EventBusSubscriber(modid = SteadySight.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SteadySightOverlay {

    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(SteadySight.MODID, "vignette");

    private static final ResourceLocation TEXTURE_SUBTLE =
            ResourceLocation.fromNamespaceAndPath(SteadySight.MODID, "textures/gui/vignette_subtle.png");
    private static final ResourceLocation TEXTURE_STANDARD =
            ResourceLocation.fromNamespaceAndPath(SteadySight.MODID, "textures/gui/vignette_standard.png");
    private static final ResourceLocation TEXTURE_STRONG =
            ResourceLocation.fromNamespaceAndPath(SteadySight.MODID, "textures/gui/vignette_strong.png");

    private SteadySightOverlay() {}

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER_ID, SteadySightOverlay::render);
    }

    private static void render(GuiGraphics gui, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        SteadySightSettings settings = SteadySightConfig.snapshot();
        float strength = VignetteStrength.strength(settings);
        if (strength > 0.0f) {
            drawVignette(gui, textureFor(settings.innerRadius()), screenWidth, screenHeight, strength);
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
    private static ResourceLocation textureFor(float innerRadius) {
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
     * <p><strong>Blend mode (revised 2026-07-31, GAP_LOG G76, a kura
     * runClient verdict that the vignette's rounded-rectangle silhouette
     * was visibly outlined against bright, shader-lit scenes)</strong>: this
     * now reproduces vanilla's own {@code Gui#renderVignette} blend
     * mechanism exactly, read from the decompiled 1.21.1 source
     * (net/minecraft/client/gui/Gui.java:1178-1211):
     *
     * <pre>{@code
     * RenderSystem.blendFuncSeparate(
     *     GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
     *     GlStateManager.SourceFactor.ONE,  GlStateManager.DestFactor.ZERO);
     * guiGraphics.setColor(f2, f2, f2, 1.0F); // f2 = vignetteBrightness, clamped [0,1]
     * guiGraphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F, w, h, w, h);
     * }</pre>
     *
     * Per-pixel, this computes {@code dst_rgb' = dst_rgb * (1 - src_rgb)},
     * where {@code src_rgb = textureRgb * tint} — a genuine multiply
     * (brightness-scaling) blend, not an alpha composite. Vanilla's own
     * {@code vignette.png} (measured from the decompiled 1.21.1 client jar)
     * is grayscale RGB with alpha pinned at 255 everywhere: center
     * {@code (0,0,0,255)}, rising toward the edges, never using the alpha
     * channel for shape at all. This mod's masks now match that layout
     * exactly (see {@code generate_vignette_textures.py}): RGB carries the
     * radial shape, alpha is always 255.
     *
     * <p><strong>Important, verified-by-derivation caveat</strong>: with the
     * old mask's non-shape channel pinned to black, the *RGB* result the two
     * blend modes paint onto the screen is provably identical pixel-for-pixel
     * — {@code dst*(1-shapeStrength)} either way (old:
     * {@code SRC_ALPHA/ONE_MINUS_SRC_ALPHA} with {@code srcRgb=(0,0,0)},
     * {@code srcAlpha=shape*strength}, giving
     * {@code 0*srcAlpha + dst*(1-srcAlpha)}; new: {@code ZERO/
     * ONE_MINUS_SRC_COLOR} with {@code srcRgb=shape*strength}, giving
     * {@code dst*(1-srcRgb)}). <strong>This change cannot alter the visible
     * RGB output by itself</strong> — if the reported artifact persists
     * after this change in a future runClient session, the cause is in RGB
     * space (most likely candidate: the mask's own shape, not its blend
     * mode — see GAP_LOG G76 for the alpha-channel side of this and the RGB
     * candidate this rules in instead).
     *
     * <p>What the two modes do <em>not</em> share is the alpha channel the
     * draw call leaves in the framebuffer: both use the identical second
     * factor pair for alpha ({@code ONE, ZERO}, i.e.
     * {@code alphaResult = srcAlpha}), but the old mask's alpha *was* the
     * shape ({@code 0} at screen center ramping up to {@code strength} at
     * the edges), so every frame wrote a vignette-shaped pattern into the
     * color buffer's alpha channel; the new mask's alpha is a constant 255
     * everywhere (matching vanilla), so {@code alphaResult} is now always
     * {@code 1.0} regardless of {@code strength}. <strong>Whether this
     * alpha difference is actually what a shader pack like Iris renders
     * differently is an unverified hypothesis, not a confirmed
     * mechanism</strong> — it has not been checked against Iris's own
     * source, and ordinarily a shader pack's post-processing composites
     * before this mod's GUI-layer overlay draws, which would put the
     * write this paragraph describes out of that pass's read order for the
     * same frame. See GAP_LOG G76 for the full derivation and the explicit
     * "unverified hypothesis" framing.
     *
     * <p>{@code strength == 0} still produces zero visible change even
     * without the {@code strength > 0.0f} guard in {@link #render}: the
     * tint becomes {@code (0,0,0,1.0)}, so {@code srcRgb = textureRgb * 0
     * = 0} at every pixel regardless of the baked shape, and
     * {@code dst*(1-0) = dst} — unchanged. The guard is kept anyway to
     * skip the draw call entirely rather than issue a no-op one.
     */
    private static void drawVignette(GuiGraphics gui, ResourceLocation texture, int screenWidth, int screenHeight, float strength) {
        float tint = Math.max(0.0f, Math.min(1.0f, strength));

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        gui.setColor(tint, tint, tint, 1.0f);
        // textureWidth/textureHeight are deliberately passed as the
        // destination size (not the file's real 256x256), which is exactly
        // how vanilla's Gui#renderVignette calls this overload — it makes
        // the sampled U/V range exactly [0,1] regardless of the texture's
        // actual resolution, stretching the whole image across the
        // destination in one shot.
        gui.blit(texture, 0, 0, 0, 0.0f, 0.0f, screenWidth, screenHeight, screenWidth, screenHeight);

        // Cleanup order matches vanilla's Gui#renderVignette exactly
        // (depthMask -> enableDepthTest -> setColor reset -> blend func
        // reset -> disableBlend), not just the individual calls.
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
