package com.sam.realmfolk.content.block;

import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.construction.ConstructionManager;
import com.sam.realmfolk.society.housing.HousingManager;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SettlementMarkerBlock extends Block {
    private final MarkerType markerType;

    public SettlementMarkerBlock(Properties properties, MarkerType markerType) {
        super(properties);
        this.markerType = markerType;
    }

    @Override
    public InteractionResult use(BlockState state, Level rawLevel, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(rawLevel instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Settlement settlement = SettlementSavedData.get(level.getServer()).at(level.dimension(), position).orElse(null);
        if (settlement == null) {
            player.sendSystemMessage(Component.literal("Este marcador precisa ficar dentro de um povoado."));
            return InteractionResult.CONSUME;
        }
        String message = apply(level, settlement, position);
        player.sendSystemMessage(Component.literal(message));
        return InteractionResult.CONSUME;
    }

    private String apply(ServerLevel level, Settlement settlement, BlockPos position) {
        boolean changed = false;
        String message;
        switch (markerType) {
            case RESIDENCE -> {
                changed = HousingManager.refresh(level, settlement);
                changed |= HousingManager.assignHouseholds(settlement, HumanSocietySavedData.get(level));
                message = "Residencias verificadas: " + settlement.residences().size() + ".";
            }
            case STORAGE -> {
                BlockPos barrel = adjacentBarrel(level, position);
                changed = barrel != null && settlement.addStorage(barrel);
                message = barrel == null ? "Coloque um barril ao lado do marco." : "Armazem registrado em " + barrel.toShortString() + ".";
            }
            case TREASURY -> {
                BlockPos barrel = adjacentBarrel(level, position);
                if (barrel != null) { settlement.setTreasuryPosition(barrel); changed = true; }
                message = barrel == null ? "Coloque o barril do tesouro ao lado do marco." : "Tesouro definido em " + barrel.toShortString() + ".";
            }
            case PATROL -> {
                changed = settlement.addPatrolPosition(position);
                message = "Ponto de patrulha registrado.";
            }
            case WORK -> {
                changed = settlement.addWorkPosition(position);
                message = "Area de trabalho registrada.";
            }
            case PROJECT -> {
                ConstructionManager.PlanResult result = ConstructionManager.planProjectAt(level, settlement,
                        ConstructionManager.SMALL_STORAGE_HUT, position.above());
                changed = result == ConstructionManager.PlanResult.SUCCESS;
                message = changed ? "Projeto de deposito criado." : "Projeto nao criado: " + result.name() + ".";
            }
            case CRADLE -> {
                changed = HousingManager.refresh(level, settlement);
                message = "Berco registrado. Residencias proximas podem receber mais duas criancas.";
            }
            default -> message = "Marcador sem funcao.";
        }
        if (changed) SettlementSavedData.get(level.getServer()).changed();
        return message;
    }

    private static BlockPos adjacentBarrel(ServerLevel level, BlockPos center) {
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            if (level.hasChunkAt(position) && level.getBlockState(position).is(Blocks.BARREL)) return position.immutable();
        }
        return null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moving) {
        if (state.getBlock() != replacement.getBlock() && level instanceof ServerLevel serverLevel) {
            SettlementSavedData.get(serverLevel.getServer()).at(serverLevel.dimension(), position).ifPresent(settlement -> {
                if (settlement.removeMarkerPosition(position)) SettlementSavedData.get(serverLevel.getServer()).changed();
            });
        }
        super.onRemove(state, level, position, replacement, moving);
    }
}
