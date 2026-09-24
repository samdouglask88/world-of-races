package com.sam.realmfolk.society;

import com.sam.realmfolk.society.construction.ConstructionManager;
import com.sam.realmfolk.society.economy.DailyEconomyManager;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementManager;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import com.sam.realmfolk.society.reproduction.ReproductionManager;
import com.sam.realmfolk.profession.forestry.ForestryManager;
import com.sam.realmfolk.profession.farming.FarmingManager;
import com.sam.realmfolk.profession.mining.MiningManager;
import com.sam.realmfolk.profession.cooking.CookingManager;
import com.sam.realmfolk.profession.fishing.FishingManager;
import com.sam.realmfolk.profession.hunting.HuntingManager;
import com.sam.realmfolk.profession.blacksmith.BlacksmithManager;
import com.sam.realmfolk.profession.merchant.MerchantManager;
import com.sam.realmfolk.profession.guard.GuardManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public final class SettlementEvents {
    private static final int UPDATE_INTERVAL = 200;

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.getGameTime() % UPDATE_INTERVAL != 0L) return;
        SettlementSavedData data = SettlementSavedData.get(level.getServer());
        for (Settlement settlement : new ArrayList<>(data.all())) {
            if (!settlement.dimension().equals(level.dimension())) continue;
            if (settlement.taskBoard().maintain(level.getGameTime())) data.changed();
            if (settlement.maintainStorageReservations(level.getGameTime())) data.changed();
            if (!level.hasChunkAt(settlement.center())) continue;
            boolean changed = false;
            for (var position : List.copyOf(settlement.storagePositions())) {
                if (level.hasChunkAt(position)
                        && !level.getBlockState(position).is(net.minecraft.world.level.block.Blocks.BARREL)) {
                    changed |= settlement.removeStorage(position);
                }
            }
            if (settlement.leaderId() != null) {
                PersonData leader = HumanSocietySavedData.get(level).getPerson(settlement.leaderId()).orElse(null);
                if (leader == null || leader.getStatus() != PersonStatus.ALIVE || !settlement.memberIds().contains(settlement.leaderId())) {
                    SettlementManager.selectLeader(level, settlement);
                    changed = true;
                }
            } else {
                SettlementManager.selectLeader(level, settlement);
                changed = true;
            }
            ConstructionManager.ensureBuildOrder(level, settlement);
            if (level.getGameTime() % 600L == 0L && ForestryManager.ensureWorkOrder(level, settlement)) {
                changed = true;
            }
            if (level.getGameTime() % 600L == 0L && FarmingManager.ensureWorkOrder(level, settlement)) {
                changed = true;
            }
            if (level.getGameTime() % 600L == 0L && MiningManager.ensureWorkOrder(level, settlement)) {
                changed = true;
            }
            if (level.getGameTime() % 600L == 0L && CookingManager.ensureWorkOrder(level, settlement)) {
                changed = true;
            }
            if (level.getGameTime() % 600L == 0L && FishingManager.ensureWorkOrder(level, settlement)) {
                changed = true;
            }
            if (level.getGameTime() % 600L == 0L && HuntingManager.ensureWorkOrder(level, settlement)) {
                changed = true;
            }
            if (level.getGameTime() % 600L == 0L && BlacksmithManager.ensureWorkOrder(level, settlement)) {
                changed = true;
            }
            if (level.getGameTime() % 600L == 0L && MerchantManager.ensureWorkOrder(level, settlement)) {
                changed = true;
            }
            if (level.getGameTime() % 600L == 0L && GuardManager.ensureWorkOrder(level, settlement)) {
                changed = true;
            }
            if (ReproductionManager.tick(level, settlement)) changed = true;
            DailyEconomyManager.run(level, settlement);
            if (changed) data.changed();
        }
    }
}
