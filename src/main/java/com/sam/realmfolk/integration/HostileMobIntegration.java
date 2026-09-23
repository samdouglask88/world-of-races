package com.sam.realmfolk.integration;

import com.sam.realmfolk.entity.NpcBehaviorMode;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Makes residents part of vanilla combat ecology. Hostiles periodically acquire residents as real
 * targets, civilians can flee through their brain, and nearby guards defend settlement members.
 */
public final class HostileMobIntegration {
    private static final int TARGET_SCAN_INTERVAL = 40;
    private static final double MIN_TARGET_RANGE = 16.0D;
    private static final double MAX_TARGET_RANGE = 32.0D;
    private static final double GUARD_RESPONSE_RANGE = 28.0D;

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob hostile) || !(hostile.level() instanceof ServerLevel level)
                || !isHostileToResidents(hostile)) return;

        LivingEntity current = hostile.getTarget();
        if (current instanceof ResidentEntity threatened && validTarget(hostile, threatened)) {
            if ((hostile.tickCount + hostile.getId()) % 10 == 0) alertGuards(level, threatened, hostile);
            return;
        }
        if (current != null && current.isAlive()) return; // Vanilla targets keep their priority.
        if (Math.floorMod(hostile.tickCount + hostile.getId(), TARGET_SCAN_INTERVAL) != 0) return;
        tryAcquireResidentTarget(level, hostile);
    }

    @SubscribeEvent
    public void onResidentHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ResidentEntity resident)
                || !(resident.level() instanceof ServerLevel level)) return;
        Entity directCause = event.getSource().getEntity();
        if (!(directCause instanceof Mob attacker) || !isHostileToResidents(attacker)) return;
        if (attacker.getTarget() == null) attacker.setTarget(resident);
        alertGuards(level, resident, attacker);
    }

    /** Exposed for deterministic game tests and for future raid integrations. */
    public static boolean tryAcquireResidentTarget(ServerLevel level, Mob hostile) {
        if (!isHostileToResidents(hostile) || (hostile.getTarget() != null && hostile.getTarget().isAlive())) return false;
        double followRange = hostile.getAttributeValue(Attributes.FOLLOW_RANGE);
        double range = Math.max(MIN_TARGET_RANGE, Math.min(MAX_TARGET_RANGE, followRange));
        ResidentEntity nearest = null;
        double nearestDistance = range * range;
        for (ResidentEntity resident : level.getEntitiesOfClass(ResidentEntity.class,
                hostile.getBoundingBox().inflate(range), resident -> validTarget(hostile, resident))) {
            double distance = hostile.distanceToSqr(resident);
            if (distance < nearestDistance && hostile.getSensing().hasLineOfSight(resident)) {
                nearest = resident;
                nearestDistance = distance;
            }
        }
        if (nearest == null) return false;
        hostile.setTarget(nearest);
        alertGuards(level, nearest, hostile);
        return true;
    }

    /** Calls every available guard from the same settlement without overriding an active fight. */
    public static int alertGuards(ServerLevel level, ResidentEntity threatened, Mob attacker) {
        int alerted = 0;
        for (ResidentEntity guard : level.getEntitiesOfClass(ResidentEntity.class,
                threatened.getBoundingBox().inflate(GUARD_RESPONSE_RANGE), ResidentEntity::isAlive)) {
            if (guard == threatened || guard.isBaby() || !guard.isWorkingAge()
                    || guard.getProfessionData().profession() != NpcProfession.GUARD
                    || guard.getBehaviorMode() != NpcBehaviorMode.WANDER
                    || !Objects.equals(guard.getSettlementId(), threatened.getSettlementId())
                    || guard.isAlliedTo(attacker)) continue;
            LivingEntity target = guard.getTarget();
            if (target != null && target.isAlive() && target != attacker) continue;
            guard.setTarget(attacker);
            guard.getNavigation().stop();
            alerted++;
        }
        return alerted;
    }

    private static boolean validTarget(Mob hostile, @Nullable ResidentEntity resident) {
        return resident != null && resident.isAlive() && !resident.isInvulnerable()
                && !hostile.isAlliedTo(resident) && hostile.canAttack(resident);
    }

    public static boolean isHostileToResidents(Mob mob) {
        if (!(mob instanceof Enemy) || mob instanceof NeutralMob || mob instanceof Warden) return false;
        // Spiders preserve their vanilla daylight behavior but hunt residents in darkness.
        return !(mob instanceof Spider) || mob.level().getMaxLocalRawBrightness(mob.blockPosition()) < 12;
    }
}
