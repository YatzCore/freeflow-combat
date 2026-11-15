package com.borislav.freeflow.mixin.client;

import com.borislav.freeflow.client.camera.FreeflowCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the orbit camera is active, mouse movement orbits the camera instead of turning the player. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void freeflow$orbitCamera(double dYaw, double dPitch, CallbackInfo ci) {
		if (FreeflowCamera.isActive() && (Object) this == Minecraft.getInstance().player) {
			FreeflowCamera.turn(dYaw, dPitch);
			ci.cancel();
		}
	}
}
