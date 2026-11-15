package com.borislav.freeflow.combat;

/** Inputs the client sends to the server. The server decides what actually happens. */
public enum CombatAction {
	NONE,
	STRIKE,
	COUNTER,
	EVADE,
	STUN,
	GROUND_TAKEDOWN,
	SPECIAL_TAKEDOWN,
	TOGGLE_MODE,
	SLIDE;

	/** Actions that only make sense while freeflow is active. */
	public boolean requiresFreeflow() {
		return this != NONE && this != TOGGLE_MODE;
	}

	public static CombatAction byId(int id) {
		CombatAction[] v = values();
		return id >= 0 && id < v.length ? v[id] : NONE;
	}
}
