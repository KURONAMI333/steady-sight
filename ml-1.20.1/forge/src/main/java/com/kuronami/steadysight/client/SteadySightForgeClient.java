package com.kuronami.steadysight.client;

import com.kuronami.steadysight.SteadySight;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Every Forge event this mod subscribes to, in one place — the 1.20.1
 * counterpart of the NeoForge cells' {@code SteadySightNeoForgeClient}. The
 * handlers are one line each; all four features live in {@code common}.
 *
 * <p><strong>Where the vignette is inserted, and why it is not
 * {@code registerAbove(CROSSHAIR)}</strong>: the NeoForge cells register above
 * {@code VanillaGuiLayers.CROSSHAIR} because on 1.21.1+ vanilla's layer order
 * is {@code CAMERA_OVERLAYS → CROSSHAIR → HOTBAR}, so "above the crosshair" is
 * also "below the hotbar". <strong>Forge 1.20.1 orders those two the other way
 * round</strong>: {@code VanillaGuiOverlay}'s declaration order (read from
 * Forge 47.2.30's own sources) is {@code VIGNETTE, SPYGLASS, HELMET,
 * FROSTBITE, PORTAL, HOTBAR, CROSSHAIR, BOSS_EVENT_PROGRESS, PLAYER_HEALTH,
 * …}, matching vanilla 1.20.1's {@code Gui#render}, which draws the hotbar
 * before the crosshair. Registering above the crosshair here would put this
 * multiply-blend mask <em>over</em> the hotbar — the exact defect GAP_LOG G84
 * measured at 14–28% darkening and rejected. Registering <em>below the
 * hotbar</em> reproduces the 1.21.1 slot: on top of every camera overlay,
 * under the hotbar and everything after it.
 *
 * <p>The one residual difference from the newer cells is that the crosshair is
 * now drawn <em>after</em> this overlay instead of before it, so the crosshair
 * is no longer multiplied by the mask. That is visually inert: the mask is
 * black (value 0) at the screen centre, so it darkens nothing there either
 * way. Recorded rather than chased.
 *
 * <p>{@code value = Dist.CLIENT} is load-bearing: it keeps this class — and
 * transitively the common client classes it names — off a dedicated server's
 * classpath. The two buses are split into nested classes because the mod bus
 * (registration, setup) and the Forge bus (tick, network) take different
 * subscriptions and {@code @Mod.EventBusSubscriber} declares its bus per class.
 */
public final class SteadySightForgeClient {

    private SteadySightForgeClient() {}

    @Mod.EventBusSubscriber(modid = SteadySight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        /**
         * Forge composes the namespace itself from the active mod container,
         * so this plain string becomes {@code steady_sight:vignette} — the
         * same overlay id the NeoForge cells register.
         */
        private static final String OVERLAY_ID = "vignette";

        private ModBus() {}

        @SubscribeEvent
        public static void registerOverlay(RegisterGuiOverlaysEvent event) {
            event.registerBelow(
                    VanillaGuiOverlay.HOTBAR.id(),
                    OVERLAY_ID,
                    (gui, guiGraphics, partialTick, screenWidth, screenHeight) ->
                            SteadySightOverlay.render(guiGraphics, partialTick));
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

    @Mod.EventBusSubscriber(modid = SteadySight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class GameBus {

        private GameBus() {}

        /**
         * Forge 1.20.1 has one {@code ClientTickEvent} carrying a phase rather
         * than NeoForge's {@code ClientTickEvent.Pre}/{@code .Post} pair;
         * {@code Phase.END} is the same moment {@code .Post} is.
         */
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                StepCameraTracker.onClientTick();
            }
        }

        @SubscribeEvent
        public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
            StepCameraTracker.resetHistory();
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            StepCameraTracker.resetHistory();
        }

        /**
         * Forge 1.20.1 has {@code Clone} (respawn and dimension change), so
         * this cell's Forge side matches the NeoForge cells exactly — the
         * respawn gap declared in {@code StepCameraTracker#resetHistory} is a
         * Fabric-only difference, not a 1.20.1 one.
         */
        @SubscribeEvent
        public static void onClone(ClientPlayerNetworkEvent.Clone event) {
            StepCameraTracker.resetHistory();
        }
    }
}
