package com.kuronami.steadysight.client;

import com.kuronami.steadysight.SteadySight;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Every NeoForge event this mod subscribes to, in one place. The handlers
 * themselves are one line each — all four features live in {@code common} and
 * this class only says <em>when</em> NeoForge runs them, which is the whole
 * point of the multiloader split.
 *
 * <p>The subscriptions are the ones the 1.21.1 cell ships: the HUD layer still
 * goes above {@link VanillaGuiLayers#CROSSHAIR} under the same
 * {@code steady_sight:vignette} id, the tracker still ticks on
 * {@link ClientTickEvent.Post} and still resets on all three of
 * {@code ClientPlayerNetworkEvent}'s LoggingIn / LoggingOut / Clone, and both
 * one-time pushes still run through {@code FMLClientSetupEvent} +
 * {@code enqueueWork}.
 *
 * <p>26.x drift, both mechanical: {@code ResourceLocation} is
 * {@link Identifier}, and {@code @EventBusSubscriber}'s {@code bus} argument
 * was removed in 26.1 (the bus is now chosen from the event type — mod bus for
 * {@code IModBusEvent}, game bus otherwise). The two nested classes are kept
 * as they are; auto-routing would make one class possible, but merging them
 * would be a refactor, not a port.
 *
 * <p>{@code value = Dist.CLIENT} is load-bearing: it keeps this class — and
 * transitively the common client classes it names — off a dedicated server's
 * classpath, so a server never resolves {@code Minecraft} or
 * {@code GuiGraphicsExtractor}.
 */
public final class SteadySightNeoForgeClient {

    private SteadySightNeoForgeClient() {}

    @EventBusSubscriber(modid = SteadySight.MODID, value = Dist.CLIENT)
    public static final class ModBus {

        private static final Identifier LAYER_ID =
                Identifier.fromNamespaceAndPath(SteadySight.MODID, "vignette");

        private ModBus() {}

        /**
         * {@code registerAbove(CROSSHAIR, ...)} inserts before NeoForge's
         * {@code AFTER_CAMERA_DECORATIONS} layer, which is the one that calls
         * {@code GuiGraphicsExtractor#nextStratum()} — so the hotbar and
         * everything after it land in a later stratum and stay above the
         * vignette, exactly as on 1.21.1.
         */
        @SubscribeEvent
        public static void registerOverlay(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER_ID, SteadySightOverlay::render);
        }

        /**
         * {@code FMLClientSetupEvent} fires on a loading thread;
         * {@code enqueueWork} defers to the main thread once startup has
         * progressed far enough that {@code Minecraft.getInstance().options}
         * is safe to touch.
         */
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(VanillaComfortSettings::applyOnce);
            event.enqueueWork(OtherModSettingsOptimizer::applyOnce);
        }
    }

    @EventBusSubscriber(modid = SteadySight.MODID, value = Dist.CLIENT)
    public static final class GameBus {

        private GameBus() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            StepCameraTracker.onClientTick();
        }

        @SubscribeEvent
        public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
            StepCameraTracker.resetHistory();
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            StepCameraTracker.resetHistory();
        }

        @SubscribeEvent
        public static void onClone(ClientPlayerNetworkEvent.Clone event) {
            StepCameraTracker.resetHistory();
        }
    }
}
