package com.example.worldofraces.client.menu;

import com.example.worldofraces.WorldOfRaces;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, WorldOfRaces.MODID);

    public static final RegistryObject<MenuType<NpcMenu>> NPC_MENU =
            MENUS.register("npc_menu", () -> IForgeMenuType.create(NpcMenu::new));

    private ModMenuTypes() {
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
