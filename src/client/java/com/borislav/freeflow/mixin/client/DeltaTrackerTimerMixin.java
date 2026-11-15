package com.borislav.freeflow.mixin.client;

import com.borislav.freeflow.client.camera.Impact;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hit-stop: while {@link Impact#isFrozen()}, game time doesn't advance on the client, so the
 * world (entities, animations, particles) holds still for a few milliseconds on impact.
 * The camera and mouse keep working. Capped at 150 ms per hit.
 */
@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {
	@Shadow
	private long lastMs;

	@Shadow
	private float deltaTicks;

	@Inject(method = "advanceGameTime", at = @At("HEAD"), cancellable = true)
	private void freeflow$hitStop(long timeMillis, CallbackInfoReturnable<Integer> cir) {
		if (Impact.isFrozen()) {
			lastMs = timeMillis;
			deltaTicks = 0.0f;
			cir.setReturnValue(0);
		}
	}
}
