package com.kuronami.steadysight.mixin;

import com.kuronami.steadysight.client.SteadySightOverlay;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric's stand-in for the Forge cell's
 * {@code RegisterGuiOverlaysEvent.registerBelow(VanillaGuiOverlay.HOTBAR.id(), ...)}.
 *
 * <p><strong>Why not {@code HudRenderCallback}</strong> (which is what
 * fabric-rendering-v1 offers on 1.20.1, and what the obvious port would use):
 * that callback fires at the end of the whole HUD, so the vignette would be
 * composited <em>over</em> the hotbar, the chat and the XP bar instead of
 * under them. Measured off the shipped {@code vignette_standard.png} with the
 * STANDARD preset's 0.28 tint, the multiply blend darkens the bottom-centre
 * hotbar band by 14–28% depending on GUI scale, and the first chat line by 28%
 * (GAP_LOG G84). PLAYBOOK_CLIENT_RENDER's 数値相場 puts the human luminance
 * discrimination threshold at 1–2%, so that is an order of magnitude into
 * plainly visible, and it would have made this build look different from the
 * one design review already signed off on.
 *
 * <p><strong>Why this injection point</strong>, read from the 1.20.1 client
 * jar's own bytecode rather than carried over from a newer cell. 1.20.1 has no
 * {@code LayeredDraw} and no {@code renderHotbarAndDecorations}:
 * {@code Gui#render(GuiGraphics, float)} is one monolithic method, and its
 * order is
 *
 * <pre>{@code
 * ... vignette, spyglass/pumpkin, frostbite, portal overlay ...
 * if (gameMode.getPlayerMode() == GameType.SPECTATOR) spectatorGui.renderHotbar(g);
 * else if (!options.hideGui)                          this.renderHotbar(partialTick, g);
 * if (!options.hideGui) { RenderSystem.enableBlend(); this.renderCrosshair(g); ... }
 * }</pre>
 *
 * so the hotbar comes <em>before</em> the crosshair here — the reverse of
 * 1.21.1+, where the layer list is {@code CAMERA_OVERLAYS → CROSSHAIR →
 * HOTBAR}. Injecting "above the crosshair" on this version would land on the
 * far side of the hotbar and reproduce exactly the darkening the paragraph
 * above rules out. The equivalent slot is the instant before the hotbar
 * branch, i.e. immediately before the first {@code getPlayerMode()} call in
 * the method ({@code ordinal = 0}; the second one, further down, guards the
 * held-item name). Anchoring there rather than at the head of
 * {@code renderHotbar} is deliberate: {@code renderHotbar} is skipped entirely
 * in spectator mode, and the vignette should still draw there.
 *
 * <p>The whole hotbar/crosshair group is gated on {@code !options.hideGui},
 * which is the same gate {@link SteadySightOverlay#render} re-checks for
 * itself, so the overlay stays hidden with the HUD either way.
 *
 * <p>This mixin lives in the fabric cell rather than in common on purpose: the
 * Forge cell uses its own overlay registration, and applying both would draw
 * the vignette twice.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;",
                            ordinal = 0))
    private void steadysight$renderVignette(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        SteadySightOverlay.render(guiGraphics, partialTick);
    }
}
