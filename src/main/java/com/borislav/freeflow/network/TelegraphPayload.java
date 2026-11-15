package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: enemy {@code mobId} will attack in {@code ticks} ticks (0 = attack over / cancelled). */
public record TelegraphPayload(int mobId, int ticks) implements CustomPacketPayload {
	public static final Type<TelegraphPayload> TYPE = new Type<>(FreeflowMod.id("telegraph"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TelegraphPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TelegraphPayload::mobId,
			ByteBufCodecs.VAR_INT, TelegraphPayload::ticks,
			TelegraphPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
