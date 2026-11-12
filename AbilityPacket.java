package com.trollmod.network;

import com.trollmod.CaineAbilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player pressed the key of this ability. */
public record AbilityPacket(int ability) {

    public static void encode(AbilityPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.ability());
    }

    public static AbilityPacket decode(FriendlyByteBuf buf) {
        return new AbilityPacket(buf.readVarInt());
    }

    public static void handle(AbilityPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                CaineAbilities.use(player, packet.ability());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
