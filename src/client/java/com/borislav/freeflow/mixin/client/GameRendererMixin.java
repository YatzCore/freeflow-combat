package com.borislav.freeflow.mixin.client;

import com.borislav.freeflow.client.camera.CombatFraming;
import com.borislav.freeflow.client.camera.FreeflowCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Widens the world FOV a little in big fights (combat framing). Hand rendering is untouched. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void freeflow$framingFov(Camera camera, float partialTick, boolean useFovSetting,
			CallbackInfoReturnable<Double> cir) {
		if (useFovSetting && FreeflowCamera.isActive() && CombatFraming.fovBonus() > 0.01) {
			cir.setReturnValue(cir.getReturnValue() + CombatFraming.fovBonus());
		}
	}
}
