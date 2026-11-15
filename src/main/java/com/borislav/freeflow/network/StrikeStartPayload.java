package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: the server started a strike. {@code targetId} is the entity id (-1 = whiff),
 * {@code travelTicks} how many ticks the client should take to lunge to it.
 */
public record StrikeStartPayload(int targetId, int travelTicks) implements CustomPacketPayload {
	public static final Type<StrikeStartPayload> TYPE = new Type<>(FreeflowMod.id("strike_start"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StrikeStartPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StrikeStartPayload::targetId,
			ByteBufCodecs.VAR_INT, StrikeStartPayload::travelTicks,
			StrikeStartPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
