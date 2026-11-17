package com.borislav.freeflow.mixin.client;

import com.borislav.freeflow.client.RunController;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In a fight there's no jump button (Space runs), so 1-block steps are climbed automatically. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "isAutoJumpEnabled", at = @At("HEAD"), cancellable = true)
	private void freeflow$autoStepInFights(CallbackInfoReturnable<Boolean> cir) {
		if (RunController.active()) {
			cir.setReturnValue(true);
		}
	}
}
