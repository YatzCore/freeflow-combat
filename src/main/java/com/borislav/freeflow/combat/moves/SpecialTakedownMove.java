package com.borislav.freeflow.combat.moves;

import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.combat.ComboTracker;
import com.borislav.freeflow.combat.TargetSelector;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.ComboPayload;
import com.borislav.freeflow.network.CombatInputPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * Special takedown (Z): needs a combo of at least {@link FreeflowConfig#specialTakedownCost};
 * spends it to lunge at the targeted enemy and knock it out in one hit (bosses take
 * heavy damage instead). The player is invulnerable during the move.
 */
public class SpecialTakedownMove implements Move {

	@Override
	public boolean execute(ServerPlayer player, CombatState state, CombatInputPayload input) {
		long now = player.level().getGameTime();
		if (state.combo < FreeflowConfig.specialTakedownCost || state.strikeHitTick >= 0) {
			return false;
		}
		Mob target = TargetSelector.select(player, input.aimYaw(), input.forward(), input.strafe(),
				ComboTracker.range(state));
		if (target == null) {
			return false; // nothing to take down: keep the combo
		}
		state.combo -= FreeflowConfig.specialTakedownCost;
		state.lastComboTick = now;
		ComboTracker.send(player, state, ComboPayload.Event.HIT);
		state.bufferedStrike = null;
		StrikeMove.launch(player, state, target, true, now);
		return true;
	}
}
