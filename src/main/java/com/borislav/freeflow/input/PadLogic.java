package com.borislav.freeflow.input;

import com.borislav.freeflow.config.FreeflowConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns one controller snapshot per tick into game actions. Pure logic (no GLFW, no Minecraft
 * client), so it can be tested headless; the client feeds it real pad state (PadInput.java).
 * <p>
 * Layout copied from Batman: Arkham Knight (PlayStation) for the moves that exist in the mod:
 * <pre>
 *   In a fight (freeflow)                      Outside a fight (Minecraft)
 *   □  strike (mash on a stunned enemy          □ / R2  attack, mine (hold)
 *      = beatdown)                              L2      use / place
 *   △  counter                                  △       inventory
 *   ○  stun (cape stun)                         ✕       jump
 *   ✕  hold = run, double-tap = evade           L3      sprint
 *      (toward an enemy = vault over it)        ○       sneak (hold)
 *   R2 hold = crouch, tap while running = slide D-pad down  drop item
 *   R2 + △  ground takedown
 *   ○ + △   special combo takedown
 *   Always: left stick move, right stick camera, L1/R1 hotbar, R3 swap shoulder,
 *   D-pad up cycle combat mode, D-pad right toggle combat camera, Options pause, Create settings.
 * </pre>
 * Shoulder swap, camera toggle, combat mode and settings have no Arkham equivalent, so they sit on
 * buttons Arkham Knight leaves free in fist fights.
 */
public final class PadLogic {

	// Button bits (same order as GLFW's gamepad buttons; PlayStation names).
	public static final int CROSS = 0;
	public static final int CIRCLE = 1;
	public static final int SQUARE = 2;
	public static final int TRIANGLE = 3;
	public static final int L1 = 4;
	public static final int R1 = 5;
	public static final int CREATE = 6;
	public static final int OPTIONS = 7;
	public static final int PS = 8;
	public static final int L3 = 9;
	public static final int R3 = 10;
	public static final int DPAD_UP = 11;
	public static final int DPAD_RIGHT = 12;
	public static final int DPAD_DOWN = 13;
	public static final int DPAD_LEFT = 14;
	/** Triggers count as buttons when pressed past half-way. */
	public static final int L2 = 15;
	public static final int R2 = 16;

	/** ○ waits this many ticks for △ to join it (special takedown) before it stuns. */
	public static final int CHORD_TICKS = 2;

	/** One-shot actions. */
	public enum Cmd {
		STRIKE, COUNTER, STUN, EVADE, SLIDE, GROUND_TAKEDOWN, SPECIAL_TAKEDOWN,
		INVENTORY, DROP, HOTBAR_NEXT, HOTBAR_PREV, PAUSE, SETTINGS, CYCLE_MODE, TOGGLE_CAMERA, SWAP_SHOULDER
	}

	/** What the game knows that changes what a button means. */
	public record Context(boolean freeflow, boolean sprinting, boolean onGround, boolean knockedDownNear,
			boolean specialReady) {}

	/** Result of one tick: held states for the vanilla keys, and the one-shot actions. */
	public static final class Output {
		public boolean jump;
		public boolean sprint;
		public boolean sneak;
		public boolean attack;
		public boolean use;
		public final List<Cmd> cmds = new ArrayList<>();
	}

	private int lastButtons;
	private long tick;
	private long lastCrossPress = -100;
	private long lastTrianglePress = -100;
	/** Ticks left for a deferred ○ (waiting to see if △ joins); 0 = none. */
	private int circleWait;
	/** The current R2 press was used for a slide: don't crouch on it. */
	private boolean r2Slid;

	public static boolean down(int buttons, int button) {
		return (buttons & (1 << button)) != 0;
	}

	public static int bit(int button) {
		return 1 << button;
	}

	/**
	 * Forgets presses in progress (a menu opened, the pad was unplugged). Buttons still held count
	 * as already pressed, so the press that closed a menu doesn't also act in the game.
	 */
	public void reset(int heldButtons) {
		lastButtons = heldButtons;
		circleWait = 0;
		r2Slid = false;
		lastCrossPress = -100;
		lastTrianglePress = -100;
	}

