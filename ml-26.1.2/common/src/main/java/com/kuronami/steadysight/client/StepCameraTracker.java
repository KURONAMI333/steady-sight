package com.kuronami.steadysight.client;

import com.kuronami.steadysight.compute.StepSmoothing;
import com.kuronami.steadysight.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
// 26.x moved this out of `world.entity.vehicle` into a `minecart` subpackage
// (verified directly against minecraft_26.1.2_client.jar's own jar entry
// list, not carried over from the 26.2 cell's jar — same subpackage there
// too). The class and isOnRails() are otherwise unchanged from 1.21.1.
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

/**
 * The one piece of per-frame mutable state this mod carries (everything else
 * — the vignette, the vanilla-settings push — is either stateless or a
 * one-time action; see {@link com.kuronami.steadysight.compute.StepSmoothing}'s
 * Javadoc for why this feature is allowed to be the exception).
 *
 * <p>Ticks (not frames) are where the actual step happens — {@code
 * LocalPlayer#getY()} and {@code #onGround()} only change once per tick, so
 * detection lives in {@link #onClientTick}, which each loader cell drives from
 * its own end-of-client-tick hook (NeoForge {@code ClientTickEvent.Post} on
 * the game bus, Fabric {@code ClientTickEvents.END_CLIENT_TICK}).
 *
 * <p>{@link #currentOffsetBlocks} is queried once per <em>frame</em> (from
 * {@code CameraMixin}, which has this frame's {@code partialTick}) so the
 * decay is smooth between ticks, not stepwise.
 *
 * <p>GAP_LOG G70 extended detection to a second, unrelated cause of the same
 * symptom: a minecart's rail-track Y snap (up to a full block, either
 * direction — {@link StepSmoothing#isVehicleTrackJump}). {@code
 * CameraMixin} itself needed no changes for this — it only ever asks this
 * tracker "how many blocks right now", never which cause produced that
 * number.
 */
public final class StepCameraTracker {

    /**
     * Unverified starting value (this task does not run runClient — see
     * GAP_LOG). Chosen so the offset decays to under 5% of its starting size
     * within about 6 ticks (0.3s): solving {@code e^(-k*6) = 0.05} gives
     * {@code k ~= 0.5}.
     */
    private static final float STEP_DECAY_PER_TICK = 0.5f;

    /**
     * GAP_LOG G70: a minecart's track snap is up to {@link
     * StepSmoothing#MAX_VEHICLE_JUMP_DELTA} (~1 block), about 1.7x {@link
     * StepSmoothing#MAX_STEP_DELTA} (~0.6 blocks). Reusing {@link
     * #STEP_DECAY_PER_TICK} at that larger amplitude would make the initial
     * catch-up move around 1.7x as fast (the exponential's rate at {@code
     * t=0} is {@code decayPerTick * initialOffsetBlocks} — larger amplitude
     * at the same decay rate means a proportionally faster initial sweep),
     * and it is exactly that peak visual velocity — not the total distance
     * or total duration — that the SPIKE research frames as the actual
     * discomfort driver ("プレイヤーが命令していない画面変化"). This constant
     * is scaled down from {@link #STEP_DECAY_PER_TICK} by the same ratio (
     * {@code MAX_STEP_DELTA / MAX_VEHICLE_JUMP_DELTA ~= 0.6}) so a
     * worst-case vehicle jump's peak catch-up speed lands close to a
     * worst-case step's, instead of scaling up with the bigger snap. The
     * trade-off is a slightly longer settle time for the vehicle case (~10
     * ticks to fall under 5%, vs. ~6 for a step) — accepted deliberately:
     * matching peak speed was judged more important than matching duration,
     * since it is the speed that reads as an uncommanded lurch. Like {@link
     * #STEP_DECAY_PER_TICK}, unverified without runClient.
     */
    private static final float VEHICLE_DECAY_PER_TICK = 0.3f;

    private static boolean hasBaseline;
    private static double lastY;
    private static boolean lastOnGround;

    private static float stepStartOffsetBlocks;
    private static float currentDecayPerTick = STEP_DECAY_PER_TICK;
    private static int ticksSinceStep;

    private StepCameraTracker() {}

