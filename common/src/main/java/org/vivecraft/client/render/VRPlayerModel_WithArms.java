package org.vivecraft.client.render;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Polygon;
import net.minecraft.client.model.geom.ModelPart.Vertex;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.vivecraft.client.utils.ModelUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;
import org.vivecraft.mod_compat_vr.sodium.SodiumHelper;

public class VRPlayerModel_WithArms<T extends LivingEntity> extends VRPlayerModel<T> {
    public ModelPart leftShoulder;
    public ModelPart rightShoulder;
    public ModelPart leftShoulder_sleeve;
    public ModelPart rightShoulder_sleeve;
    public ModelPart leftHand;
    public ModelPart rightHand;

    public VRPlayerModel_WithArms(ModelPart modelPart, boolean isSlim) {
        super(modelPart, isSlim);
        // use left/right arm as shoulders
        this.leftShoulder = modelPart.getChild("left_arm");
        this.rightShoulder = modelPart.getChild("right_arm");
        this.leftShoulder_sleeve = modelPart.getChild("leftShoulder_sleeve");
        this.rightShoulder_sleeve = modelPart.getChild("rightShoulder_sleeve");
        this.rightHand = modelPart.getChild("rightHand");
        this.leftHand = modelPart.getChild("leftHand");


        //finger hax
        // some mods remove the base parts
        if (!this.leftShoulder.cubes.isEmpty()) {
            copyUV(this.leftShoulder.cubes.get(0).polygons[1], this.leftHand.cubes.get(0).polygons[1]);
            copyUV(this.leftShoulder.cubes.get(0).polygons[1], this.leftHand.cubes.get(0).polygons[0]);
            if (SodiumHelper.isLoaded()) {
                SodiumHelper.copyModelCuboidUV(this.leftShoulder, this.leftHand, 3, 3);
                SodiumHelper.copyModelCuboidUV(this.leftShoulder, this.leftHand, 3, 2);
            }
        }
        if (!this.rightShoulder.cubes.isEmpty()) {
            copyUV(this.rightShoulder.cubes.get(0).polygons[1], this.rightHand.cubes.get(0).polygons[1]);
            copyUV(this.rightShoulder.cubes.get(0).polygons[1], this.rightHand.cubes.get(0).polygons[0]);
            if (SodiumHelper.isLoaded()) {
                SodiumHelper.copyModelCuboidUV(this.rightShoulder, this.rightHand, 3, 3);
                SodiumHelper.copyModelCuboidUV(this.rightShoulder, this.rightHand, 3, 2);
            }
        }

        if (!this.rightSleeve.cubes.isEmpty()) {
            copyUV(this.rightShoulder_sleeve.cubes.get(0).polygons[1], this.rightSleeve.cubes.get(0).polygons[1]);
            copyUV(this.rightShoulder_sleeve.cubes.get(0).polygons[1], this.rightSleeve.cubes.get(0).polygons[0]);
            if (SodiumHelper.isLoaded()) {
                SodiumHelper.copyModelCuboidUV(this.rightShoulder_sleeve, this.rightSleeve, 3, 3);
                SodiumHelper.copyModelCuboidUV(this.rightShoulder_sleeve, this.rightSleeve, 3, 2);
            }
        }
        if (!this.leftSleeve.cubes.isEmpty()) {
            copyUV(this.leftShoulder_sleeve.cubes.get(0).polygons[1], this.leftSleeve.cubes.get(0).polygons[1]);
            copyUV(this.leftShoulder_sleeve.cubes.get(0).polygons[1], this.leftSleeve.cubes.get(0).polygons[0]);
            if (SodiumHelper.isLoaded()) {
                SodiumHelper.copyModelCuboidUV(this.leftShoulder_sleeve, this.leftSleeve, 3, 3);
                SodiumHelper.copyModelCuboidUV(this.leftShoulder_sleeve, this.leftSleeve, 3, 2);
            }
        }
    }

    private void copyUV(Polygon source, Polygon dest) {
        for (int i = 0; i < source.vertices.length; i++) {
            Vertex newVertex = new Vertex(dest.vertices[i].pos, source.vertices[i].u, source.vertices[i].v);
            if (OptifineHelper.isOptifineLoaded()) {
                OptifineHelper.copyRenderPositions(dest.vertices[i], newVertex);
            }
            dest.vertices[i] = newVertex;
        }
    }

