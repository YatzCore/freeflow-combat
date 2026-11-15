package com.borislav.freeflow.client;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.client.anim.CombatAnimations;
import com.borislav.freeflow.client.camera.CinematicShots;
import com.borislav.freeflow.network.AnimationPayload;
import com.borislav.freeflow.client.camera.CombatFraming;
import com.borislav.freeflow.client.camera.FreeflowCamera;
import com.borislav.freeflow.client.hud.TargetIndicator;
import com.borislav.freeflow.client.hud.ComboHud;
import com.borislav.freeflow.client.hud.DebugOverlay;
import com.borislav.freeflow.client.hud.StatusMarkers;
import com.borislav.freeflow.client.hud.TelegraphRenderer;
import com.borislav.freeflow.network.CombatEventPayload;
import com.borislav.freeflow.network.ComboPayload;
import com.borislav.freeflow.network.CombatSyncPayload;
import com.borislav.freeflow.network.CounterPayload;
import com.borislav.freeflow.network.EvadePayload;
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
		ClientPlayNetworking.registerGlobalReceiver(AnimationPayload.TYPE,
				(payload, context) -> CombatAnimations.onAnimation(payload));
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
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
		});
		HudRenderCallback.EVENT.register(ComboHud::render);
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
		ClientPlayNetworking.registerGlobalReceiver(EvadePayload.TYPE,
				(payload, context) -> EvadeController.onEvade(payload));
		ClientPlayNetworking.registerGlobalReceiver(CombatEventPayload.TYPE, (payload, context) -> {
			StatusMarkers.onEvent(payload);
			ClientCombo.onEvent(payload);
			switch (payload.event()) {
				case STUN -> LungeController.faceEntity(payload.mobId());
				case TAKEDOWN, SPECIAL_TAKEDOWN, FINAL_HIT -> CinematicShots.start(payload.mobId());
				default -> {}
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(TelegraphRenderer::render);
		WorldRenderEvents.AFTER_ENTITIES.register(StatusMarkers::render);
		WorldRenderEvents.AFTER_ENTITIES.register(TargetIndicator::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			CinematicShots.reset();
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
