package com.kuronami.steadysight.mixin;

import com.kuronami.steadysight.client.StepCameraTracker;
import com.kuronami.steadysight.platform.Services;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
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
 * <p>Same shape as the 1.21.1 cell's mixin (injecting at the {@code TAIL} of
 * {@code Camera#setup}), because 1.21.11 still has that method — it has not
 * been replaced by the extract/render-state split 26.x uses. Verified against
 * {@code minecraft_1.21.11_client_mappings.txt}: {@code Camera} has no
 * {@code extractRenderState} method at all on this version, only {@code
 * void setup(net.minecraft.world.level.Level, net.minecraft.world.entity.Entity,
 * boolean, boolean, float)}.
 *
 * <p><strong>The one signature change from 1.21.1</strong>: the first
 * parameter is {@link Level}, not {@code BlockGetter} — confirmed from the
 * same mappings file. Nothing else about the method's shape or the injection
 * point differs, so the body below is otherwise identical to the 1.21.1
 * cell's: {@code TAIL} still means this runs after vanilla's own
 * position/rotation math (including the third-person zoom-out {@code move()}
 * call) has finished, so the offset applies uniformly in first- and
 * third-person alike, and calling the shadowed {@link #setPosition(Vec3)}
 * (rather than writing the {@code position} field directly) keeps {@code
 * blockPosition} — which vanilla derives from it and uses for fog/fluid
 * detection — consistent with the smoothed value.
 *
 * <p><strong>Why a mixin and not a NeoForge event</strong>: unchanged from
 * 1.21.1 — {@code ViewportEvent.ComputeCameraAngles} only exposes yaw/pitch/roll
 * setters, and no NeoForge event lets a mod touch the camera's position.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private Vec3 position;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Inject(method = "setup", at = @At("TAIL"))
    private void steadysight$smoothStepCamera(
            Level level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!Services.CONFIG.smoothStepCamera()) {
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
