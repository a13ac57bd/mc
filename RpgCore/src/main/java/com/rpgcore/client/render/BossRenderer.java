package com.rpgcore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rpgcore.boss.RpgBoss;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Placeholder boss look (§14): a scaled-up zombie model with the husk texture. Arms rise during wind-up so the
 * telegraph is readable; GeckoLib animations replace this in the art pass (the move name is already synced).
 */
public class BossRenderer extends HumanoidMobRenderer<RpgBoss, HumanoidModel<RpgBoss>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/zombie/husk.png");

    public BossRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BossModel(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5F * RpgBoss.SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(RpgBoss entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(RpgBoss entity, PoseStack pose, float partialTick) {
        pose.scale(RpgBoss.SCALE, RpgBoss.SCALE, RpgBoss.SCALE);
    }

    private static final class BossModel extends HumanoidModel<RpgBoss> {
        BossModel(net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(RpgBoss boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(boss, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            switch (boss.state()) {
                case WINDUP -> {
                    rightArm.xRot = (float) -Math.PI * 0.9F;
                    leftArm.xRot = (float) -Math.PI * 0.9F;
                }
                case STRIKE -> {
                    rightArm.xRot = (float) -Math.PI * 0.3F;
                    leftArm.xRot = (float) -Math.PI * 0.3F;
                }
                case TRANSITION -> {
                    rightArm.zRot = 1.2F;
                    leftArm.zRot = -1.2F;
                }
                default -> {
                }
            }
        }
    }
}
