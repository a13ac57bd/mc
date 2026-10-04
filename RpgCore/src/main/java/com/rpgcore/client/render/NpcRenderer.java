package com.rpgcore.client.render;

import com.rpgcore.town.NpcEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Town NPCs use the villager model and texture until the art pass. */
public class NpcRenderer extends MobRenderer<NpcEntity, VillagerModel<NpcEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("textures/entity/villager/villager.png");

    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VillagerModel<>(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity entity) {
        return TEXTURE;
    }
}
