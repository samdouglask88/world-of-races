package com.sam.realmfolk.profession;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Supplier;

public enum NpcProfession {
    NONE("none", "Sem profissão", ProfessionCategory.SERVICES, "Nenhuma profissão atribuída", false,
            () -> Blocks.AIR, () -> Items.AIR, "Nenhuma", "Nenhuma"),
    FARMER("farmer", "Fazendeiro", ProfessionCategory.PRODUCTION, "Cultiva alimentos e abastece a comunidade.", true,
            () -> Blocks.COMPOSTER, () -> Items.IRON_HOE, "Composteira", "Enxada de ferro"),
    LUMBERJACK("lumberjack", "Lenhador", ProfessionCategory.GATHERING, "Coleta e prepara madeira para construção.", true,
            () -> Blocks.CRAFTING_TABLE, () -> Items.IRON_AXE, "Bancada de trabalho", "Machado de ferro"),
    MINER("miner", "Minerador", ProfessionCategory.GATHERING, "Extrai pedra, carvão e minérios.", true,
            () -> Blocks.BLAST_FURNACE, () -> Items.IRON_PICKAXE, "Alto-forno", "Picareta de ferro"),
    FISHERMAN("fisherman", "Pescador", ProfessionCategory.GATHERING, "Pesca alimentos e recursos aquáticos.", true,
            () -> Blocks.BARREL, () -> Items.FISHING_ROD, "Barril", "Vara de pesca"),
    HUNTER("hunter", "Caçador", ProfessionCategory.GATHERING, "Caça e fornece carne, couro e penas.", true,
            () -> Blocks.FLETCHING_TABLE, () -> Items.BOW, "Bancada de flechas", "Arco"),
    BLACKSMITH("blacksmith", "Ferreiro", ProfessionCategory.PRODUCTION, "Produz armas, ferramentas e armaduras.", true,
            () -> Blocks.ANVIL, () -> ModProfessionItems.BLACKSMITH_HAMMER.get(), "Bigorna", "Martelo de ferreiro"),
    COOK("cook", "Cozinheiro", ProfessionCategory.PRODUCTION, "Prepara refeições usando os ingredientes recebidos.", true,
            () -> Blocks.SMOKER, () -> com.sam.realmfolk.content.ModItems.COOK_LADLE.get(),
            "Defumador", "Concha ou faca de cozinha"),
    MERCHANT("merchant", "Comerciante", ProfessionCategory.SERVICES, "Organiza o estoque e negocia mercadorias.", true,
            () -> Blocks.LECTERN, () -> Items.AIR, "Atril", "Nenhuma"),
    BUILDER("builder", "Construtor", ProfessionCategory.SERVICES, "Transforma materiais em blocos de construção.", true,
            () -> Blocks.STONECUTTER, () -> com.sam.realmfolk.content.ModItems.BUILDER_HAMMER.get(),
            "Cortador de pedra", "Martelo de construtor"),
    GUARD("guard", "Guarda", ProfessionCategory.MILITARY, "Protege habitantes e enfrenta criaturas hostis.", true,
            () -> Blocks.BELL, () -> Items.IRON_SWORD, "Sino", "Espada de ferro");

    public final String id;
    public final String name;
    public final ProfessionCategory category;
    public final String description;
    public final boolean implemented;
    private final Supplier<Block> workstation;
    private final Supplier<Item> tool;
    private final String stationName;
    private final String toolName;

    NpcProfession(String id, String name, ProfessionCategory category, String description,
                  boolean implemented, Supplier<Block> workstation, Supplier<Item> tool,
                  String stationName, String toolName) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.implemented = implemented;
        this.workstation = workstation;
        this.tool = tool;
        this.stationName = stationName;
        this.toolName = toolName;
    }

    public static NpcProfession byId(String id) {
        for (NpcProfession value : values()) if (value.id.equals(id)) return value;
        return NONE;
    }

    public int maxLevel() { return 5; }
    public String station() { return stationName; }
    public String tool() { return toolName; }
    public Block workstationBlock() { return workstation.get(); }
    public Item requiredTool() { return tool.get(); }
    public boolean requiresTool() { return requiredTool() != Items.AIR; }

    public ItemStack icon() {
        return new ItemStack(switch (this) {
            case FARMER -> Items.WHEAT;
            case LUMBERJACK -> Items.OAK_LOG;
            case MINER -> Items.IRON_PICKAXE;
            case FISHERMAN -> Items.FISHING_ROD;
            case HUNTER -> Items.BOW;
            case BLACKSMITH -> ModProfessionItems.BLACKSMITH_HAMMER.get();
            case COOK -> Items.COOKED_BEEF;
            case MERCHANT -> Items.EMERALD;
            case BUILDER -> Items.BRICKS;
            case GUARD -> Items.IRON_SWORD;
            default -> Items.BARRIER;
        });
    }
}
