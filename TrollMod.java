package com.trollmod;

import com.trollmod.item.CaineItem;
import com.trollmod.network.TrollNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(TrollMod.MODID)
public class TrollMod {
    public static final String MODID = "trollmod";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Item> CAINE =
            ITEMS.register("caine", () -> new CaineItem(new Item.Properties().stacksTo(1)));

    /** The "ТРОЛЛИНГ" creative tab (name comes from the itemGroup.trollmod lang key). */
    public static final RegistryObject<CreativeModeTab> TROLLING_TAB = TABS.register("trolling",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.trollmod"))
                    .icon(() -> new ItemStack(CAINE.get()))
                    .displayItems((params, output) -> output.accept(CAINE.get()))
                    .build());

    public TrollMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        TABS.register(bus);
        TrollNetwork.register();
    }
}
