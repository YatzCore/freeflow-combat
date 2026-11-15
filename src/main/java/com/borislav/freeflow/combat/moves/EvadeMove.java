package com.borislav.freeflow.combat.moves;

import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.combat.Directions;
import com.borislav.freeflow.combat.TargetSelector;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.CombatInputPayload;
import com.borislav.freeflow.network.EvadePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * Evade: a quick dodge in the held direction (camera-relative; backward if nothing is
 * held) with brief invulnerability. Evading toward a close enemy vaults over it and
 * lands behind it. Both interrupt a strike in progress and keep the combo; a vault
 * also refreshes the combo timer (it's part of the flow).
 */
public class EvadeMove implements Move {

	@Override
	public boolean execute(ServerPlayer player, CombatState state, CombatInputPayload input) {
		long now = player.level().getGameTime();
		if (now < state.evadeCooldownUntil) {
			return false;
		}

		boolean directional = input.forward() * input.forward() + input.strafe() * input.strafe() > 0.04f;
		float yaw = directional
				? Directions.worldYaw(input.aimYaw(), input.forward(), input.strafe())
				: input.aimYaw() + 180.0f; // no direction: hop back
		Mob vaultTarget = directional ? findVaultTarget(player, yaw) : null;

		// Interrupt any strike in flight.
		state.strikeTarget = null;
		state.strikeHitTick = -1;
		state.bufferedStrike = null;
		state.nextStrikeTick = now + 2;

		state.invulnerableUntil = Math.max(state.invulnerableUntil, now + FreeflowConfig.evadeIframeTicks);
		state.evadeCooldownUntil = now + FreeflowConfig.evadeCooldownTicks;
		state.lastVaultTarget = vaultTarget;

		float distance;
		int ticks;
		if (vaultTarget != null) {
			yaw = Directions.yawTo(player, vaultTarget);
			distance = (float) (Directions.horizontalDistance(player, vaultTarget) + FreeflowConfig.vaultLandBehind);
			ticks = FreeflowConfig.vaultTicks;
			if (state.combo > 0) {
				state.lastComboTick = now;
			}
		} else {
			distance = (float) FreeflowConfig.evadeDistance;
			ticks = FreeflowConfig.evadeTicks;
		}

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				vaultTarget != null ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_NODAMAGE,
				SoundSource.PLAYERS, 0.7f, vaultTarget != null ? 1.3f : 1.6f);

		if (ServerPlayNetworking.canSend(player, EvadePayload.TYPE)) {
			ServerPlayNetworking.send(player,
					new EvadePayload(yaw, distance, ticks, vaultTarget != null ? vaultTarget.getId() : -1));
		}
		return true;
	}

	/** Closest enemy roughly in the evade direction and close enough to vault over. */
	@Nullable
	public static Mob findVaultTarget(ServerPlayer player, float yaw) {
		double range = FreeflowConfig.vaultRange;
		Mob best = null;
		double bestDist = Double.MAX_VALUE;
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range),
				mob -> TargetSelector.isValidTarget(player, mob))) {
			double dist = Directions.horizontalDistance(player, mob);
			double angle = Math.abs(Mth.wrapDegrees(Directions.yawTo(player, mob) - yaw));
			if (dist <= range && angle <= FreeflowConfig.vaultCone && dist < bestDist) {
				best = mob;
				bestDist = dist;
			}
		}
		return best;
	}
}
