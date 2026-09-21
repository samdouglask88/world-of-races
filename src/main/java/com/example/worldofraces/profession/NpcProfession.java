package com.example.worldofraces.profession;

public enum NpcProfession {
    NONE("none","Sem profissao",ProfessionCategory.SERVICES,"Nenhuma profissao atribuida",false),
    FARMER("farmer","Agricultor",ProfessionCategory.PRODUCTION,"Cultiva e colhe alimentos",false),
    LUMBERJACK("lumberjack","Lenhador",ProfessionCategory.GATHERING,"Coleta e trabalha madeira",false),
    MINER("miner","Minerador",ProfessionCategory.GATHERING,"Extrai minerais",false),
    FISHERMAN("fisherman","Pescador",ProfessionCategory.GATHERING,"Pesca alimentos e recursos",false),
    HUNTER("hunter","Cacador",ProfessionCategory.GATHERING,"Caca e explora a regiao",false),
    BLACKSMITH("blacksmith","Ferreiro",ProfessionCategory.PRODUCTION,"Forja ferramentas e armaduras de ferro",true),
    COOK("cook","Cozinheiro",ProfessionCategory.PRODUCTION,"Prepara refeicoes",false),
    MERCHANT("merchant","Mercador",ProfessionCategory.SERVICES,"Negocia mercadorias",false),
    BUILDER("builder","Construtor",ProfessionCategory.SERVICES,"Constroi e repara estruturas",false),
    GUARD("guard","Guarda",ProfessionCategory.MILITARY,"Protege habitantes e propriedades",false);
    public final String id,name; public final ProfessionCategory category; public final String description; public final boolean implemented;
    NpcProfession(String id,String name,ProfessionCategory category,String description,boolean implemented){this.id=id;this.name=name;this.category=category;this.description=description;this.implemented=implemented;}
    public static NpcProfession byId(String id){for(var value:values())if(value.id.equals(id))return value;return NONE;}
    public int maxLevel(){return 5;} public String station(){return this==BLACKSMITH?"Bigorna":"Nao definida";} public String tool(){return this==BLACKSMITH?"Martelo de ferreiro":"Nao definida";}
    public net.minecraft.world.item.ItemStack icon(){return new net.minecraft.world.item.ItemStack(switch(this){case FARMER->net.minecraft.world.item.Items.WHEAT;case LUMBERJACK->net.minecraft.world.item.Items.IRON_AXE;case MINER->net.minecraft.world.item.Items.IRON_PICKAXE;case FISHERMAN->net.minecraft.world.item.Items.FISHING_ROD;case HUNTER->net.minecraft.world.item.Items.BOW;case BLACKSMITH->ModProfessionItems.BLACKSMITH_HAMMER.get();case COOK->net.minecraft.world.item.Items.COOKED_BEEF;case MERCHANT->net.minecraft.world.item.Items.EMERALD;case BUILDER->net.minecraft.world.item.Items.BRICKS;case GUARD->net.minecraft.world.item.Items.IRON_SWORD;default->net.minecraft.world.item.Items.BARRIER;});}
}
