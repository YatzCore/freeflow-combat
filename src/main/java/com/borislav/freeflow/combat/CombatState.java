package com.borislav.freeflow.combat;

import com.borislav.freeflow.ai.EnemySlot;
import com.borislav.freeflow.network.CombatInputPayload;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

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

	// --- Strike ---
	@Nullable
	public Mob strikeTarget;
	/** Game tick at which the current strike lands, or -1 when no strike is in flight. */
	public long strikeHitTick = -1;
	/** Earliest game tick the next strike may start. */
	public long nextStrikeTick = 0;
	/** Player ignores damage until this game tick (lunge i-frames). */
	public long invulnerableUntil = 0;
	/** One-deep input buffer for presses made while busy. */
	@Nullable
	public CombatInputPayload bufferedStrike;
	public int strikeIndex = 0;

	// --- Combo & rhythm ---
	public int combo = 0;
	/** Best combo of the current fight (for end-of-fight scoring later). */
	public int maxCombo = 0;
	/** Game tick of the last combo hit. */
	public long lastComboTick = 0;
	/** Off-beat presses during the current strike. */
	public int earlyPresses = 0;
	/** Inputs are ignored until this game tick. */
	public long staggerUntil = 0;

	// --- Director & counter ---
	/** Enemies fighting this player, by entity id. */
	public final Map<Integer, EnemySlot> enemies = new HashMap<>();
	public long lastTelegraphTick = -100;
	public long counterCooldownUntil = 0;

	/** Cancels everything in flight (combat ended, mode changed, death...). */
	public void clearMoves() {
		strikeTarget = null;
		strikeHitTick = -1;
		bufferedStrike = null;
		earlyPresses = 0;
	}

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
