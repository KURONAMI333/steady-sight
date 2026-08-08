package com.kuronami.steadysight.client;

import com.kuronami.steadysight.SteadySight;
import net.minecraft.resources.ResourceLocation;
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
 * <p>The subscriptions are byte-for-byte the ones v0.2.0 shipped, only moved:
 * the HUD layer still goes above {@link VanillaGuiLayers#CROSSHAIR} under the
 * same {@code steady_sight:vignette} id, the tracker still ticks on
 * {@link ClientTickEvent.Post} and still resets on all three of
 * {@code ClientPlayerNetworkEvent}'s LoggingIn / LoggingOut / Clone, and both
 * one-time pushes still run through {@code FMLClientSetupEvent} +
 * {@code enqueueWork}.
 *
 * <p>{@code value = Dist.CLIENT} is load-bearing, exactly as it was on the
 * four separate classes before: it keeps this class — and transitively the
 * common client classes it names — off a dedicated server's classpath, so a
 * server never resolves {@code Minecraft} or {@code GuiGraphics}. The two
 * buses are split into nested classes because the mod bus (registration,
 * setup) and the game bus (tick, network) take different subscriptions and
 * {@code @EventBusSubscriber} declares its bus per class.
 */
public final class SteadySightNeoForgeClient {

    private SteadySightNeoForgeClient() {}

    @EventBusSubscriber(modid = SteadySight.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private static final ResourceLocation LAYER_ID =
                ResourceLocation.fromNamespaceAndPath(SteadySight.MODID, "vignette");

        private ModBus() {}

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

    @EventBusSubscriber(modid = SteadySight.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
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
