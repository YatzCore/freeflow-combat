package com.borislav.freeflow.mixin.client;

import com.borislav.freeflow.client.anim.BentLimbs;
import com.borislav.freeflow.client.anim.MobAnimator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** What a hand holds follows the forearm when the elbow is bent (see {@link BentLimbs}). */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
			shift = At.Shift.AFTER))
	private void freeflow$followForearm(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
			PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		ModelPart part = MobAnimator.armPart(((RenderLayer<?, ?>) (Object) this).getParentModel(), arm);
		if (part != null) {
			BentLimbs.applyLower(part, pose);
		}
	}
}
