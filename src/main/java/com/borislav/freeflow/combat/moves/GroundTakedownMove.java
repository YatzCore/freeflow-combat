package com.borislav.freeflow.combat.moves;

import com.borislav.freeflow.ai.EnemySlot;
import com.borislav.freeflow.ai.EnemyStatus;
import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.combat.ComboTracker;
import com.borislav.freeflow.combat.Directions;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.CombatEventPayload;
import com.borislav.freeflow.network.CombatInputPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/**
 * Ground takedown (V): finishes the nearest knocked-down enemy. Takes
 * {@link FreeflowConfig#groundTakedownTicks} during which the player is locked in place
 * and NOT invulnerable: getting hit interrupts it (the enemy stays down).
 */
public class GroundTakedownMove implements Move {

	@Override
	public boolean execute(ServerPlayer player, CombatState state, CombatInputPayload input) {
		long now = player.level().getGameTime();
		if (state.takedownTarget != null) {
			return false;
		}
		Mob target = null;
		double best = FreeflowConfig.groundTakedownRange;
		for (EnemySlot slot : state.enemies.values()) {
			double dist = slot.mob.distanceTo(player);
			if (slot.mob.isAlive() && slot.isKnockedDown(now) && dist <= best) {
				target = slot.mob;
				best = dist;
			}
		}
		if (target == null) {
			return false;
		}

		state.strikeTarget = null;
		state.strikeHitTick = -1;
		state.bufferedStrike = null;
		state.takedownTarget = target;
		state.takedownEndTick = now + FreeflowConfig.groundTakedownTicks;
		state.lockedUntil = state.takedownEndTick;

		// Keep the enemy down until the takedown finishes.
		EnemySlot slot = state.enemies.get(target.getId());
		slot.knockedDownUntil = Math.max(slot.knockedDownUntil, state.takedownEndTick + 5);

		double dist = Directions.horizontalDistance(player, target);
		int travel = dist > StrikeMove.stopDistance(target) + 0.3
				? Mth.clamp(Mth.ceil((dist - StrikeMove.stopDistance(target)) / FreeflowConfig.lungeSpeed), 1, 3) : 0;
		StrikeMove.send(player, target.getId(), travel);
		EnemyStatus.event(player, CombatEventPayload.Type.TAKEDOWN_START, target, FreeflowConfig.groundTakedownTicks);
		return true;
	}

	@Override
	public void tick(ServerPlayer player, CombatState state) {
		Mob target = state.takedownTarget;
		if (target == null || player.level().getGameTime() < state.takedownEndTick) {
			return;
		}
		state.takedownTarget = null;
		state.takedownEndTick = -1;
		if (!target.isAlive() || target.distanceTo(player) > FreeflowConfig.groundTakedownRange + 1.5) {
			return;
		}
		EnemyStatus.knockOut(player, target);
		ComboTracker.onHit(player, state);
		EnemyStatus.event(player, CombatEventPayload.Type.TAKEDOWN, target, 0);
		player.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.3f, 0.5f);
		player.serverLevel().sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.3), target.getZ(),
				16, 0.4, 0.1, 0.4, 0.3);
	}

	/** Getting hit during a ground takedown interrupts it. */
	public static void interrupt(ServerPlayer player, CombatState state) {
		if (state.takedownTarget == null) {
			return;
		}
		EnemyStatus.event(player, CombatEventPayload.Type.TAKEDOWN_INTERRUPTED, state.takedownTarget, 0);
		state.takedownTarget = null;
		state.takedownEndTick = -1;
		state.lockedUntil = 0;
	}
}
