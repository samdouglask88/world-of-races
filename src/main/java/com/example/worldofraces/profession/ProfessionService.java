package com.example.worldofraces.profession;

import com.example.worldofraces.entity.RaceEntity;
import com.example.worldofraces.profession.blacksmith.BlacksmithRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Comparator;
import java.util.Optional;

public final class ProfessionService {
    public static final int SEARCH_RADIUS = 32;

    private ProfessionService() {}

    public static Result assign(ServerPlayer player, RaceEntity npc, NpcProfession profession) {
        if (!npc.isAlive() || npc.distanceToSqr(player) > 64) return new Result(false, "Habitante distante ou inválido");
        if (!profession.implemented || profession == NpcProfession.NONE) return new Result(false, "Profissão indisponível");
        ServerLevel level = player.serverLevel();
        Optional<BlockPos> station = findStation(level, npc.blockPosition(), npc.getUUID(), profession);
        if (station.isEmpty()) return new Result(false, "Nenhuma estação livre encontrada em um raio de 32 blocos");
        remove(level, npc);
        BlockPos position = station.get();
        if (!WorkstationSavedData.get(level.getServer()).claim(level.dimension().location(), position, npc.getUUID())) {
            return new Result(false, "A estação já pertence a outro habitante");
        }
        npc.getProfessionData().assign(profession, position, level.dimension().location());
        return new Result(true, hasRequiredTool(npc) ? profession.name + " atribuído" : "Aguardando " + profession.tool());
    }

    public static void remove(ServerLevel level, RaceEntity npc) {
        ProfessionData data = npc.getProfessionData();
        if (data.workstation() != null && data.dimension() != null) {
            WorkstationSavedData.get(level.getServer()).release(data.dimension(), data.workstation(), npc.getUUID());
        }
        data.remove();
    }

    public static boolean validateOrFindStation(ServerLevel level, RaceEntity npc) {
        ProfessionData data = npc.getProfessionData();
        NpcProfession profession = data.profession();
        if (!profession.implemented || profession == NpcProfession.NONE) return false;
        if (data.workstation() != null && data.dimension() != null
                && data.dimension().equals(level.dimension().location())
                && level.hasChunkAt(data.workstation()) && isStation(level, data.workstation(), profession)
                && WorkstationSavedData.get(level.getServer()).claim(data.dimension(), data.workstation(), npc.getUUID())) {
            return true;
        }
        if (data.workstation() != null && data.dimension() != null) {
            WorkstationSavedData.get(level.getServer()).release(data.dimension(), data.workstation(), npc.getUUID());
        }
        data.invalidateStation();
        Optional<BlockPos> replacement = findStation(level, npc.blockPosition(), npc.getUUID(), profession);
        if (replacement.isEmpty()) return false;
        BlockPos position = replacement.get();
        if (!WorkstationSavedData.get(level.getServer()).claim(level.dimension().location(), position, npc.getUUID())) return false;
        data.assign(profession, position, level.dimension().location());
        return true;
    }

    private static Optional<BlockPos> findStation(ServerLevel level, BlockPos center, java.util.UUID npc,
                                                  NpcProfession profession) {
        return BlockPos.betweenClosedStream(center.offset(-SEARCH_RADIUS, -8, -SEARCH_RADIUS),
                        center.offset(SEARCH_RADIUS, 8, SEARCH_RADIUS))
                .filter(level::hasChunkAt)
                .filter(position -> isStation(level, position, profession))
                .filter(position -> WorkstationSavedData.get(level.getServer())
                        .available(level.dimension().location(), position, npc))
                .min(Comparator.comparingDouble(position -> position.distSqr(center)))
                .map(BlockPos::immutable);
    }

    private static boolean isStation(ServerLevel level, BlockPos position, NpcProfession profession) {
        var block = level.getBlockState(position).getBlock();
        if (profession == NpcProfession.BLACKSMITH) {
            return block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL;
        }
        return block == profession.workstationBlock();
    }

    public static boolean hasHammer(RaceEntity npc) { return findTool(npc, ModProfessionItems.BLACKSMITH_HAMMER.get()) != -1; }

