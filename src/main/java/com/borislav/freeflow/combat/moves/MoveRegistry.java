package com.borislav.freeflow.combat.moves;

import com.borislav.freeflow.combat.CombatAction;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/** Maps combat inputs to moves. Later milestones (and gadgets) register here. */
public final class MoveRegistry {
	private static final Map<CombatAction, Move> MOVES = new EnumMap<>(CombatAction.class);

	private MoveRegistry() {}

	public static void init() {
		register(CombatAction.STRIKE, new StrikeMove());
		register(CombatAction.COUNTER, new CounterMove());
		register(CombatAction.EVADE, new EvadeMove());
		register(CombatAction.STUN, new StunMove());
		register(CombatAction.GROUND_TAKEDOWN, new GroundTakedownMove());
		register(CombatAction.SPECIAL_TAKEDOWN, new SpecialTakedownMove());
	}

	public static void register(CombatAction action, Move move) {
		MOVES.put(action, move);
	}

	@Nullable
	public static Move get(CombatAction action) {
		return MOVES.get(action);
	}

	public static Collection<Move> all() {
		return MOVES.values();
	}
}
