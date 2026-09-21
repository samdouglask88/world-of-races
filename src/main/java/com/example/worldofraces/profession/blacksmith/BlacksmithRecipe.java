package com.example.worldofraces.profession.blacksmith;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.List;

public enum BlacksmithRecipe {
    IRON_SHOVEL(1,Items.IRON_SHOVEL,10,List.of(new Ingredient(Items.IRON_INGOT,1),new Ingredient(Items.STICK,2))),
    IRON_SWORD(1,Items.IRON_SWORD,15,List.of(new Ingredient(Items.IRON_INGOT,2),new Ingredient(Items.STICK,1))),
    IRON_AXE(2,Items.IRON_AXE,20,List.of(new Ingredient(Items.IRON_INGOT,3),new Ingredient(Items.STICK,2))),
    IRON_PICKAXE(2,Items.IRON_PICKAXE,25,List.of(new Ingredient(Items.IRON_INGOT,3),new Ingredient(Items.STICK,2))),
    IRON_HELMET(3,Items.IRON_HELMET,35,List.of(new Ingredient(Items.IRON_INGOT,5))),
    IRON_CHESTPLATE(3,Items.IRON_CHESTPLATE,50,List.of(new Ingredient(Items.IRON_INGOT,8)));
    public final int level,xp;public final Item result;public final List<Ingredient> ingredients;
    BlacksmithRecipe(int level,Item result,int xp,List<Ingredient> ingredients){this.level=level;this.result=result;this.xp=xp;this.ingredients=ingredients;}
    public record Ingredient(Item item,int count){}
}
