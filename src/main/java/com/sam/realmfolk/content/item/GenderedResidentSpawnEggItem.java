package com.sam.realmfolk.content.item;

import com.sam.realmfolk.entity.ModEntityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeSpawnEggItem;

/** Spawn egg that applies gender before ResidentEntity creates its persistent PersonData. */
public final class GenderedResidentSpawnEggItem extends ForgeSpawnEggItem {
    public static final String FORCED_GENDER_TAG = "RealmfolkForcedGender";
    private final boolean male;

    public GenderedResidentSpawnEggItem(boolean male, int backgroundColor, int highlightColor,
                                        Item.Properties properties) {
        super(ModEntityTypes.RESIDENT, backgroundColor, highlightColor, properties);
        this.male = male;
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        CompoundTag entityTag = new CompoundTag();
        entityTag.putBoolean("IsMale", male);
        entityTag.putBoolean(FORCED_GENDER_TAG, true);
        stack.getOrCreateTag().put("EntityTag", entityTag);
        return stack;
    }
}
