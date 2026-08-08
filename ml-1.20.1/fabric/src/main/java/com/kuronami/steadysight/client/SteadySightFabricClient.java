package com.kuronami.steadysight.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * The Fabric half of "when do the four features run". Registered as the
 * {@code client} entrypoint, so a dedicated server never loads it or anything
 * it names.
 *
 * <p>Two of the four features are not wired here: the vignette is drawn from
 * {@code GuiMixin} (Forge's HUD-overlay slot has no Fabric API equivalent on
 * 1.20.1 — see that class), and the step-camera offset is applied from the
 * common {@code CameraMixin}. What is left is the tick that detects steps and
 * the two one-time pushes.
 *
 * <p><strong>Why the one-time pushes are not run from
 * {@link #onInitializeClient()}</strong>: Forge runs them off
 * {@code FMLClientSetupEvent} <em>plus</em> {@code enqueueWork}, i.e.
 * deliberately not at the moment the setup event fires, because
 * {@code Minecraft.getInstance().options} is not safe to read — let alone
 * {@code save()} — that early. {@code onInitializeClient} runs inside
 * {@code Minecraft}'s own constructor, which is earlier still. Deferring to
 * the first end-of-client-tick is the equivalent moment on this loader: the
 * game is fully constructed and we are on the client thread.
 */
public final class SteadySightFabricClient implements ClientModInitializer {

    private static boolean oneTimePushesDone;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!oneTimePushesDone) {
                oneTimePushesDone = true;
                VanillaComfortSettings.applyOnce();
                OtherModSettingsOptimizer.applyOnce();
            }
            StepCameraTracker.onClientTick();
        });

        // Fabric 1.20.1 has no respawn/dimension-change equivalent of
        // Forge's ClientPlayerNetworkEvent.Clone; that gap is declared in
        // StepCameraTracker#resetHistory rather than worked around here.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> StepCameraTracker.resetHistory());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> StepCameraTracker.resetHistory());
    }
}
