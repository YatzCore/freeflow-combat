package com.borislav.freeflow.client.hud;

import com.borislav.freeflow.client.ClientCombo;
import com.borislav.freeflow.network.TelegraphPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Floating attack warning above enemies that are about to strike: a cyan "!" that
 * grows as the hit approaches and flashes white right before it lands. Rendered
 * through walls so you can always counter.
 */
public final class TelegraphRenderer {
	private TelegraphRenderer() {}

	private record Telegraph(long startTick, long endTick, int kind) {}

	private static final Map<Integer, Telegraph> ACTIVE = new HashMap<>();

	public static void onTelegraph(TelegraphPayload payload) {
		if (payload.ticks() <= 0) {
			ACTIVE.remove(payload.mobId());
		} else {
			ACTIVE.put(payload.mobId(), new Telegraph(ClientCombo.clientTick, ClientCombo.clientTick + payload.ticks(), payload.kind()));
		}
	}

	public static void clear(int mobId) {
		ACTIVE.remove(mobId);
	}

	public static void reset() {
		ACTIVE.clear();
	}

	public static int activeCount() {
		return ACTIVE.size();
	}

	/** Two colours, like the Arkham games: blue = counter it, red = can't be countered, evade it. */
	static int color(int kind) {
		return kind == TelegraphPayload.ARMED || kind == TelegraphPayload.RANGED ? 0xFFFF3B30 : 0xFF4FD8FF;
	}

	/**
	 * HUD part: attacks coming from off-screen (behind or to the side) get a pulsing "!"
	 * on an ellipse around the screen centre, pointing toward the attacker.
	 */
	public static void renderOffscreen(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || ACTIVE.isEmpty() || mc.options.hideGui) {
			return;
		}
		Camera camera = mc.gameRenderer.getMainCamera();
		Vec3 cam = camera.getPosition();
		float camYaw = camera.getYRot();
		Font font = mc.font;
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		float rx = g.guiWidth() * 0.42f;
		float ry = g.guiHeight() * 0.38f;

		for (Map.Entry<Integer, Telegraph> entry : ACTIVE.entrySet()) {
			Entity entity = mc.level.getEntity(entry.getKey());
			Telegraph t = entry.getValue();
			if (entity == null || ClientCombo.clientTick > t.endTick()) {
				continue;
			}
			float toEntity = (float) Math.toDegrees(Math.atan2(-(entity.getX() - cam.x), entity.getZ() - cam.z));
			float rel = Mth.wrapDegrees(toEntity - camYaw);
			if (Math.abs(rel) < 50f) {
				continue; // on screen: the world marker is enough
			}
			float r = rel * Mth.DEG_TO_RAD;
			int x = cx + Math.round(Mth.sin(r) * rx);
			int y = cy - Math.round(Mth.cos(r) * ry);
			boolean pulse = (ClientCombo.clientTick / 3) % 2 == 0;
			int color = t.endTick() - ClientCombo.clientTick <= 3 && pulse ? 0xFFFFFFFF : color(t.kind());

			PoseStack pose = g.pose();
			pose.pushPose();
			pose.translate(x, y, 0);
			float s = pulse ? 2.2f : 1.9f;
			pose.scale(s, s, 1f);
			g.fill(-5, -6, 5, 6, 0x90000000);
			String text = "!";
			g.drawString(font, text, -font.width(text) / 2, -4, color, true);
			pose.popPose();
		}
	}

	public static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ACTIVE.isEmpty()) {
			return;
		}
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		if (pose == null || buffers == null) {
			return;
		}
		ACTIVE.values().removeIf(t -> ClientCombo.clientTick > t.endTick() + 2);

		Camera camera = ctx.camera();
		Vec3 cam = camera.getPosition();
		float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Font font = mc.font;

		for (Map.Entry<Integer, Telegraph> entry : ACTIVE.entrySet()) {
			Entity entity = mc.level.getEntity(entry.getKey());
			if (entity == null) {
				continue;
			}
			Telegraph t = entry.getValue();
			float duration = Math.max(1, t.endTick() - t.startTick());
			float progress = Mth.clamp((ClientCombo.clientTick + pt - t.startTick()) / duration, 0f, 1f);
			boolean imminent = t.endTick() - ClientCombo.clientTick <= 3;
			// Blue = counter it. Red = can't be countered: evade it.
			int base = color(t.kind());
			int color = imminent && (ClientCombo.clientTick % 2 == 0) ? 0xFFFFFFFF : base;

			double x = Mth.lerp(pt, entity.xo, entity.getX());
			double y = Mth.lerp(pt, entity.yo, entity.getY()) + entity.getBbHeight() + 0.6;
			double z = Mth.lerp(pt, entity.zo, entity.getZ());
			float scale = 0.035f * (1.3f + 0.8f * progress);

			pose.pushPose();
			pose.translate(x - cam.x, y - cam.y, z - cam.z);
			pose.mulPose(camera.rotation());
			pose.scale(scale, -scale, scale);
			String s = "!";
			font.drawInBatch(s, -font.width(s) / 2.0f, -font.lineHeight / 2.0f, color, false, pose.last().pose(),
					buffers, Font.DisplayMode.SEE_THROUGH, 0x50000000, 0xF000F0);
			pose.popPose();
		}
	}
}
