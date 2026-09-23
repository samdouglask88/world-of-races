package com.sam.realmfolk.client.menu;

import com.sam.realmfolk.Realmfolk;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Realmfolk.MODID);

    public static final RegistryObject<MenuType<NpcMenu>> NPC_MENU =
            MENUS.register("npc_menu", () -> IForgeMenuType.create(NpcMenu::new));
    public static final RegistryObject<MenuType<FamilyTreeMenu>> FAMILY_TREE_MENU =
            MENUS.register("family_tree_menu", () -> IForgeMenuType.create(FamilyTreeMenu::new));
    public static final RegistryObject<MenuType<ConversationMenu>> CONVERSATION_MENU =
            MENUS.register("conversation_menu", () -> IForgeMenuType.create(ConversationMenu::new));
    public static final RegistryObject<MenuType<ProfileMenu>> PROFILE_MENU =
            MENUS.register("profile_menu", () -> IForgeMenuType.create(ProfileMenu::new));
    public static final RegistryObject<MenuType<TradeMenu>> TRADE_MENU =
            MENUS.register("trade_menu", () -> IForgeMenuType.create(TradeMenu::new));
    public static final RegistryObject<MenuType<ProfessionMenu>> PROFESSION_MENU =
            MENUS.register("profession_menu", () -> IForgeMenuType.create(ProfessionMenu::new));
    public static final RegistryObject<MenuType<DocumentMenu>> DOCUMENT_MENU =
            MENUS.register("document_menu", () -> IForgeMenuType.create(DocumentMenu::new));

    private ModMenuTypes() {
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
