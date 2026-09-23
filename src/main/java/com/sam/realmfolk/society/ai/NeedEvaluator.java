package com.sam.realmfolk.society.ai;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.society.needs.NeedType;

public final class NeedEvaluator {
    public static final int REST_THRESHOLD = 70;

    private NeedEvaluator() {}

    public static int hungerPriority(ResidentEntity resident) {
        if (resident.getNeeds().hungerEmergency()) return ActionPriority.CRITICAL_HUNGER;
        if (resident.getNeeds().needsFood()) return ActionPriority.BASIC_NEED;
        return 0;
    }

    public static int restPriority(ResidentEntity resident) {
        return resident.getNeeds().get(NeedType.REST) >= REST_THRESHOLD ? ActionPriority.BASIC_NEED : 0;
    }
}
