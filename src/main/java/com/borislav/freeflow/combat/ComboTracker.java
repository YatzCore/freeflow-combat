package com.borislav.freeflow.combat;

import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.ComboPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Combo meter rules: +1 per landed hit, breaks after {@link FreeflowConfig#comboTimeoutTicks}
 * without a hit, on a whiff, on a stagger, or when the player takes damage.
 * Scaling helpers turn the combo into damage, range and speed bonuses.
 */
public final class ComboTracker {
	private ComboTracker() {}

	public static void onHit(ServerPlayer player, CombatState state) {
		state.combo++;
		state.maxCombo = Math.max(state.maxCombo, state.combo);
		state.lastComboTick = player.level().getGameTime();
		send(player, state, ComboPayload.Event.HIT);
	}

	public static void breakCombo(ServerPlayer player, CombatState state) {
		if (state.combo == 0) {
			return;
		}
		state.combo = 0;
		send(player, state, ComboPayload.Event.BREAK);
	}

	public static void stagger(ServerPlayer player, CombatState state) {
		long now = player.level().getGameTime();
		state.staggerUntil = now + FreeflowConfig.staggerTicks;
		state.bufferedStrike = null;
		state.earlyPresses = 0;
		if (FreeflowConfig.staggerBreaksCombo) {
			state.combo = 0;
		}
		send(player, state, ComboPayload.Event.STAGGER);
	}

	/** Breaks the combo when it has timed out. Never while a strike is still in flight. */
	public static void tick(ServerPlayer player, CombatState state) {
		long now = player.level().getGameTime();
		if (state.combo > 0 && state.strikeHitTick < 0 && now - state.lastComboTick > FreeflowConfig.comboTimeoutTicks) {
			breakCombo(player, state);
		}
	}

	/** Combo count that still adds bonuses. */
	private static int scaled(CombatState state) {
		return Mth.clamp(state.combo, 0, FreeflowConfig.comboScalingCap);
	}

	public static float damage(CombatState state) {
		return FreeflowConfig.strikeDamage * (1.0f + scaled(state) * FreeflowConfig.comboDamagePerHit);
	}

	public static double range(CombatState state) {
		return FreeflowConfig.strikeRange + scaled(state) * FreeflowConfig.comboRangePerHit;
	}

	public static int recoveryTicks(CombatState state) {
		return Math.max(1, FreeflowConfig.strikeRecoveryTicks - state.combo / FreeflowConfig.comboRecoveryStep);
	}

	public static void send(ServerPlayer player, CombatState state, ComboPayload.Event event) {
		if (!ServerPlayNetworking.canSend(player, ComboPayload.TYPE)) {
			return;
		}
		long now = player.level().getGameTime();
		int ticksLeft = state.combo > 0
				? (int) Math.max(0, FreeflowConfig.comboTimeoutTicks - (now - state.lastComboTick)) : 0;
		int stagger = (int) Math.max(0, state.staggerUntil - now);
		ServerPlayNetworking.send(player, new ComboPayload(state.combo, ticksLeft, stagger, event));
	}
}
