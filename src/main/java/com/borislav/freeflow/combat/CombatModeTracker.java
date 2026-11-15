package com.borislav.freeflow.combat;

import com.borislav.freeflow.config.FreeflowConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.List;

/** Decides when a player enters and leaves combat mode. */
public final class CombatModeTracker {
	private CombatModeTracker() {}

	public static void tick(ServerPlayer player, CombatState state) {
		if (player.isSpectator() || !player.isAlive()) {
			state.setEngagedCount(0);
			state.setInCombat(false);
			state.calmTicks = 0;
			return;
		}

		if (player.tickCount % FreeflowConfig.combatScanInterval == 0) {
			state.setEngagedCount(findEngagedMobs(player).size());
		}

		switch (state.mode) {
			case OFF -> state.setInCombat(false);
			case FORCED_ON -> state.setInCombat(true);
			case AUTO -> {
				if (state.engagedCount > 0) {
					state.calmTicks = 0;
					state.setInCombat(true);
				} else if (state.inCombat && ++state.calmTicks >= FreeflowConfig.combatCalmTicks) {
					state.calmTicks = 0;
					state.setInCombat(false);
				}
			}
		}
	}

	/** Living mobs within the engage radius whose current target is this player. */
	public static List<Mob> findEngagedMobs(ServerPlayer player) {
		double r = FreeflowConfig.combatEngageRadius;
		return player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(r),
				mob -> mob.isAlive() && mob.getTarget() == player && mob.distanceToSqr(player) <= r * r);
	}

	/** Whether freeflow inputs are allowed right now (in combat + fists). */
	public static boolean isFreeflowActive(ServerPlayer player, CombatState state) {
		return state.inCombat && (FreeflowConfig.allowNonEmptyHand || player.getMainHandItem().isEmpty());
	}
}
