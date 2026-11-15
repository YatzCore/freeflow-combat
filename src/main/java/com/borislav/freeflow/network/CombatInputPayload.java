package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.combat.CombatAction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: a combat input. {@code forward}/{@code strafe} are the player's movement
 * input (-1..1) at the moment of the press, used later for directional targeting.
 */
public record CombatInputPayload(CombatAction action, float forward, float strafe) implements CustomPacketPayload {
	public static final Type<CombatInputPayload> TYPE = new Type<>(FreeflowMod.id("combat_input"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CombatInputPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(CombatAction::byId, CombatAction::ordinal), CombatInputPayload::action,
			ByteBufCodecs.FLOAT, CombatInputPayload::forward,
			ByteBufCodecs.FLOAT, CombatInputPayload::strafe,
			CombatInputPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
