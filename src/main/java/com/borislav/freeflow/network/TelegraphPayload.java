package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: enemy {@code mobId} will attack in {@code ticks} ticks (0 = attack over / cancelled).
 * {@code kind}: 0 = counterable melee, 1 = armed (can't counter, evade), 2 = ranged shot,
 * 3 = creeper fuse (counter kicks it away).
 */
public record TelegraphPayload(int mobId, int ticks, int kind) implements CustomPacketPayload {
	public static final int NORMAL = 0;
	public static final int ARMED = 1;
	public static final int RANGED = 2;
	public static final int CREEPER = 3;

	public static final Type<TelegraphPayload> TYPE = new Type<>(FreeflowMod.id("telegraph"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TelegraphPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TelegraphPayload::mobId,
			ByteBufCodecs.VAR_INT, TelegraphPayload::ticks,
			ByteBufCodecs.VAR_INT, TelegraphPayload::kind,
			TelegraphPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
