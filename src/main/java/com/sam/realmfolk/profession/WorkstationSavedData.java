package com.sam.realmfolk.profession;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WorkstationSavedData extends SavedData {
    private static final String NAME="realmfolk_workstations"; private final Map<String,UUID> owners=new HashMap<>();
    public static WorkstationSavedData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(WorkstationSavedData::load,WorkstationSavedData::new,NAME);}
    private static String key(ResourceLocation dim,BlockPos pos){return dim+"|"+pos.asLong();}
    public boolean claim(ResourceLocation dim,BlockPos pos,UUID npc){String key=key(dim,pos);UUID owner=owners.get(key);if(owner!=null&&!owner.equals(npc))return false;owners.put(key,npc);setDirty();return true;}
    public boolean available(ResourceLocation dim,BlockPos pos,UUID npc){UUID owner=owners.get(key(dim,pos));return owner==null||owner.equals(npc);}
    public void release(ResourceLocation dim,BlockPos pos,UUID npc){String key=key(dim,pos);if(npc.equals(owners.get(key))){owners.remove(key);setDirty();}}
    @Override public CompoundTag save(CompoundTag t){ListTag list=new ListTag();owners.forEach((key,id)->{CompoundTag n=new CompoundTag();n.putString("Key",key);n.putUUID("Owner",id);list.add(n);});t.put("Claims",list);return t;}
    private static WorkstationSavedData load(CompoundTag t){WorkstationSavedData d=new WorkstationSavedData();ListTag list=t.getList("Claims",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++){CompoundTag n=list.getCompound(i);if(n.hasUUID("Owner"))d.owners.put(n.getString("Key"),n.getUUID("Owner"));}return d;}
}
