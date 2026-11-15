package com.borislav.freeflow;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (client + server) entrypoint for Freeflow Combat.
 * Registries, networking and server-side combat systems are wired up here.
 */
public class FreeflowMod implements ModInitializer {
	public static final String MOD_ID = "freeflow";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Freeflow Combat initializing");
	}
}
