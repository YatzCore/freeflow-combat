package com.borislav.freeflow.client.hud;

import com.borislav.freeflow.network.FightSummaryPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * End-of-fight rating card: slides in from the right, the score counts up, then the
 * breakdown (max combo, counters, takedowns, variety, FLAWLESS) and the XP bonus.
 * Fades out after a few seconds.
 */
public final class FightSummaryHud {
	private FightSummaryHud() {}

	private static final long SHOW_MS = 6000;
	private static final long COUNT_MS = 900;

	@Nullable
	private static FightSummaryPayload summary;
	private static long shownAt;

	public static void show(FightSummaryPayload payload) {
		summary = payload;
		shownAt = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.playSound(payload.flawless() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP,
					0.6f, 1.2f);
		}
	}

	public static void reset() {
		summary = null;
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		FightSummaryPayload s = summary;
		Minecraft mc = Minecraft.getInstance();
		if (s == null || mc.options.hideGui) {
			return;
		}
		long age = System.currentTimeMillis() - shownAt;
		if (age > SHOW_MS) {
			summary = null;
			return;
		}
		Font font = mc.font;
		float slide = 1f - easeOut(Mth.clamp(age / 250f, 0f, 1f));
		float alpha = Mth.clamp((SHOW_MS - age) / 600f, 0f, 1f);
		int a = (int) (alpha * 255);
		if (a < 8) {
			return;
		}

		int w = 150;
		int x = g.guiWidth() - w - 10 + (int) (slide * (w + 20));
		int y = g.guiHeight() / 2 - 70;
		int lines = 6 + (s.flawless() ? 1 : 0);
		int h = 34 + lines * 11;
		g.fill(x, y, x + w, y + h, ((int) (alpha * 0xB0) << 24));
		g.fill(x, y, x + 2, y + h, (a << 24) | 0x4FD8FF);

		int score = (int) (s.score() * easeOut(Mth.clamp(age / (float) COUNT_MS, 0f, 1f)));
		g.drawString(font, "FREEFLOW", x + 8, y + 6, (a << 24) | 0x4FD8FF, true);
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(x + 8, y + 18, 0);
		pose.scale(1.6f, 1.6f, 1f);
		g.drawString(font, String.valueOf(score), 0, 0, (a << 24) | 0xFFFFFF, true);
		pose.popPose();

		int ly = y + 34;
		int grey = (a << 24) | 0xC8C8C8;
		ly = row(g, font, x, ly, w, "Max combo", "x" + s.maxCombo(), grey);
		ly = row(g, font, x, ly, w, "Hits", String.valueOf(s.hits()), grey);
		ly = row(g, font, x, ly, w, "Counters", String.valueOf(s.counters()), grey);
		ly = row(g, font, x, ly, w, "Takedowns", String.valueOf(s.takedowns()), grey);
		ly = row(g, font, x, ly, w, "Variety", s.variety() + " moves", grey);
		if (s.flawless()) {
			ly = row(g, font, x, ly, w, "FLAWLESS", "x1.5", (a << 24) | 0xFFE14D);
		}
		row(g, font, x, ly, w, "Bonus XP", "+" + s.xp(), (a << 24) | 0x7CFF6B);
	}

	private static int row(GuiGraphics g, Font font, int x, int y, int w, String label, String value, int color) {
		g.drawString(font, label, x + 8, y, color, true);
		g.drawString(font, value, x + w - 8 - font.width(value), y, color, true);
		return y + 11;
	}

	private static float easeOut(float k) {
		return 1f - (1f - k) * (1f - k);
	}
}
