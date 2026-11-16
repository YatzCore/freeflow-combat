package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: move the local player to (x, z) over {@code ticks} ticks, ending facing {@code faceYaw}. */
public record MoveToPayload(double x, double z, int ticks, float faceYaw) implements CustomPacketPayload {
	public static final Type<MoveToPayload> TYPE = new Type<>(FreeflowMod.id("move_to"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MoveToPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, MoveToPayload::x,
			ByteBufCodecs.DOUBLE, MoveToPayload::z,
			ByteBufCodecs.VAR_INT, MoveToPayload::ticks,
			ByteBufCodecs.FLOAT, MoveToPayload::faceYaw,
			MoveToPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
