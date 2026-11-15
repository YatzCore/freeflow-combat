package com.borislav.freeflow.client.hud;

import com.borislav.freeflow.client.ClientCombatState;
import com.borislav.freeflow.client.ClientCombo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Combat HUD: big combo counter on the right (pops on each hit, changes colour at
 * x5 / x10 / x20, drains a timer bar), a rhythm pulse under the crosshair, and a
 * "STAGGERED" / broken-combo flash.
 */
public final class ComboHud {
	private ComboHud() {}

	private static final int WHITE = 0xFFFFFFFF;
	private static final int YELLOW = 0xFFFFE14D;
	private static final int ORANGE = 0xFFFF9A2E;
	private static final int RED = 0xFFFF4A3D;
	private static final int GREEN = 0xFF5CFF7A;
	private static final int GREY = 0xFF9A9A9A;

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) {
			return;
		}
		float pt = delta.getGameTimeDeltaPartialTick(false);
		int w = g.guiWidth();
		int h = g.guiHeight();
		Font font = mc.font;

		renderCombo(g, font, w, h, pt);
		if (ClientCombatState.isFreeflowActive()) {
			renderRhythm(g, w, h, pt);
		}
		renderFlash(g, font, w, h, pt);
	}

	private static void renderCombo(GuiGraphics g, Font font, int w, int h, float pt) {
		int combo = ClientCombo.combo;
		if (combo <= 0) {
			return;
		}
		int color = tierColor(combo);

		// Pop: scale up briefly after each hit.
		float sinceHit = ClientCombo.clientTick - ClientCombo.lastHitTick + pt;
		float pop = sinceHit < 4 ? 1.0f + 0.35f * (1.0f - sinceHit / 4.0f) : 1.0f;
		float scale = (combo >= 20 ? 3.2f : combo >= 10 ? 2.9f : 2.6f) * pop;

		String text = "x" + combo;
		int x = w - 24;
		int y = h / 2 - 30;

		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(x, y, 0);
		pose.scale(scale, scale, 1.0f);
		g.drawString(font, text, -font.width(text), -font.lineHeight / 2, color, true);
		pose.popPose();

		g.drawString(font, "COMBO", x - font.width("COMBO"), y + 16, GREY, true);

		// Timer bar draining toward a break.
		int barW = 60;
		int barX = x - barW;
		int barY = y + 27;
		float frac = ClientCombo.comboTimeFraction(pt);
		g.fill(barX, barY, barX + barW, barY + 3, 0x80000000);
		g.fill(barX + (int) (barW * (1 - frac)), barY, barX + barW, barY + 3, color);
	}

	/** Small bar under the crosshair: fills toward the hit, green when a press would be on-beat. */
	private static void renderRhythm(GuiGraphics g, int w, int h, float pt) {
		int barW = 30;
		int x = w / 2 - barW / 2;
		int y = h / 2 + 14;

		if (ClientCombo.isStaggered()) {
			g.fill(x, y, x + barW, y + 2, RED);
			return;
		}
		long land = ClientCombo.strikeLandTick;
		boolean inFlight = land >= 0 && ClientCombo.clientTick < land;
		if (!inFlight) {
			return; // nothing to time against
		}
		float total = Math.max(1, land - ClientCombo.strikeStartTick);
		float progress = Mth.clamp((ClientCombo.clientTick + pt - ClientCombo.strikeStartTick) / total, 0f, 1f);
		int color = ClientCombo.isOnBeat() ? GREEN : WHITE;
		g.fill(x, y, x + barW, y + 2, 0x80000000);
		g.fill(x, y, x + (int) (barW * progress), y + 2, color);
	}

	private static void renderFlash(GuiGraphics g, Font font, int w, int h, float pt) {
		float since = ClientCombo.clientTick - ClientCombo.brokenAtTick + pt;
		if (since > 25) {
			return;
		}
		int alpha = (int) (255 * Mth.clamp(1.0f - since / 25.0f, 0f, 1f));
		if (alpha < 8) {
			return;
		}
		int x = w - 24;
		int y = h / 2 - 30;
		if (ClientCombo.lastBreakWasStagger) {
			String s = "STAGGERED";
			g.drawString(font, s, w / 2 - font.width(s) / 2, h / 2 + 20, (alpha << 24) | (RED & 0xFFFFFF), true);
		}
		if (ClientCombo.combo == 0 && ClientCombo.brokenCombo > 0) {
			String s = "x" + ClientCombo.brokenCombo;
			PoseStack pose = g.pose();
			pose.pushPose();
			pose.translate(x, y, 0);
			pose.scale(2.6f, 2.6f, 1.0f);
			g.drawString(font, s, -font.width(s), -font.lineHeight / 2, (alpha << 24) | (GREY & 0xFFFFFF), true);
			pose.popPose();
			String b = "COMBO BROKEN";
			g.drawString(font, b, x - font.width(b), y + 16, (alpha << 24) | (RED & 0xFFFFFF), true);
		}
	}

	private static int tierColor(int combo) {
		if (combo >= 20) {
			return RED;
		}
		if (combo >= 10) {
			return ORANGE;
		}
		if (combo >= 5) {
			return YELLOW;
		}
		return WHITE;
	}
}