    public static boolean hasRequiredTool(RaceEntity npc) {
        NpcProfession profession = npc.getProfessionData().profession();
        return !profession.requiresTool() || findTool(npc, profession.requiredTool()) != -1;
    }

    private static int findTool(RaceEntity npc, Item item) {
        if (item == Items.AIR) return -2;
        if (npc.getMainHandItem().is(item)) return -2;
        Container inventory = npc.getNpcInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(item)) return i;
        return -1;
    }

    public static String status(RaceEntity npc, NpcProfession selected) {
        ProfessionData data = npc.getProfessionData();
        if (!selected.implemented) return "Em breve";
        if (data.profession() != selected) return "Pronto para atribuição";
        if (data.workstation() == null) return "Procurando " + selected.station().toLowerCase() + "...";
        if (!hasRequiredTool(npc)) return "Aguardando " + selected.tool();
        if (data.active()) return selected == NpcProfession.GUARD ? "Patrulhando" : "Trabalhando";
        return "Aguardando horário ou materiais";
    }

    public static ProductionResult produce(RaceEntity npc) {
        NpcProfession profession = npc.getProfessionData().profession();
        if (!hasRequiredTool(npc)) return ProductionResult.NO_TOOL;
        return switch (profession) {
            case FARMER -> craft(npc, Items.WHEAT_SEEDS, 1, Items.WHEAT, 2, 10);
            case LUMBERJACK -> gather(npc, Items.OAK_LOG, 2, 12);
            case MINER -> mine(npc);
            case FISHERMAN -> gather(npc, npc.getRandom().nextInt(4) == 0 ? Items.SALMON : Items.COD, 1, 12);
            case HUNTER -> craft(npc, Items.ARROW, 1,
                    npc.getRandom().nextBoolean() ? Items.BEEF : Items.LEATHER, 1, 14);
            case BLACKSMITH -> forge(npc);
            case COOK -> cook(npc);
            case MERCHANT -> stockStore(npc);
            case BUILDER -> craft(npc, Items.COBBLESTONE, 4, Items.STONE_BRICKS, 4, 16);
            case GUARD -> gainExperience(npc, 8);
            default -> ProductionResult.NO_RECIPE;
        };
    }

    private static ProductionResult mine(RaceEntity npc) {
        int roll = npc.getRandom().nextInt(10);
        Item output = npc.getProfessionData().level() >= 3 && roll == 0 ? Items.RAW_IRON
                : roll < 3 ? Items.COAL : Items.COBBLESTONE;
        return gather(npc, output, 1, output == Items.RAW_IRON ? 24 : 12);
    }

    private static ProductionResult cook(RaceEntity npc) {
        if (count(npc.getNpcInventory(), Items.BEEF) > 0)
            return craft(npc, Items.BEEF, 1, Items.COOKED_BEEF, 1, 12);
        if (count(npc.getNpcInventory(), Items.CHICKEN) > 0)
            return craft(npc, Items.CHICKEN, 1, Items.COOKED_CHICKEN, 1, 10);
        if (count(npc.getNpcInventory(), Items.POTATO) > 0)
            return craft(npc, Items.POTATO, 1, Items.BAKED_POTATO, 1, 8);
        return ProductionResult.NO_RECIPE;
    }

    private static ProductionResult stockStore(RaceEntity npc) {
        Container source = npc.getNpcInventory();
        Container stock = npc.getTradeInventory();
        for (int i = 0; i < source.getContainerSize(); i++) {
            ItemStack stack = source.getItem(i);
            if (stack.isEmpty() || stack.is(Items.EMERALD) || !canFit(stock, stack.copyWithCount(1))) continue;
            ItemStack moved = stack.copyWithCount(1);
            stack.shrink(1);
            if (stack.isEmpty()) source.setItem(i, ItemStack.EMPTY);
            add(stock, moved);
            source.setChanged();
            stock.setChanged();
            return gainExperience(npc, 10);
        }
        return ProductionResult.NO_RECIPE;
    }

    private static ProductionResult forge(RaceEntity npc) {
        ProfessionData data = npc.getProfessionData();
        Container inventory = npc.getNpcInventory();
        for (BlacksmithRecipe recipe : BlacksmithRecipe.values()) {
            ItemStack output = new ItemStack(recipe.result);
            if (recipe.level > data.level() || !hasIngredients(inventory, recipe) || !canFit(inventory, output)) continue;
            recipe.ingredients.forEach(ingredient -> remove(inventory, ingredient.item(), ingredient.count()));
            add(inventory, output);
            damageTool(npc);
            inventory.setChanged();
            return gainExperience(npc, recipe.xp);
        }
        return ProductionResult.NO_RECIPE;
    }

    private static ProductionResult craft(RaceEntity npc, Item input, int inputCount,
                                          Item output, int outputCount, int experience) {
        Container inventory = npc.getNpcInventory();
        ItemStack result = new ItemStack(output, outputCount);
        if (count(inventory, input) < inputCount || !canFit(inventory, result)) return ProductionResult.NO_RECIPE;
        remove(inventory, input, inputCount);
        add(inventory, result);
        damageTool(npc);
        inventory.setChanged();
        return gainExperience(npc, experience);
    }

    private static ProductionResult gather(RaceEntity npc, Item output, int count, int experience) {
        Container inventory = npc.getNpcInventory();
        ItemStack result = new ItemStack(output, count);
        if (!canFit(inventory, result)) return ProductionResult.NO_RECIPE;
        add(inventory, result);
        damageTool(npc);
        inventory.setChanged();
        return gainExperience(npc, experience);
    }

    private static ProductionResult gainExperience(RaceEntity npc, int amount) {
        return npc.getProfessionData().addExperience(amount) ? ProductionResult.LEVEL_UP : ProductionResult.SUCCESS;
    }

    private static void damageTool(RaceEntity npc) {
        NpcProfession profession = npc.getProfessionData().profession();
        if (!profession.requiresTool()) return;
        int slot = findTool(npc, profession.requiredTool());
        if (slot == -1) return;
        ItemStack tool = slot == -2 ? npc.getMainHandItem() : npc.getNpcInventory().getItem(slot);
        if (!tool.isDamageableItem()) return;
        tool.setDamageValue(tool.getDamageValue() + 1);
        if (tool.getDamageValue() >= tool.getMaxDamage()) {
            if (slot == -2) npc.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            else npc.getNpcInventory().setItem(slot, ItemStack.EMPTY);
        }
    }

    private static boolean hasIngredients(Container container, BlacksmithRecipe recipe) {
        for (var ingredient : recipe.ingredients)
            if (count(container, ingredient.item()) < ingredient.count()) return false;
        return true;
    }

    private static int count(Container container, Item item) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++)
            if (container.getItem(i).is(item)) count += container.getItem(i).getCount();
        return count;
    }

    private static void remove(Container container, Item item, int amount) {
        for (int i = 0; i < container.getContainerSize() && amount > 0; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.is(item)) continue;
            int take = Math.min(amount, stack.getCount());
            stack.shrink(take);
            amount -= take;
            if (stack.isEmpty()) container.setItem(i, ItemStack.EMPTY);
        }
    }

    private static boolean canFit(Container container, ItemStack item) {
        int remaining = item.getCount();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) remaining -= item.getMaxStackSize();
            else if (ItemStack.isSameItemSameTags(stack, item)) remaining -= stack.getMaxStackSize() - stack.getCount();
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static void add(Container container, ItemStack item) {
        ItemStack remaining = item.copy();
        for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, remaining)) {
                int moved = Math.min(remaining.getCount(), stack.getMaxStackSize() - stack.getCount());
                stack.grow(moved);
                remaining.shrink(moved);
            }
        }
        for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
            if (!container.getItem(i).isEmpty()) continue;
            int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            container.setItem(i, remaining.copyWithCount(moved));
            remaining.shrink(moved);
        }
    }

    public record Result(boolean success, String message) {}
    public enum ProductionResult { SUCCESS, LEVEL_UP, NO_TOOL, NO_RECIPE }
}