    public static void onClientTick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            hasBaseline = false;
            return;
        }

        double y = player.getY();
        boolean onGround = player.onGround();

        if (!hasBaseline) {
            lastY = y;
            lastOnGround = onGround;
            hasBaseline = true;
            return;
        }

        if (Services.CONFIG.smoothStepCamera()) {
            double deltaY = y - lastY;
            if (StepSmoothing.isStepUp(deltaY, lastOnGround, onGround)) {
                stepStartOffsetBlocks = (float) deltaY;
                currentDecayPerTick = STEP_DECAY_PER_TICK;
                ticksSinceStep = 0;
            } else if (StepSmoothing.isVehicleTrackJump(deltaY, isOnMinecartRails(player))) {
                stepStartOffsetBlocks = (float) deltaY;
                currentDecayPerTick = VEHICLE_DECAY_PER_TICK;
                ticksSinceStep = 0;
            } else {
                ticksSinceStep++;
            }
        } else {
            stepStartOffsetBlocks = 0.0f;
            ticksSinceStep = 0;
        }

        lastY = y;
        lastOnGround = onGround;
    }

    /**
     * Whether {@code player} is currently a passenger of a minecart that is
     * itself on rail track this tick.
     *
     * <p>{@code getVehicle()} returning something other than an {@link
     * AbstractMinecart} (a boat, a horse, nothing) makes the {@code
     * instanceof} pattern false and short-circuits before {@code
     * isOnRails()} is even called — boats and horses are excluded by type,
     * not by behavior, per the research decision to leave them out of scope
     * entirely (SPIKE_PASSIVE_MOTION_2026-07.md §6: a boat's discomfort comes
     * from inertia/sliding physics a camera-only fix cannot touch, and horses
     * had essentially no user reports). {@code isOnRails()} (decompiled 1.21.1
     * {@code AbstractMinecart.java}, a public accessor for a field set
     * immediately before the game decides whether to run {@code
     * moveAlongTrack()} that tick) is what tells an on-track cart apart from
     * a derailed, freely falling one — see {@link
     * StepSmoothing#isVehicleTrackJump}'s Javadoc for why that distinction
     * matters.
     */
    private static boolean isOnMinecartRails(LocalPlayer player) {
        Entity vehicle = player.getVehicle();
        return vehicle instanceof AbstractMinecart minecart && minecart.isOnRails();
    }

    /**
     * Resets tracked state on any event that swaps in a new player instance
     * or otherwise discontinues the Y history (respawn, dimension change,
     * (dis)connect) — same reasoning as the deleted movement-linked
     * vignette's history reset (GAP_LOG G21): without this, the first tick
     * after such a swap could read as a huge, spurious Y delta against a
     * stale baseline from the previous world/life.
     *
     * <p>Which of those the loader can actually report differs, and that
     * difference is declared rather than papered over (GAP_LOG G85): NeoForge
     * calls this from {@code ClientPlayerNetworkEvent}'s LoggingIn, LoggingOut
     * <em>and</em> Clone (the last covers respawn and dimension change), while
     * Fabric 1.21.1 only offers {@code ClientPlayConnectionEvents} JOIN and
     * DISCONNECT — nothing fires on a respawn. The failure mode of the missing
     * reset is bounded: one tick's Y delta against a stale baseline, which
     * {@code StepSmoothing#isStepUp} only converts into an offset when it also
     * looks like a step (small, upward, ending on the ground), so at worst a
     * single sub-block smoothing impulse right after respawning.
     */
    public static void resetHistory() {
        hasBaseline = false;
        stepStartOffsetBlocks = 0.0f;
        currentDecayPerTick = STEP_DECAY_PER_TICK;
        ticksSinceStep = 0;
    }

    /**
     * How many blocks the render camera should currently be pulled down by
     * (0 if there's nothing to smooth). {@code partialTick} is the same
     * value the caller's {@code Camera#extractRenderState} received (26.x has
     * no {@code Camera#setup}; {@code GameRenderer#extractCamera} passes
     * {@code Camera#getCameraEntityPartialTicks}) — folding it into the
     * elapsed-ticks figure is what makes the decay advance smoothly across
     * frames within a tick, not just jump once per tick.
     */
    public static float currentOffsetBlocks(float partialTick) {
        if (stepStartOffsetBlocks == 0.0f) {
            return 0.0f;
        }
        float clampedPartialTick = Math.max(0.0f, Math.min(1.0f, partialTick));
        float elapsedTicks = ticksSinceStep + clampedPartialTick;
        return StepSmoothing.remainingOffset(elapsedTicks, stepStartOffsetBlocks, currentDecayPerTick);
    }
}
