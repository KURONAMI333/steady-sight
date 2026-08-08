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
 * hotbar and chat. On 26.1.2 {@code HudRenderCallback} is gone (confirmed
 * absent from the fabric-rendering-v1 jar-in-jar'd inside fabric-api
 * 0.155.2+26.1.2, same as 26.2) and {@link HudElementRegistry} replaced it,
 * and it supports insertion relative to a named vanilla element — so the slot
 * the mixin was working around is now an API call, and the mixin is deleted
 * rather than ported.
 *
 * <p>{@code attachElementAfter(CROSSHAIR, ...)} lands exactly where NeoForge's
 * {@code registerAbove(VanillaGuiLayers.CROSSHAIR, ...)} does: fabric-api wraps
 * each vanilla element at its own call site inside {@code Gui#extractRenderState}
 * (there is no separate {@code Hud} class at 26.1.2 — {@code Minecraft#gui} is
 * still a plain {@code Gui}, confirmed against minecraft_26.1.2_client.jar),
 * so this runs immediately after {@code extractCrosshair} — before that
 * method's {@code nextStratum()} and before {@code extractHotbarAndDecorations},
 * and inside the branch guarded by {@code !Options.hideGui}.
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
