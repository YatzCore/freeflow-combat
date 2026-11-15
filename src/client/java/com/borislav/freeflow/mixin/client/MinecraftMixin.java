package com.borislav.freeflow.mixin.client;

import com.borislav.freeflow.client.ClientCombatState;
import com.borislav.freeflow.client.ClientInputHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While freeflow is active, the attack key becomes "strike", the use key becomes "counter",
 * and block breaking is disabled.
 * Outside combat (or with an item in hand) vanilla behaviour is untouched.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void freeflow$replaceAttack(CallbackInfoReturnable<Boolean> cir) {
		if (ClientCombatState.isFreeflowActive()) {
			ClientInputHandler.onStrike();
			cir.setReturnValue(false);
		}
	}

	/** Right click = counter while freeflow is active (vanilla repeats this while held; only new presses count). */
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void freeflow$replaceUse(CallbackInfo ci) {
		if (ClientCombatState.isFreeflowActive()) {
			ClientInputHandler.onUsePressed();
			ci.cancel();
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void freeflow$blockMining(boolean leftClick, CallbackInfo ci) {
		if (ClientCombatState.isFreeflowActive()) {
			Minecraft self = (Minecraft) (Object) this;
			if (self.gameMode != null) {
				self.gameMode.stopDestroyBlock();
			}
			ci.cancel();
		}
	}
}
