package com.kuronami.steadysight.mixin;

import com.kuronami.steadysight.client.SteadySightOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric's stand-in for NeoForge's
 * {@code RegisterGuiLayersEvent.registerAbove(VanillaGuiLayers.CROSSHAIR, ...)}.
 *
 * <p><strong>Why not {@code HudRenderCallback}</strong> (which is what
 * fabric-rendering-v1 offers on 1.21.1, and what the obvious port would use):
 * that callback fires at the end of the whole HUD, so the vignette would be
 * composited <em>over</em> the hotbar, the chat and the XP bar instead of
 * under them. That is not a cosmetic quibble — measured off the shipped
 * {@code vignette_standard.png} with the STANDARD preset's 0.28 tint, the
 * multiply blend darkens the bottom-centre hotbar band by 14–28% depending on
 * GUI scale, and the first chat line by 28%. PLAYBOOK_CLIENT_RENDER's 数値相場
 * puts the human luminance discrimination threshold at 1–2%, so that is an
 * order of magnitude into plainly visible, and it would have made the Fabric
 * build look different from the NeoForge build KURONAMI333 already signed off on.
 *
 * <p><strong>Why this injection point</strong>: read from the decompiled
 * 1.21.1 sources on both sides. Vanilla {@code Gui}'s constructor builds
 * {@code LayeredDraw().add(this::renderCameraOverlays).add(this::renderCrosshair)
 * .add(this::renderHotbarAndDecorations)...}; NeoForge's patched {@code Gui}
 * builds the same sequence with names,
 * {@code .add(CAMERA_OVERLAYS, ...).add(CROSSHAIR, ...).add(HOTBAR, ...)}. So
 * "above CROSSHAIR" and "the head of {@code renderHotbarAndDecorations}" are
 * the same gap between the same two neighbours, and nothing else renders in
 * between. The whole layer group is already gated on
 * {@code !options.hideGui} in both, which is the same gate
 * {@link SteadySightOverlay#render} re-checks for itself.
 *
 * <p>This mixin lives in the fabric cell rather than in common on purpose:
 * NeoForge must keep using its own event registration, and applying both
 * would draw the vignette twice.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"))
    private void steadysight$renderVignette(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        SteadySightOverlay.render(guiGraphics, deltaTracker);
    }
}
