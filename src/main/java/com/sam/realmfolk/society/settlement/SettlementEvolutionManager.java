package com.sam.realmfolk.society.settlement;

import com.mojang.logging.LogUtils;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.construction.ConstructionManager;
import com.sam.realmfolk.society.construction.ConstructionStage;
import com.sam.realmfolk.society.economy.Treasury;
import com.sam.realmfolk.society.storage.SettlementStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;

public final class SettlementEvolutionManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private SettlementEvolutionManager() {}

    public static boolean evaluate(ServerLevel level, Settlement settlement) {
        if (settlement.level() != SettlementLevel.CAMP || settlement.memberIds().size() < 5) return false;
        SettlementStorage storage = new SettlementStorage(level, settlement);
        int foodRequired = settlement.memberIds().size() * 6;
        if (storage.countFood() < foodRequired || new Treasury(level, settlement).balance() < 16) return false;
        boolean functionalStorage = settlement.storagePositions().stream().anyMatch(position -> storage.container(position) != null);
        if (!functionalStorage) return false;
        boolean hutComplete = settlement.projects().stream().anyMatch(project -> project.blueprintId().equals(ConstructionManager.SMALL_STORAGE_HUT)
                && project.stage() == ConstructionStage.COMPLETED);
        if (!hutComplete || activeProfessions(level, settlement) < 2) return false;
        settlement.setLevel(SettlementLevel.VILLAGE);
        SettlementSavedData.get(level.getServer()).changed();
        LOGGER.info("Settlement {} ({}) evolved from CAMP to VILLAGE", settlement.name(), settlement.id());
        level.players().stream().filter(player -> settlement.contains(player.blockPosition())).forEach(player ->
                player.sendSystemMessage(Component.literal(settlement.name() + " evoluiu de acampamento para aldeia.")));
        return true;
    }

    private static int activeProfessions(ServerLevel level, Settlement settlement) {
        Set<NpcProfession> professions = new HashSet<>();
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        for (java.util.UUID personId : settlement.memberIds()) {
            PersonData person = society.getPerson(personId).orElse(null);
            if (person == null || person.getEntityId() == null) continue;
            Entity entity = level.getEntity(person.getEntityId());
            if (entity instanceof ResidentEntity resident && resident.isAlive() && resident.isWorkingAge()
                    && resident.getProfessionData().profession() != NpcProfession.NONE) professions.add(resident.getProfessionData().profession());
        }
        return professions.size();
    }
}
