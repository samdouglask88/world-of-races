package com.sam.realmfolk.content;

import com.sam.realmfolk.Realmfolk;
import com.sam.realmfolk.content.item.RealmfolkUtilityItem;
import com.sam.realmfolk.content.item.UtilityAction;
import com.sam.realmfolk.content.item.GenderedResidentSpawnEggItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public final class ModItems {
    public static final RegistryObject<Item> SETTLEMENT_CHARTER = utility("settlement_charter", 1, UtilityAction.SETTLEMENT_CHARTER);
    public static final RegistryObject<Item> SETTLEMENT_LEDGER = utility("settlement_ledger", 1, UtilityAction.SETTLEMENT_LEDGER);
    public static final RegistryObject<Item> FAMILY_REGISTER = utility("family_register", 1, UtilityAction.FAMILY_REGISTER);
    public static final RegistryObject<Item> RESIDENCE_DEED = utility("residence_deed", 1, UtilityAction.RESIDENCE_DEED);
    public static final RegistryObject<Item> CONSTRUCTION_BLUEPRINT = utility("construction_blueprint", 16, UtilityAction.CONSTRUCTION_BLUEPRINT);
    public static final RegistryObject<Item> SURVEYORS_ROD = utility("surveyors_rod", 1, UtilityAction.SURVEYORS_ROD);
    public static final RegistryObject<Item> BUILDER_HAMMER = Realmfolk.ITEMS.register("builder_hammer",
            () -> new RealmfolkUtilityItem(new Item.Properties().durability(384), UtilityAction.BUILDER_HAMMER));
    public static final RegistryObject<Item> WORK_CONTRACT = utility("work_contract", 16, UtilityAction.WORK_CONTRACT);
    public static final RegistryObject<Item> WORK_ORDER_BOOK = utility("work_order_book", 1, UtilityAction.WORK_ORDER_BOOK);
    public static final RegistryObject<Item> LEADERS_SEAL = utility("leaders_seal", 1, UtilityAction.LEADERS_SEAL);
    public static final RegistryObject<Item> GUARD_HORN = utility("guard_horn", 1, UtilityAction.GUARD_HORN);
    public static final RegistryObject<Item> GUARD_BADGE = utility("guard_badge", 16, UtilityAction.GUARD_BADGE);
    public static final RegistryObject<Item> PROVISIONS_PACK = Realmfolk.ITEMS.register("provisions_pack",
            () -> new Item(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(12).saturationMod(0.9F).build())));
    public static final RegistryObject<Item> HEALER_SATCHEL = Realmfolk.ITEMS.register("healer_satchel",
            () -> new RealmfolkUtilityItem(new Item.Properties().durability(96), UtilityAction.HEALER_SATCHEL));
    public static final RegistryObject<Item> WEDDING_RING = utility("wedding_ring", 1, UtilityAction.WEDDING_RING);
    public static final RegistryObject<Item> SIMPLE_GIFT = utility("simple_gift", 16, UtilityAction.SIMPLE_GIFT);
    public static final RegistryObject<Item> WOODEN_TOY = utility("wooden_toy", 16, UtilityAction.WOODEN_TOY);
    public static final RegistryObject<Item> FAMILY_CERTIFICATE = utility("family_certificate", 1, UtilityAction.FAMILY_CERTIFICATE);
    public static final RegistryObject<Item> COOK_LADLE = Realmfolk.ITEMS.register("cook_ladle",
            () -> new Item(new Item.Properties().durability(192)));
    public static final RegistryObject<Item> KITCHEN_KNIFE = Realmfolk.ITEMS.register("kitchen_knife",
            () -> new Item(new Item.Properties().durability(192)));

    public static final RegistryObject<Item> RESIDENCE_MARKER = blockItem("residence_marker", ModBlocks.RESIDENCE_MARKER);
    public static final RegistryObject<Item> STORAGE_MARKER = blockItem("storage_marker", ModBlocks.STORAGE_MARKER);
    public static final RegistryObject<Item> TREASURY_MARKER = blockItem("treasury_marker", ModBlocks.TREASURY_MARKER);
    public static final RegistryObject<Item> PATROL_MARKER = blockItem("patrol_marker", ModBlocks.PATROL_MARKER);
    public static final RegistryObject<Item> WORK_MARKER = blockItem("work_marker", ModBlocks.WORK_MARKER);
    public static final RegistryObject<Item> PROJECT_BOARD = blockItem("project_board", ModBlocks.PROJECT_BOARD);
    public static final RegistryObject<Item> CRADLE = blockItem("cradle", ModBlocks.CRADLE);
    public static final RegistryObject<Item> MALE_RESIDENT_SPAWN_EGG = Realmfolk.ITEMS.register("male_resident_spawn_egg",
            () -> new GenderedResidentSpawnEggItem(true, 0x6B4930, 0x3A78A1, new Item.Properties()));
    public static final RegistryObject<Item> FEMALE_RESIDENT_SPAWN_EGG = Realmfolk.ITEMS.register("female_resident_spawn_egg",
            () -> new GenderedResidentSpawnEggItem(false, 0x80534A, 0xD6A35B, new Item.Properties()));

    public static final List<RegistryObject<Item>> ALL = List.of(
            MALE_RESIDENT_SPAWN_EGG, FEMALE_RESIDENT_SPAWN_EGG,
            SETTLEMENT_CHARTER, SETTLEMENT_LEDGER, FAMILY_REGISTER, RESIDENCE_DEED,
            CONSTRUCTION_BLUEPRINT, SURVEYORS_ROD, BUILDER_HAMMER, WORK_CONTRACT,
            WORK_ORDER_BOOK, LEADERS_SEAL, GUARD_HORN, GUARD_BADGE, PROVISIONS_PACK,
            HEALER_SATCHEL, WEDDING_RING, SIMPLE_GIFT, WOODEN_TOY, FAMILY_CERTIFICATE,
            COOK_LADLE, KITCHEN_KNIFE, RESIDENCE_MARKER, STORAGE_MARKER, TREASURY_MARKER,
            PATROL_MARKER, WORK_MARKER, PROJECT_BOARD, CRADLE);

    private static RegistryObject<Item> utility(String id, int stackSize, UtilityAction action) {
        return Realmfolk.ITEMS.register(id,
                () -> new RealmfolkUtilityItem(new Item.Properties().stacksTo(stackSize), action));
    }

    private static RegistryObject<Item> blockItem(String id,
                                                   RegistryObject<net.minecraft.world.level.block.Block> block) {
        return Realmfolk.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private ModItems() {}
    public static void bootstrap() {}
}
