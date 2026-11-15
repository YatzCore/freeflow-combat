package com.borislav.freeflow.client.hud;

import com.borislav.freeflow.client.ClientCombo;
import com.borislav.freeflow.network.CombatEventPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Enemy status markers: spinning yellow stars over stunned enemies, an orange arrow over
 * knocked-down ones (ground takedown available). Placeholder glyphs until real icons.
 */
public final class StatusMarkers {
	private StatusMarkers() {}

	private enum Status { STUNNED, KNOCKED_DOWN }

	private record Marker(Status status, long endTick) {}

	private static final Map<Integer, Marker> MARKERS = new HashMap<>();

	public static void onEvent(CombatEventPayload payload) {
		long end = ClientCombo.clientTick + payload.ticks();
		switch (payload.event()) {
			case STUN -> MARKERS.put(payload.mobId(), new Marker(Status.STUNNED, end));
			case KNOCKDOWN -> MARKERS.put(payload.mobId(), new Marker(Status.KNOCKED_DOWN, end));
			case STUN_END, TAKEDOWN, SPECIAL_TAKEDOWN -> MARKERS.remove(payload.mobId());
			case TAKEDOWN_START -> {
				Marker m = MARKERS.get(payload.mobId());
				if (m != null) { // stays down for the whole takedown
					MARKERS.put(payload.mobId(), new Marker(m.status(), Math.max(m.endTick(), end + 5)));
				}
			}
			default -> {}
		}
	}

	public static void reset() {
		MARKERS.clear();
	}

	/** True if a knocked-down enemy is within {@code range} of the player (for the HUD prompt). */
	public static boolean knockedDownNear(Player player, double range) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return false;
		}
		for (Map.Entry<Integer, Marker> e : MARKERS.entrySet()) {
			if (e.getValue().status() == Status.KNOCKED_DOWN && ClientCombo.clientTick < e.getValue().endTick()) {
				Entity entity = mc.level.getEntity(e.getKey());
				if (entity != null && entity.isAlive() && entity.distanceTo(player) <= range) {
					return true;
				}
			}
		}
		return false;
	}

	public static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || MARKERS.isEmpty()) {
			return;
		}
		MARKERS.values().removeIf(m -> ClientCombo.clientTick >= m.endTick());
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		if (pose == null || buffers == null) {
			return;
		}
		Camera camera = ctx.camera();
		Vec3 cam = camera.getPosition();
		float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Font font = mc.font;

		for (Map.Entry<Integer, Marker> entry : MARKERS.entrySet()) {
			Entity entity = mc.level.getEntity(entry.getKey());
			if (entity == null || !entity.isAlive()) {
				continue;
			}
			boolean down = entry.getValue().status() == Status.KNOCKED_DOWN;
			String text = down ? "▼" : ((ClientCombo.clientTick / 3) % 2 == 0 ? "✦ ✧" : "✧ ✦");
			int color = down ? 0xFFFF9A2E : 0xFFFFE14D;

			double x = Mth.lerp(pt, entity.xo, entity.getX());
			double y = Mth.lerp(pt, entity.yo, entity.getY()) + entity.getBbHeight() + (down ? 0.35 : 0.3);
			double z = Mth.lerp(pt, entity.zo, entity.getZ());
			float scale = 0.03f;

			pose.pushPose();
			pose.translate(x - cam.x, y - cam.y, z - cam.z);
			pose.mulPose(camera.rotation());
			pose.scale(scale, -scale, scale);
			font.drawInBatch(text, -font.width(text) / 2.0f, -font.lineHeight / 2.0f, color, false, pose.last().pose(),
					buffers, Font.DisplayMode.SEE_THROUGH, 0x40000000, 0xF000F0);
			pose.popPose();
		}
	}
}