	public Output tick(int buttons, Context c) {
		tick++;
		Output out = new Output();
		int pressed = buttons & ~lastButtons;
		lastButtons = buttons;

		// --- always ---
		if (down(pressed, OPTIONS)) out.cmds.add(Cmd.PAUSE);
		if (down(pressed, CREATE)) out.cmds.add(Cmd.SETTINGS);
		if (down(pressed, DPAD_UP)) out.cmds.add(Cmd.CYCLE_MODE);
		if (down(pressed, DPAD_RIGHT)) out.cmds.add(Cmd.TOGGLE_CAMERA);
		if (down(pressed, R3)) out.cmds.add(Cmd.SWAP_SHOULDER);
		if (down(pressed, L1)) out.cmds.add(Cmd.HOTBAR_PREV);
		if (down(pressed, R1)) out.cmds.add(Cmd.HOTBAR_NEXT);

		if (c.freeflow()) {
			fight(buttons, pressed, c, out);
		} else {
			circleWait = 0;
			r2Slid = false;
			out.jump = down(buttons, CROSS);
			out.sprint = down(buttons, L3);
			out.sneak = down(buttons, CIRCLE);
			out.attack = down(buttons, SQUARE) || down(buttons, R2);
			out.use = down(buttons, L2);
			if (down(pressed, TRIANGLE)) out.cmds.add(Cmd.INVENTORY);
			if (down(pressed, DPAD_DOWN)) out.cmds.add(Cmd.DROP);
		}
		return out;
	}

	private void fight(int buttons, int pressed, Context c, Output out) {
		// ✕: hold to run, double-tap to evade (toward an enemy: vault). Never jumps, as in Arkham.
		out.sprint = down(buttons, CROSS);
		if (down(pressed, CROSS)) {
			if (tick - lastCrossPress <= FreeflowConfig.doubleTapWindowTicks) {
				out.cmds.add(Cmd.EVADE);
				lastCrossPress = -100; // a third tap starts a new pair
			} else {
				lastCrossPress = tick;
			}
		}

		// R2: tap while running = slide; otherwise hold = crouch.
		if (down(pressed, R2)) {
			r2Slid = c.sprinting() && c.onGround();
			if (r2Slid) {
				out.cmds.add(Cmd.SLIDE);
			}
		}
		if (!down(buttons, R2)) {
			r2Slid = false;
		}
		out.sneak = down(buttons, R2) && !r2Slid;

		if (down(pressed, SQUARE)) {
			out.cmds.add(Cmd.STRIKE);
		}

		// △ never waits: counters must be instant.
		if (down(pressed, TRIANGLE)) {
			lastTrianglePress = tick;
			if (down(buttons, R2) && c.knockedDownNear()) {
				out.cmds.add(Cmd.GROUND_TAKEDOWN); // R2 + △
			} else if (c.specialReady() && (circleWait > 0 || down(pressed, CIRCLE))) {
				out.cmds.add(Cmd.SPECIAL_TAKEDOWN); // ○ + △
				circleWait = 0;
				pressed &= ~bit(CIRCLE);
			} else {
				out.cmds.add(Cmd.COUNTER);
			}
		}

		// ○: stun, unless it is the start (or the late half) of ○ + △.
		if (down(pressed, CIRCLE)) {
			if (c.specialReady() && tick != lastTrianglePress && tick - lastTrianglePress <= CHORD_TICKS) {
				out.cmds.add(Cmd.SPECIAL_TAKEDOWN); // △ came a moment earlier
			} else if (c.specialReady()) {
				if (circleWait > 0) {
					out.cmds.add(Cmd.STUN); // the previous ○ was a plain stun
				}
				circleWait = CHORD_TICKS;
			} else {
				out.cmds.add(Cmd.STUN);
			}
		} else if (circleWait > 0 && --circleWait == 0) {
			out.cmds.add(Cmd.STUN); // △ never joined
		}
	}
}
