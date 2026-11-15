package com.borislav.freeflow.combat;

/** Player-selected combat mode, cycled with the toggle key. */
public enum CombatMode {
	/** Enter combat automatically when hostiles engage, leave when calm. */
	AUTO,
	/** Always in combat mode (useful for testing / sparring). */
	FORCED_ON,
	/** Freeflow disabled entirely; vanilla combat. */
	OFF;

	public CombatMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static CombatMode byId(int id) {
		CombatMode[] v = values();
		return id >= 0 && id < v.length ? v[id] : AUTO;
	}
}
