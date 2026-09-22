package com.sam.realmfolk.entity;

import com.sam.realmfolk.Realmfolk;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Realmfolk.MODID);

    public static final RegistryObject<EntityType<ResidentEntity>> RESIDENT =
            ENTITY_TYPES.register("resident",
                    () -> EntityType.Builder.of(ResidentEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.95f)
                            .build("resident"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
