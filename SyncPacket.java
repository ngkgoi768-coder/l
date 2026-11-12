package com.trollmod.network;

import com.trollmod.client.ClientCaineState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server -> client: is this player КЕЙН, and are they currently invisible? */
public record SyncPacket(UUID player, boolean caine, boolean invisible) {

    public static void encode(SyncPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.player());
        buf.writeBoolean(packet.caine());
        buf.writeBoolean(packet.invisible());
    }

    public static SyncPacket decode(FriendlyByteBuf buf) {
        return new SyncPacket(buf.readUUID(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(SyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientCaineState.set(packet.player(), packet.caine(), packet.invisible())));
        ctx.get().setPacketHandled(true);
    }
}
