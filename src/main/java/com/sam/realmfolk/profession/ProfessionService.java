package com.sam.realmfolk.profession;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.blacksmith.BlacksmithRecipe;
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

    public static Result assign(ServerPlayer player, ResidentEntity npc, NpcProfession profession) {
        if (!npc.isWorkingAge()) return new Result(false, "Somente habitantes adultos podem exercer profissao");
        if (!npc.isAlive() || npc.distanceToSqr(player) > 64) return new Result(false, "Habitante distante ou inválido");
        if (!profession.implemented || profession == NpcProfession.NONE) return new Result(false, "Profissão indisponível");
        ServerLevel level = player.serverLevel();
        Optional<BlockPos> station = findStation(level, npc.blockPosition(), npc.getUUID(), profession);
        if (station.isEmpty()) return new Result(false, "Nenhuma estação livre encontrada em um raio de 32 blocos");
        remove(level, npc);
        BlockPos position = station.get();
        if (!WorkstationSavedData.get(level.getServer()).claimActive(level, position, npc.getUUID())) {
            return new Result(false, "A estação já pertence a outro habitante");
        }
        npc.getProfessionData().assign(profession, position, level.dimension().location());
        return new Result(true, hasRequiredTool(npc) ? profession.name + " atribuído" : "Aguardando " + profession.tool());
    }

    public static void remove(ServerLevel level, ResidentEntity npc) {
        ProfessionData data = npc.getProfessionData();
        if (data.workstation() != null && data.dimension() != null) {
            WorkstationSavedData.get(level.getServer()).release(data.dimension(), data.workstation(), npc.getUUID());
        }
        data.remove();
    }

    public static boolean validateOrFindStation(ServerLevel level, ResidentEntity npc) {
        ProfessionData data = npc.getProfessionData();
        NpcProfession profession = data.profession();
        if (!profession.implemented || profession == NpcProfession.NONE) return false;
        if (data.workstation() != null && data.dimension() != null
                && data.dimension().equals(level.dimension().location())
                && level.hasChunkAt(data.workstation()) && isStation(level, data.workstation(), profession)
                && WorkstationSavedData.get(level.getServer()).claimActive(level, data.workstation(), npc.getUUID())) {
            return true;
        }
        if (data.workstation() != null && data.dimension() != null) {
            WorkstationSavedData.get(level.getServer()).release(data.dimension(), data.workstation(), npc.getUUID());
        }
        data.invalidateStation();
        Optional<BlockPos> replacement = findStation(level, npc.blockPosition(), npc.getUUID(), profession);
        if (replacement.isEmpty()) return false;
        BlockPos position = replacement.get();
        if (!WorkstationSavedData.get(level.getServer()).claimActive(level, position, npc.getUUID())) return false;
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
                        .availableActive(level, position, npc))
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

    public static boolean hasHammer(ResidentEntity npc) { return findTool(npc, ModProfessionItems.BLACKSMITH_HAMMER.get()) != -1; }

    public static boolean hasRequiredTool(ResidentEntity npc) {
        NpcProfession profession = npc.getProfessionData().profession();
        if (profession == NpcProfession.COOK) {
            return findTool(npc, com.sam.realmfolk.content.ModItems.COOK_LADLE.get()) != -1
                    || findTool(npc, com.sam.realmfolk.content.ModItems.KITCHEN_KNIFE.get()) != -1;
        }
        return !profession.requiresTool() || findTool(npc, profession.requiredTool()) != -1;
    }

    public static ItemStack requiredToolStack(ResidentEntity npc) {
        NpcProfession profession = npc.getProfessionData().profession();
        Item required = profession.requiredTool();
        int slot = findTool(npc, required);
        if (slot == -1 && profession == NpcProfession.COOK) {
            required = com.sam.realmfolk.content.ModItems.KITCHEN_KNIFE.get();
            slot = findTool(npc, required);
        }
        if (slot == -2) return npc.getMainHandItem();
        return slot >= 0 ? npc.getNpcInventory().getItem(slot) : ItemStack.EMPTY;
    }

    private static int findTool(ResidentEntity npc, Item item) {
        if (item == Items.AIR) return -2;
        if (npc.getMainHandItem().is(item)) return -2;
        Container inventory = npc.getNpcInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(item)) return i;
        return -1;
    }

    public static String status(ResidentEntity npc, NpcProfession selected) {
        ProfessionData data = npc.getProfessionData();
        if (!selected.implemented) return "Em breve";
        if (data.profession() != selected) return "Pronto para atribuição";
        if (data.workstation() == null) return "Procurando " + selected.station().toLowerCase() + "...";
        if (!hasRequiredTool(npc)) return "Aguardando " + selected.tool();
        if (data.active()) return selected == NpcProfession.GUARD ? "Patrulhando" : "Trabalhando";
        return "Aguardando horário ou materiais";
    }

    public static ProductionResult produce(ResidentEntity npc) {
        return produce(npc, Items.AIR);
    }

    public static ProductionResult produce(ResidentEntity npc, Item desiredResult) {
        NpcProfession profession = npc.getProfessionData().profession();
        if (!hasRequiredTool(npc)) return ProductionResult.NO_TOOL;
        return switch (profession) {
            case FARMER -> ProductionResult.NO_RECIPE; // Physical farming owns crop production.
            case LUMBERJACK -> ProductionResult.NO_RECIPE; // Physical forestry owns lumber production.
            case MINER -> ProductionResult.NO_RECIPE; // Physical mining owns mineral production.
            case FISHERMAN -> ProductionResult.NO_RECIPE; // Physical fishing owns aquatic production.
            case HUNTER -> ProductionResult.NO_RECIPE; // Physical hunting owns animal drops.
            case BLACKSMITH -> forge(npc, desiredResult);
            case COOK -> ProductionResult.NO_RECIPE; // Physical kitchen orders own food production.
            case MERCHANT -> ProductionResult.NO_RECIPE; // Physical restocking owns merchant stock movement.
            case BUILDER -> ProductionResult.NO_RECIPE; // Construction projects own block placement.
            case GUARD -> ProductionResult.NO_RECIPE; // Patrol and combat own guard experience.
            default -> ProductionResult.NO_RECIPE;
        };
    }

    private static ProductionResult forge(ResidentEntity npc, Item desiredResult) {
        ProfessionData data = npc.getProfessionData();
        Container inventory = npc.getNpcInventory();
        for (BlacksmithRecipe recipe : BlacksmithRecipe.values()) {
            if (desiredResult != Items.AIR && recipe.result != desiredResult) continue;
            ItemStack output = new ItemStack(recipe.result);
            if (recipe.level > data.level() || !hasIngredients(inventory, recipe) || !canFit(inventory, output)) continue;
            recipe.ingredients.forEach(ingredient -> remove(inventory, ingredient.item(), ingredient.count()));
            add(inventory, output);
            damageRequiredTool(npc);
            inventory.setChanged();
            return gainExperience(npc, recipe.xp);
        }
        return ProductionResult.NO_RECIPE;
    }

    private static ProductionResult gainExperience(ResidentEntity npc, int amount) {
        return npc.getProfessionData().addExperience(amount) ? ProductionResult.LEVEL_UP : ProductionResult.SUCCESS;
    }

    public static void damageRequiredTool(ResidentEntity npc) {
        NpcProfession profession = npc.getProfessionData().profession();
        if (!profession.requiresTool()) return;
        Item toolItem = profession.requiredTool();
        int slot = findTool(npc, toolItem);
        if (slot == -1 && profession == NpcProfession.COOK) {
            toolItem = com.sam.realmfolk.content.ModItems.KITCHEN_KNIFE.get();
            slot = findTool(npc, toolItem);
        }
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
