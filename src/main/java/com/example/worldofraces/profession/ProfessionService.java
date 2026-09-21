package com.example.worldofraces.profession;

import com.example.worldofraces.entity.RaceEntity;
import com.example.worldofraces.profession.blacksmith.BlacksmithRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import java.util.Comparator;
import java.util.Optional;

public final class ProfessionService {
    public static final int SEARCH_RADIUS=32;
    private ProfessionService(){}
    public static Result assign(ServerPlayer player,RaceEntity npc,NpcProfession profession){
        if(!npc.isAlive()||npc.distanceToSqr(player)>64)return new Result(false,"Habitante distante ou invalido");
        if(!profession.implemented)return new Result(false,"Profissao indisponivel");
        if(profession!=NpcProfession.BLACKSMITH)return new Result(false,"Profissao ainda nao implementada");
        ServerLevel level=player.serverLevel();ProfessionData data=npc.getProfessionData();
        Optional<BlockPos> station=findAnvil(level,npc.blockPosition(),npc.getUUID());
        if(station.isEmpty())return new Result(false,"Nenhuma bigorna livre encontrada em um raio de 32 blocos");
        remove(level,npc);BlockPos pos=station.get();if(!WorkstationSavedData.get(level.getServer()).claim(level.dimension().location(),pos,npc.getUUID()))return new Result(false,"A bigorna ja pertence a outro habitante");
        data.assign(profession,pos,level.dimension().location());return new Result(true,hasHammer(npc)?"Ferreiro atribuido":"Aguardando Martelo de ferreiro");
    }
    public static void remove(ServerLevel level,RaceEntity npc){ProfessionData d=npc.getProfessionData();if(d.workstation()!=null&&d.dimension()!=null)WorkstationSavedData.get(level.getServer()).release(d.dimension(),d.workstation(),npc.getUUID());d.remove();}
    public static boolean validateOrFindStation(ServerLevel level,RaceEntity npc){ProfessionData d=npc.getProfessionData();if(d.profession()!=NpcProfession.BLACKSMITH)return false;
        if(d.workstation()!=null&&d.dimension()!=null&&d.dimension().equals(level.dimension().location())&&level.hasChunkAt(d.workstation())&&isAnvil(level,d.workstation())&&WorkstationSavedData.get(level.getServer()).claim(d.dimension(),d.workstation(),npc.getUUID()))return true;
        if(d.workstation()!=null&&d.dimension()!=null)WorkstationSavedData.get(level.getServer()).release(d.dimension(),d.workstation(),npc.getUUID());d.invalidateStation();
        Optional<BlockPos> replacement=findAnvil(level,npc.blockPosition(),npc.getUUID());if(replacement.isEmpty())return false;BlockPos pos=replacement.get();if(!WorkstationSavedData.get(level.getServer()).claim(level.dimension().location(),pos,npc.getUUID()))return false;d.assign(NpcProfession.BLACKSMITH,pos,level.dimension().location());return true;
    }
    private static Optional<BlockPos> findAnvil(ServerLevel level,BlockPos center,java.util.UUID npc){return BlockPos.betweenClosedStream(center.offset(-SEARCH_RADIUS,-8,-SEARCH_RADIUS),center.offset(SEARCH_RADIUS,8,SEARCH_RADIUS)).filter(level::hasChunkAt).filter(p->isAnvil(level,p)).filter(p->WorkstationSavedData.get(level.getServer()).available(level.dimension().location(),p,npc)).min(Comparator.comparingDouble(p->p.distSqr(center))).map(BlockPos::immutable);}
    private static boolean isAnvil(ServerLevel level,BlockPos pos){var b=level.getBlockState(pos).getBlock();return b==Blocks.ANVIL||b==Blocks.CHIPPED_ANVIL||b==Blocks.DAMAGED_ANVIL;}
    public static boolean hasHammer(RaceEntity npc){return findHammer(npc)>=0;}
    private static int findHammer(RaceEntity npc){Container c=npc.getNpcInventory();for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(ModProfessionItems.BLACKSMITH_HAMMER.get()))return i;if(npc.getMainHandItem().is(ModProfessionItems.BLACKSMITH_HAMMER.get()))return -2;return -1;}
    public static ProductionResult produce(RaceEntity npc){ProfessionData data=npc.getProfessionData();Container inv=npc.getNpcInventory();int hammerSlot=findHammer(npc);if(hammerSlot<0)return ProductionResult.NO_HAMMER;
        for(BlacksmithRecipe recipe:BlacksmithRecipe.values())if(recipe.level<=data.level()&&hasIngredients(inv,recipe)&&canFit(inv,new ItemStack(recipe.result))){
            recipe.ingredients.forEach(i->remove(inv,i.item(),i.count()));ItemStack hammer=hammerSlot==-2?npc.getMainHandItem():inv.getItem(hammerSlot);hammer.setDamageValue(hammer.getDamageValue()+1);if(hammer.getDamageValue()>=hammer.getMaxDamage()){if(hammerSlot==-2)npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,ItemStack.EMPTY);else inv.setItem(hammerSlot,ItemStack.EMPTY);}add(inv,new ItemStack(recipe.result));boolean leveled=data.addExperience(recipe.xp);inv.setChanged();return leveled?ProductionResult.LEVEL_UP:ProductionResult.SUCCESS;
        }return ProductionResult.NO_RECIPE;
    }
    private static boolean hasIngredients(Container c,BlacksmithRecipe r){for(var ingredient:r.ingredients)if(count(c,ingredient.item())<ingredient.count())return false;return true;}
    private static int count(Container c,net.minecraft.world.item.Item item){int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();return n;}
    private static void remove(Container c,net.minecraft.world.item.Item item,int n){for(int i=0;i<c.getContainerSize()&&n>0;i++)if(c.getItem(i).is(item)){ItemStack s=c.getItem(i);int take=Math.min(n,s.getCount());s.shrink(take);n-=take;if(s.isEmpty())c.setItem(i,ItemStack.EMPTY);}}
    private static boolean canFit(Container c,ItemStack item){for(int i=0;i<c.getContainerSize();i++){ItemStack s=c.getItem(i);if(s.isEmpty()||ItemStack.isSameItemSameTags(s,item)&&s.getCount()<s.getMaxStackSize())return true;}return false;}
    private static void add(Container c,ItemStack item){for(int i=0;i<c.getContainerSize();i++){ItemStack s=c.getItem(i);if(!s.isEmpty()&&ItemStack.isSameItemSameTags(s,item)&&s.getCount()<s.getMaxStackSize()){s.grow(1);return;}}for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).isEmpty()){c.setItem(i,item);return;}}
    public record Result(boolean success,String message){}
    public enum ProductionResult{SUCCESS,LEVEL_UP,NO_HAMMER,NO_RECIPE}
}
