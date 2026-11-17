package com.borislav.freeflow.mixin.client;

import com.borislav.freeflow.client.anim.BentLimbs;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * Gives every model part a bend (elbows and knees, see {@link BentLimbs}). A bent part is drawn
 * by BentLimbs instead of vanilla; a straight one is left completely alone.
 *
 * Priority above the default so this runs ahead of other mods' model-part render hooks.
 */
@Mixin(value = ModelPart.class, priority = 1500)
public abstract class ModelPartMixin implements BentLimbs.Part {
	@Shadow
	public boolean visible;

	@Shadow
	public boolean skipDraw;

	@Shadow
	@Final
	private List<ModelPart.Cube> cubes;

	@Shadow
	@Final
	private Map<String, ModelPart> children;

	@Shadow
	public abstract void translateAndRotate(PoseStack pose);

	@Unique
	private float freeflow$bendAngle = 0f;

	@Unique
	private float freeflow$bendTurnAngle = 0f;

	@Override
	public float freeflow$bend() {
		return freeflow$bendAngle;
	}

	@Override
	public float freeflow$bendTurn() {
		return freeflow$bendTurnAngle;
	}

	@Override
	public void freeflow$setBend(float bend, float turn) {
		freeflow$bendAngle = bend;
		freeflow$bendTurnAngle = turn;
	}

	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
			at = @At("HEAD"), cancellable = true)
	private void freeflow$renderBent(PoseStack pose, VertexConsumer consumer, int light, int overlay, int color,
			CallbackInfo ci) {
		if (freeflow$bendAngle == 0f) {
			return;
		}
		ci.cancel();
		if (!visible || (cubes.isEmpty() && children.isEmpty())) {
			return;
		}
		pose.pushPose();
		translateAndRotate(pose);
		if (!skipDraw) {
			BentLimbs.draw((ModelPart) (Object) this, pose.last(), consumer, light, overlay, color);
		}
		for (ModelPart child : children.values()) {
			child.render(pose, consumer, light, overlay, color);
		}
		pose.popPose();
	}

	/** Outer skin layers and armour copy their pose from the limb: the bend goes with it. */
	@Inject(method = "copyFrom", at = @At("TAIL"))
	private void freeflow$copyBend(ModelPart other, CallbackInfo ci) {
		BentLimbs.setRadians((ModelPart) (Object) this, BentLimbs.bend(other), BentLimbs.turn(other));
	}
}
