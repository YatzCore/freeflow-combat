package com.borislav.freeflow.mixin;

import com.borislav.freeflow.ai.CombatDirector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Skeletons (and other bow users) shooting a freeflow player ask the director for a turn
 * instead of firing: the director telegraphs the shot and fires it later.
 */
@Mixin(RangedBowAttackGoal.class)
public abstract class RangedBowAttackGoalMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/monster/RangedAttackMob;performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V"))
	private void freeflow$requestShot(RangedAttackMob shooter, LivingEntity target, float power) {
		if (target instanceof ServerPlayer player && shooter instanceof Mob mob && CombatDirector.isDirected(player)) {
			CombatDirector.requestRangedAttack(player, mob);
			return;
		}
		shooter.performRangedAttack(target, power);
	}
}
