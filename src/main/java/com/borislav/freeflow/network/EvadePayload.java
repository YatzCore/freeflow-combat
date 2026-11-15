package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: start a dodge. The client moves {@code distance} blocks along {@code yaw} over
 * {@code ticks} ticks. {@code vaultTargetId} is the enemy being vaulted over, or -1 for
 * a plain evade.
 */
public record EvadePayload(float yaw, float distance, int ticks, int vaultTargetId) implements CustomPacketPayload {
	public static final Type<EvadePayload> TYPE = new Type<>(FreeflowMod.id("evade"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EvadePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, EvadePayload::yaw,
			ByteBufCodecs.FLOAT, EvadePayload::distance,
			ByteBufCodecs.VAR_INT, EvadePayload::ticks,
			ByteBufCodecs.VAR_INT, EvadePayload::vaultTargetId,
			EvadePayload::new);

	public boolean isVault() {
		return vaultTargetId >= 0;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
