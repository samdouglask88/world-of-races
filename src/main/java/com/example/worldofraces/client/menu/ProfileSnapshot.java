package com.example.worldofraces.client.menu;

import com.example.worldofraces.society.HouseRegistry;
import com.example.worldofraces.society.HumanSocietySavedData;
import com.example.worldofraces.society.PersonData;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record ProfileSnapshot(UUID personId, String name, String house, String gender,
                              String lifeStage, boolean alive, int affinity, int knowledge,
                              String father, String mother, String spouse, int children,
                              boolean loaded, boolean canLocate, int age, String profession,
                              String personality, String morality, String origin, String residence,
                              String lastInteraction, int strength, int intelligence, int charisma, int courage) {
    public static ProfileSnapshot create(PersonData person, HumanSocietySavedData society,
                                         UUID playerId, boolean loaded, boolean recruited, long gameTime,
                                         String actualProfession) {
        int affinity = person.getAffinity(playerId);
        int knowledge = affinity >= 50 ? 3 : affinity >= 25 ? 2 : affinity >= 10 ? 1 : 0;
        String house = person.getHouseId() == null ? "Sem Casa" : HouseRegistry.get(person.getHouseId())
                .map(value -> "Casa " + value.surname()).orElse("Casa desconhecida");
        return new ProfileSnapshot(person.getPersonId(), person.getDisplayName(), house,
                person.getGender().name(), person.getLifeStage().name(),
                person.getStatus().name().equals("ALIVE"), affinity, knowledge,
                relative(society, person.getFatherId(), "Desconhecido"),
                relative(society, person.getMotherId(), "Desconhecida"),
                relative(society, person.getSpouseId(), "Nenhum"), person.getChildrenIds().size(),
                loaded, loaded && (affinity >= 60 || recruited), person.getAge(), actualProfession,
                person.getPersonality(), person.getMorality(), person.getOrigin(), person.getResidence(),
                interactionLabel(person.getLastInteractionTime(), gameTime), person.getStrength(),
                person.getIntelligence(), person.getCharisma(), person.getCourage());
    }

    private static String relative(HumanSocietySavedData society, UUID id, String fallback) {
        return id == null ? fallback : society.getPerson(id).map(PersonData::getDisplayName).orElse(fallback);
    }

    public void write(FriendlyByteBuf b) {
        b.writeUUID(personId); b.writeUtf(name); b.writeUtf(house); b.writeUtf(gender); b.writeUtf(lifeStage);
        b.writeBoolean(alive); b.writeInt(affinity); b.writeVarInt(knowledge);
        b.writeUtf(father); b.writeUtf(mother); b.writeUtf(spouse); b.writeVarInt(children);
        b.writeBoolean(loaded); b.writeBoolean(canLocate);
        b.writeVarInt(age); b.writeUtf(profession); b.writeUtf(personality); b.writeUtf(morality);
        b.writeUtf(origin); b.writeUtf(residence); b.writeUtf(lastInteraction);
        b.writeVarInt(strength); b.writeVarInt(intelligence); b.writeVarInt(charisma); b.writeVarInt(courage);
    }

    public static ProfileSnapshot read(FriendlyByteBuf b) {
        return new ProfileSnapshot(b.readUUID(), b.readUtf(), b.readUtf(), b.readUtf(), b.readUtf(),
                b.readBoolean(), b.readInt(), b.readVarInt(), b.readUtf(), b.readUtf(), b.readUtf(),
                b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readUtf(), b.readUtf(),
                b.readUtf(), b.readUtf(), b.readUtf(), b.readUtf(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarInt());
    }

    private static String interactionLabel(long last, long now) {
        if (last < 0) return "Nunca";
        long days = Math.max(0, (now - last) / 24000L);
        return days == 0 ? "Hoje" : days == 1 ? "Ontem" : "Ha " + days + " dias";
    }
}
