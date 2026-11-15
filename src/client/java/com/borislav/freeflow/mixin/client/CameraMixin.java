package com.borislav.freeflow.mixin.client;

import com.borislav.freeflow.client.camera.FreeflowCamera;
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

/** Replaces the vanilla third-person camera transform with the orbit camera while it is active. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Inject(method = "setup", at = @At("TAIL"))
	private void freeflow$orbit(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
			float partialTick, CallbackInfo ci) {
		if (FreeflowCamera.isActive() && entity == Minecraft.getInstance().player) {
			Vec3 pos = FreeflowCamera.computePosition(level, entity, partialTick);
			setRotation(FreeflowCamera.yaw(), FreeflowCamera.pitch());
			setPosition(pos);
		}
	}
}
