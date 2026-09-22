package com.sam.realmfolk.client.menu;

import com.sam.realmfolk.dialogue.DialogueRegistry;
import com.sam.realmfolk.society.HouseRegistry;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record ConversationSnapshot(String nodeId, String speaker, String text,
                                   int affinity, List<Response> responses) {
    public static ConversationSnapshot create(DialogueRegistry.DialogueNode node, PersonData person,
                                              HumanSocietySavedData society, int affinity) {
        String resolvedText = resolve(node.text(), person, society, affinity);
        List<Response> responses = node.responses().stream()
                .limit(4)
                .map(value -> new Response(value.text(), affinity >= value.requiresAffinity(),
                        value.requiresAffinity()))
                .toList();
        return new ConversationSnapshot(node.id(), person.getDisplayName(), resolvedText,
                affinity, responses);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(nodeId, 128);
        buffer.writeUtf(speaker, 128);
        buffer.writeUtf(text, 1024);
        buffer.writeInt(affinity);
        buffer.writeVarInt(responses.size());
        responses.forEach(response -> {
            buffer.writeUtf(response.text(), 256);
            buffer.writeBoolean(response.unlocked());
            buffer.writeInt(response.requiredAffinity());
        });
    }

    public static ConversationSnapshot read(FriendlyByteBuf buffer) {
        String nodeId = buffer.readUtf(128);
        String speaker = buffer.readUtf(128);
        String text = buffer.readUtf(1024);
        int affinity = buffer.readInt();
        int size = buffer.readVarInt();
        List<Response> responses = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            responses.add(new Response(buffer.readUtf(256), buffer.readBoolean(), buffer.readInt()));
        }
        return new ConversationSnapshot(nodeId, speaker, text, affinity, List.copyOf(responses));
    }

    private static String resolve(String text, PersonData person,
                                  HumanSocietySavedData society, int affinity) {
        String house = person.getHouseId() == null ? "nenhuma Casa nobre"
                : HouseRegistry.get(person.getHouseId()).map(value -> "Casa " + value.surname())
                .orElse("uma Casa desconhecida");
        String father = person.getFatherId() == null ? "nao conheco meu pai"
                : society.getPerson(person.getFatherId()).map(PersonData::getDisplayName).orElse("pai desconhecido");
        String mother = person.getMotherId() == null ? "nao conheco minha mae"
                : society.getPerson(person.getMotherId()).map(PersonData::getDisplayName).orElse("mae desconhecida");
        String mood = affinity >= 60 ? "muito feliz em ver voce"
                : affinity >= 20 ? "contente em conversar"
                : affinity <= -20 ? "desconfiado com sua presenca" : "tranquilo";
        return text.replace("{name}", person.getFirstName())
                .replace("{house}", house)
                .replace("{father}", father)
                .replace("{mother}", mother)
                .replace("{children}", Integer.toString(person.getChildrenIds().size()))
                .replace("{mood}", mood);
    }

    public record Response(String text, boolean unlocked, int requiredAffinity) {}
}
