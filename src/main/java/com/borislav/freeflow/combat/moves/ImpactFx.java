package com.borislav.freeflow.combat.moves;

import com.borislav.freeflow.ai.EnemyStatus;
import com.borislav.freeflow.combat.Anim;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.CombatEventPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Hit impact: damage/knockback scaling by move strength, layered sounds and particles, and
 * the HIT event that makes the client do hit-stop, camera shake and an FOV punch.
 */
public final class ImpactFx {
	private ImpactFx() {}

	/** Damage multiplier per strength (light jabs are quick, heavy moves hit harder). */
	public static float damageMultiplier(int strength) {
		return switch (strength) {
			case Anim.LIGHT -> 0.8f;
			case Anim.HEAVY -> 1.35f;
			default -> 1.0f;
		};
	}

	/** Knockback per strength; heavy hits also lift the enemy off its feet. */
	public static void knockback(ServerPlayer player, Mob target, int strength) {
		double kb = switch (strength) {
			case Anim.LIGHT -> FreeflowConfig.strikeKnockback * 0.6;
			case Anim.HEAVY -> FreeflowConfig.heavyKnockback;
			default -> FreeflowConfig.strikeKnockback;
		};
		target.knockback(kb, player.getX() - target.getX(), player.getZ() - target.getZ());
		if (strength == Anim.HEAVY && !EnemyStatus.isBoss(target)) {
			Vec3 v = target.getDeltaMovement();
			target.setDeltaMovement(v.x, Math.max(v.y, FreeflowConfig.heavyLift), v.z);
			target.hurtMarked = true;
		}
	}

	/** Sound + particles for everyone, and the HIT event for the striking player's camera. */
	public static void hit(ServerPlayer player, Mob target, int strength) {
		ServerLevel level = player.serverLevel();
		double x = target.getX();
		double y = target.getY(0.6);
		double z = target.getZ();
		float pitch = 0.9f + level.random.nextFloat() * 0.2f;

		level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS,
				strength == Anim.LIGHT ? 0.8f : 1.0f, strength == Anim.LIGHT ? pitch + 0.25f : pitch);
		level.sendParticles(ParticleTypes.CRIT, x, y, z, 6 + strength * 4, 0.3, 0.3, 0.3, 0.25);
		if (strength >= Anim.MEDIUM) {
			level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9f, pitch - 0.15f);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0, 0, 0, 0);
		}
		if (strength >= Anim.HEAVY) {
			level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.1f, 0.6f);
			level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0);
		}
		EnemyStatus.event(player, CombatEventPayload.Type.HIT, target, strength);
	}
}
