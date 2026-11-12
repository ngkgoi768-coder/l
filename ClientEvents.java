package com.trollmod.client;

import com.trollmod.CaineAbilities;
import com.trollmod.TrollMod;
import com.trollmod.network.AbilityPacket;
import com.trollmod.network.TrollNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = TrollMod.MODID, value = Dist.CLIENT)
public class ClientEvents {

    /**
     * An invisible player is not drawn at all (no body, armor, held items or name).
     * Otherwise a transformed player is drawn with the КЕЙН model instead of the vanilla one.
     */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        UUID id = player.getUUID();

        if (ClientCaineState.isVanished(id)) {
            event.setCanceled(true);
            return;
        }
        if (!ClientCaineState.is(id)) {
            return;
        }
        CaineModel model = CaineModel.get();
        if (model == null) {
            return;     // model failed to load (see log): keep the normal player model
        }
        event.setCanceled(true);
        model.render(player, event.getPartialTick(), event.getPoseStack(),
                event.getMultiBufferSource(), event.getPackedLight());
    }

    /** No floating hand in first person while invisible. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && ClientCaineState.isVanished(mc.player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean canAct = mc.player != null && mc.screen == null
                && ClientCaineState.is(mc.player.getUUID());

        // Always drain the click queue so presses made in menus don't fire later.
        while (Keys.INVISIBILITY.consumeClick()) {
            if (canAct) {
                TrollNetwork.CHANNEL.sendToServer(new AbilityPacket(CaineAbilities.INVISIBILITY));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCaineState.clear();
    }
}
