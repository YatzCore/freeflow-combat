package com.borislav.freeflow.client.anim;

import com.borislav.freeflow.FreeflowMod;
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

	public static void init() {
		PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
			ModifierLayer<IAnimation> layer = new ModifierLayer<>();
			stack.addAnimLayer(LAYER_PRIORITY, layer);
			PlayerAnimationAccess.getPlayerAssociatedData(player).set(LAYER_ID, layer);
		});
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
