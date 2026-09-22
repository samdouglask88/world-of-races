package com.sam.realmfolk.society;

import com.sam.realmfolk.Realmfolk;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class HouseRegistry {
    private static final Random RANDOM = new Random();

    public static final List<House> HUMAN_HOUSES = List.of(
            house("taylor", "Taylor"),
            house("trollbane", "Trollbane"),
            house("rivendare", "Rivendare"),
            house("reed", "Reed"),
            house("frostvale", "Frostvale"),
            house("blackwood", "BlackWood"),
            house("blackfyre", "Blackfyre"),
            house("von_paro", "Von'Paro"),
            house("von_astur", "von Astur"),
            house("lothar", "Lothar"),
            house("lencaster", "Lencaster")
    );

    private HouseRegistry() {
    }

    private static House house(String path, String surname) {
        return new House(new ResourceLocation(Realmfolk.MODID, path), surname);
    }

    public static House randomHumanHouse() {
        return HUMAN_HOUSES.get(RANDOM.nextInt(HUMAN_HOUSES.size()));
    }

    public static Optional<House> get(ResourceLocation id) {
        return HUMAN_HOUSES.stream().filter(house -> house.id().equals(id)).findFirst();
    }

    public static Optional<House> getBySurname(String surname) {
        return HUMAN_HOUSES.stream().filter(house -> house.surname().equals(surname)).findFirst();
    }
}
