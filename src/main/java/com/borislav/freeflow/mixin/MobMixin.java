package com.borislav.freeflow.mixin;

import com.borislav.freeflow.ai.CombatDirector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Catch-all for melee mobs that don't use MeleeAttackGoal (piglins, piglin brutes and other
 * brain-driven mobs): their hits on a freeflow player become director turn requests.
 * Hits the director performs itself are let through.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void freeflow$requestTurn(Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (!CombatDirector.performing && target instanceof ServerPlayer player && CombatDirector.isDirected(player)) {
			CombatDirector.requestAttack(player, (Mob) (Object) this);
			cir.setReturnValue(false);
		}
	}
}
