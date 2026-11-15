package com.borislav.freeflow.client.hud;

import com.borislav.freeflow.client.ClientCombatState;
import com.borislav.freeflow.client.LungeController;
import com.borislav.freeflow.client.camera.FreeflowCamera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Toggleable (F7) text overlay showing the synced combat state. */
public final class DebugOverlay {
	private DebugOverlay() {}

	public static boolean enabled = false; // F7 to show

	public static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!enabled || mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}

		Font font = mc.font;
		boolean active = ClientCombatState.isFreeflowActive();
		String[] lines = {
				"Freeflow [F7]",
				"Mode: " + ClientCombatState.mode + "  (B to cycle)",
				"In combat: " + ClientCombatState.inCombat + "  engaged: " + ClientCombatState.engagedCount,
				"Freeflow active: " + active + (ClientCombatState.inCombat && !active ? " (hand not empty)" : ""),
				"Sent: " + ClientCombatState.lastSentAction + " #" + ClientCombatState.sentInputs,
				"Server ack: " + ClientCombatState.lastAcceptedAction + " #" + ClientCombatState.acceptedInputs,
				"Strike target: " + LungeController.lastTargetName + (LungeController.isLunging() ? " (lunging)" : ""),
				"Camera: " + (FreeflowCamera.isActive() ? "orbit" : (FreeflowCamera.userEnabled ? "idle" : "disabled [J]"))
						+ "  shoulder: " + (FreeflowCamera.shoulderSide() > 0 ? "R" : "L") + " [H]"
						+ String.format("  dist %.1f", FreeflowCamera.currentDistance()),
		};

		int x = 4;
		int y = 4;
		int width = 0;
		for (String line : lines) {
			width = Math.max(width, font.width(line));
		}
		graphics.fill(x - 2, y - 2, x + width + 2, y + lines.length * 10, 0x90000000);
		for (int i = 0; i < lines.length; i++) {
			int color = i == 0 ? (active ? 0xFF55FF55 : 0xFFFFFF55) : 0xFFFFFFFF;
			graphics.drawString(font, lines[i], x, y + i * 10, color, false);
		}
	}
}
