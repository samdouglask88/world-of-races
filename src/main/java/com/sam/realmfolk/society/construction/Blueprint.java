package com.sam.realmfolk.society.construction;

import com.sam.realmfolk.society.settlement.SettlementLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record Blueprint(ResourceLocation id, String name, BlockPos size, List<BlockEntry> blocks,
                        Map<Item, Integer> materials, BlockPos entrance, Set<String> tags,
                        SettlementLevel minimumLevel) {
    public record BlockEntry(BlockPos relativePosition, BlockState state) {}
}
