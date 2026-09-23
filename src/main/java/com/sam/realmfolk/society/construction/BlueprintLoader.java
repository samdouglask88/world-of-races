package com.sam.realmfolk.society.construction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sam.realmfolk.society.settlement.SettlementLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BlueprintLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    public static final BlueprintLoader INSTANCE = new BlueprintLoader();
    private final Map<ResourceLocation, Blueprint> blueprints = new LinkedHashMap<>();

    private BlueprintLoader() { super(GSON, "blueprints"); }

    public Optional<Blueprint> get(ResourceLocation id) { return Optional.ofNullable(blueprints.get(id)); }
    public Map<ResourceLocation, Blueprint> all() { return Collections.unmodifiableMap(blueprints); }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Blueprint> loaded = new LinkedHashMap<>();
        resources.forEach((fileId, element) -> {
            Blueprint blueprint = parse(GsonHelper.convertToJsonObject(element, fileId.toString()), fileId);
            loaded.put(blueprint.id(), blueprint);
        });
        blueprints.clear();
        blueprints.putAll(loaded);
    }

    private static Blueprint parse(JsonObject json, ResourceLocation fallbackId) {
        ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, "id", fallbackId.toString()));
        if (id == null) id = fallbackId;
        BlockPos size = vector(GsonHelper.getAsJsonArray(json, "size"));
        BlockPos entrance = vector(GsonHelper.getAsJsonArray(json, "entrance"));
        List<Blueprint.BlockEntry> blocks = new ArrayList<>();
        Map<Item, Integer> derivedMaterials = new LinkedHashMap<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "blocks")) {
            JsonObject entry = element.getAsJsonObject();
            ResourceLocation blockId = ResourceLocation.tryParse(GsonHelper.getAsString(entry, "block"));
            Block block = blockId == null ? null : ForgeRegistries.BLOCKS.getValue(blockId);
            if (block == null || block.asItem() == Items.AIR) continue;
            blocks.add(new Blueprint.BlockEntry(vector(GsonHelper.getAsJsonArray(entry, "pos")), block.defaultBlockState()));
            derivedMaterials.merge(block.asItem(), 1, Integer::sum);
        }
        Set<String> tags = new LinkedHashSet<>();
        JsonArray tagsJson = GsonHelper.getAsJsonArray(json, "tags", new com.google.gson.JsonArray());
        for (JsonElement tag : tagsJson) tags.add(tag.getAsString());
        SettlementLevel minimum;
        try { minimum = SettlementLevel.valueOf(GsonHelper.getAsString(json, "minimum_level", "CAMP").toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { minimum = SettlementLevel.CAMP; }
        return new Blueprint(id, GsonHelper.getAsString(json, "name", id.getPath()), size,
                List.copyOf(blocks), Map.copyOf(derivedMaterials), entrance, Set.copyOf(tags), minimum);
    }

    private static BlockPos vector(JsonArray array) {
        if (array.size() != 3) throw new IllegalArgumentException("Vetor de blueprint deve conter três números");
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }
}
