package com.borislav.freeflow.mixin.client;

import com.borislav.freeflow.client.pad.PadInput;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The right stick turns the view every frame, right after the mouse does (smooth, not 20 Hz). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
	private void freeflow$stickLook(CallbackInfo ci) {
		PadInput.turnPerFrame();
	}
}
