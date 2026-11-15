package com.borislav.freeflow.mixin.client;

import com.borislav.freeflow.client.camera.RelativeMovement;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks movement input to make it camera-relative while the orbit camera is active. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void freeflow$cameraRelative(boolean sneaking, float sneakSpeed, CallbackInfo ci) {
		RelativeMovement.onInputTick((KeyboardInput) (Object) this);
	}
}
