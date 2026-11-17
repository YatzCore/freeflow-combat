package com.borislav.freeflow.client.pad;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.client.ClientCombatState;
import com.borislav.freeflow.client.ClientCombo;
import com.borislav.freeflow.client.ClientInputHandler;
import com.borislav.freeflow.client.camera.FreeflowCamera;
import com.borislav.freeflow.client.config.SettingsScreen;
import com.borislav.freeflow.client.hud.StatusMarkers;
import com.borislav.freeflow.combat.CombatAction;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.input.PadLogic;
import com.borislav.freeflow.mixin.client.KeyMappingAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * PS5 DualSense (or any gamepad GLFW recognises) through GLFW's gamepad API: no extra library.
 * <ul>
 *   <li>Buttons: once per tick, through {@link PadLogic} (the Arkham Knight layout).</li>
 *   <li>Left stick: written into the movement input before it is made camera-relative, so it
 *       also aims strikes and evades exactly like WASD.</li>
 *   <li>Right stick: every frame, through the same path as the mouse (orbits the combat camera).</li>
 * </ul>
 * Keyboard and mouse keep working alongside. Menus are left to the mouse (or Controlify); the pad
 * only closes them (○, △ or Options).
 */
public final class PadInput {
	private PadInput() {}

	private static final PadLogic LOGIC = new PadLogic();
	private static final GLFWGamepadState STATE = GLFWGamepadState.create();
	private static final boolean CONTROLIFY = FabricLoader.getInstance().isModLoaded("controlify");

	private static int jid = -1;
	private static int scanCooldown = 0;
	private static boolean loggedJoysticks = false;
	private static boolean loggedControlify = false;

	private static int buttons;
	private static float lx;
	private static float ly;
	private static long lastFrameNanos = System.nanoTime();
	private static int menuButtons;

	// Vanilla keys the pad is holding down (only changed on the pad's own presses/releases,
	// so a key held on the keyboard is never released by the pad).
	private static final boolean[] held = new boolean[5];
	private static final int JUMP = 0;
	private static final int SPRINT = 1;
	private static final int SNEAK = 2;
	private static final int ATTACK = 3;
	private static final int USE = 4;

	public static boolean enabled() {
		if (CONTROLIFY && !loggedControlify) {
			loggedControlify = true;
			FreeflowMod.LOGGER.info("Controlify is installed: freeflow's own controller support is off");
		}
		return FreeflowConfig.padEnabled && !CONTROLIFY;
	}

	public static boolean connected() {
		return jid >= 0;
	}

	/** True while the pad holds sprint (the keyboard's tap-Ctrl slide must not read it as a tap). */
	public static boolean holdsSprint() {
		return held[SPRINT];
	}

	// ------------------------------------------------------------------ per tick: buttons

	public static void tick(Minecraft mc) {
		if (!enabled() || !poll()) {
			releaseAll(mc);
			return;
		}
		LocalPlayer player = mc.player;
		if (player == null) {
			releaseAll(mc);
			return;
		}
		if (mc.screen != null) {
			releaseAll(mc);
			LOGIC.reset(buttons);
			int pressed = buttons & ~menuButtons;
			menuButtons = buttons;
			if (PadLogic.down(pressed, PadLogic.CIRCLE) || PadLogic.down(pressed, PadLogic.TRIANGLE)
					|| PadLogic.down(pressed, PadLogic.OPTIONS)) {
				mc.screen.onClose();
			}
			return;
		}
		menuButtons = buttons;

		boolean freeflow = ClientCombatState.isFreeflowActive();
		PadLogic.Context ctx = new PadLogic.Context(freeflow, player.isSprinting(), player.onGround(),
				freeflow && StatusMarkers.knockedDownNear(player, FreeflowConfig.groundTakedownRange),
				ClientCombo.combo >= FreeflowConfig.specialTakedownCost);
		PadLogic.Output out = LOGIC.tick(buttons, ctx);

		boolean wasSprinting = held[SPRINT];
		hold(mc.options.keyJump, JUMP, out.jump);
		hold(mc.options.keySprint, SPRINT, out.sprint);
		hold(mc.options.keyShift, SNEAK, out.sneak);
		hold(mc.options.keyAttack, ATTACK, out.attack);
		hold(mc.options.keyUse, USE, out.use);
		if (freeflow && wasSprinting && !out.sprint) {
			player.setSprinting(false); // as in Arkham: let go of ✕ and you stop running
		}

		for (PadLogic.Cmd cmd : out.cmds) {
			run(mc, player, cmd);
		}
	}

