package com.borislav.freeflow.mixin.client;

import com.borislav.freeflow.client.anim.BentLimbs;
import com.borislav.freeflow.client.anim.CombatAnimations;
import com.borislav.freeflow.client.anim.GroundFit;
import com.borislav.freeflow.client.anim.MobAnimator;
import com.borislav.freeflow.client.dev.AnimationPreview;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@link MobAnimator} into mob rendering (whole-body motion and limb poses) and keeps
 * animated bodies, enemies and players alike, out of the floor ({@link GroundFit}).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
	@Shadow
	protected M model;

	private static final String RENDER =
			"render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

	@Inject(method = RENDER, at = @At("HEAD"))
	private void freeflow$beforeRender(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		GroundFit.begin(pose);
		MobAnimator.beforeRender(entity);
	}

	@Inject(method = RENDER, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
			shift = At.Shift.AFTER))
	private void freeflow$pose(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		MobAnimator.applyPose(entity, model, pose, partialTick);
		if (entity instanceof AbstractClientPlayer player) {
			CombatAnimations.applyBends(player, model);
			if (CombatAnimations.isAnimating(player)) {
				float low = GroundFit.keepAbove(pose, model);
				if (AnimationPreview.ENABLED) {
					AnimationPreview.lowest = low;
				}
			}
		}
	}

	@Inject(method = RENDER, at = @At("RETURN"))
	private void freeflow$afterRender(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		MobAnimator.afterRender(entity, model);
		BentLimbs.straighten(); // models are shared: the next entity starts with straight limbs
		GroundFit.end();
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void freeflow$body(LivingEntity entity, PoseStack pose, float bob, float yBodyRot, float partialTick,
			float scale, CallbackInfo ci) {
		MobAnimator.applyBody(entity, model, pose, partialTick);
	}
}
