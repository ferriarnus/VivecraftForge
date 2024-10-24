package org.vivecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.vivecraft.client.VRPlayersClient;

public class VRPlayerModel<T extends LivingEntity> extends PlayerModel<T> {
    public ModelPart vrHMD;
    protected VRPlayersClient.RotInfo rotInfo;
    protected boolean laying;

    public VRPlayerModel(ModelPart modelPart, boolean isSlim) {
        super(modelPart, isSlim);
        this.vrHMD = modelPart.getChild("vrHMD");
    }

    public static MeshDefinition createMesh(CubeDeformation cubeDeformation, boolean slim) {
        MeshDefinition meshDefinition = PlayerModel.createMesh(cubeDeformation, slim);
        meshDefinition.getRoot().addOrReplaceChild("vrHMD",
            CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -6.0F, -7.5F, 7.0F, 4.0F, 5.0F, cubeDeformation),
            PartPose.ZERO);
        return meshDefinition;
    }

    @Override
    public void setupAnim(T player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.rotInfo = VRPlayersClient.getInstance().getRotationsForPlayer(player.getUUID());

        if (this.rotInfo == null) {
            return; //how
        }

        float hmdYaw = (float) Math.atan2(-this.rotInfo.headRot.x(), -this.rotInfo.headRot.z());
        float hmdPitch = (float) Math.asin(this.rotInfo.headRot.y() / this.rotInfo.headRot.length());
        float bodyYaw = this.rotInfo.getBodyYawRad();

        this.head.xRot = -hmdPitch;
        this.head.yRot = Mth.PI - hmdYaw - bodyYaw;

        this.laying = this.swimAmount > 0.0F || (player.isFallFlying() && !player.isAutoSpinAttack());

        if (this.laying) {
            // 90° rotation up when laying
            this.head.xRot = this.head.xRot - Mth.HALF_PI * (this.swimAmount > 0.0F ? this.swimAmount : 1.0F);
        }

        this.vrHMD.visible = true;

        this.vrHMD.copyFrom(this.head);
        this.hat.copyFrom(this.head);
    }

    public void renderHMD(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay) {
        this.vrHMD.render(poseStack, vertexConsumer, packedLight, packedOverlay);
    }
}
