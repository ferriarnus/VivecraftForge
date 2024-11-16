package org.vivecraft.client.render;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import org.vivecraft.client.render.models.HandModel;
import org.vivecraft.client.utils.ModelUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.render.RenderPass;

public class VRPlayerModel_WithArms<T extends LivingEntity> extends VRPlayerModel<T> implements HandModel {
    public static final int LOWER_EXTENSION = 2;
    public static final int UPPER_EXTENSION = 3;

    // shoulders use the vanilla arm parts
    public final ModelPart leftHand;
    public final ModelPart rightHand;
    public final ModelPart leftHandSleeve;
    public final ModelPart rightHandSleeve;

    public VRPlayerModel_WithArms(ModelPart root, boolean isSlim) {
        super(root, isSlim);
        this.leftHandSleeve = root.getChild("left_hand_sleeve");
        this.rightHandSleeve = root.getChild("right_hand_sleeve");
        this.leftHand = root.getChild("left_hand");
        this.rightHand = root.getChild("right_hand");

        // copy textures
        ModelUtils.textureHack(this.leftArm, this.leftHand);
        ModelUtils.textureHack(this.rightArm, this.rightHand);
        ModelUtils.textureHack(this.leftSleeve, this.leftHandSleeve);
        ModelUtils.textureHack(this.rightSleeve, this.rightHandSleeve);
    }

