package com.borislav.freeflow.client.anim;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.client.EvadeController;
import com.borislav.freeflow.client.LungeController;
import com.borislav.freeflow.client.camera.FreeflowCamera;
import com.borislav.freeflow.combat.Anim;
import com.borislav.freeflow.network.AnimationPayload;
import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Plays combat animations (assets/freeflow/player_animations) on players through a
 * playerAnimator layer added to every client-side player.
 */
public final class CombatAnimations {
	private CombatAnimations() {}

	private static final ResourceLocation LAYER_ID = FreeflowMod.id("combat");
	/** Above vanilla, below emote mods' usual priorities. */
	private static final int LAYER_PRIORITY = 1000;

	/** Looping locomotion (run) sits below the combat moves, which override it. */
	private static final ResourceLocation RUN_LAYER_ID = FreeflowMod.id("locomotion");
	private static final int RUN_LAYER_PRIORITY = 900;
	private static boolean running = false;

	public static void init() {
		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
			ModifierLayer<IAnimation> layer = new ModifierLayer<>();
			stack.addAnimLayer(LAYER_PRIORITY, layer);
			PlayerAnimationAccess.getPlayerAssociatedData(player).set(LAYER_ID, layer);

			ModifierLayer<IAnimation> run = new ModifierLayer<>();
			stack.addAnimLayer(RUN_LAYER_PRIORITY, run);
			PlayerAnimationAccess.getPlayerAssociatedData(player).set(RUN_LAYER_ID, run);
		});
	}

	/** Starts/stops the combat run cycle for the local player (sprinting on the ground in freeflow). */
	@SuppressWarnings("unchecked")
	public static void tickRun(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			running = false;
			return;
		}
		boolean shouldRun = FreeflowCamera.isActive() && player.isSprinting() && player.onGround()
				&& !EvadeController.isActive() && !LungeController.isLunging();
		if (shouldRun == running) {
			return;
		}
		IAnimation data = PlayerAnimationAccess.getPlayerAssociatedData(player).get(RUN_LAYER_ID);
		if (!(data instanceof ModifierLayer<?> raw)) {
			return;
		}
		ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) raw;
		running = shouldRun;
		if (shouldRun) {
			IPlayable run = PlayerAnimationRegistry.getAnimation(FreeflowMod.id(Anim.RUN));
			if (run != null) {
				layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(3, Ease.INOUTSINE), run.playAnimation());
			}
		} else {
			layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(3, Ease.INOUTSINE), null);
		}
	}

	/** Developer preview diagnostics: is the local player's combat layer animating? */
	public static String debugState() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return "no player";
		}
		IAnimation data = PlayerAnimationAccess.getPlayerAssociatedData(mc.player).get(LAYER_ID);
		if (data instanceof ModifierLayer<?> layer) {
			IAnimation anim = layer.getAnimation();
			return "combat layer: " + (anim == null ? "empty" : (anim.isActive() ? "active" : "finished"));
		}
		return "no layer";
	}

	/** Plays an animation on the local player (developer preview). */
	public static void playLocal(String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			onAnimation(new AnimationPayload(mc.player.getId(), name));
		}
	}

	@SuppressWarnings("unchecked")
	public static void onAnimation(AnimationPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Entity entity = mc.level.getEntity(payload.entityId());
		if (!(entity instanceof AbstractClientPlayer player)) {
			return;
		}
		IPlayable animation = PlayerAnimationRegistry.getAnimation(FreeflowMod.id(payload.name()));
		if (animation == null) {
			FreeflowMod.LOGGER.warn("Missing combat animation {}", payload.name());
			return;
		}
		IAnimation data = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER_ID);
		if (data instanceof ModifierLayer<?> layer) {
			((ModifierLayer<IAnimation>) layer).replaceAnimationWithFade(
					AbstractFadeModifier.standardFadeIn(1, Ease.OUTQUAD), animation.playAnimation());
		}
	}
}
