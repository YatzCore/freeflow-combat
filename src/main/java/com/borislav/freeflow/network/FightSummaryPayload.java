package com.borislav.freeflow.network;

import com.borislav.freeflow.FreeflowMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: a fight ended; show the rating. */
public record FightSummaryPayload(int score, int maxCombo, int hits, int counters, int takedowns, int knockouts,
		int variety, boolean flawless, int xp, int seconds) implements CustomPacketPayload {
	public static final Type<FightSummaryPayload> TYPE = new Type<>(FreeflowMod.id("fight_summary"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FightSummaryPayload> CODEC =
			StreamCodec.of(FightSummaryPayload::write, FightSummaryPayload::read);

	private static void write(FriendlyByteBuf buf, FightSummaryPayload p) {
		buf.writeVarInt(p.score);
		buf.writeVarInt(p.maxCombo);
		buf.writeVarInt(p.hits);
		buf.writeVarInt(p.counters);
		buf.writeVarInt(p.takedowns);
		buf.writeVarInt(p.knockouts);
		buf.writeVarInt(p.variety);
		buf.writeBoolean(p.flawless);
		buf.writeVarInt(p.xp);
		buf.writeVarInt(p.seconds);
	}

	private static FightSummaryPayload read(FriendlyByteBuf buf) {
		return new FightSummaryPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
