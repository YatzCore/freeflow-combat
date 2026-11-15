package com.borislav.freeflow.client;

import com.borislav.freeflow.client.camera.FreeflowCamera;
import com.borislav.freeflow.client.camera.RelativeMovement;
import com.borislav.freeflow.client.hud.DebugOverlay;
import com.borislav.freeflow.combat.CombatAction;
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
		pressed(Keybinds.groundTakedown, active, CombatAction.GROUND_TAKEDOWN);
		pressed(Keybinds.specialTakedown, active, CombatAction.SPECIAL_TAKEDOWN);
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
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !ClientPlayNetworking.canSend(CombatInputPayload.TYPE)) {
			return; // server doesn't have the mod
		}
		// Direction is sent as raw WASD relative to the aim yaw (camera yaw when orbiting),
		// so the server can resolve "attack the enemy I'm pushing toward".
		float aimYaw = FreeflowCamera.isActive() ? FreeflowCamera.yaw() : player.getYRot();
		ClientPlayNetworking.send(new CombatInputPayload(action, RelativeMovement.rawForward, RelativeMovement.rawLeft, aimYaw));
		ClientCombatState.lastSentAction = action;
		ClientCombatState.sentInputs++;
	}
}
