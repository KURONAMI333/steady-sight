package com.kuronami.steadysight.mixin;

import com.kuronami.steadysight.client.StepCameraTracker;
import com.kuronami.steadysight.platform.Services;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
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
 * ViewportEvent.ComputeCameraAngles} only exposes yaw/pitch/roll setters —
 * there is no NeoForge event that lets a mod touch the camera's
 * <em>position</em>. Same as on 1.21.1.
 *
 * <p><strong>Injection point on 26.x</strong> (the 1.21.1 cell injects at the
 * tail of {@code Camera#setup}, which does not exist here). 26.x renders from
 * extracted state, and the frame order in {@code GameRenderer} — read from
 * the bytecode of {@code minecraft_26.2_client.jar} — is:
 *
 * <ol>
 *   <li>{@code GameRenderer#update} → {@code Camera#update(DeltaTracker)}:
 *       recomputes {@code position} from the camera entity every frame (which
 *       is why an offset written here can never accumulate).</li>
 *   <li>{@code Minecraft#pick}: uses {@code getCameraEntity()}, <em>not</em>
 *       the camera position, so block targeting is unaffected by this mixin
 *       either way.</li>
 *   <li>{@code GameRenderer#extractCamera} → {@code
 *       Camera#extractRenderState(CameraRenderState, float)} → then {@code
 *       Camera#getFluidInCamera()} and the fog setup, both off the
 *       <em>Camera</em>.</li>
 *   <li>{@code LevelExtractor#extract(DeltaTracker, Camera, float)}: reads
 *       {@code Camera#position()} five times, and two of those are render
 *       origins, not culling — {@code extractVisibleBlockEntities} and
 *       {@code extractBlockOutline} build a {@code PoseStack.translate(
 *       blockPos - cameraPos)}.</li>
 *   <li>{@code LevelRenderer}: reads {@code CameraRenderState#pos}.</li>
 * </ol>
 *
 * <p>That last pair is why this writes through {@link #setPosition(Vec3)} at
 * the <em>head</em> of {@code extractRenderState} rather than rewriting
 * {@code CameraRenderState#pos} at its tail. Rewriting only the render state
 * would move the view matrix while leaving {@code Camera#position()} — which
 * everything in step 4 still reads, after this method returns — at the
 * unsmoothed value, shearing the block-breaking overlay and the block outline
 * away from the terrain they sit on. Offsetting the Camera itself before the
 * extraction reproduces exactly what the 1.21.1 cell gets from injecting into
 * {@code setup}: one position, and every downstream reader consistent with it.
 * Calling the shadowed {@link #setPosition(Vec3)} (rather than writing the
 * {@code position} field) is also what keeps {@code blockPosition} — which
 * vanilla derives from it, and which {@code extractRenderState} copies into
 * {@code CameraRenderState#blockPos} a few instructions later — in sync.
 *
 * <p>Known and accepted: {@code cullFrustum} is prepared inside {@code
 * Camera#update}, i.e. before this offset exists, so culling is computed
 * against the unsmoothed position. The offset is a fraction of a block and
 * decays within about six ticks, so this cannot pull anything visible out of
 * the frustum. The 1.21.1 cell has the same property.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private Vec3 position;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    public abstract Entity entity();

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void steadysight$smoothStepCamera(CameraRenderState state, float partialTick, CallbackInfo ci) {
        if (!Services.CONFIG.smoothStepCamera()) {
            return;
        }
        // Only ever offset the camera that is following this client's own
        // player — a camera set up for some other entity (e.g. a spectated
        // player) has nothing to do with StepCameraTracker's tracked history.
        Entity entity = this.entity();
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
