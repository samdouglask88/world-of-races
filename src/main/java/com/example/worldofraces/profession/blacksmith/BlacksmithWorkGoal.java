package com.example.worldofraces.profession.blacksmith;

import com.example.worldofraces.entity.NpcBehaviorMode;
import com.example.worldofraces.entity.RaceEntity;
import com.example.worldofraces.profession.NpcProfession;
import com.example.worldofraces.profession.ProfessionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Rotina compartilhada por todas as profissões que usam uma estação de trabalho. */
public final class BlacksmithWorkGoal extends Goal {
    public static final long DAY_START = 1000, DAY_END = 11000, COOLDOWN = 600;
    public static final int WORK_DURATION = 100;
    public static final double RANGE_SQR = 6.25;

    private final RaceEntity npc;
    private int workTicks;

    public BlacksmithWorkGoal(RaceEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(npc.level() instanceof ServerLevel level) || !npc.isAlive()
                || npc.getBehaviorMode() != NpcBehaviorMode.WANDER || npc.getTarget() != null) return false;
        long time = level.getDayTime() % 24000;
        if (time < DAY_START || time > DAY_END) return false;
        var data = npc.getProfessionData();
        return data.profession() != NpcProfession.NONE
                && level.getGameTime() - data.lastWorkGameTime() >= COOLDOWN
                && ProfessionService.validateOrFindStation(level, npc)
                && ProfessionService.hasRequiredTool(npc);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && workTicks < WORK_DURATION;
    }

    @Override
    public void start() {
        workTicks = 0;
        npc.getProfessionData().setActive(true);
    }

    @Override
    public void tick() {
        BlockPos station = npc.getProfessionData().workstation();
        if (station == null) return;
        double distance = npc.distanceToSqr(station.getX() + .5, station.getY() + .5, station.getZ() + .5);
        if (distance > RANGE_SQR) {
            npc.getNavigation().moveTo(station.getX() + .5, station.getY(), station.getZ() + .5, 1.0);
            return;
        }
        npc.getNavigation().stop();
        npc.getLookControl().setLookAt(station.getX() + .5, station.getY() + .5, station.getZ() + .5);
        workTicks++;
        if (workTicks % 20 == 0) {
            npc.swing(InteractionHand.MAIN_HAND);
            npc.playSound(npc.getProfessionData().profession() == NpcProfession.BLACKSMITH
                    ? SoundEvents.ANVIL_USE : SoundEvents.ITEM_PICKUP, .35F, 1.1F);
            if (npc.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, station.getX() + .5, station.getY() + 1,
                        station.getZ() + .5, 2, .15, .1, .15, .02);
            }
        }
        if (workTicks >= WORK_DURATION && npc.level() instanceof ServerLevel level) {
            var result = ProfessionService.produce(npc);
            npc.getProfessionData().setLastWorkGameTime(level.getGameTime());
            if (result == ProfessionService.ProductionResult.LEVEL_UP) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, npc.getX(), npc.getY() + 1,
                        npc.getZ(), 8, .3, .4, .3, .05);
            }
        }
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        npc.getProfessionData().setActive(false);
        workTicks = 0;
    }
}
