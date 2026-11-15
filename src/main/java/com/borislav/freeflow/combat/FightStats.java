package com.borislav.freeflow.combat;

import java.util.EnumSet;
import java.util.Set;

/**
 * Everything the player did during one fight, turned into an Arkham-style rating at the end:
 * points per action, a max-combo bonus, a variety multiplier for mixing moves, and a
 * FLAWLESS bonus for taking no damage.
 */
public class FightStats {

	/** Distinct kinds of moves, for the variety multiplier. */
	public enum MoveType {
		STRIKE, FLYING, BEATDOWN, FINISHER, COUNTER, COUNTER_KICK, COUNTER_THROW, DEFLECT, STUN, EVADE, VAULT,
		GROUND_TAKEDOWN, SPECIAL_TAKEDOWN, SLIDE
	}

	public static final int POINTS_HIT = 10;
	public static final int POINTS_COUNTER = 25;
	public static final int POINTS_KNOCKOUT = 20;
	public static final int POINTS_TAKEDOWN = 50;
	public static final int POINTS_SPECIAL = 75;
	public static final int POINTS_PER_MAX_COMBO = 15;
	public static final double VARIETY_STEP = 0.1;
	public static final double VARIETY_MAX = 1.8;
	public static final double FLAWLESS_MULTIPLIER = 1.5;
	public static final int POINTS_PER_XP = 50;

	public long startTick;
	public int hits;
	public int counters;
	public int knockouts;
	public int takedowns;
	public int specials;
	public int damageTaken;
	public final Set<MoveType> moves = EnumSet.noneOf(MoveType.class);

	public void record(MoveType type) {
		moves.add(type);
		switch (type) {
			case STRIKE, FLYING, BEATDOWN, FINISHER -> hits++;
			case COUNTER, COUNTER_KICK, COUNTER_THROW, DEFLECT -> counters++;
			case GROUND_TAKEDOWN -> takedowns++;
			case SPECIAL_TAKEDOWN -> specials++;
			default -> {}
		}
	}

	/** True if anything worth rating happened. */
	public boolean hasActivity() {
		return hits + counters + takedowns + specials + knockouts > 0;
	}

	public boolean isFlawless() {
		return damageTaken == 0;
	}

	public double varietyMultiplier() {
		return Math.min(VARIETY_MAX, 1.0 + VARIETY_STEP * Math.max(0, moves.size() - 1));
	}

	public int score(int maxCombo) {
		double base = hits * POINTS_HIT + counters * POINTS_COUNTER + knockouts * POINTS_KNOCKOUT
				+ takedowns * POINTS_TAKEDOWN + specials * POINTS_SPECIAL + maxCombo * POINTS_PER_MAX_COMBO;
		double total = base * varietyMultiplier() * (isFlawless() ? FLAWLESS_MULTIPLIER : 1.0);
		return (int) Math.round(total);
	}

	public static int xpFor(int score) {
		return score <= 0 ? 0 : Math.max(1, score / POINTS_PER_XP);
	}
}
