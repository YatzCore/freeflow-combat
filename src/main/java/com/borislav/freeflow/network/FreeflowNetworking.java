package com.borislav.freeflow.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers every payload type. Must run on both sides before any packet is sent. */
public final class FreeflowNetworking {
	private FreeflowNetworking() {}

	public static void registerPayloads() {
		PayloadTypeRegistry.playC2S().register(CombatInputPayload.TYPE, CombatInputPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CombatSyncPayload.TYPE, CombatSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StrikeStartPayload.TYPE, StrikeStartPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ComboPayload.TYPE, ComboPayload.CODEC);
	}
}
