package com.borislav.freeflow.combat;

/**
 * Server-side combat state for one player. Later milestones add combo,
 * current target, cooldowns, etc.
 */
public class CombatState {
	public CombatMode mode = CombatMode.AUTO;
	public boolean inCombat = false;
	/** Number of mobs within range currently targeting this player. */
	public int engagedCount = 0;
	/** Ticks since an engaged mob was last seen (AUTO mode exit timer). */
	public int calmTicks = 0;

	/** Last accepted input + running counter, mainly for the debug overlay. */
	public CombatAction lastAction = CombatAction.NONE;
	public int acceptedInputs = 0;

	/** Set whenever a synced field changes; the manager sends a sync packet and clears it. */
	public boolean dirty = true;

	public void setInCombat(boolean value) {
		if (inCombat != value) {
			inCombat = value;
			dirty = true;
		}
	}

	public void setEngagedCount(int value) {
		if (engagedCount != value) {
			engagedCount = value;
			dirty = true;
		}
	}
}
