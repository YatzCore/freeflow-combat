package com.borislav.freeflow.combat.moves;

import com.borislav.freeflow.ai.CombatDirector;
import com.borislav.freeflow.ai.EnemyStatus;
import com.borislav.freeflow.ai.MobReaction;
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
import net.minecraft.world.phys.Vec3;

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

	/**
	 * Counter variants rotate so counters don't all look the same (bosses always get the strike):
	 * <ul>
	 *   <li>STRIKE: block the arm, punch to the face: the enemy staggers, stunned.</li>
	 *   <li>KICK: parry, kick to the chest: the enemy is launched back and rolls, stunned.</li>
	 *   <li>THROW: grab the attacking arm and throw the enemy over your shoulder: it flies over
	 *       you and lands on its back behind you, knocked down (ground takedown ready).</li>
	 * </ul>
	 */
	public static int chooseVariant(CombatState state, Mob mob) {
		if (EnemyStatus.isBoss(mob)) {
			return MobReaction.COUNTER_STRIKE;
		}
		return Math.floorMod(state.counterIndex++, 3);
	}

	private static void counter(ServerPlayer player, CombatState state, EnemySlot slot, long now) {
		Mob mob = slot.mob;
		int variant = chooseVariant(state, mob);
		state.lastCounterVariant = variant;

		mob.invulnerableTime = 0;
		mob.hurt(player.damageSources().playerAttack(player), FreeflowConfig.counterDamage);
		int downTicks;
		switch (variant) {
			case MobReaction.COUNTER_THROW -> {
				// Over the shoulder: send it flying over the player to land behind them.
				Vec3 over = new Vec3(player.getX() - mob.getX(), 0, player.getZ() - mob.getZ()).normalize();
				mob.setDeltaMovement(over.x * FreeflowConfig.counterThrowSpeed, FreeflowConfig.counterThrowLift,
						over.z * FreeflowConfig.counterThrowSpeed);
				mob.hurtMarked = true;
				EnemyStatus.knockDown(player, state, mob, FreeflowConfig.counterThrowDownTicks);
				downTicks = FreeflowConfig.counterThrowDownTicks;
				Anim.play(player, Anim.COUNTER_THROW);
			}
			case MobReaction.COUNTER_KICK -> {
				ImpactFx.knockback(player, mob, Anim.HEAVY);
				EnemyStatus.stun(player, state, mob, FreeflowConfig.counterStunTicks);
				downTicks = FreeflowConfig.counterStunTicks;
				Anim.play(player, Anim.COUNTER_KICK);
			}
			default -> {
				mob.knockback(FreeflowConfig.counterKnockback, player.getX() - mob.getX(), player.getZ() - mob.getZ());
				EnemyStatus.stun(player, state, mob, FreeflowConfig.counterStunTicks); // cancels the attack too
				downTicks = FreeflowConfig.counterStunTicks;
				Anim.play(player, Anim.COUNTER);
			}
		}
		slot.nextAllowed = Math.max(slot.nextAllowed, now + downTicks + FreeflowConfig.enemyAttackCooldownMin);
		state.lastStrikeMob = mob; // strike right after a counter = beatdown
		ComboTracker.onHit(player, state);

		// Paired enemy animation (sent last so it overrides the generic stun/knockdown pose).
		if (mob.isAlive()) {
			MobReaction.send(mob, MobReaction.COUNTERED, downTicks, variant, player);
		} else {
			MobReaction.send(mob, MobReaction.KNOCKED_OUT, 40, player);
		}
		ImpactFx.hit(player, mob, variant == MobReaction.COUNTER_STRIKE ? Anim.MEDIUM : Anim.HEAVY);

		ServerLevel level = player.serverLevel();
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
