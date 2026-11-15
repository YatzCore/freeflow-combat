package com.borislav.freeflow.combat;

import com.borislav.freeflow.config.FreeflowConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

/**
 * Picks which enemy a strike snaps to. With a direction held, the enemy closest to that
 * direction wins (Arkham-style "push toward who you want to hit"); with no direction, the
 * camera facing is used with a wider cone. Very close enemies are always eligible.
 */
public final class TargetSelector {
	private TargetSelector() {}

	@Nullable
	public static Mob select(ServerPlayer player, float aimYaw, float forward, float left, double range) {
		boolean directional = forward * forward + left * left > 0.04f;
		float desiredYaw = directional ? Directions.worldYaw(aimYaw, forward, left) : aimYaw;
		double cone = directional ? FreeflowConfig.strikeConeDirectional : FreeflowConfig.strikeConeNeutral;

		Mob best = null;
		double bestScore = Double.MAX_VALUE;
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range),
				mob -> isValidTarget(player, mob))) {
			double dist = Directions.horizontalDistance(player, mob);
			if (dist > range) {
				continue;
			}
			double angle = Math.abs(Mth.wrapDegrees(Directions.yawTo(player, mob) - desiredYaw));
			boolean inCone = angle <= cone;
			if (!inCone && dist > FreeflowConfig.strikeCloseRange) {
				continue;
			}

			double score = angle / 180.0 * 1.5 + dist / range;
			if (mob.getTarget() == player) {
				score -= 0.15; // prefer enemies actually fighting us
			}
			if (!inCone) {
				score += 1.0; // close-range fallback only
			}
			if (score < bestScore) {
				bestScore = score;
				best = mob;
			}
		}
		return best;
	}

	public static boolean isValidTarget(ServerPlayer player, Mob mob) {
		return mob.isAlive() && !mob.isRemoved() && !mob.isInvulnerable()
				&& (mob instanceof Enemy || mob.getTarget() == player)
				&& player.hasLineOfSight(mob);
	}
}
