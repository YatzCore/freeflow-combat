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
		PayloadTypeRegistry.playS2C().register(TelegraphPayload.TYPE, TelegraphPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CounterPayload.TYPE, CounterPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(EvadePayload.TYPE, EvadePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CombatEventPayload.TYPE, CombatEventPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(AnimationPayload.TYPE, AnimationPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MobReactionPayload.TYPE, MobReactionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FightSummaryPayload.TYPE, FightSummaryPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MoveToPayload.TYPE, MoveToPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC);
	}
}
