package com.sam.realmfolk.entity.client;

import com.sam.realmfolk.entity.ResidentEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;

public class ResidentEntityRenderer extends HumanoidMobRenderer<ResidentEntity, HumanoidModel<ResidentEntity>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/zombie/zombie.png");

    public ResidentEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(ResidentEntity entity) {
        return TEXTURE;
    }
}
