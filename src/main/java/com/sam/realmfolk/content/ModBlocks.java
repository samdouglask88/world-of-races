package com.sam.realmfolk.content;

import com.sam.realmfolk.Realmfolk;
import com.sam.realmfolk.content.block.MarkerType;
import com.sam.realmfolk.content.block.SettlementMarkerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private static BlockBehaviour.Properties markerProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).noOcclusion();
    }

    public static final RegistryObject<Block> RESIDENCE_MARKER = marker("residence_marker", MarkerType.RESIDENCE);
    public static final RegistryObject<Block> STORAGE_MARKER = marker("storage_marker", MarkerType.STORAGE);
    public static final RegistryObject<Block> TREASURY_MARKER = marker("treasury_marker", MarkerType.TREASURY);
    public static final RegistryObject<Block> PATROL_MARKER = marker("patrol_marker", MarkerType.PATROL);
    public static final RegistryObject<Block> WORK_MARKER = marker("work_marker", MarkerType.WORK);
    public static final RegistryObject<Block> PROJECT_BOARD = marker("project_board", MarkerType.PROJECT);
    public static final RegistryObject<Block> CRADLE = marker("cradle", MarkerType.CRADLE);

    private static RegistryObject<Block> marker(String id, MarkerType type) {
        return Realmfolk.BLOCKS.register(id, () -> new SettlementMarkerBlock(markerProperties(), type));
    }

    private ModBlocks() {}
    public static void bootstrap() {}
}
