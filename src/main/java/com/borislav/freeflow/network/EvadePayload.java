package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: start a dodge-type move. The client moves {@code distance} blocks along {@code yaw}
 * over {@code ticks} ticks. {@code mode}: ROLL, VAULT (over {@code vaultTargetId}),
 * BACKFLIP (no direction held) or SLIDE.
 */
public record EvadePayload(float yaw, float distance, int ticks, int vaultTargetId, int mode)
		implements CustomPacketPayload {
	public static final int ROLL = 0;
	public static final int VAULT = 1;
	public static final int BACKFLIP = 2;
	public static final int SLIDE = 3;

	public static final Type<EvadePayload> TYPE = new Type<>(FreeflowMod.id("evade"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EvadePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, EvadePayload::yaw,
			ByteBufCodecs.FLOAT, EvadePayload::distance,
			ByteBufCodecs.VAR_INT, EvadePayload::ticks,
			ByteBufCodecs.VAR_INT, EvadePayload::vaultTargetId,
			ByteBufCodecs.VAR_INT, EvadePayload::mode,
			EvadePayload::new);

	public boolean isVault() {
		return mode == VAULT;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
