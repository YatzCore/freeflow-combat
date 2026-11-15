package com.borislav.freeflow.ai;

import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.CombatEventPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;

/** Stuns, knockdowns and knock-outs applied to enemies, plus the matching client events. */
public final class EnemyStatus {
	private EnemyStatus() {}

	/** Dazes the enemy: cancels its attack, it can't move much or attack for {@code ticks}. */
	public static void stun(ServerPlayer player, CombatState state, Mob mob, int ticks) {
		long now = player.level().getGameTime();
		if (isBoss(mob)) {
			ticks = Math.max(1, Math.round(ticks * FreeflowConfig.bossStunMultiplier));
		}
		EnemySlot slot = CombatDirector.slot(state, mob);
		cancelAttack(player, slot);
		slot.stunnedUntil = Math.max(slot.stunnedUntil, now + ticks);
		slot.nextAllowed = Math.max(slot.nextAllowed, slot.stunnedUntil + 5);
		immobilize(mob, ticks, 6);
		event(player, CombatEventPayload.Type.STUN, mob, (int) (slot.stunnedUntil - now));
	}

	/** Puts the enemy on the ground: a ground takedown can finish it. */
	public static void knockDown(ServerPlayer player, CombatState state, Mob mob, int ticks) {
		long now = player.level().getGameTime();
		EnemySlot slot = CombatDirector.slot(state, mob);
		if (isBoss(mob)) {
			slot.beatdownHits = 0; // bosses can't be knocked down
			return;
		}
		cancelAttack(player, slot);
		slot.knockedDownUntil = now + ticks;
		slot.stunnedUntil = 0;
		slot.beatdownHits = 0;
		slot.nextAllowed = Math.max(slot.nextAllowed, slot.knockedDownUntil + 10);
		immobilize(mob, ticks, 10);
		event(player, CombatEventPayload.Type.KNOCKDOWN, mob, ticks);
	}

	public static boolean isBoss(Mob mob) {
		return EnemyRole.of(mob) == EnemyRole.BOSS;
	}

	/** True if a shielded enemy blocks a strike from {@code player} (not stunned, attacked from the front). */
	public static boolean shieldBlocks(ServerPlayer player, Mob mob, boolean stunned) {
		if (stunned || EnemyRole.of(mob) != EnemyRole.SHIELDED) {
			return false;
		}
		float toPlayer = com.borislav.freeflow.combat.Directions.yawTo(mob, player);
		double angle = Math.abs(net.minecraft.util.Mth.wrapDegrees(toPlayer - mob.yBodyRot));
		return angle <= FreeflowConfig.shieldBlockAngle;
	}

	/** Takedown damage: knocks out normal enemies, heavy damage to bosses. */
	public static void knockOut(ServerPlayer player, Mob mob) {
		mob.invulnerableTime = 0;
		float damage = isBoss(mob) ? FreeflowConfig.bossTakedownDamage : mob.getMaxHealth() * 10.0f + 100.0f;
		mob.hurt(player.damageSources().playerAttack(player), damage);
	}

	private static void cancelAttack(ServerPlayer player, EnemySlot slot) {
		if (slot.isTelegraphing()) {
			slot.attackAt = -1;
			CombatDirector.sendTelegraph(player, slot.mob, 0);
		}
	}

	private static void immobilize(Mob mob, int ticks, int amplifier) {
		mob.getNavigation().stop();
		mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, amplifier, false, false));
	}

	public static void event(ServerPlayer player, CombatEventPayload.Type type, Mob mob, int ticks) {
		if (ServerPlayNetworking.canSend(player, CombatEventPayload.TYPE)) {
			ServerPlayNetworking.send(player, new CombatEventPayload(type, mob.getId(), ticks));
		}
	}
}
