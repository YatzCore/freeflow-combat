package com.borislav.freeflow.combat.moves;

import com.borislav.freeflow.combat.CombatModeTracker;
import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.combat.Directions;
import com.borislav.freeflow.combat.TargetSelector;
import com.borislav.freeflow.config.FreeflowConfig;
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

/**
 * Strike: snap to the best target, lunge to it (movement done by the client, which
 * the server is told about via {@link StrikeStartPayload}) and hit on arrival.
 *
 * Timing is server-authoritative: the hit resolves after the lunge's travel ticks,
 * provided the player actually got close enough. Presses during a strike are
 * buffered (one deep) and fire as soon as the current one recovers.
 */
public class StrikeMove implements Move {

	@Override
	public boolean execute(ServerPlayer player, CombatState state, CombatInputPayload input) {
		long now = player.level().getGameTime();
		if (state.strikeHitTick >= 0 || now < state.nextStrikeTick) {
			state.bufferedStrike = input; // busy: keep the latest press
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
			boolean hit = resolveHit(player, state);
			state.nextStrikeTick = now + (hit ? FreeflowConfig.strikeRecoveryTicks : FreeflowConfig.whiffRecoveryTicks);
		}

		if (state.bufferedStrike != null && state.strikeHitTick < 0 && now >= state.nextStrikeTick) {
			CombatInputPayload buffered = state.bufferedStrike;
			state.bufferedStrike = null;
			if (CombatModeTracker.isFreeflowActive(player, state)) {
				start(player, state, buffered, now);
			}
		}
	}

	private static void start(ServerPlayer player, CombatState state, CombatInputPayload input, long now) {
		state.strikeIndex++;
		InteractionHand hand = state.strikeIndex % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		player.swing(hand, true);

		Mob target = TargetSelector.select(player, input.aimYaw(), input.forward(), input.strafe(),
				FreeflowConfig.strikeRange);
		if (target == null) {
			state.strikeTarget = null;
			state.nextStrikeTick = now + FreeflowConfig.whiffRecoveryTicks;
			send(player, -1, 0);
			return;
		}

		double dist = Directions.horizontalDistance(player, target);
		double stop = stopDistance(target);
		int travel = 0;
		if (dist > stop + 0.3) {
			travel = Mth.clamp(Mth.ceil((dist - stop) / FreeflowConfig.lungeSpeed), 1, FreeflowConfig.maxLungeTicks);
		}

		state.strikeTarget = target;
		state.strikeHitTick = now + Math.max(1, travel);
		state.invulnerableUntil = now + travel + 2; // brief i-frames while dashing in
		send(player, target.getId(), travel);
	}

	/** Applies the hit if the target is still valid and the player is close enough. */
	private static boolean resolveHit(ServerPlayer player, CombatState state) {
		Mob target = state.strikeTarget;
		state.strikeTarget = null;
		if (target == null || !target.isAlive() || target.level() != player.level()) {
			return false;
		}
		if (Directions.horizontalDistance(player, target) > stopDistance(target) + FreeflowConfig.hitValidationSlack) {
			return false; // the lunge didn't get us there (blocked, lag, or forged input)
		}

		target.invulnerableTime = 0; // freeflow hits are faster than vanilla i-frames
		boolean hurt = target.hurt(player.damageSources().playerAttack(player), FreeflowConfig.strikeDamage);
		if (!hurt) {
			return false;
		}
		target.knockback(FreeflowConfig.strikeKnockback, player.getX() - target.getX(), player.getZ() - target.getZ());
		player.setLastHurtMob(target);
		player.causeFoodExhaustion(0.1f);

		ServerLevel level = player.serverLevel();
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG,
				SoundSource.PLAYERS, 1.0f, 0.9f + level.random.nextFloat() * 0.2f);
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.25, 0.25, 0.25, 0.2);
		return true;
	}

	public static double stopDistance(Mob target) {
		return target.getBbWidth() / 2.0 + FreeflowConfig.lungeStopGap;
	}

	private static void send(ServerPlayer player, int targetId, int travelTicks) {
		if (ServerPlayNetworking.canSend(player, StrikeStartPayload.TYPE)) {
			ServerPlayNetworking.send(player, new StrikeStartPayload(targetId, travelTicks));
		}
	}
}
