package com.borislav.freeflow.client;

import com.borislav.freeflow.client.camera.FreeflowCamera;
import com.borislav.freeflow.client.camera.RelativeMovement;
import com.borislav.freeflow.client.hud.DebugOverlay;
import com.borislav.freeflow.client.hud.StatusMarkers;
import com.borislav.freeflow.combat.CombatAction;
import com.borislav.freeflow.config.FreeflowConfig;
import com.borislav.freeflow.network.CombatInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Turns key presses into combat inputs sent to the server. */
public final class ClientInputHandler {
	private ClientInputHandler() {}

	public static void tick(Minecraft client) {
		if (client.player == null) {
			return;
		}

		while (Keybinds.settings.consumeClick()) {
			client.setScreen(com.borislav.freeflow.client.config.SettingsScreen.create(client.screen));
		}
		while (Keybinds.debugOverlay.consumeClick()) {
			DebugOverlay.enabled = !DebugOverlay.enabled;
		}
		while (Keybinds.toggleMode.consumeClick()) {
			send(CombatAction.TOGGLE_MODE);
		}
		while (Keybinds.swapShoulder.consumeClick()) {
			FreeflowCamera.swapShoulder();
		}
		while (Keybinds.toggleCamera.consumeClick()) {
			FreeflowCamera.userEnabled = !FreeflowCamera.userEnabled;
		}

		useHeld = client.options.keyUse.isDown(); // re-arm the right-click counter once released

		boolean active = ClientCombatState.isFreeflowActive();
		pressed(Keybinds.counter, active, CombatAction.COUNTER);
		pressed(Keybinds.evade, active, CombatAction.EVADE);
		pressed(Keybinds.stun, active, CombatAction.STUN);
		while (Keybinds.groundTakedown.consumeClick()) {
			if (!active) {
				continue;
			}
			if (StatusMarkers.knockedDownNear(client.player, FreeflowConfig.groundTakedownRange)) {
				send(CombatAction.GROUND_TAKEDOWN);
			}
		}
		while (Keybinds.specialTakedown.consumeClick()) {
			if (!active) {
				continue;
			}
			if (ClientCombo.combo >= FreeflowConfig.specialTakedownCost) {
				send(CombatAction.SPECIAL_TAKEDOWN);
			}
		}
		checkDoubleTap(client, active);
		checkSlide(client, active);
	}

	// --- Slide: tap the sprint key (Ctrl) while already running ---

	private static boolean sprintWasDown = false;

	private static void checkSlide(Minecraft client, boolean active) {
		boolean down = client.options.keySprint.isDown();
		if (down && !sprintWasDown && active && client.player.isSprinting() && client.player.onGround()) {
			send(CombatAction.SLIDE);
		}
		sprintWasDown = down;
	}

	// --- Double-tap a movement key to evade in that direction ---

	/** forward/left input for W, S, A, D. */
	private static final float[][] TAP_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final boolean[] wasDown = new boolean[4];
	private static final long[] lastTap = {-100, -100, -100, -100};

	private static void checkDoubleTap(Minecraft client, boolean active) {
		KeyMapping[] keys = {client.options.keyUp, client.options.keyDown, client.options.keyLeft, client.options.keyRight};
		long now = ClientCombo.clientTick;
		for (int i = 0; i < 4; i++) {
			boolean down = keys[i].isDown();
			if (down && !wasDown[i]) { // rising edge
				if (active && FreeflowConfig.doubleTapEvade && now - lastTap[i] <= FreeflowConfig.doubleTapWindowTicks) {
					send(CombatAction.EVADE, TAP_DIRS[i][0], TAP_DIRS[i][1]);
					lastTap[i] = -100; // a third tap starts a new pair
				} else {
					lastTap[i] = now;
				}
			}
			wasDown[i] = down;
		}
	}

	/** Called from the attack-key mixin instead of the vanilla attack. */
	public static void onStrike() {
		send(CombatAction.STRIKE);
	}

	/** True while the use key (right mouse) is held after a counter was sent. */
	private static boolean useHeld = false;

	/**
	 * Called from the use-key mixin instead of vanilla item use. Vanilla calls it again
	 * every tick while the button is held, so only the first call per press counters.
	 */
	public static void onUsePressed() {
		if (!useHeld) {
			useHeld = true;
			send(CombatAction.COUNTER);
		}
	}

	private static void pressed(KeyMapping key, boolean active, CombatAction action) {
		while (key.consumeClick()) {
			if (active) {
				send(action);
			}
		}
	}

	private static void send(CombatAction action) {
		send(action, RelativeMovement.rawForward, RelativeMovement.rawLeft);
	}

	private static void send(CombatAction action, float forward, float left) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !ClientPlayNetworking.canSend(CombatInputPayload.TYPE)) {
			return; // server doesn't have the mod
		}
		// Direction is sent as raw WASD relative to the aim yaw (camera yaw when orbiting),
		// so the server can resolve "attack the enemy I'm pushing toward".
		float aimYaw = FreeflowCamera.isActive() ? FreeflowCamera.yaw() : player.getYRot();
		ClientPlayNetworking.send(new CombatInputPayload(action, forward, left, aimYaw));
		ClientCombatState.lastSentAction = action;
		ClientCombatState.sentInputs++;
	}
}
