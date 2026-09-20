package com.example.worldofraces.family;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FamilyRegistry {

    private static final List<Family> NOBLE_FAMILIES = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private static final String[] NOBLE_SURNAMES = {
        "Blackwood", "Stormwind", "Ashford", "Ironheart", "Ravenscroft",
        "Wintermere", "Thornfield", "Silverbrook", "Goldleaf", "Ironforge"
    };

    // Retorna uma familia nobre existente aleatoria, ou cria uma nova se ainda 
    // nao houver familias registradas ou por chance de criar uma nova linhagem
    public static Family getOrCreateNobleFamily() {
        // 30% de chance de criar uma nova familia, se ainda houver sobrenomes disponiveis
        boolean shouldCreateNew = NOBLE_FAMILIES.size() < NOBLE_SURNAMES.length 
                && (NOBLE_FAMILIES.isEmpty() || RANDOM.nextDouble() < 0.3);

        if (shouldCreateNew) {
            String surname = NOBLE_SURNAMES[NOBLE_FAMILIES.size()];
            Family newFamily = new Family(surname);
            NOBLE_FAMILIES.add(newFamily);
            return newFamily;
        }

        return NOBLE_FAMILIES.get(RANDOM.nextInt(NOBLE_FAMILIES.size()));
    }

    public static List<Family> getAllNobleFamilies() {
        return NOBLE_FAMILIES;
    }
}