package com.borislav.freeflow.mixin;

import com.borislav.freeflow.ai.CombatDirector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes melee attacks against freeflow players through the {@link CombatDirector}:
 * mobs ask for a turn instead of hitting, and hold back (circle) while waiting.
 * Mobs fighting anyone else behave exactly like vanilla.
 */
@Mixin(MeleeAttackGoal.class)
public abstract class MeleeAttackGoalMixin {
	@Shadow
	@Final
	protected PathfinderMob mob;

	@Shadow
	protected abstract boolean canPerformAttack(LivingEntity target);

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void freeflow$waitForTurn(CallbackInfo ci) {
		if (mob.getTarget() instanceof ServerPlayer player && CombatDirector.shouldWait(player, mob)) {
			CombatDirector.circle(player, mob);
			ci.cancel();
		}
	}

	@Inject(method = "checkAndPerformAttack", at = @At("HEAD"), cancellable = true)
	private void freeflow$requestTurn(LivingEntity target, CallbackInfo ci) {
		if (target instanceof ServerPlayer player && CombatDirector.isDirected(player)) {
			ci.cancel();
			if (canPerformAttack(target)) {
				CombatDirector.requestAttack(player, mob);
			}
		}
	}
}
