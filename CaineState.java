package com.trollmod;

import com.trollmod.network.SyncPacket;
import com.trollmod.network.TrollNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-side state: is this player currently КЕЙН?
 * Stored in the player's persisted data (survives relog and death) and mirrored to clients by packet.
 */
public final class CaineState {
    private static final String KEY = "trollmod_caine";
    private static final String FLIGHT_GRANTED_KEY = "trollmod_caine_flight";
    private static final float FLY_SPEED = 0.1F;      // vanilla default is 0.05
    private static final float DEFAULT_FLY_SPEED = 0.05F;

    private CaineState() {
    }

    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void savePersisted(Player player, CompoundTag tag) {
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }

    public static boolean isCaine(Player player) {
        return persisted(player).getBoolean(KEY);
    }

    public static void set(ServerPlayer player, boolean caine) {
        CompoundTag tag = persisted(player);
        tag.putBoolean(KEY, caine);
        savePersisted(player, tag);
        if (!caine) {
            CaineAbilities.setInvisible(player, false, false);   // abilities end with the transformation
        }
        applyFlight(player);
        sync(player);
    }

    /** Grants (or takes back) flight, even in survival. Cheap enough to call every few ticks. */
    public static void applyFlight(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        CompoundTag tag = persisted(player);

        if (tag.getBoolean(KEY)) {
            if (!abilities.mayfly || abilities.getFlyingSpeed() != FLY_SPEED) {
                abilities.mayfly = true;
                abilities.setFlyingSpeed(FLY_SPEED);
                player.onUpdateAbilities();
            }
            if (!tag.getBoolean(FLIGHT_GRANTED_KEY)) {
                tag.putBoolean(FLIGHT_GRANTED_KEY, true);
                savePersisted(player, tag);
            }
            player.resetFallDistance();
        } else if (tag.getBoolean(FLIGHT_GRANTED_KEY)) {
            boolean keep = player.isCreative() || player.isSpectator();
            abilities.mayfly = keep;
            if (!keep) {
                abilities.flying = false;
            }
            abilities.setFlyingSpeed(DEFAULT_FLY_SPEED);
            player.onUpdateAbilities();
            tag.putBoolean(FLIGHT_GRANTED_KEY, false);
            savePersisted(player, tag);
        }
    }

    /** Tells the player and everyone who can see them. */
    public static void sync(ServerPlayer player) {
        TrollNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new SyncPacket(player.getUUID(), isCaine(player), CaineAbilities.isInvisible(player)));
    }

    /** Tells one viewer about one target (used when the target comes into view). */
    public static void syncTo(ServerPlayer viewer, ServerPlayer target) {
        TrollNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new SyncPacket(target.getUUID(), isCaine(target), CaineAbilities.isInvisible(target)));
    }
}
