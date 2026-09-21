package com.example.worldofraces.dialogue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DialogueRegistry extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    public static final DialogueRegistry INSTANCE = new DialogueRegistry();
    private final Map<String, DialogueNode> nodes = new LinkedHashMap<>();

    private DialogueRegistry() {
        super(GSON, "dialogue");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<String, DialogueNode> loaded = new LinkedHashMap<>();
        resources.forEach((location, json) -> {
            JsonObject root = GsonHelper.convertToJsonObject(json, location.toString());
            for (JsonElement element : GsonHelper.getAsJsonArray(root, "dialogues")) {
                DialogueNode node = parseNode(element.getAsJsonObject());
                loaded.put(node.id(), node);
            }
        });
        nodes.clear();
        nodes.putAll(loaded);
    }

    public Optional<DialogueNode> get(String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    private static DialogueNode parseNode(JsonObject json) {
        String id = GsonHelper.getAsString(json, "id");
        String text = GsonHelper.getAsString(json, "text");
        List<DialogueResponse> responses = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "responses")) {
            JsonObject response = element.getAsJsonObject();
            responses.add(new DialogueResponse(
                    GsonHelper.getAsString(response, "text"),
                    GsonHelper.getAsString(response, "nextDialogue", "greeting"),
                    GsonHelper.getAsInt(response, "requiresAffinity", -100),
                    GsonHelper.getAsInt(response, "affinityChange", 0),
                    GsonHelper.getAsBoolean(response, "endsConversation", false)
            ));
        }
        return new DialogueNode(id, text, List.copyOf(responses));
    }

    public record DialogueNode(String id, String text, List<DialogueResponse> responses) {}

    public record DialogueResponse(String text, String nextDialogue, int requiresAffinity,
                                   int affinityChange, boolean endsConversation) {}
}
