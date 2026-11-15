package com.borislav.freeflow.combat.moves;

import com.borislav.freeflow.ai.CombatDirector;
import com.borislav.freeflow.ai.EnemySlot;
import com.borislav.freeflow.ai.EnemyStatus;
import com.borislav.freeflow.combat.Anim;
import com.borislav.freeflow.combat.CombatModeTracker;
import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.combat.ComboTracker;
import com.borislav.freeflow.combat.Directions;
import com.borislav.freeflow.combat.TargetSelector;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.CombatEventPayload;
import com.borislav.freeflow.network.CombatInputPayload;
import com.borislav.freeflow.network.StrikeStartPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * Strike: snap to the best target, lunge to it (movement done by the client, which
 * the server is told about via {@link StrikeStartPayload}) and hit on arrival.
 *
 * Timing is server-authoritative: the hit resolves after the lunge's travel ticks,
 * provided the player actually got close enough. Presses during a strike are
 * buffered (one deep) and fire as soon as the current one recovers.
 *
 * Striking a stunned enemy is a beatdown: fast, light hits where mashing is allowed;
 * the Nth hit is a finisher that knocks the enemy down. The same lunge/hit pipeline
 * also carries special takedowns (see {@link SpecialTakedownMove}).
 */
public class StrikeMove implements Move {

	/** resolveHit result: the strike bounced off a shield. */
	private static final int BLOCKED = -2;

	@Override
	public boolean execute(ServerPlayer player, CombatState state, CombatInputPayload input) {
		long now = player.level().getGameTime();
		if (now < state.staggerUntil) {
			return false; // staggered: presses are ignored
		}
		if (state.strikeHitTick >= 0 || now < state.nextStrikeTick) {
			// Busy. On-beat = during recovery or just before the current hit lands.
			// Beatdowns on a stunned enemy are meant to be mashed: never stagger.
			boolean onBeat = state.strikeHitTick < 0 || state.strikeHitTick - now <= FreeflowConfig.rhythmWindowTicks
					|| beatdownTarget(state, now) != null;
			if (!onBeat && ++state.earlyPresses >= FreeflowConfig.rhythmSpamLimit) {
				ComboTracker.stagger(player, state); // button mashing
				return true;
			}
			state.bufferedStrike = input; // keep the latest press
			return true;
		}
		start(player, state, input, now);
		return true;
	}

	@Override
	public void tick(ServerPlayer player, CombatState state) {
		long now = player.level().getGameTime();

		if (state.strikeHitTick >= 0 && now >= state.strikeHitTick) {
			state.strikeHitTick = -1;
			int recovery = resolveHit(player, state, now);
			if (recovery >= 0) {
				ComboTracker.onHit(player, state);
				state.nextStrikeTick = now + recovery;
			} else if (recovery == BLOCKED) {
				state.nextStrikeTick = now + FreeflowConfig.shieldRecoilTicks; // bounced: combo kept, no gain
			} else {
				ComboTracker.breakCombo(player, state);
				state.nextStrikeTick = now + FreeflowConfig.whiffRecoveryTicks;
			}
		}
		ComboTracker.tick(player, state);

		if (state.bufferedStrike != null && state.strikeHitTick < 0 && now >= state.nextStrikeTick
				&& now >= state.staggerUntil && now >= state.lockedUntil) {
			CombatInputPayload buffered = state.bufferedStrike;
			state.bufferedStrike = null;
			if (CombatModeTracker.isFreeflowActive(player, state)) {
				start(player, state, buffered, now);
			}
		}
	}

	/** The enemy being beaten down: last target, still stunned (not knocked down) and close. */
	@Nullable
	private static Mob beatdownTarget(CombatState state, long now) {
		Mob mob = state.lastStrikeMob;
		if (mob == null || !mob.isAlive()) {
			return null;
		}
		EnemySlot slot = state.enemies.get(mob.getId());
		return slot != null && slot.mob == mob && now < slot.stunnedUntil && !slot.isKnockedDown(now) ? mob : null;
	}

	private static void start(ServerPlayer player, CombatState state, CombatInputPayload input, long now) {
		Mob target = null;
		boolean directional = input.forward() * input.forward() + input.strafe() * input.strafe() > 0.04f;
		Mob beatdown = beatdownTarget(state, now);
		if (!directional && beatdown != null && beatdown.distanceTo(player) < 3.5) {
			target = beatdown; // keep pounding the stunned enemy unless steering elsewhere
		}
		if (target == null) {
			target = TargetSelector.select(player, input.aimYaw(), input.forward(), input.strafe(),
					ComboTracker.range(state));
		}
		launch(player, state, target, false, now);
	}

	/** Starts the lunge + hit at a chosen target ({@code null} = whiff). */
	public static void launch(ServerPlayer player, CombatState state, @Nullable Mob target, boolean special, long now) {
		state.strikeIndex++;
		state.earlyPresses = 0;
		InteractionHand hand = state.strikeIndex % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		player.swing(hand, true);

		if (target == null) {
			state.strikeTarget = null;
			state.nextStrikeTick = now + FreeflowConfig.whiffRecoveryTicks;
			ComboTracker.breakCombo(player, state); // swinging at air breaks the flow
			Anim.play(player, Anim.chooseStrike(state.strikeIndex, 0, 0));
			send(player, -1, 0);
			return;
		}

		double dist = Directions.horizontalDistance(player, target);
		double stop = stopDistance(target);
		int travel = 0;
		if (dist > stop + 0.3) {
			travel = Mth.clamp(Mth.ceil((dist - stop) / FreeflowConfig.lungeSpeed), 1, FreeflowConfig.maxLungeTicks);
		}

		// Pick the move: special takedown, beatdown jabs, a flying attack for long lunges,
		// or the next strike in the combo string (flashier as the combo grows).
		EnemySlot targetSlot = state.enemies.get(target.getId());
		boolean beatdown = !special && targetSlot != null && targetSlot.mob == target
				&& now < targetSlot.stunnedUntil && !targetSlot.isKnockedDown(now);
		String move = special ? Anim.SPECIAL_TAKEDOWN
				: beatdown ? Anim.beatdown(state.strikeIndex)
				: Anim.chooseStrike(state.strikeIndex, travel, state.combo);
		state.strikeStrength = Anim.strength(move);
		Anim.play(player, move);

		state.strikeTarget = target;
		state.strikeIsSpecial = special;
		state.lastStrikeMob = target;
		state.strikeHitTick = now + Math.max(1, travel);
		state.invulnerableUntil = Math.max(state.invulnerableUntil, now + travel + (special ? 6 : 2));
		send(player, target.getId(), travel);
	}

