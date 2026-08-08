package com.kuronami.steadysight.client;

import com.kuronami.steadysight.Constants;
import com.kuronami.steadysight.compute.SteadySightSettings;
import com.kuronami.steadysight.compute.StrengthPreset;
import com.kuronami.steadysight.compute.VignetteStrength;
import com.kuronami.steadysight.platform.Services;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * that is {@code registerAbove(VanillaGuiLayers.CROSSHAIR, ...)}; on Fabric
 * it is {@code HudElementRegistry.attachElementAfter(
 * VanillaHudElements.CROSSHAIR, ...)}. Both land in the same gap, and on 26.x
 * that gap is also a stratum boundary: vanilla {@code Gui#extractRenderState}
 * calls {@code extractCrosshair} → {@code GuiGraphicsExtractor#nextStratum()}
 * → {@code extractHotbarAndDecorations} (confirmed by disassembling
 * {@code Gui#extractRenderState} in {@code minecraft_26.1.2_client.jar} —
 * same call order as the 26.2 cell documents), so everything drawn after this
 * layer — hotbar, chat, XP bar — is in a later stratum and stays above the
 * mask, which is what picking that slot buys.
 *
 * <p>Keeping this class off a dedicated server's classpath is the loader
 * cell's job: it is only ever reached from a client-side registration
 * ({@code Dist.CLIENT} event subscriber on NeoForge, the {@code client}
 * entrypoint on Fabric), so a server never classloads it and never resolves
 * {@link Minecraft} / {@link GuiGraphicsExtractor} through it.
 */
public final class SteadySightOverlay {

    private static final Identifier TEXTURE_SUBTLE =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/vignette_subtle.png");
    private static final Identifier TEXTURE_STANDARD =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/vignette_standard.png");
    private static final Identifier TEXTURE_STRONG =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/vignette_strong.png");

    private SteadySightOverlay() {}

    public static void render(GuiGraphicsExtractor gui, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        // Unlike 26.2, `Options#hideGui` still exists on 26.1.2 (confirmed by
        // disassembling minecraft_26.1.2_client.jar: there is no separate
        // `Hud` class at this version — `Minecraft#gui` is still a plain
        // `Gui`, and `Gui#extractRenderState` itself reads
        // `Options.hideGui` directly, the same field vanilla's 1.21.1 and
        // 1.21.11 `Gui#render` check). This cell checks it the same way the
        // 1.21.1 and 1.21.11 cells do. NeoForge's GuiLayerManager inserts a
        // mod layer without the vanilla group's shouldRender supplier, so a
        // layer that does not check this itself would still draw with the
        // HUD hidden.
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
     * pipeline to borrow. 26.x has one: {@code RenderPipelines.VIGNETTE}
     * carries {@code BlendFunction(ZERO, ONE_MINUS_SRC_COLOR, ZERO, ONE)} —
     * confirmed by disassembling {@code minecraft_26.1.2_client.jar}'s own
     * {@code RenderPipelines} directly (not carried over from the 26.2 cell's
     * jar: G93's discipline about re-deriving per version applies here too),
     * and it is byte-for-byte the same four-argument {@code BlendFunction}
     * constructor call the 26.2 cell documents. Its colour factors are the
     * same multiply, so this cell uses the pipeline and issues no manual
     * {@code RenderSystem} state at all. Per-pixel that is
     * {@code dst_rgb' = dst_rgb * (1 - src_rgb)} with
     * {@code src_rgb = textureRgb * tint} — a brightness-scaling blend, not
     * an alpha composite.
     *
     * <p>The alpha factors differ from the 1.21.1 cell's ({@code ZERO, ONE}
     * instead of {@code ONE, ZERO}, i.e. the destination alpha is left alone
     * rather than overwritten with the source's) — and, unlike 1.21.11
     * (G98: a two-argument {@code BlendFunction} constructor that multiplies
     * alpha down the same way as colour), 26.1.2 already carries the
     * four-argument, alpha-preserving form 26.2 also has. That is vanilla's
     * own choice on this version and it is strictly the safer one: it is
     * physically incapable of the alpha-channel write that PLAYBOOK_CLIENT_RENDER
     * §2-0 records as the cause of the hard silhouette a shader pack drew in
     * KURONAMI333's 1.21.1 environment. The RGB output is unchanged either way.
     *
     * <p>This is only correct because the three masks carry their shape in
     * <em>RGB</em> with alpha pinned at 255 — the pipeline ignores source
     * alpha entirely, so an alpha-shaped mask would render as nothing at all
     * and no build or test gate would notice. Measured on the shipped PNGs
     * before this cell was written: centre {@code (0,0,0,255)}, edge and
     * corner {@code (255,255,255,255)}, alpha exactly 255 at every sampled
     * pixel of all three files. Same layout as vanilla's own
     * {@code vignette.png}.
     *
     * <p>Strength is the tint argument rather than a {@code setColor} call —
     * {@code GuiGraphicsExtractor} has no {@code setColor}, and this is the
     * exact call shape vanilla {@code Gui#extractVignette} uses (confirmed
     * against minecraft_26.1.2_client.jar — there is no separate {@code Hud}
     * class at this version): {@code ARGB.colorFromFloat(F,F,F,F)} into the
     * 11-argument {@code blit}.
     *
     * <p>{@code strength == 0} would already produce zero visible change even
     * without the {@code strength > 0.0f} guard in {@link #render}: the tint
     * becomes {@code (0,0,0)}, so {@code srcRgb = textureRgb * 0 = 0} at every
     * pixel regardless of the baked shape, and {@code dst*(1-0) = dst}. The
     * guard is kept anyway to skip the draw call entirely rather than issue a
     * no-op one.
     */
    private static void drawVignette(GuiGraphicsExtractor gui, Identifier texture, float strength) {
        float tint = Math.max(0.0f, Math.min(1.0f, strength));
        int width = gui.guiWidth();
        int height = gui.guiHeight();

        // textureWidth/textureHeight are deliberately passed as the
        // destination size (not the file's real 256x256), which is exactly
        // how vanilla's Gui#extractVignette calls this overload — it makes
        // the sampled U/V range exactly [0,1] regardless of the texture's
        // actual resolution, stretching the whole image across the
        // destination in one shot.
        gui.blit(RenderPipelines.VIGNETTE, texture,
                0, 0, 0.0f, 0.0f,
                width, height, width, height,
                ARGB.colorFromFloat(1.0f, tint, tint, tint));
    }
}
