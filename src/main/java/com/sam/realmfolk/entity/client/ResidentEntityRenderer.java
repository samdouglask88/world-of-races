package com.sam.realmfolk.entity.client;

import com.sam.realmfolk.entity.ResidentEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;

public class ResidentEntityRenderer extends HumanoidMobRenderer<ResidentEntity, PlayerModel<ResidentEntity>> {
    private final PlayerModel<ResidentEntity> wideModel;
    private final PlayerModel<ResidentEntity> slimModel;

    public ResidentEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wideModel = this.getModel();
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new ElytraLayer<>(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(ResidentEntity entity) {
        return DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
    }

    @Override
    public void render(ResidentEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        this.model = "slim".equals(DefaultPlayerSkin.getSkinModelName(entity.getUUID())) ? slimModel : wideModel;
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    protected void scale(ResidentEntity entity, PoseStack poseStack, float partialTick) {
        float scale = switch (entity.getLifeStage()) {
            case BABY -> 0.5F;
            case CHILD -> 0.7F;
            case TEENAGER -> 0.88F;
            default -> 1.0F;
        };
        poseStack.scale(scale, scale, scale);
    }
}
