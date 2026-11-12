package com.trollmod.item;

import com.trollmod.CaineState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Right-click: turn into КЕЙН (and fly). Right-click again: back to a normal player. */
public class CaineItem extends Item {
    public CaineItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            boolean becomeCaine = !CaineState.isCaine(serverPlayer);
            CaineState.set(serverPlayer, becomeCaine);
            serverPlayer.displayClientMessage(Component.translatable(
                    becomeCaine ? "msg.trollmod.caine_on" : "msg.trollmod.caine_off"), true);
            serverPlayer.getCooldowns().addCooldown(this, 20);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