	/**
	 * Applies the hit if the target is still valid and the player is close enough.
	 * Returns the recovery ticks before the next strike, or -1 if nothing was hit.
	 */
	private static int resolveHit(ServerPlayer player, CombatState state, long now) {
		Mob target = state.strikeTarget;
		boolean special = state.strikeIsSpecial;
		state.strikeTarget = null;
		state.strikeIsSpecial = false;
		if (target == null || !target.isAlive() || target.level() != player.level()) {
			return -1;
		}
		if (Directions.horizontalDistance(player, target) > stopDistance(target) + FreeflowConfig.hitValidationSlack) {
			return -1; // the lunge didn't get us there (blocked, lag, or forged input)
		}
		ServerLevel level = player.serverLevel();

		if (special) {
			EnemyStatus.knockOut(player, target);
			EnemyStatus.event(player, CombatEventPayload.Type.SPECIAL_TAKEDOWN, target, 0);
			ImpactFx.hit(player, target, Anim.HEAVY);
			return ComboTracker.recoveryTicks(state) + 2;
		}

		EnemySlot slot = CombatDirector.slot(state, target);
		boolean beatdown = now < slot.stunnedUntil && !slot.isKnockedDown(now);
		if (EnemyStatus.shieldBlocks(player, target, beatdown)) {
			// Bounced off the shield: recoil. Stun it first, or vault over and hit it from behind.
			state.staggerUntil = now + FreeflowConfig.shieldRecoilTicks;
			state.bufferedStrike = null;
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK,
					SoundSource.PLAYERS, 1.0f, 0.9f);
			EnemyStatus.event(player, CombatEventPayload.Type.BLOCKED, target, FreeflowConfig.shieldRecoilTicks);
			return BLOCKED;
		}

		int strength = beatdown ? Anim.LIGHT : state.strikeStrength;
		float damage = beatdown
				? FreeflowConfig.beatdownDamage * ComboTracker.damage(state) / FreeflowConfig.strikeDamage
				: ComboTracker.damage(state) * ImpactFx.damageMultiplier(strength);

		target.invulnerableTime = 0; // freeflow hits are faster than vanilla i-frames
		if (!target.hurt(player.damageSources().playerAttack(player), damage)) {
			return -1;
		}
		if (beatdown) {
			// Vanilla hurt() knocks the target back ~0.4 per hit; a beatdown pins it in place,
			// otherwise it drifts out of reach before the finisher (and the ground takedown).
			target.setDeltaMovement(0.0, target.getDeltaMovement().y, 0.0);
			target.hurtMarked = true;
		}
		player.setLastHurtMob(target);
		player.causeFoodExhaustion(0.1f);
		if (!target.isAlive() && isLastEnemy(player, target)) {
			state.lastFinalHitTick = now;
			EnemyStatus.event(player, CombatEventPayload.Type.FINAL_HIT, target, 0);
		}

		int recovery = ComboTracker.recoveryTicks(state);
		if (beatdown) {
			recovery = FreeflowConfig.beatdownRecoveryTicks;
			slot.stunnedUntil = Math.max(slot.stunnedUntil, now + FreeflowConfig.beatdownStunExtendTicks);
			if (++slot.beatdownHits >= FreeflowConfig.beatdownFinisherHits && target.isAlive()) {
				target.knockback(FreeflowConfig.finisherKnockback, player.getX() - target.getX(), player.getZ() - target.getZ());
				EnemyStatus.knockDown(player, state, target, FreeflowConfig.knockdownTicks);
				Anim.play(player, Anim.FINISHER);
				strength = Anim.HEAVY;
				recovery = ComboTracker.recoveryTicks(state) + 2;
			}
		} else {
			slot.beatdownHits = 0;
			ImpactFx.knockback(player, target, strength);
		}

		if (!target.isAlive() || !slot.isKnockedDown(now)) {
			EnemyStatus.reactToHit(player, target, strength); // a finisher already sent its knockdown fall
		}
		ImpactFx.hit(player, target, strength);
		return recovery;
	}

	/** True if no other living enemy within combat range is still fighting the player. */
	private static boolean isLastEnemy(ServerPlayer player, Mob killed) {
		for (Mob mob : CombatModeTracker.findEngagedMobs(player)) {
			if (mob != killed && mob.isAlive()) {
				return false;
			}
		}
		return true;
	}

	public static double stopDistance(Mob target) {
		return target.getBbWidth() / 2.0 + FreeflowConfig.lungeStopGap;
	}

	public static void send(ServerPlayer player, int targetId, int travelTicks) {
		if (ServerPlayNetworking.canSend(player, StrikeStartPayload.TYPE)) {
			ServerPlayNetworking.send(player, new StrikeStartPayload(targetId, travelTicks));
		}
	}
}
