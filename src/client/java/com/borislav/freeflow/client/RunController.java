package com.borislav.freeflow.client;

import com.borislav.freeflow.combat.CombatAction;
import com.borislav.freeflow.config.FreeflowConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;

/**
 * Running in a fight, the Arkham way: hold the jump key (Space) to run, let go to stop; double-tap
 * it to evade in the direction you're moving (toward an enemy: vault over it). There is no jumping
 * in a fight; 1-block steps are climbed automatically (auto-jump, see LocalPlayerMixin).
 * Tap Ctrl while running to slide (ClientInputHandler). Outside fights Space jumps as usual.
 */
public final class RunController {
	private RunController() {}

	private static boolean runWasHeld;
	private static boolean ran;
	private static long lastTap = -100;

	/** True while freeflow controls the jump key. */
	public static boolean active() {
		return ClientCombatState.isFreeflowActive() && FreeflowConfig.holdToRun;
	}

	/** End of the movement input tick (after it was made camera-relative). */
	public static void onInputTick(Input input) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !active()) {
			if (ran && player != null) {
				player.setSprinting(false);
			}
			ran = false;
			runWasHeld = false;
			return;
		}
		if (player.isInWater() || player.isInLava() || player.onClimbable()) {
			return; // Space still swims up and climbs
		}
		input.jumping = false; // Space runs instead (vanilla auto-jump still climbs steps)

		boolean held = mc.options.keyJump.isDown();
		if (held && !runWasHeld) {
			long now = ClientCombo.clientTick;
			if (now - lastTap <= FreeflowConfig.doubleTapWindowTicks) {
				ClientInputHandler.padAction(CombatAction.EVADE, com.borislav.freeflow.client.camera.RelativeMovement.rawForward,
						com.borislav.freeflow.client.camera.RelativeMovement.rawLeft);
				lastTap = -100; // a third tap starts a new pair
			} else {
				lastTap = now;
			}
		}
		runWasHeld = held;

		if (held) {
			boolean moving = input.forwardImpulse >= 0.8f;
			boolean fed = player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly;
			if (moving && fed && !input.shiftKeyDown && !player.isUsingItem() && !player.isPassenger()) {
				player.setSprinting(true);
				ran = true;
			}
		} else if (ran) {
			player.setSprinting(false); // let go and you stop running
			ran = false;
		}
	}
}
