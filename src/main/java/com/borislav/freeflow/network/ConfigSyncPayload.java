package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: the server's gameplay settings (JSON), so the client predicts moves with the same numbers. */
public record ConfigSyncPayload(String json) implements CustomPacketPayload {
	public static final Type<ConfigSyncPayload> TYPE = new Type<>(FreeflowMod.id("config_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(1 << 18), ConfigSyncPayload::json, ConfigSyncPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