	private static void run(Minecraft mc, LocalPlayer player, PadLogic.Cmd cmd) {
		switch (cmd) {
			case STRIKE -> ClientInputHandler.padAction(CombatAction.STRIKE);
			case COUNTER -> ClientInputHandler.padAction(CombatAction.COUNTER);
			case STUN -> ClientInputHandler.padAction(CombatAction.STUN);
			case SLIDE -> ClientInputHandler.padAction(CombatAction.SLIDE);
			case GROUND_TAKEDOWN -> ClientInputHandler.padAction(CombatAction.GROUND_TAKEDOWN);
			case SPECIAL_TAKEDOWN -> ClientInputHandler.padAction(CombatAction.SPECIAL_TAKEDOWN);
			case EVADE -> {
				float[] dir = stick(lx, ly);
				ClientInputHandler.padAction(CombatAction.EVADE, dir[0], dir[1]);
			}
			case CYCLE_MODE -> ClientInputHandler.padAction(CombatAction.TOGGLE_MODE);
			case TOGGLE_CAMERA -> FreeflowCamera.userEnabled = !FreeflowCamera.userEnabled;
			case SWAP_SHOULDER -> FreeflowCamera.swapShoulder();
			case INVENTORY -> click(mc.options.keyInventory);
			case DROP -> click(mc.options.keyDrop);
			case HOTBAR_NEXT -> player.getInventory().swapPaint(-1);
			case HOTBAR_PREV -> player.getInventory().swapPaint(1);
			case PAUSE -> mc.pauseGame(false);
			case SETTINGS -> mc.setScreen(SettingsScreen.create(null));
		}
	}

	private static void hold(KeyMapping key, int slot, boolean want) {
		if (held[slot] == want) {
			return;
		}
		held[slot] = want;
		key.setDown(want);
		if (want) {
			click(key); // what a real key press does: attack/use act on the press itself
		}
	}

	private static void click(KeyMapping key) {
		KeyMappingAccessor k = (KeyMappingAccessor) key;
		k.freeflow$setClickCount(k.freeflow$getClickCount() + 1);
	}

	private static void releaseAll(Minecraft mc) {
		KeyMapping[] keys = {mc.options.keyJump, mc.options.keySprint, mc.options.keyShift, mc.options.keyAttack,
				mc.options.keyUse};
		for (int i = 0; i < held.length; i++) {
			if (held[i]) {
				held[i] = false;
				keys[i].setDown(false);
			}
		}
	}

	// ------------------------------------------------------------------ left stick: movement

	/** Called from the movement input tick, before it is made camera-relative. */
	public static void applyMovement(Input input, boolean sneaking, float sneakSpeed) {
		Minecraft mc = Minecraft.getInstance();
		if (!enabled() || jid < 0 || mc.screen != null) {
			return;
		}
		if (input.forwardImpulse != 0 || input.leftImpulse != 0) {
			return; // the keyboard is moving: it wins
		}
		float[] dir = stick(lx, ly);
		if (dir[0] == 0 && dir[1] == 0) {
			return;
		}
		float scale = sneaking ? sneakSpeed : 1.0f;
		input.forwardImpulse = dir[0] * scale;
		input.leftImpulse = dir[1] * scale;
		input.up = dir[0] > 0.3f;
		input.down = dir[0] < -0.3f;
		input.left = dir[1] > 0.3f;
		input.right = dir[1] < -0.3f;
	}

	/** Stick position as (forward, left), radial deadzone removed, length 0..1. */
	private static float[] stick(float x, float y) {
		float mag = Mth.sqrt(x * x + y * y);
		float dz = (float) FreeflowConfig.padDeadzone;
		if (mag <= dz) {
			return new float[] {0, 0};
		}
		float m = Math.min(1.0f, (mag - dz) / (1.0f - dz));
		return new float[] {-y / mag * m, -x / mag * m};
	}

	// ------------------------------------------------------------------ right stick: camera

