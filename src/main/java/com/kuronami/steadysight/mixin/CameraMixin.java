package com.kuronami.steadysight.mixin;

import com.kuronami.steadysight.client.StepCameraTracker;
import com.kuronami.steadysight.config.SteadySightConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nudges the render camera's Y position down right after an automatic
 * step-up, then lets it decay back to zero over a few frames — this is what
 * "smooth step camera" actually is (DESIGN_COMPILE.md's non-negotiable for
 * this feature: only the camera moves, never the player's real position).
 *
 * <p><strong>Why a mixin and not a NeoForge event:</strong> {@code
 * ViewportEvent.ComputeCameraAngles} (checked in the decompiled 1.21.1
 * NeoForge sources, {@code net/neoforged/neoforge/client/event/ViewportEvent.java})
 * only exposes yaw/pitch/roll setters — there is no NeoForge event anywhere
 * in that file, or in {@code ViewportEvent}'s Javadoc-listed siblings ({@code
 * RenderFog}, {@code ComputeFogColor}, {@code ComputeFov}), that lets a mod
 * touch the camera's <em>position</em>. {@link Camera#setup} (decompiled
 * 1.21.1 {@code net/minecraft/client/Camera.java}) is the only place that
 * value gets computed, and it isn't wrapped in any event.
 *
 * <p><strong>Prior art (researched, not guessed):</strong> the same author's
 * open-source {@code Coun7ered/smooth_f5} ("Countered's Smooth F5", a sibling
 * to the closed-source "Countered's Smooth Steps" this task was pointed at)
 * solves the same category of problem — smoothing the camera across a
 * discontinuous change — with exactly this shape of mixin: {@code
 * CameraMixin} injects into {@code Camera}'s position-setting method and
 * overwrites the vanilla-computed position with a smoothed one, never
 * touching the entity/player at all. That confirms camera-only interpolation
 * via a {@code Camera} mixin is this problem's established solution, not
 * something invented for this mod.
 *
 * <p>Injecting at {@code @At("TAIL")} means this runs after {@link
 * Camera#setup} has already finished its own position/rotation math
 * (including the third-person zoom-out {@code move()} call), so the offset
 * applies uniformly in first- and third-person alike. Calling the shadowed
 * {@link #setPosition(Vec3)} (rather than writing the {@code position} field
 * directly) keeps {@code blockPosition} — which vanilla derives from it and
 * uses for fog/fluid detection — consistent with the smoothed value instead
 * of quietly drifting out of sync with it.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private Vec3 position;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Inject(method = "setup", at = @At("TAIL"))
    private void steadysight$smoothStepCamera(
            BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!SteadySightConfig.smoothStepCamera()) {
            return;
        }
        // Only ever offset the camera that is following this client's own
        // player — a camera set up for some other entity (e.g. a spectated
        // player) has nothing to do with StepCameraTracker's tracked history.
        if (entity == null || entity != Minecraft.getInstance().player) {
            return;
        }

        float offset = StepCameraTracker.currentOffsetBlocks(partialTick);
        if (offset == 0.0f) {
            return;
        }

        this.setPosition(this.position.subtract(0.0, offset, 0.0));
    }
}
