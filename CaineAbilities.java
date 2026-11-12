package com.trollmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The abilities of КЕЙН. Every ability has a numeric id that the client sends to the server. */
public final class CaineAbilities {
    public static final int INVISIBILITY = 0;

    private static final String INVISIBLE_KEY = "trollmod_invisible";
    private static final int SMOKE_TICKS = 5 * 20;      // the smoke cloud lasts 5 seconds

    /** A puff of white smoke left where the player vanished. */
    private static final class Cloud {
        final ServerLevel level;
        final double x, y, z;
        int ticksLeft = SMOKE_TICKS;

        Cloud(ServerLevel level, double x, double y, double z) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final Map<UUID, Cloud> CLOUDS = new HashMap<>();

    private CaineAbilities() {
    }

    /** Called when a player presses an ability key. Abilities only work while КЕЙН. */
    public static void use(ServerPlayer player, int ability) {
        if (!CaineState.isCaine(player)) {
            return;
        }
        if (ability == INVISIBILITY) {
            setInvisible(player, !isInvisible(player), true);
        }
    }

    public static boolean isInvisible(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(INVISIBLE_KEY);
    }

    public static void setInvisible(ServerPlayer player, boolean on, boolean notify) {
        if (isInvisible(player) == on) {
            return;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag tag = root.getCompound(Player.PERSISTED_NBT_TAG);
        tag.putBoolean(INVISIBLE_KEY, on);
        root.put(Player.PERSISTED_NBT_TAG, tag);

        if (on) {
            refreshEffect(player);
            CLOUDS.put(player.getUUID(),
                    new Cloud(player.serverLevel(), player.getX(), player.getY(), player.getZ()));
        } else {
            player.removeEffect(MobEffects.INVISIBILITY);
            CLOUDS.remove(player.getUUID());
        }
        if (notify) {
            player.displayClientMessage(Component.translatable(
                    on ? "msg.trollmod.vanish_on" : "msg.trollmod.vanish_off"), true);
        }
        CaineState.sync(player);
    }

    /** Every tick: keep the invisibility effect alive and emit the smoke. */
    public static void tick(ServerPlayer player) {
        if (isInvisible(player)) {
            if (!CaineState.isCaine(player)) {
                setInvisible(player, false, false);
            } else if (player.tickCount % 40 == 0) {
                refreshEffect(player);
            }
        }

        Cloud cloud = CLOUDS.get(player.getUUID());
        if (cloud != null) {
            cloud.level.sendParticles(ParticleTypes.WHITE_SMOKE, cloud.x, cloud.y + 1.0D, cloud.z,
                    5, 0.5D, 0.7D, 0.5D, 0.01D);
            if (--cloud.ticksLeft <= 0) {
                CLOUDS.remove(player.getUUID());
            }
        }
    }

    public static void forget(ServerPlayer player) {
        CLOUDS.remove(player.getUUID());
    }

    /** The vanilla effect makes mobs lose track of the player; our renderer hides the rest. */
    private static void refreshEffect(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false, false));
    }
}
