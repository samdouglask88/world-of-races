package com.sam.realmfolk.client.menu;

import com.sam.realmfolk.society.HouseRegistry;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record FamilyTreeSnapshot(Node focus, @Nullable Node father, @Nullable Node mother,
                                 @Nullable Node spouse, List<Node> children,
                                 int childPage, int childPageCount, int totalChildren) {
    public static final int CHILDREN_PER_PAGE = 4;

    public static FamilyTreeSnapshot create(HumanSocietySavedData society, UUID focusId, int requestedPage) {
        PersonData focus = society.getPerson(focusId)
                .orElseThrow(() -> new IllegalArgumentException("Pessoa da arvore nao encontrada: " + focusId));
        int pages = Math.max(1, (focus.getChildrenIds().size() + CHILDREN_PER_PAGE - 1) / CHILDREN_PER_PAGE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int from = page * CHILDREN_PER_PAGE;
        int to = Math.min(from + CHILDREN_PER_PAGE, focus.getChildrenIds().size());
        List<Node> children = new ArrayList<>();
        for (UUID id : focus.getChildrenIds().subList(from, to)) node(society, id).ifPresent(children::add);
        return new FamilyTreeSnapshot(
                Node.from(focus),
                nodeOrNull(society, focus.getFatherId()),
                nodeOrNull(society, focus.getMotherId()),
                nodeOrNull(society, focus.getSpouseId()),
                List.copyOf(children), page, pages, focus.getChildrenIds().size());
    }

    public void write(FriendlyByteBuf buffer) {
        focus.write(buffer);
        writeNullable(buffer, father);
        writeNullable(buffer, mother);
        writeNullable(buffer, spouse);
        buffer.writeVarInt(children.size());
        children.forEach(node -> node.write(buffer));
        buffer.writeVarInt(childPage);
        buffer.writeVarInt(childPageCount);
        buffer.writeVarInt(totalChildren);
    }

    public static FamilyTreeSnapshot read(FriendlyByteBuf buffer) {
        Node focus = Node.read(buffer);
        Node father = readNullable(buffer);
        Node mother = readNullable(buffer);
        Node spouse = readNullable(buffer);
        int size = buffer.readVarInt();
        List<Node> children = new ArrayList<>(size);
        for (int i = 0; i < size; i++) children.add(Node.read(buffer));
        return new FamilyTreeSnapshot(focus, father, mother, spouse, List.copyOf(children),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    @Nullable
    public Node selectable(int buttonId) {
        if (buttonId == 0) return father;
        if (buttonId == 1) return mother;
        if (buttonId == 2) return spouse;
        int child = buttonId - 3;
        return child >= 0 && child < children.size() ? children.get(child) : null;
    }

    private static java.util.Optional<Node> node(HumanSocietySavedData society, @Nullable UUID id) {
        return id == null ? java.util.Optional.empty() : society.getPerson(id).map(Node::from);
    }

    @Nullable
    private static Node nodeOrNull(HumanSocietySavedData society, @Nullable UUID id) {
        return node(society, id).orElse(null);
    }

    private static void writeNullable(FriendlyByteBuf buffer, @Nullable Node node) {
        buffer.writeBoolean(node != null);
        if (node != null) node.write(buffer);
    }

    @Nullable
    private static Node readNullable(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? Node.read(buffer) : null;
    }

    public record Node(UUID id, String name, String house, String gender,
                       String lifeStage, boolean alive, int childCount) {
        private static Node from(PersonData person) {
            String house = person.getHouseId() == null ? "Sem Casa" : HouseRegistry.get(person.getHouseId())
                    .map(value -> "Casa " + value.surname()).orElse("Casa desconhecida");
            return new Node(person.getPersonId(), person.getDisplayName(), house,
                    person.getGender().name(), person.getLifeStage().name(),
                    person.getStatus().name().equals("ALIVE"), person.getChildrenIds().size());
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(id);
            buffer.writeUtf(name, 128);
            buffer.writeUtf(house, 128);
            buffer.writeUtf(gender, 16);
            buffer.writeUtf(lifeStage, 16);
            buffer.writeBoolean(alive);
            buffer.writeVarInt(childCount);
        }

        private static Node read(FriendlyByteBuf buffer) {
            return new Node(buffer.readUUID(), buffer.readUtf(128), buffer.readUtf(128),
                    buffer.readUtf(16), buffer.readUtf(16), buffer.readBoolean(), buffer.readVarInt());
        }
    }
}
