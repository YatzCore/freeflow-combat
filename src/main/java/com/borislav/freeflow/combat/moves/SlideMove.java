package com.borislav.freeflow.combat.moves;

import com.borislav.freeflow.ai.EnemyStatus;
import com.borislav.freeflow.ai.MobReaction;
import com.borislav.freeflow.combat.Anim;
import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.combat.ComboTracker;
import com.borislav.freeflow.combat.FightStats;
import com.borislav.freeflow.combat.TargetSelector;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.CombatInputPayload;
import com.borislav.freeflow.network.EvadePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

/**
 * Slide (tap sprint while running): a feet-first slide along the running direction. Every
 * enemy the slide passes through is tripped and knocked down (bosses just stagger), adding to
 * the combo. Brief invulnerability at the start.
 */
public class SlideMove implements Move {

	@Override
	public boolean execute(ServerPlayer player, CombatState state, CombatInputPayload input) {
		long now = player.level().getGameTime();
		if (!player.isSprinting() || !player.onGround() || now < state.slideCooldownUntil || now < state.slideUntil) {
			return false;
		}
		state.strikeTarget = null;
		state.strikeHitTick = -1;
		state.bufferedStrike = null;

		state.slideUntil = now + FreeflowConfig.slideTicks;
		state.slideCooldownUntil = now + FreeflowConfig.slideTicks + FreeflowConfig.slideCooldownTicks;
		state.slideHit.clear();
		state.invulnerableUntil = Math.max(state.invulnerableUntil, now + FreeflowConfig.slideIframeTicks);
		state.nextStrikeTick = now + FreeflowConfig.slideTicks;

		Anim.play(player, Anim.SLIDE);
		state.fight.record(FightStats.MoveType.SLIDE);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRAVEL_STEP,
				SoundSource.PLAYERS, 1.0f, 0.6f);
		if (ServerPlayNetworking.canSend(player, EvadePayload.TYPE)) {
			ServerPlayNetworking.send(player, new EvadePayload(player.getYRot(), (float) FreeflowConfig.slideDistance,
					FreeflowConfig.slideTicks, -1, EvadePayload.SLIDE));
		}
		tripNearby(player, state, now); // anyone already in the way
		return true;
	}

	@Override
	public void tick(ServerPlayer player, CombatState state) {
		long now = player.level().getGameTime();
		if (state.slideUntil >= 0 && now <= state.slideUntil) {
			tripNearby(player, state, now);
		}
	}

	private static void tripNearby(ServerPlayer player, CombatState state, long now) {
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class,
				player.getBoundingBox().inflate(FreeflowConfig.slideTripRadius, 0.5, FreeflowConfig.slideTripRadius),
				m -> TargetSelector.isValidTarget(player, m) && !state.slideHit.contains(m.getId()))) {
			state.slideHit.add(mob.getId());
			mob.invulnerableTime = 0;
			mob.hurt(player.damageSources().playerAttack(player), FreeflowConfig.slideDamage);
			if (!mob.isAlive()) {
				MobReaction.send(mob, MobReaction.KNOCKED_OUT, 40, player);
			} else if (EnemyStatus.isBoss(mob)) {
				EnemyStatus.reactToHit(player, mob, Anim.MEDIUM);
			} else {
				EnemyStatus.knockDown(player, state, mob, FreeflowConfig.slideKnockdownTicks);
			}
			ComboTracker.onHit(player, state);
			ImpactFx.hit(player, mob, Anim.MEDIUM);
		}
	}
}
