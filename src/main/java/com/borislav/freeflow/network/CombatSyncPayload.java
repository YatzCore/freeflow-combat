package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.combat.CombatAction;
import com.borislav.freeflow.combat.CombatMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: the player's combat state, sent whenever it changes. */
public record CombatSyncPayload(CombatMode mode, boolean inCombat, int engagedCount, CombatAction lastAction,
		int acceptedInputs) implements CustomPacketPayload {
	public static final Type<CombatSyncPayload> TYPE = new Type<>(FreeflowMod.id("combat_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CombatSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(CombatMode::byId, CombatMode::ordinal), CombatSyncPayload::mode,
			ByteBufCodecs.BOOL, CombatSyncPayload::inCombat,
			ByteBufCodecs.VAR_INT, CombatSyncPayload::engagedCount,
			ByteBufCodecs.VAR_INT.map(CombatAction::byId, CombatAction::ordinal), CombatSyncPayload::lastAction,
			ByteBufCodecs.VAR_INT, CombatSyncPayload::acceptedInputs,
			CombatSyncPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
