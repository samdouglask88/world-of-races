package com.example.worldofraces.util;

import java.util.Random;

public class NameGenerator {

    private static final String[] MALE_NAMES = {
        "Aldric", "Gareth", "Roderick", "Thane", "Baldric", "Cedric",
        "Edmund", "Godfrey", "Harold", "Leofric", "Osric", "Wystan",
        "Alaric", "Bertram", "Dunstan", "Eldric", "Fenwick", "Godric"
    };

    private static final String[] FEMALE_NAMES = {
        "Elara", "Mira", "Rowena", "Isolde", "Wynne", "Adelina",
        "Brielle", "Ceridwen", "Elowen", "Freya", "Guinevere", "Ophira",
        "Seraphina", "Thessaly", "Wilhelmina", "Ysolde", "Adaline", "Briar"
    };

    private static final String[] SURNAMES = {
        "Blackwood", "Stormwind", "Ashford", "Ironheart", "Ravenscroft",
        "Wintermere", "Thornfield", "Oakhaven", "Greymoor", "Hawthorne",
        "Fairwind", "Duskwood", "Stonebridge", "Silverbrook", "Nightshade",
        "Goldleaf", "Ironforge", "Wolfsbane"
    };

    private static final Random RANDOM = new Random();

    public static String generateFullName(boolean isMale) {
        String firstName = isMale
            ? MALE_NAMES[RANDOM.nextInt(MALE_NAMES.length)]
            : FEMALE_NAMES[RANDOM.nextInt(FEMALE_NAMES.length)];
        String surname = SURNAMES[RANDOM.nextInt(SURNAMES.length)];
        return firstName + " " + surname;
    }

    public static String generateFirstName(boolean isMale) {
        return isMale
            ? MALE_NAMES[RANDOM.nextInt(MALE_NAMES.length)]
            : FEMALE_NAMES[RANDOM.nextInt(FEMALE_NAMES.length)];
    }

    public static String generateRandomFullName() {
        return generateFullName(RANDOM.nextBoolean());
    }
}