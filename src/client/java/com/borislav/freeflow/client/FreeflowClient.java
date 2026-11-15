package com.borislav.freeflow.client;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.client.camera.FreeflowCamera;
import com.borislav.freeflow.client.hud.DebugOverlay;
import com.borislav.freeflow.network.CombatSyncPayload;
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
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientInputHandler.tick(client);
			FreeflowCamera.tick(client);
		});
		HudRenderCallback.EVENT.register(DebugOverlay::render);

		ClientPlayNetworking.registerGlobalReceiver(CombatSyncPayload.TYPE,
				(payload, context) -> ClientCombatState.apply(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			FreeflowCamera.exit(client);
			ClientCombatState.reset();
		});
	}
}