	/** Called every frame right after the mouse turned the view. */
	public static void turnPerFrame() {
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - lastFrameNanos) / 1.0e9);
		lastFrameNanos = now;
		Minecraft mc = Minecraft.getInstance();
		if (!enabled() || jid < 0 || mc.player == null || mc.screen != null || !mc.isWindowActive()) {
			return;
		}
		if (!GLFW.glfwGetGamepadState(jid, STATE)) {
			return;
		}
		float rx = STATE.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X);
		float ry = STATE.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y);
		float mag = Mth.sqrt(rx * rx + ry * ry);
		float dz = (float) FreeflowConfig.padDeadzone;
		if (mag <= dz) {
			return;
		}
		float m = Math.min(1.0f, (mag - dz) / (1.0f - dz));
		float curve = m * m; // fine aim near the centre, full speed at the edge
		double speed = FreeflowConfig.padLookSpeed * curve * dt;
		double yaw = rx / mag * speed;
		double pitch = ry / mag * speed * FreeflowConfig.padLookPitchScale * (FreeflowConfig.padInvertY ? -1 : 1);
		// Entity.turn multiplies by 0.15; the combat camera intercepts it while it orbits.
		mc.player.turn(yaw / 0.15, pitch / 0.15);
	}

	// ------------------------------------------------------------------ GLFW

	/** Reads the pad; false if none is connected. */
	private static boolean poll() {
		if (jid >= 0 && !GLFW.glfwJoystickIsGamepad(jid)) {
			FreeflowMod.LOGGER.info("Controller disconnected");
			jid = -1;
			LOGIC.reset(0);
		}
		if (jid < 0) {
			if (scanCooldown-- > 0) {
				return false;
			}
			scanCooldown = 20;
			jid = find();
			if (jid < 0) {
				return false;
			}
			FreeflowMod.LOGGER.info("Controller connected: {} ({}), guid {}", GLFW.glfwGetGamepadName(jid),
					GLFW.glfwGetJoystickName(jid), GLFW.glfwGetJoystickGUID(jid));
		}
		if (!GLFW.glfwGetGamepadState(jid, STATE)) {
			return false;
		}
		int b = 0;
		for (int i = 0; i <= GLFW.GLFW_GAMEPAD_BUTTON_LAST; i++) {
			if (STATE.buttons(i) == GLFW.GLFW_PRESS) {
				b |= PadLogic.bit(i);
			}
		}
		if (trigger(STATE.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER))) {
			b |= PadLogic.bit(PadLogic.L2);
		}
		if (trigger(STATE.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER))) {
			b |= PadLogic.bit(PadLogic.R2);
		}
		buttons = b;
		lx = STATE.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X);
		ly = STATE.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
		return true;
	}

	/** Trigger axes rest at -1 and reach +1 fully pulled. */
	private static boolean trigger(float axis) {
		return (axis + 1.0f) / 2.0f >= FreeflowConfig.padTriggerThreshold;
	}

	private static int find() {
		for (int i = GLFW.GLFW_JOYSTICK_1; i <= GLFW.GLFW_JOYSTICK_LAST; i++) {
			if (GLFW.glfwJoystickPresent(i) && GLFW.glfwJoystickIsGamepad(i)) {
				return i;
			}
		}
		if (!loggedJoysticks) {
			// A controller GLFW can't map shows up here: its GUID is what a mapping line needs.
			for (int i = GLFW.GLFW_JOYSTICK_1; i <= GLFW.GLFW_JOYSTICK_LAST; i++) {
				if (GLFW.glfwJoystickPresent(i)) {
					loggedJoysticks = true;
					FreeflowMod.LOGGER.warn("Joystick {} ({}) is not recognised as a gamepad",
							GLFW.glfwGetJoystickName(i), GLFW.glfwGetJoystickGUID(i));
				}
			}
		}
		return -1;
	}

	/** For the F7 debug overlay. */
	public static String debugLine() {
		if (!enabled()) {
			return "Controller: off" + (CONTROLIFY ? " (Controlify)" : "");
		}
		if (jid < 0) {
			return "Controller: none";
		}
		float[] d = stick(lx, ly);
		return String.format("Controller: %s  buttons %s  move %.2f/%.2f", GLFW.glfwGetGamepadName(jid),
				Integer.toBinaryString(buttons), d[0], d[1]);
	}
}
