package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import com.borislav.freeflow.combat.CombatAction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: a combat input. {@code forward}/{@code strafe} are the raw movement input
 * (-1..1) relative to {@code aimYaw} (the camera yaw while orbiting, otherwise the
 * player yaw), used for directional targeting.
 */
public record CombatInputPayload(CombatAction action, float forward, float strafe, float aimYaw)
		implements CustomPacketPayload {
	public static final Type<CombatInputPayload> TYPE = new Type<>(FreeflowMod.id("combat_input"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CombatInputPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(CombatAction::byId, CombatAction::ordinal), CombatInputPayload::action,
			ByteBufCodecs.FLOAT, CombatInputPayload::forward,
			ByteBufCodecs.FLOAT, CombatInputPayload::strafe,
			ByteBufCodecs.FLOAT, CombatInputPayload::aimYaw,
			CombatInputPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