    public static MeshDefinition createMesh(CubeDeformation cubeDeformation, boolean slim) {
        MeshDefinition meshDefinition = VRPlayerModel.createMesh(cubeDeformation, slim);
        PartDefinition partDefinition = meshDefinition.getRoot();
        boolean connected = ClientDataHolderVR.getInstance().vrSettings.playerLimbsConnected;
        int upperExtension = connected ? UPPER_EXTENSION : 0;
        int lowerExtension = connected ? LOWER_EXTENSION : 0;
        float lowerShrinkage = connected ? -0.05F : 0F;

        if (slim) {
            partDefinition.addOrReplaceChild("left_hand", CubeListBuilder.create()
                    .texOffs(32, 55 - lowerExtension)
                    .addBox(-1.5F, -5.0F - lowerExtension, -2.0F, 3.0F, 5.0F +  lowerExtension, 4.0F, cubeDeformation.extend(lowerShrinkage)),
                PartPose.offset(5.5F, 12.0F, 0.0F));
            partDefinition.addOrReplaceChild("left_hand_sleeve", CubeListBuilder.create()
                    .texOffs(48, 55 - lowerExtension)
                    .addBox(-1.5F, -5.0F - lowerExtension, -2.0F, 3.0F, 5.0F +  lowerExtension, 4.0F, cubeDeformation.extend(0.25f + lowerShrinkage)),
                PartPose.offset(5.5F, 12.0F, 0.0F));
            partDefinition.addOrReplaceChild("right_hand", CubeListBuilder.create()
                    .texOffs(40, 23 - lowerExtension)
                    .addBox(-1.5F, -5.0F - lowerExtension, -2.0F, 3.0F, 5.0F + lowerExtension, 4.0F, cubeDeformation.extend(lowerShrinkage)),
                PartPose.offset(-5.5F, 12.0F, 0.0F));
            partDefinition.addOrReplaceChild("right_hand_sleeve", CubeListBuilder.create()
                    .texOffs(40, 39 - lowerExtension)
                    .addBox(-1.5F, -5.0F - lowerExtension, -2.0F, 3.0F, 5.0F +  lowerExtension, 4.0F, cubeDeformation.extend(0.25f + lowerShrinkage)),
                PartPose.offset(-5.5F, 12.0F, 0.0F));
            partDefinition.addOrReplaceChild("left_arm", CubeListBuilder.create()
                    .texOffs(32, 48)
                    .addBox(-1.0F, -2.0F, -2.0F, 3.0F, 5.0F + upperExtension, 4.0F, cubeDeformation),
                PartPose.offset(5.0F, 2.0F, 0.0F));
            partDefinition.addOrReplaceChild("left_sleeve", CubeListBuilder.create()
                    .texOffs(48, 48)
                    .addBox(-1.0F, -2.0F, -2.0F, 3.0F, 5.0F + upperExtension, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(5.0F, 2.0F, 0.0F));
            partDefinition.addOrReplaceChild("right_arm", CubeListBuilder.create()
                    .texOffs(40, 16)
                    .addBox(-2.0F, -2.0F, -2.0F, 3.0F, 5.0F + upperExtension, 4.0F, cubeDeformation),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
            partDefinition.addOrReplaceChild("right_sleeve", CubeListBuilder.create()
                    .texOffs(40, 32)
                    .addBox(-2.0F, -2.0F, -2.0F, 3.0F, 5.0F + upperExtension, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        } else {
            partDefinition.addOrReplaceChild("left_hand", CubeListBuilder.create()
                    .texOffs(32, 55 - lowerExtension)
                    .addBox(-2.0F, -5.0F - lowerExtension, -2.0F, 4.0F, 5.0F +  lowerExtension, 4.0F, cubeDeformation.extend(lowerShrinkage)),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("left_hand_sleeve", CubeListBuilder.create()
                    .texOffs(48, 55 - lowerExtension)
                    .addBox(-2.0F, -5.0F - lowerExtension, -2.0F, 4.0F, 5.0F + lowerExtension, 4.0F, cubeDeformation.extend(0.25f + lowerShrinkage)),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("right_hand", CubeListBuilder.create()
                    .texOffs(40, 23 - lowerExtension)
                    .addBox(-2.0F, -5.0F - lowerExtension, -2.0F, 4.0F, 5.0F + lowerExtension, 4.0F, cubeDeformation.extend(lowerShrinkage)),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("right_hand_sleeve", CubeListBuilder.create()
                    .texOffs(40, 39 - lowerExtension)
                    .addBox(-2.0F, -5.0F - lowerExtension, -2.0F, 4.0F, 5.0F + lowerExtension, 4.0F, cubeDeformation.extend(0.25f + lowerShrinkage)),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("left_arm", CubeListBuilder.create()
                    .texOffs(32, 48)
                    .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 5.0F + upperExtension, 4.0F, cubeDeformation),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("left_sleeve", CubeListBuilder.create()
                    .texOffs(48, 48)
                    .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 5.0F + upperExtension, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("right_arm", CubeListBuilder.create()
                    .texOffs(40, 16)
                    .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 5.0F + upperExtension, 4.0F, cubeDeformation),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("right_sleeve", CubeListBuilder.create()
                    .texOffs(40, 32)
                    .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 5.0F + upperExtension, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
        }
        return meshDefinition;
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return Iterables.concat(super.bodyParts(),
            ImmutableList.of(this.leftHand, this.rightHand, this.leftHandSleeve, this.rightHandSleeve));
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
        ModelPart actualLeftShoulder = this.leftArm;
        ModelPart actualRightShoulder = this.rightArm;
        if (this.rotInfo.reverse) {
            actualLeftHand = this.rightHand;
            actualRightHand = this.leftHand;
            actualLeftShoulder = this.rightArm;
            actualRightShoulder = this.leftArm;
        }

        float limbScale = 1F;
        if (player == Minecraft.getInstance().player && RenderPass.isFirstPerson(ClientDataHolderVR.getInstance().currentPass)) {
            limbScale = ClientDataHolderVR.getInstance().vrSettings.playerModelArmsScale;
        }

        if (this.rotInfo.leftArmPos.distanceSquared(this.rotInfo.rightArmPos) > 0.001F) {
            float offset = (this.slim ? 0.5F : 1F) * limbScale * (this.rotInfo.reverse ? -1F : 1F);

            // left arm
            if (ClientDataHolderVR.getInstance().vrSettings.playerLimbsConnected) {
                positionConnectedLimb(actualLeftShoulder, actualLeftHand, this.rotInfo.leftArmPos,
                    this.rotInfo.leftArmQuat, this.rotInfo.leftElbowPos, true, HumanoidArm.LEFT);

                this.tempM.rotateLocalX(this.xRot);
                this.tempM.transform(offset, 0, 0, this.tempV);
                actualLeftHand.x += this.tempV.x;
                actualLeftHand.y += this.tempV.y;
                actualLeftHand.z -= this.tempV.z;
            } else {
                positionSplitLimb(actualLeftShoulder, actualLeftHand, this.rotInfo.leftArmPos, this.rotInfo.leftArmRot,
                    this.rotInfo.leftArmQuat, 0F, offset, this.rotInfo.leftElbowPos, true, HumanoidArm.LEFT);
                // undo lay rotation
                this.tempM.rotateLocalX(this.xRot);
            }

            if (this.isMainPlayer && ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
                ClientDataHolderVR.getInstance().vrSettings.shouldRenderModelArms)
            {
                GuiHandler.GUI_ROTATION_PLAYER_MODEL.set3x3(this.tempM);
                // ModelParts are rotated 90°
                GuiHandler.GUI_ROTATION_PLAYER_MODEL.rotateX(-Mth.HALF_PI);
                // undo body yaw
                GuiHandler.GUI_ROTATION_PLAYER_MODEL.rotateLocalY(-this.bodyYaw - Mth.PI);

                ModelUtils.modelToWorld(actualLeftHand.x, actualLeftHand.y, actualLeftHand.z, this.rotInfo,
                    this.bodyYaw, this.isMainPlayer, this.tempV);

                GuiHandler.GUI_POS_PLAYER_MODEL = player.getPosition(Minecraft.getInstance().getFrameTime())
                    .add(this.tempV.x, this.tempV.y, this.tempV.z);
            }

            // right arm
            if (ClientDataHolderVR.getInstance().vrSettings.playerLimbsConnected) {
                positionConnectedLimb(actualRightShoulder, actualRightHand, this.rotInfo.rightArmPos,
                    this.rotInfo.rightArmQuat, this.rotInfo.rightElbowPos, true, HumanoidArm.RIGHT);

                this.tempM.rotateLocalX(this.xRot);
                this.tempM.transform(-offset, 0, 0, this.tempV);
                actualRightHand.x += this.tempV.x;
                actualRightHand.y += this.tempV.y;
                actualRightHand.z -= this.tempV.z;
            } else {
                positionSplitLimb(actualRightShoulder, actualRightHand, this.rotInfo.rightArmPos,
                    this.rotInfo.rightArmRot, this.rotInfo.rightArmQuat, 0F, -offset, this.rotInfo.rightElbowPos, true,
                    HumanoidArm.RIGHT);
            }
        } else {
            this.tempV.set(-limbScale, 10, 0)
                .rotateZ(actualRightShoulder.zRot)
                .rotateY(actualRightShoulder.yRot)
                .rotateX(actualRightShoulder.xRot);
            actualRightHand.copyFrom(actualRightShoulder);
            actualRightHand.x += this.tempV.x;
            actualRightHand.y += this.tempV.y;
            actualRightHand.z += this.tempV.z;

            this.tempV.set(limbScale, 10, 0)
                .rotateZ(actualLeftShoulder.zRot)
                .rotateY(actualLeftShoulder.yRot)
                .rotateX(actualLeftShoulder.xRot);
            actualLeftHand.copyFrom(actualLeftShoulder);
            actualLeftHand.x += this.tempV.x;
            actualLeftHand.y += this.tempV.y;
            actualLeftHand.z += this.tempV.z;

            if (this.isMainPlayer && ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
                ClientDataHolderVR.getInstance().vrSettings.shouldRenderModelArms)
            {
                GuiHandler.GUI_POS_PLAYER_MODEL = Vec3.ZERO;
            }
        }

        // first person scale
        this.leftHand.xScale = this.leftHand.zScale = this.rightHand.xScale = this.rightHand.zScale =
            this.leftArm.xScale = this.leftArm.zScale = this.rightArm.xScale = this.rightArm.zScale = limbScale;

        if (this.layAmount > 0F) {
            ModelUtils.applySwimRotationOffset(player, this.xRot, this.tempV, this.tempV2,
                this.leftArm, this.rightArm,
                this.leftHand, this.rightHand);
        }

        this.leftHandSleeve.copyFrom(this.leftHand);
        this.rightHandSleeve.copyFrom(this.rightHand);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftHandSleeve.visible &= this.leftSleeve.visible;
        this.rightHandSleeve.visible &= this.rightSleeve.visible;
    }

    /**
     * positions the hand/foot and applies its rotation. also rotates the shoulder/thigh to point at the elbow/knee
     * @param upper upper body part (shoulder/thigh)
     * @param lower lower body part (hand/foot)
     * @param lowerPos player space position the lower body part should be at
     * @param lowerRot direction the lower body part should face
     * @param lowerXRot additional rotation around the X axis that should be applied to the {@code lower}
     * @param jointPos elbow/knee position, if {@code null} a middle point will be estimated
     * @param jointDown if the estimated joint should prefer up/forward or down/back
     * @param arm arm this is positioning, to check if the swing animation should be applied
     */
    protected void positionSplitLimb(
        ModelPart upper, ModelPart lower, Vector3fc lowerPos, Vector3fc lowerDir, Quaternionfc lowerRot, float lowerXRot,
        float lowerXOffset, Vector3fc jointPos, boolean jointDown, HumanoidArm arm)
    {
        // place lower directly at the lower point
        ModelUtils.worldToModel(lowerPos, this.rotInfo, this.bodyYaw, this.isMainPlayer, this.tempV);
        lower.setPos(this.tempV.x, this.tempV.y, this.tempV.z);

        // joint estimation
        if (jointPos == null) {
            // point the elbow away from the hand direction
            ModelUtils.worldToModelDirection(lowerDir, this.bodyYaw, this.tempV2);
            if (jointDown) {
                this.tempV2.mul(-1F);
            }

            ModelUtils.estimateJoint(
                upper.x, upper.y, upper.z,
                lower.x - lowerXOffset, lower.y, lower.z,
                this.tempV2, 12.0F, this.tempV);
        } else {
            ModelUtils.worldToModel(jointPos, this.rotInfo, this.bodyYaw, this.isMainPlayer, this.tempV);
        }

        // upper position and rotation
        ModelUtils.pointModelAtModel(upper, this.tempV.x, this.tempV.y, this.tempV.z,
            this.tempV, this.tempV2, this.tempM);

        this.tempM.rotateLocalX(-this.xRot);
        ModelUtils.setRotation(upper, this.tempM, this.tempV);

        // lower rotation
        ModelUtils.toModelDir(this.bodyYaw, lowerRot, this.tempM);

        if (arm != null && this.attackArm == arm) {
            ModelUtils.swingAnimation(lower, arm, -3F, this.attackTime, this.isMainPlayer, this.tempM,
                this.tempV, this.tempV2);
        }

        this.tempM.rotateLocalX(-this.xRot + lowerXRot);
        ModelUtils.setRotation(lower, this.tempM, this.tempV);
    }

    /**
     * positions the hand/foot and shoulder/thigh to point at the elbow/knee
     * @param upper upper body part (shoulder/thigh)
     * @param lower lower body part (hand/foot)
     * @param lowerPos player space position the lower body part should be at
     * @param lowerRot direction the lower body part should face
     * @param jointPos elbow/knee position, if {@code null} a middle point will be estimated
     * @param jointDown if the estimated joint should prefer up/forward or down/back
     * @param arm arm this is positioning, to check if the swing animation should be applied
     */
    protected void positionConnectedLimb(
        ModelPart upper, ModelPart lower, Vector3fc lowerPos, Quaternionfc lowerRot, Vector3fc jointPos,
        boolean jointDown, HumanoidArm arm)
    {
        // position lower
        ModelUtils.worldToModel(lowerPos, this.rotInfo, this.bodyYaw, this.isMainPlayer, this.tempV);
        float armLength = 12F;
        // limit length to 12, no limb stretching, for now
        float length = this.tempV.distance(upper.x, upper.y, upper.z);
        if (ClientDataHolderVR.getInstance().vrSettings.playerLimbsLimit && length > armLength) {
            this.tempV.sub(upper.x, upper.y, upper.z);
            this.tempV.normalize().mul(armLength);
            this.tempV.add(upper.x, upper.y, upper.z);
        }
        lower.setPos(this.tempV.x, this.tempV.y, this.tempV.z);

        // point the elbow away from the hand direction
        // arm dir
        this.tempV.sub(upper.x, upper.y, upper.z);

        // hand direction, up forward/down back
        lowerRot.transform(0F, jointDown ? -1F : 1F, jointDown ? 1F : -1F, this.tempV2);
        ModelUtils.worldToModelDirection(this.tempV2, this.bodyYaw, this.tempV2);

        // calculate the vector perpendicular to the arm dir
        float dot = this.tempV.dot(this.tempV2) / this.tempV.dot(this.tempV);
        this.tempV.mul(dot);
        this.tempV2.sub(this.tempV).normalize();

        // get joint
        if (jointPos == null) {
            ModelUtils.estimateJoint(
                upper.x, upper.y, upper.z,
                lower.x, lower.y, lower.z,
                this.tempV2, armLength, this.tempV);
        } else {
            ModelUtils.worldToModel(jointPos, this.rotInfo, this.bodyYaw, this.isMainPlayer, this.tempV);
        }

        // invert joint dir, use it for up in the point at
        if (jointDown) {
            this.tempV2.mul(-1F);
        }

        float jointX = this.tempV.x;
        float jointY = this.tempV.y;
        float jointZ = this.tempV.z;

        // upper part rotation
        // dir
        this.tempV.set(jointX - upper.x, jointY - upper.y, jointZ - upper.z);

        ModelUtils.pointAtModel(this.tempV, this.tempV2, this.tempM);
        this.tempM.rotateLocalX(-this.xRot);
        ModelUtils.setRotation(upper, this.tempM, this.tempV);

        // lower part rotation
        // dir
        this.tempV.set(lower.x - jointX, lower.y - jointY, lower.z - jointZ);

        ModelUtils.pointAtModel(this.tempV, this.tempV2, this.tempM);

        if (arm != null && this.attackArm == arm) {
            ModelUtils.swingAnimation(lower, arm, -armLength * 0.5F, this.attackTime, this.isMainPlayer, this.tempM,
                this.tempV, this.tempV2);
        }

        this.tempM.rotateLocalX(-this.xRot);
        ModelUtils.setRotation(lower, this.tempM, this.tempV);
    }

    @Override
    public void copyPropertiesTo(HumanoidModel<T> model) {
        super.copyPropertiesTo(model);
        if (model instanceof HandModel handModel) {
            handModel.getLeftHand().copyFrom(this.leftHand);
            handModel.getRightHand().copyFrom(this.rightHand);
        }
    }

    @Override
    public void setAllVisible(boolean visible) {
        super.setAllVisible(visible);

        this.leftHand.visible = visible;
        this.rightHand.visible = visible;
        this.leftHandSleeve.visible = visible;
        this.rightHandSleeve.visible = visible;
    }

    @Override
    public ModelPart getLeftHand() {
        return this.leftHand;
    }

    @Override
    public ModelPart getRightHand() {
        return this.rightHand;
    }

    @Override
    public void hideLeftHand() {
        this.leftHand.visible = false;
        this.leftHandSleeve.visible = false;
    }

    @Override
    public void hideRightHand() {
        this.rightHand.visible = false;
        this.rightHandSleeve.visible = false;
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

        modelpart.translateAndRotate(poseStack);

        poseStack.translate(side == HumanoidArm.LEFT ? -0.0625F : 0.0625F, -0.65F, 0.0F);
        if (this.attackArm == side) {
            poseStack.translate(0.0F, 0.5F, 0.0F);
            poseStack.mulPose(Axis.XP.rotation(Mth.sin(this.attackTime * Mth.PI)));
            poseStack.translate(0.0F, -0.5F, 0.0F);
        }
    }
}
