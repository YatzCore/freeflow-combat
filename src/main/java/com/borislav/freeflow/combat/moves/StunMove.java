package com.borislav.freeflow.combat.moves;

import com.borislav.freeflow.ai.EnemyStatus;
import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.combat.TargetSelector;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.CombatInputPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;

/**
 * Stun (G): dazes the closest enemy in the held direction (short range). A stunned enemy
 * can't attack and can be beaten down by striking it. Keeps the combo alive but doesn't
 * add to it. Also cancels the enemy's attack if it was winding one up.
 */
public class StunMove implements Move {

	@Override
	public boolean execute(ServerPlayer player, CombatState state, CombatInputPayload input) {
		long now = player.level().getGameTime();
		if (now < state.stunCooldownUntil) {
			return false;
		}
		Mob target = TargetSelector.select(player, input.aimYaw(), input.forward(), input.strafe(),
				FreeflowConfig.stunRange);
		player.swing(InteractionHand.OFF_HAND, true);
		if (target == null) {
			state.stunCooldownUntil = now + FreeflowConfig.stunWhiffTicks;
			return true;
		}

		EnemyStatus.stun(player, state, target, FreeflowConfig.stunTicks);
		state.lastStrikeMob = target;
		state.stunCooldownUntil = now + FreeflowConfig.stunCooldownTicks;
		if (state.combo > 0) {
			state.lastComboTick = now; // keeps the flow going
		}

		player.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.7f);
		player.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.6), target.getZ(),
				1, 0, 0, 0, 0);
		return true;
	}
}
