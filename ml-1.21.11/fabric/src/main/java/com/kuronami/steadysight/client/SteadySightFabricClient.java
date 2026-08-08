package com.kuronami.steadysight.client;

import com.kuronami.steadysight.Constants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

/**
 * The Fabric half of "when do the four features run". Registered as the
 * {@code client} entrypoint, so a dedicated server never loads it or anything
 * it names.
 *
 * <p>Unlike the 1.21.1 cell, the vignette is registered here rather than drawn
 * from a mixin. That cell reaches NeoForge's "above the crosshair, below the
 * hotbar" slot by injecting at the head of {@code Gui#renderHotbarAndDecorations},
 * because fabric-rendering-v1 on 1.21.1 only offers {@code HudRenderCallback},
 * which fires after the whole HUD and would composite the vignette over the
 * hotbar and chat. On 1.21.11 {@link HudElementRegistry} exists alongside
 * {@code HudRenderCallback} (both are present in this version's
 * fabric-rendering-v1, confirmed from its sources) and supports insertion
 * relative to a named vanilla element — so the slot the mixin was working
 * around is now an API call, and the mixin is deleted rather than ported.
 *
 * <p>{@code attachElementAfter(CROSSHAIR, ...)} lands exactly where NeoForge's
 * {@code registerAbove(VanillaGuiLayers.CROSSHAIR, ...)} does. Read from
 * fabric-rendering-v1's own {@code GuiMixin} (package-private, not part of the
 * public API, at {@code net.fabricmc.fabric.mixin.client.rendering.GuiMixin}
 * in this version's fabric-rendering-v1 jar): it {@code @WrapOperation}s the
 * {@code Gui#renderCrosshair(GuiGraphics, DeltaTracker)} call site inside
 * vanilla {@code Gui#render}, so an element attached after {@code CROSSHAIR}
 * runs immediately once that wrapped call returns — still inside
 * {@code Gui#render}, and before {@code renderHotbarAndDecorations} is called
 * later in that same method body. No extract/render-state split exists on
 * this version (that is a 26.x-only change); {@code Gui#render} still does
 * the drawing itself in one pass, the same shape the 1.21.1 cell's mixin
 * targets.
 *
 * <p>The step-camera offset is still applied from the common
 * {@code CameraMixin}. What is left here is the tick that detects steps and
 * the two one-time pushes.
 *
 * <p><strong>Why the one-time pushes are not run from
 * {@link #onInitializeClient()}</strong>: NeoForge runs them off
 * {@code FMLClientSetupEvent} <em>plus</em> {@code enqueueWork}, i.e.
 * deliberately not at the moment the setup event fires, because
 * {@code Minecraft.getInstance().options} is not safe to read — let alone
 * {@code save()} — that early. {@code onInitializeClient} runs inside
 * {@code Minecraft}'s own constructor, which is earlier still. Deferring to
 * the first end-of-client-tick is the equivalent moment on this loader: the
 * game is fully constructed and we are on the client thread.
 */
public final class SteadySightFabricClient implements ClientModInitializer {

    private static final Identifier VIGNETTE_ELEMENT =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "vignette");

    private static boolean oneTimePushesDone;

    @Override
    public void onInitializeClient() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CROSSHAIR, VIGNETTE_ELEMENT, SteadySightOverlay::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!oneTimePushesDone) {
                oneTimePushesDone = true;
                VanillaComfortSettings.applyOnce();
                OtherModSettingsOptimizer.applyOnce();
            }
            StepCameraTracker.onClientTick();
        });

        // Fabric has no respawn/dimension-change equivalent of NeoForge's
        // ClientPlayerNetworkEvent.Clone; that gap is declared in
        // StepCameraTracker#resetHistory rather than worked around here.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> StepCameraTracker.resetHistory());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> StepCameraTracker.resetHistory());
    }
}
