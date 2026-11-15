package com.borislav.freeflow.combat.moves;

import com.borislav.freeflow.combat.CombatState;
import com.borislav.freeflow.network.CombatInputPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * A server-side freeflow move triggered by a combat input. Gadgets will plug in
 * through the same interface later (registered in {@link MoveRegistry}).
 */
public interface Move {
	/**
	 * Handles the input. Returns true if it was accepted (executed or buffered).
	 * Only called while freeflow is active for the player.
	 */
	boolean execute(ServerPlayer player, CombatState state, CombatInputPayload input);

	/** Per-tick update for moves that span several ticks. */
	default void tick(ServerPlayer player, CombatState state) {}
}
