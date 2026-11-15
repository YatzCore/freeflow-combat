package com.borislav.freeflow.client.hud;

import com.borislav.freeflow.client.ClientCombatState;
import com.borislav.freeflow.client.ClientCombo;
import com.borislav.freeflow.client.EvadeController;
import com.borislav.freeflow.client.LungeController;
import com.borislav.freeflow.client.camera.FreeflowCamera;
import com.borislav.freeflow.client.camera.RelativeMovement;
import com.borislav.freeflow.combat.TargetSelector;
import com.borislav.freeflow.config.FreeflowConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces the crosshair in combat: a small marker under the enemy your next strike would
 * snap to, predicted with the same {@link TargetSelector} the server uses.
 */
public final class TargetIndicator {
	private TargetIndicator() {}

	@Nullable
	private static Mob predicted;

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		predicted = null;
		if (player == null || !FreeflowConfig.targetIndicator || !ClientCombatState.isFreeflowActive()
				|| LungeController.isLunging() || EvadeController.isActive()) {
			return;
		}
		float aimYaw = FreeflowCamera.isActive() ? FreeflowCamera.yaw() : player.getYRot();
		int scaled = Math.min(ClientCombo.combo, FreeflowConfig.comboScalingCap);
		double range = FreeflowConfig.strikeRange + scaled * FreeflowConfig.comboRangePerHit;
		predicted = TargetSelector.select(player, aimYaw, RelativeMovement.rawForward, RelativeMovement.rawLeft, range);
	}

	public static void render(WorldRenderContext ctx) {
		Mob mob = predicted;
		Minecraft mc = Minecraft.getInstance();
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		if (mob == null || !mob.isAlive() || pose == null || buffers == null) {
			return;
		}
		Camera camera = ctx.camera();
		Vec3 cam = camera.getPosition();
		float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Font font = mc.font;

		double x = Mth.lerp(pt, mob.xo, mob.getX());
		double y = Mth.lerp(pt, mob.yo, mob.getY()) + mob.getBbHeight() + 0.1;
		double z = Mth.lerp(pt, mob.zo, mob.getZ());
		float bob = Mth.sin((ClientCombo.clientTick + pt) * 0.3f) * 0.04f;
		float scale = 0.022f;

		pose.pushPose();
		pose.translate(x - cam.x, y - cam.y + bob, z - cam.z);
		pose.mulPose(camera.rotation());
		pose.scale(scale, -scale, scale);
		String s = "◆";
		font.drawInBatch(s, -font.width(s) / 2.0f, -font.lineHeight / 2.0f, 0xD0FFFFFF, false, pose.last().pose(),
				buffers, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
		pose.popPose();
	}

	public static void reset() {
		predicted = null;
	}
}
