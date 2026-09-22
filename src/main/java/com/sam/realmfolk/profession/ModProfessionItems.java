package com.sam.realmfolk.profession;

import com.sam.realmfolk.Realmfolk;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

public final class ModProfessionItems {
    public static final RegistryObject<Item> BLACKSMITH_HAMMER=Realmfolk.ITEMS.register("blacksmith_hammer",()->new Item(new Item.Properties().durability(384)));
    private ModProfessionItems(){}
    public static void bootstrap(){}
}
