package com.borislav.freeflow.mixin;

import com.borislav.freeflow.ai.CombatDirector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Pillagers (crossbow users) shooting a freeflow player ask the director for a turn.
 * The crossbow stays loaded, so the director's delayed shot still fires the bolt.
 */
@Mixin(RangedCrossbowAttackGoal.class)
public abstract class RangedCrossbowAttackGoalMixin {
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