    public static MeshDefinition createMesh(CubeDeformation cubeDeformation, boolean slim) {
        MeshDefinition meshDefinition = VRPlayerModel.createMesh(cubeDeformation, slim);
        PartDefinition partDefinition = meshDefinition.getRoot();

        if (slim) {
            partDefinition.addOrReplaceChild("leftHand", CubeListBuilder.create()
                    .texOffs(32, 55)
                    .addBox(-1.5F, -3.0F, -2.0F, 3.0F, 5.0F, 4.0F, cubeDeformation),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("left_sleeve", CubeListBuilder.create()
                    .texOffs(48, 55)
                    .addBox(-1.5F, -3.0F, -2.0F, 3.0F, 5.0F, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("rightHand", CubeListBuilder.create()
                    .texOffs(40, 23)
                    .addBox(-1.5F, -3.0F, -2.0F, 3.0F, 5.0F, 4.0F, cubeDeformation),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("right_sleeve", CubeListBuilder.create()
                    .texOffs(40, 39)
                    .addBox(-1.5F, -3.0F, -2.0F, 3.0F, 5.0F, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("left_arm", CubeListBuilder.create()
                    .texOffs(32, 48)
                    .addBox(-1.0F, -2.0F, -2.0F, 3.0F, 5.0F, 4.0F, cubeDeformation),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("right_arm", CubeListBuilder.create()
                    .texOffs(40, 16)
                    .addBox(-2.0F, -2.0F, -2.0F, 3.0F, 5.0F, 4.0F, cubeDeformation),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("leftShoulder_sleeve", CubeListBuilder.create()
                    .texOffs(48, 48)
                    .addBox(-1.0F, -2.0F, -2.0F, 3.0F, 5.0F, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("rightShoulder_sleeve", CubeListBuilder.create()
                    .texOffs(40, 32)
                    .addBox(-2.0F, -2.0F, -2.0F, 3.0F, 5.0F, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
        } else {
            partDefinition.addOrReplaceChild("leftHand", CubeListBuilder.create()
                    .texOffs(32, 55)
                    .addBox(-2.0F, -5.0F, -2.0F, 4.0F, 5.0F, 4.0F, cubeDeformation),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("left_sleeve", CubeListBuilder.create()
                    .texOffs(48, 55)
                    .addBox(-2.0F, -5.0F, -2.0F, 4.0F, 5.0F, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("rightHand", CubeListBuilder.create()
                    .texOffs(40, 23)
                    .addBox(-2.0F, -5.0F, -2.0F, 4.0F, 5.0F, 4.0F, cubeDeformation),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("right_sleeve", CubeListBuilder.create()
                    .texOffs(40, 39)
                    .addBox(-2.0F, -5.0F, -2.0F, 4.0F, 5.0F, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("left_arm", CubeListBuilder.create()
                    .texOffs(32, 48)
                    .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 5.0F, 4.0F, cubeDeformation),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("leftShoulder_sleeve", CubeListBuilder.create()
                    .texOffs(48, 48)
                    .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 5.0F, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("right_arm", CubeListBuilder.create()
                    .texOffs(40, 16)
                    .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 5.0F, 4.0F, cubeDeformation),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("rightShoulder_sleeve", CubeListBuilder.create()
                    .texOffs(40, 32)
                    .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 5.0F, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
        }
        return meshDefinition;
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(this.body, this.jacket, this.hat,
            this.leftHand, this.rightHand, this.leftSleeve, this.rightSleeve,
            this.leftShoulder, this.rightShoulder, this.leftShoulder_sleeve, this.rightShoulder_sleeve,
            this.leftLeg, this.rightLeg, this.leftPants, this.rightPants);
    }

    @Override
    public void setupAnim(
        T player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        super.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        if (this.rotInfo == null) {
            return;
        }

        ModelPart actualLeftHand = this.leftHand;
        ModelPart actualRightHand = this.rightHand;
        ModelPart actualLeftShoulder = this.leftShoulder;
        ModelPart actualRightShoulder = this.rightShoulder;
        if (this.rotInfo.reverse) {
            actualLeftHand = this.rightHand;
            actualRightHand = this.leftHand;
            actualLeftShoulder = this.rightShoulder;
            actualRightShoulder = this.leftShoulder;
        }

        // left arm
        ModelUtils.positionAndRotateModelToLocal(actualLeftHand, this.rotInfo.leftArmPos, this.rotInfo.leftArmQuat,
            this.rotInfo, this.bodyYaw, this.tempV);
        if (this.attackArm == HumanoidArm.LEFT) {
            ModelUtils.attackAnimation(actualLeftHand, HumanoidArm.LEFT, this.attackTime, this.isMainPlayer,
                this.tempM, this.tempV);
        }

        ModelUtils.worldToModelDirection(this.rotInfo.leftArmRot, this.bodyYaw, this.tempV2);

        ModelUtils.estimateJoint(
            actualLeftShoulder.x, actualLeftShoulder.y, actualLeftShoulder.z,
            actualLeftHand.x, actualLeftHand.y, actualLeftHand.z,
            MathUtils.LEFT, 12.0F, this.tempV);

        ModelUtils.pointModelAtModel(actualLeftShoulder, this.tempV.x, this.tempV.y, this.tempV.z, new Quaternionf(),
            this.bodyYaw, true, this.tempV, this.tempM);

        if (VRState.VR_RUNNING && player == Minecraft.getInstance().player &&
            ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
            ClientDataHolderVR.getInstance().vrSettings.shouldRenderModelArms) {
            GuiHandler.guiRotation_playerModel.set(this.rotInfo.leftArmQuat);
            GuiHandler.guiPos_playerModel = player.getPosition(Minecraft.getInstance().getFrameTime())
                .add(this.rotInfo.leftArmPos.x(), this.rotInfo.leftArmPos.y(), this.rotInfo.leftArmPos.z());
        }

        // right arm
        ModelUtils.positionAndRotateModelToLocal(actualRightHand, this.rotInfo.rightArmPos, this.rotInfo.rightArmQuat,
            this.rotInfo, this.bodyYaw, this.tempV);
        if (this.attackArm == HumanoidArm.RIGHT) {
            ModelUtils.attackAnimation(actualRightHand, HumanoidArm.RIGHT, this.attackTime, this.isMainPlayer,
                this.tempM, this.tempV);
        }

        ModelUtils.estimateJoint(
            actualRightShoulder.x, actualRightShoulder.y, actualRightShoulder.z,
            actualRightHand.x, actualRightHand.y, actualRightHand.z,
            MathUtils.LEFT, 12.0F, this.tempV);

        ModelUtils.pointModelAtModel(actualRightShoulder, this.tempV.x, this.tempV.y, this.tempV.z, new Quaternionf(),
            this.bodyYaw, true, this.tempV, this.tempM);

        // first person scale
        if (player == Minecraft.getInstance().player &&
            (ClientDataHolderVR.getInstance().currentPass == RenderPass.LEFT ||
                ClientDataHolderVR.getInstance().currentPass == RenderPass.RIGHT ||
                ClientDataHolderVR.getInstance().currentPass == RenderPass.CENTER
            ))
        {
            this.leftHand.xScale = this.leftHand.zScale = this.rightHand.xScale = this.rightHand.zScale =
                this.leftShoulder.xScale = this.leftShoulder.zScale =
                    this.rightShoulder.xScale = this.rightShoulder.zScale =
                        ClientDataHolderVR.getInstance().vrSettings.playerModelArmsScale;

        } else {
            this.leftHand.xScale = this.leftHand.zScale = this.rightHand.xScale = this.rightHand.zScale =
                this.leftShoulder.xScale = this.leftShoulder.zScale =
                    this.rightShoulder.xScale = this.rightShoulder.zScale = 1.0F;
        }

        /*
        this.tempV.set(0,0,0);
        this.tempM.transform(this.tempV);
        this.leftShoulder.x += this.tempV.x;
        this.leftShoulder.y += this.tempV.y;
        this.leftShoulder.z += this.tempV.z;

        this.leftShoulder.yScale = 1.0F;
*/

        this.leftSleeve.copyFrom(this.leftHand);
        this.rightSleeve.copyFrom(this.rightHand);
        this.leftShoulder_sleeve.copyFrom(this.leftShoulder);
        this.rightShoulder_sleeve.copyFrom(this.rightShoulder);
        this.leftShoulder_sleeve.visible = this.leftSleeve.visible;
        this.rightShoulder_sleeve.visible = this.rightSleeve.visible;
    }

    @Override
    public void setAllVisible(boolean visible) {
        super.setAllVisible(visible);

        this.rightShoulder.visible = visible;
        this.leftShoulder.visible = visible;
        this.rightShoulder_sleeve.visible = visible;
        this.leftShoulder_sleeve.visible = visible;
        this.rightHand.visible = visible;
        this.leftHand.visible = visible;
    }

    @Override
    protected ModelPart getArm(HumanoidArm side) {
        if (this.rotInfo != null && this.rotInfo.reverse) {
            return side == HumanoidArm.RIGHT ? this.leftHand : this.rightHand;
        } else {
            return side == HumanoidArm.LEFT ? this.leftHand : this.rightHand;
        }
    }

    @Override
    public void translateToHand(HumanoidArm side, PoseStack poseStack) {
        ModelPart modelpart = this.getArm(side);

        if (this.laying) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        }

        modelpart.translateAndRotate(poseStack);
        if (this.attackArm == side) {
            poseStack.mulPose(Axis.XP.rotation(Mth.sin(this.attackTime * Mth.PI)));
        }

        poseStack.translate(side == HumanoidArm.LEFT ? -0.0625F : 0.0625F, -0.6F, 0.0F);
    }
}
