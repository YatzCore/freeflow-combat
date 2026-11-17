package com.borislav.freeflow.client;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.client.anim.CombatAnimations;
import com.borislav.freeflow.client.anim.MobAnimator;
import com.borislav.freeflow.client.dev.AnimationPreview;
import com.borislav.freeflow.network.MobReactionPayload;
import com.borislav.freeflow.client.camera.CinematicShots;
import com.borislav.freeflow.network.AnimationPayload;
import com.borislav.freeflow.client.camera.CombatFraming;
import com.borislav.freeflow.client.camera.FreeflowCamera;
import com.borislav.freeflow.client.camera.Impact;
import com.borislav.freeflow.client.hud.TargetIndicator;
import com.borislav.freeflow.client.pad.PadInput;
import com.borislav.freeflow.client.hud.ComboHud;
import com.borislav.freeflow.client.hud.FightSummaryHud;
import com.borislav.freeflow.network.FightSummaryPayload;
import com.borislav.freeflow.client.hud.DebugOverlay;
import com.borislav.freeflow.client.hud.StatusMarkers;
import com.borislav.freeflow.client.hud.TelegraphRenderer;
import com.borislav.freeflow.network.CombatEventPayload;
import com.borislav.freeflow.network.ComboPayload;
import com.borislav.freeflow.network.CombatSyncPayload;
import com.borislav.freeflow.network.CounterPayload;
import com.borislav.freeflow.network.EvadePayload;
import com.borislav.freeflow.network.MoveToPayload;
import com.borislav.freeflow.network.ConfigSyncPayload;
import com.borislav.freeflow.config.ConfigStore;
import net.minecraft.client.Minecraft;
import com.borislav.freeflow.network.StrikeStartPayload;
import com.borislav.freeflow.network.TelegraphPayload;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Client-only entrypoint: keybinds, HUD, camera and animations are wired up here.
 */
public class FreeflowClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FreeflowMod.LOGGER.info("Freeflow Combat client initializing");

		Keybinds.register();
		CombatAnimations.init();
		ClientPlayNetworking.registerGlobalReceiver(MobReactionPayload.TYPE,
				(payload, context) -> MobAnimator.onReaction(payload));
		// A server's gameplay settings apply while playing there (in your own world the fields are shared already).
		ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) -> {
			if (!Minecraft.getInstance().hasSingleplayerServer()) {
				ConfigStore.applyRemote(payload.json());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(AnimationPayload.TYPE,
				(payload, context) -> CombatAnimations.onAnimation(payload));
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			PadInput.tick(client); // before vanilla reads its keys this tick
			ClientCombo.tick();
			LungeController.tick(client);
			EvadeController.tick(client);
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientInputHandler.tick(client);
			FreeflowCamera.tick(client);
			CombatFraming.tick(client);
			CinematicShots.tick();
			TargetIndicator.tick(client);
			MobAnimator.tick(client);
			CombatAnimations.tickLocomotion(client);
			AnimationPreview.tick(client);
		});
		HudRenderCallback.EVENT.register(ComboHud::render);
		HudRenderCallback.EVENT.register(FightSummaryHud::render);
		HudRenderCallback.EVENT.register(TelegraphRenderer::renderOffscreen);
		ClientPlayNetworking.registerGlobalReceiver(FightSummaryPayload.TYPE,
				(payload, context) -> FightSummaryHud.show(payload));
		HudRenderCallback.EVENT.register(DebugOverlay::render);

		ClientPlayNetworking.registerGlobalReceiver(CombatSyncPayload.TYPE,
				(payload, context) -> ClientCombatState.apply(payload));
		ClientPlayNetworking.registerGlobalReceiver(StrikeStartPayload.TYPE, (payload, context) -> {
			LungeController.onStrikeStart(payload);
			ClientCombo.onStrikeStart(payload);
		});
		ClientPlayNetworking.registerGlobalReceiver(ComboPayload.TYPE,
				(payload, context) -> ClientCombo.onCombo(payload));
		ClientPlayNetworking.registerGlobalReceiver(TelegraphPayload.TYPE,
				(payload, context) -> TelegraphRenderer.onTelegraph(payload));
		ClientPlayNetworking.registerGlobalReceiver(CounterPayload.TYPE, (payload, context) -> {
			payload.mobIds().forEach(TelegraphRenderer::clear);
			LungeController.onCounter(payload.mobIds());
			ClientCombo.onCounter(payload.mobIds().size());
		});
		ClientPlayNetworking.registerGlobalReceiver(MoveToPayload.TYPE,
				(payload, context) -> LungeController.onMoveTo(payload));
		ClientPlayNetworking.registerGlobalReceiver(EvadePayload.TYPE,
				(payload, context) -> EvadeController.onEvade(payload));
		ClientPlayNetworking.registerGlobalReceiver(CombatEventPayload.TYPE, (payload, context) -> {
			StatusMarkers.onEvent(payload);
			ClientCombo.onEvent(payload);
			switch (payload.event()) {
				case STUN -> LungeController.faceEntity(payload.mobId());
				case TAKEDOWN_START -> MobAnimator.holdDown(payload.mobId(), payload.ticks());
				case TAKEDOWN, SPECIAL_TAKEDOWN, FINAL_HIT -> CinematicShots.start(payload.mobId());
				default -> {}
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(TelegraphRenderer::render);
		WorldRenderEvents.AFTER_ENTITIES.register(StatusMarkers::render);
		WorldRenderEvents.AFTER_ENTITIES.register(TargetIndicator::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ConfigStore.applyLocal(); // back to your own settings after a server
			CinematicShots.reset();
			Impact.reset();
			MobAnimator.reset();
			FightSummaryHud.reset();
			CombatFraming.reset();
			TargetIndicator.reset();
			StatusMarkers.reset();
			EvadeController.reset();
			TelegraphRenderer.reset();
			FreeflowCamera.exit(client);
			LungeController.reset();
			ClientCombatState.reset();
			ClientCombo.reset();
		});
	}
}
