package com.example.worldofraces.entity;

import com.example.worldofraces.WorldOfRaces;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, WorldOfRaces.MODID);

    public static final RegistryObject<EntityType<RaceEntity>> RACE_ENTITY =
            ENTITY_TYPES.register("race_entity",
                    () -> EntityType.Builder.of(RaceEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.95f)
                            .build("race_entity"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}