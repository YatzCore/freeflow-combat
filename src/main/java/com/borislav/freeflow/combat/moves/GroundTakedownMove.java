package com.borislav.freeflow.combat.moves;

import com.borislav.freeflow.ai.EnemySlot;
import com.borislav.freeflow.ai.EnemyStatus;
import com.borislav.freeflow.combat.Anim;
import com.borislav.freeflow.combat.FightStats;
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
import net.minecraft.world.phys.Vec3;
import com.borislav.freeflow.network.MoveToPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

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

		// The enemy lies on its back with its head away from where it was facing, so its chest is
		// ~0.9 blocks behind its feet. Kneel beside the chest (on the player's side) facing it.
		slot.downYaw = target.yBodyRot;
		Vec3 spot = approachSpot(player, target);
		Vec3 chest = chest(target);
		float faceYaw = (float) Math.toDegrees(Math.atan2(-(chest.x - spot.x), chest.z - spot.z));
		double dist = Math.sqrt(player.distanceToSqr(spot.x, player.getY(), spot.z));
		int travel = Mth.clamp(Mth.ceil(dist / FreeflowConfig.lungeSpeed), 1, FreeflowConfig.maxLungeTicks);
		if (ServerPlayNetworking.canSend(player, MoveToPayload.TYPE)) {
			ServerPlayNetworking.send(player, new MoveToPayload(spot.x, spot.z, travel, faceYaw));
		}
		EnemyStatus.event(player, CombatEventPayload.Type.TAKEDOWN_START, target, FreeflowConfig.groundTakedownTicks);
		Anim.play(player, Anim.GROUND_TAKEDOWN, FreeflowConfig.groundTakedownTicks);
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
		ImpactFx.hit(player, target, Anim.HEAVY);
		state.fight.record(FightStats.MoveType.GROUND_TAKEDOWN);
		EnemyStatus.event(player, CombatEventPayload.Type.TAKEDOWN, target, 0);
		player.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.3f, 0.5f);
		player.serverLevel().sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.3), target.getZ(),
				16, 0.4, 0.1, 0.4, 0.3);
	}

	/** Where a knocked-down enemy's chest is (it lies on its back, head behind its feet). */
	public static Vec3 chest(Mob mob) {
		float r = mob.yBodyRot * Mth.DEG_TO_RAD;
		double back = mob.getBbHeight() * 0.45;
		return new Vec3(mob.getX() + Mth.sin(r) * back, mob.getY(), mob.getZ() - Mth.cos(r) * back);
	}

	/** Spot beside the downed enemy's chest, on whichever side is closer to the player. */
	public static Vec3 approachSpot(ServerPlayer player, Mob mob) {
		Vec3 chest = chest(mob);
		float r = mob.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 side = new Vec3(Mth.cos(r), 0, Mth.sin(r)).scale(FreeflowConfig.groundTakedownSideOffset);
		Vec3 a = chest.add(side);
		Vec3 b = chest.subtract(side);
		return player.distanceToSqr(a) <= player.distanceToSqr(b) ? a : b;
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
