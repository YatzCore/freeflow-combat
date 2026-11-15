package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** S2C: the player countered these enemies (empty list = whiffed counter). */
public record CounterPayload(List<Integer> mobIds) implements CustomPacketPayload {
	public static final Type<CounterPayload> TYPE = new Type<>(FreeflowMod.id("counter"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CounterPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), CounterPayload::mobIds,
			CounterPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
