package com.borislav.freeflow.mixin.client;

import com.borislav.freeflow.client.camera.CinematicShots;
import com.borislav.freeflow.client.dev.AnimationPreview;
import com.borislav.freeflow.client.camera.FreeflowCamera;
import com.borislav.freeflow.client.camera.Impact;
import com.borislav.freeflow.config.FreeflowConfig;
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
 * Replaces the vanilla third-person camera transform with the orbit camera while it is
 * active, blends in cinematic shots, and hides the player model when the camera is
 * squeezed right up against it (by marking the camera as not detached).
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Inject(method = "setup", at = @At("TAIL"))
	private void freeflow$orbit(BlockGetter level, Entity entity, boolean detachedArg, boolean mirrored,
			float partialTick, CallbackInfo ci) {
		if (AnimationPreview.cameraPos != null && entity == Minecraft.getInstance().player) {
			setRotation(AnimationPreview.cameraYaw, AnimationPreview.cameraPitch); // dev preview: fixed camera
			setPosition(AnimationPreview.cameraPos);
			return;
		}
		if (!FreeflowCamera.isActive() || entity != Minecraft.getInstance().player) {
			return;
		}
		Vec3 pos = FreeflowCamera.computePosition(level, entity, partialTick);
		float yaw = FreeflowCamera.yaw();
		float pitch = FreeflowCamera.pitch();

		CinematicShots.Pose shot = CinematicShots.apply(level, entity, pos, yaw, pitch, partialTick);
		if (shot != null) {
			pos = shot.position();
			yaw = shot.yaw();
			pitch = shot.pitch();
		} else if (FreeflowCamera.currentDistance() < FreeflowConfig.hidePlayerDistance) {
			detached = false; // too close: don't draw the player model over the whole screen
		}
		Impact.frame();
		setRotation(yaw, pitch);
		setPosition(pos.add(Impact.shakeOffset()));
	}
}
