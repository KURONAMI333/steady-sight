package com.kuronami.steadysight.client;

import com.kuronami.steadysight.SteadySight;
import com.kuronami.steadysight.compute.SteadySightSettings;
import com.kuronami.steadysight.compute.StrengthPreset;
import com.kuronami.steadysight.compute.VignetteStrength;
import com.kuronami.steadysight.config.SteadySightConfig;
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
     * <p>{@code maxOpacity} is applied here, not baked into the texture,
     * via {@code GuiGraphics#setColor}'s alpha multiplier — the same
     * mechanism vanilla's own {@code Gui#renderVignette} uses to scale its
     * vignette texture (confirmed in the decompiled 1.21.1 source; there is
     * no {@code blit} overload that takes a packed ARGB color directly).
     * The blend function is deliberately the plain default alpha blend,
     * <em>not</em> the multiply blend vanilla's vignette uses (see GAP_LOG
     * G10) — this mask is a normal alpha-composited black overlay, not a
     * brightness filter.
     */
    private static void drawVignette(GuiGraphics gui, ResourceLocation texture, int screenWidth, int screenHeight, float strength) {
        float alpha = Math.max(0.0f, Math.min(1.0f, strength));

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        gui.setColor(1.0f, 1.0f, 1.0f, alpha);
        // textureWidth/textureHeight are deliberately passed as the
        // destination size (not the file's real 256x256), which is exactly
        // how vanilla's Gui#renderVignette calls this overload — it makes
        // the sampled U/V range exactly [0,1] regardless of the texture's
        // actual resolution, stretching the whole image across the
        // destination in one shot.
        gui.blit(texture, 0, 0, 0, 0.0f, 0.0f, screenWidth, screenHeight, screenWidth, screenHeight);
        gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
