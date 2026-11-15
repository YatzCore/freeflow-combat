package com.borislav.freeflow.client;

import com.borislav.freeflow.FreeflowMod;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-only entrypoint: keybinds, HUD, camera and animations are wired up here.
 */
public class FreeflowClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FreeflowMod.LOGGER.info("Freeflow Combat client initializing");
	}
}
