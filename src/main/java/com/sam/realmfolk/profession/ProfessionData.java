package com.sam.realmfolk.profession;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

public final class ProfessionData {
    private NpcProfession profession=NpcProfession.NONE; private ProfessionAptitude aptitude;
    @Nullable private BlockPos workstation; @Nullable private ResourceLocation dimension;
    private boolean active; private long lastWorkGameTime; private final Map<NpcProfession,Progress> progress=new EnumMap<>(NpcProfession.class);
    public ProfessionData(ProfessionAptitude aptitude){this.aptitude=aptitude;}
    public NpcProfession profession(){return profession;} public ProfessionAptitude aptitude(){return aptitude;}
    public int level(){return progress().level;} public int experience(){return progress().experience;}
    @Nullable public BlockPos workstation(){return workstation;} @Nullable public ResourceLocation dimension(){return dimension;}
    public boolean active(){return active;} public long lastWorkGameTime(){return lastWorkGameTime;}
    public int experienceNeeded(){return needed(level());}
    public void assign(NpcProfession value,BlockPos pos,ResourceLocation dimension){profession=value;workstation=pos.immutable();this.dimension=dimension;active=false;progress();}
    public void remove(){profession=NpcProfession.NONE;workstation=null;dimension=null;active=false;}
    public void invalidateStation(){workstation=null;dimension=null;active=false;}
    public void setActive(boolean value){active=value;} public void setLastWorkGameTime(long value){lastWorkGameTime=value;}
    public boolean addExperience(int base){Progress p=progress();int gained=Math.max(1,Math.round(base*aptitude.multiplier));p.experience+=gained;boolean leveled=false;while(p.level<5&&p.experience>=needed(p.level)){p.experience-=needed(p.level);p.level++;leveled=true;}return leveled;}
    private Progress progress(){return progress.computeIfAbsent(profession,p->new Progress());}
    public static int needed(int level){return switch(level){case 1->100;case 2->250;case 3->500;case 4->1000;default->0;};}
    public CompoundTag save(){CompoundTag t=new CompoundTag();t.putString("Profession",profession.id);t.putString("Aptitude",aptitude.name());if(workstation!=null)t.putLong("Workstation",workstation.asLong());if(dimension!=null)t.putString("Dimension",dimension.toString());t.putBoolean("Active",active);t.putLong("LastWork",lastWorkGameTime);CompoundTag all=new CompoundTag();progress.forEach((p,v)->{CompoundTag n=new CompoundTag();n.putInt("Level",v.level);n.putInt("Experience",v.experience);all.put(p.id,n);});t.put("Progress",all);return t;}
    public static ProfessionData load(CompoundTag t,ProfessionAptitude fallback){ProfessionAptitude a;try{a=ProfessionAptitude.valueOf(t.getString("Aptitude"));}catch(Exception e){a=fallback;}ProfessionData d=new ProfessionData(a);d.profession=NpcProfession.byId(t.getString("Profession"));if(t.contains("Workstation"))d.workstation=BlockPos.of(t.getLong("Workstation"));if(t.contains("Dimension"))d.dimension=ResourceLocation.tryParse(t.getString("Dimension"));d.active=t.getBoolean("Active");d.lastWorkGameTime=t.getLong("LastWork");CompoundTag all=t.getCompound("Progress");for(NpcProfession p:NpcProfession.values())if(all.contains(p.id)){CompoundTag n=all.getCompound(p.id);d.progress.put(p,new Progress(Math.max(1,Math.min(5,n.getInt("Level"))),Math.max(0,n.getInt("Experience"))));}return d;}
    private static final class Progress{int level=1,experience;Progress(){}Progress(int l,int e){level=l;experience=e;}}
}
