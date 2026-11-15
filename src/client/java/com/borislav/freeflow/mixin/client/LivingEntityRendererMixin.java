package com.borislav.freeflow.mixin.client;

import com.borislav.freeflow.client.anim.MobAnimator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks {@link MobAnimator} into mob rendering: whole-body motion and limb poses. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
	@Shadow
	protected M model;

	private static final String RENDER =
			"render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

	@Inject(method = RENDER, at = @At("HEAD"))
	private void freeflow$beforeRender(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		MobAnimator.beforeRender(entity);
	}

	@Inject(method = RENDER, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
			shift = At.Shift.AFTER))
	private void freeflow$pose(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		MobAnimator.applyPose(entity, model, partialTick);
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void freeflow$body(LivingEntity entity, PoseStack pose, float bob, float yBodyRot, float partialTick,
			float scale, CallbackInfo ci) {
		MobAnimator.applyBody(entity, pose, partialTick);
	}
}
