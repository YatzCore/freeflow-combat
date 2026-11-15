package com.borislav.freeflow.combat.moves;

import com.borislav.freeflow.ai.CombatDirector;
import com.borislav.freeflow.ai.EnemyStatus;
import com.borislav.freeflow.network.CombatEventPayload;
import com.borislav.freeflow.network.TelegraphPayload;
import com.borislav.freeflow.ai.EnemySlot;
import com.borislav.freeflow.combat.Anim;
import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.combat.ComboTracker;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.CombatInputPayload;
import com.borislav.freeflow.network.CounterPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * Counter: cancels the telegraphed attack that lands soonest (one press per attacker,
 * so multiple warnings need multiple presses), hits and stuns it, +1 combo. Pressing it
 * with nothing to counter locks counters out briefly (no spamming).
 */
public class CounterMove implements Move {

	@Override
	public boolean execute(ServerPlayer player, CombatState state, CombatInputPayload input) {
		long now = player.level().getGameTime();
		if (now < state.counterCooldownUntil) {
			return false;
		}

		// One press counters one attacker: the one whose hit lands soonest.
		// Several warnings at once need several presses; any you miss will hit you.
		// Armed (red) attacks can't be countered; ranged shots are deflected at any distance.
		EnemySlot target = null;
		boolean armedIncoming = false;
		double range = FreeflowConfig.counterRange;
		for (EnemySlot slot : state.enemies.values()) {
			if (!slot.isTelegraphing() || !slot.mob.isAlive()) {
				continue;
			}
			if (slot.kind == TelegraphPayload.ARMED) {
				armedIncoming = true;
				continue;
			}
			boolean inReach = slot.ranged || slot.mob.distanceToSqr(player) <= range * range;
			if (inReach && (target == null || slot.attackAt < target.attackAt)) {
				target = slot;
			}
		}

		if (target == null) {
			state.counterCooldownUntil = now + FreeflowConfig.counterWhiffTicks;
			Mob armed = armedIncoming ? firstArmed(state) : null;
			if (armed != null) {
				EnemyStatus.event(player, CombatEventPayload.Type.CANT_COUNTER, armed, 0); // "evade it!"
			}
			send(player, List.of());
			return true;
		}

		if (target.ranged) {
			deflect(player, state, target, now);
			Anim.play(player, Anim.COUNTER);
			send(player, List.of(target.mob.getId()));
			return true;
		}

		// A counter interrupts whatever strike was in flight.
		state.strikeTarget = null;
		state.strikeHitTick = -1;
		state.bufferedStrike = null;
		state.invulnerableUntil = now + 6;
		state.nextStrikeTick = now + 4;

		counter(player, state, target, now);
		Anim.play(player, Anim.COUNTER);
		player.swing(InteractionHand.MAIN_HAND, true);
		send(player, List.of(target.mob.getId()));
		return true;
	}

	/** Ranged counter: knock the shot aside. The shooter isn't stunned (it's far away). */
	private static void deflect(ServerPlayer player, CombatState state, EnemySlot slot, long now) {
		slot.attackAt = -1;
		slot.nextAllowed = now + FreeflowConfig.rangedCooldownMin;
		CombatDirector.sendTelegraph(player, slot.mob, 0);
		state.invulnerableUntil = Math.max(state.invulnerableUntil, now + 4);
		ComboTracker.onHit(player, state);
		player.swing(InteractionHand.OFF_HAND, true);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK,
				SoundSource.PLAYERS, 0.8f, 1.6f);
		EnemyStatus.event(player, CombatEventPayload.Type.DEFLECT, slot.mob, 0);
	}

	private static Mob firstArmed(CombatState state) {
		for (EnemySlot slot : state.enemies.values()) {
			if (slot.isTelegraphing() && slot.kind == TelegraphPayload.ARMED) {
				return slot.mob;
			}
		}
		return null;
	}

	private static void counter(ServerPlayer player, CombatState state, EnemySlot slot, long now) {
		Mob mob = slot.mob;
		mob.invulnerableTime = 0;
		mob.hurt(player.damageSources().playerAttack(player), FreeflowConfig.counterDamage);
		mob.knockback(FreeflowConfig.counterKnockback, player.getX() - mob.getX(), player.getZ() - mob.getZ());
		EnemyStatus.stun(player, state, mob, FreeflowConfig.counterStunTicks); // cancels the attack too
		slot.nextAllowed = now + FreeflowConfig.counterStunTicks + FreeflowConfig.enemyAttackCooldownMin;
		state.lastStrikeMob = mob; // strike right after a counter = beatdown
		ComboTracker.onHit(player, state);

		ServerLevel level = player.serverLevel();
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
				SoundSource.PLAYERS, 1.0f, 1.0f);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHIELD_BLOCK,
				SoundSource.PLAYERS, 0.6f, 1.4f);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, mob.getX(), mob.getY(0.6), mob.getZ(), 14, 0.3, 0.3, 0.3, 0.3);
	}

	private static void send(ServerPlayer player, List<Integer> ids) {
		if (ServerPlayNetworking.canSend(player, CounterPayload.TYPE)) {
			ServerPlayNetworking.send(player, new CounterPayload(ids));
		}
	}
}
