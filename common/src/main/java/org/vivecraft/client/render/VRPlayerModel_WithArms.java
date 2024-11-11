package org.vivecraft.client.render;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
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
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import org.vivecraft.client.utils.ModelUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;
import org.vivecraft.mod_compat_vr.sodium.SodiumHelper;

public class VRPlayerModel_WithArms<T extends LivingEntity> extends VRPlayerModel<T> {
    // shoulders use the vanilla arm parts
    public ModelPart leftHand;
    public ModelPart rightHand;
    public ModelPart leftHandSleeve;
    public ModelPart rightHandSleeve;

    public VRPlayerModel_WithArms(ModelPart modelPart, boolean isSlim) {
        super(modelPart, isSlim);
        this.leftHandSleeve = modelPart.getChild("left_hand_sleeve");
        this.rightHandSleeve = modelPart.getChild("right_hand_sleeve");
        this.rightHand = modelPart.getChild("right_hand");
        this.leftHand = modelPart.getChild("left_hand");

        // copy textures
        textureHack(this.leftArm, this.leftHand);
        textureHack(this.rightArm, this.rightHand);
        textureHack(this.rightSleeve, this.rightHandSleeve);
        textureHack(this.leftSleeve, this.leftHandSleeve);
    }

    /**
     * copies the bottom face texture from the {@code source} ModelPart to the top/bottom face of the {@code target} ModelPart
     * @param source ModelPart to copy the top/bottom face from
     * @param target ModelPart to copy the top/bottom face to
     */
    protected void textureHack(ModelPart source, ModelPart target) {
        // some mods remove the base parts
        if (source.cubes.isEmpty()) return;

        copyUV(source.cubes.get(0).polygons[1], target.cubes.get(0).polygons[1]);
        copyUV(source.cubes.get(0).polygons[1], target.cubes.get(0).polygons[0]);

        // sodium has custom internal ModelPart geometry which also needs to be modified
        if (SodiumHelper.isLoaded()) {
            SodiumHelper.copyModelCuboidUV(source, target, 3, 3);
            SodiumHelper.copyModelCuboidUV(source, target, 3, 2);
        }
    }

    /**
     * copies the top/bottom face texture from the {@code source} ModelPart to the {@code target} ModelPart
     * @param source ModelPart to copy the top/bottom face from
     * @param target ModelPart to copy the top/bottom face to
     */
    protected void textureHackUpper(ModelPart source, ModelPart target) {
        // some mods remove the base parts
        if (source.cubes.isEmpty()) return;

        // set bottom of target
        copyUV(source.cubes.get(0).polygons[1], target.cubes.get(0).polygons[1]);
        // set those to the top of the source
        copyUV(source.cubes.get(0).polygons[0], target.cubes.get(0).polygons[0]);
        copyUV(source.cubes.get(0).polygons[0], source.cubes.get(0).polygons[1]);

        // sodium has custom internal ModelPart geometry which also needs to be modified
        if (SodiumHelper.isLoaded()) {
            SodiumHelper.copyModelCuboidUV(source, target, 3, 3);
            SodiumHelper.copyModelCuboidUV(source, target, 2, 2);
            SodiumHelper.copyModelCuboidUV(source, source, 2, 3);
        }
    }

    /**
     * copies the UV from the {@code source} Polygon to the {@code target} Polygon
     * @param source Polygon to copy the UV from
     * @param target Polygon to copy the UV to
     */
    private void copyUV(Polygon source, Polygon target) {
        for (int i = 0; i < source.vertices.length; i++) {
            Vertex newVertex = new Vertex(target.vertices[i].pos, source.vertices[i].u, source.vertices[i].v);
            // Optifine has custom internal polygon data which also needs to be modified
            if (OptifineHelper.isOptifineLoaded()) {
                OptifineHelper.copyRenderPositions(target.vertices[i], newVertex);
            }
            target.vertices[i] = newVertex;
        }
    }

    public static MeshDefinition createMesh(CubeDeformation cubeDeformation, boolean slim) {
        MeshDefinition meshDefinition = VRPlayerModel.createMesh(cubeDeformation, slim);
        PartDefinition partDefinition = meshDefinition.getRoot();
        boolean connected = ClientDataHolderVR.getInstance().vrSettings.playerLimbsConnected;
        int upperExtension = connected ? 3 : 0;
        int lowerExtension = connected ? 2 : 0;
        float lowerShrinkage = connected ? -0.05F : 0F;

        if (slim) {
            partDefinition.addOrReplaceChild("left_hand", CubeListBuilder.create()
                    .texOffs(32, 55 - lowerExtension)
                    .addBox(-1.5F, -5.0F - lowerExtension, -2.0F, 3.0F, 5.0F +  lowerExtension, 4.0F, cubeDeformation.extend(lowerShrinkage)),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("left_hand_sleeve", CubeListBuilder.create()
                    .texOffs(48, 55 - lowerExtension)
                    .addBox(-1.5F, -5.0F - lowerExtension, -2.0F, 3.0F, 5.0F +  lowerExtension, 4.0F, cubeDeformation.extend(0.25f + lowerShrinkage)),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("right_hand", CubeListBuilder.create()
                    .texOffs(40, 23 - lowerExtension)
                    .addBox(-1.5F, -5.0F - lowerExtension, -2.0F, 3.0F, 5.0F + lowerExtension, 4.0F, cubeDeformation.extend(lowerShrinkage)),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("right_hand_sleeve", CubeListBuilder.create()
                    .texOffs(40, 39 - lowerExtension)
                    .addBox(-1.5F, -5.0F - lowerExtension, -2.0F, 3.0F, 5.0F +  lowerExtension, 4.0F, cubeDeformation.extend(0.25f + lowerShrinkage)),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("left_arm", CubeListBuilder.create()
                    .texOffs(32, 48)
                    .addBox(-1.0F, -2.0F, -2.0F, 3.0F, 5.0F + upperExtension, 4.0F, cubeDeformation),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("left_sleeve", CubeListBuilder.create()
                    .texOffs(48, 48)
                    .addBox(-1.0F, -2.0F, -2.0F, 3.0F, 5.0F + upperExtension, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("right_arm", CubeListBuilder.create()
                    .texOffs(40, 16)
                    .addBox(-2.0F, -2.0F, -2.0F, 3.0F, 5.0F + upperExtension, 4.0F, cubeDeformation),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
            partDefinition.addOrReplaceChild("right_sleeve", CubeListBuilder.create()
                    .texOffs(40, 32)
                    .addBox(-2.0F, -2.0F, -2.0F, 3.0F, 5.0F + upperExtension, 4.0F, cubeDeformation.extend(0.25f)),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
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
        if (player == Minecraft.getInstance().player &&
            (ClientDataHolderVR.getInstance().currentPass == RenderPass.LEFT ||
                ClientDataHolderVR.getInstance().currentPass == RenderPass.RIGHT ||
                ClientDataHolderVR.getInstance().currentPass == RenderPass.CENTER
            ))
        {
            limbScale = ClientDataHolderVR.getInstance().vrSettings.playerModelArmsScale;
        }

        float offset = (this.slim ? 0.5F : 1F) * limbScale * (this.rotInfo.reverse ? -1F : 1F);

        // left arm
        if (ClientDataHolderVR.getInstance().vrSettings.playerLimbsConnected) {
            positionConnectedLimb(actualLeftShoulder, actualLeftHand, this.rotInfo.leftArmPos, this.rotInfo.leftArmRot,
                this.rotInfo.leftArmQuat, this.rotInfo.leftElbowPos, false, HumanoidArm.LEFT);

            this.tempM.transform(offset, 0, 0, this.tempV);
            actualLeftHand.x += this.tempV.x;
            actualLeftHand.y += this.tempV.y;
            actualLeftHand.z += this.tempV.z;
        } else {
            positionSplitLimb(actualLeftShoulder, actualLeftHand, this.rotInfo.leftArmPos, this.rotInfo.leftArmRot,
                this.rotInfo.leftArmQuat, 0F, offset, this.rotInfo.leftElbowPos, false, -3F, HumanoidArm.LEFT);
        }

        if (this.isMainPlayer && ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
            ClientDataHolderVR.getInstance().vrSettings.shouldRenderModelArms)
        {
            if (ClientDataHolderVR.getInstance().vrSettings.playerLimbsConnected) {
                // tempM is in model space so that doesn't work directly
                GuiHandler.GUI_ROTATION_PLAYER_MODEL.rotationZYX(actualLeftHand.zRot, -actualLeftHand.yRot,
                    -actualLeftHand.xRot);
            } else {
                GuiHandler.GUI_ROTATION_PLAYER_MODEL.set3x3(this.tempM);
            }
            // ModelParts are rotated 90°
            GuiHandler.GUI_ROTATION_PLAYER_MODEL.rotateX(-Mth.HALF_PI);
            // undo body yaw
            GuiHandler.GUI_ROTATION_PLAYER_MODEL.rotateLocalY(-this.bodyYaw - Mth.PI);

            ModelUtils.modelToWorld(actualLeftHand.x, actualLeftHand.y, actualLeftHand.z,
                this.rotInfo, this.bodyYaw, this.tempV);

            GuiHandler.GUI_POS_PLAYER_MODEL = player.getPosition(Minecraft.getInstance().getFrameTime())
                .add(this.tempV.x, this.tempV.y, this.tempV.z);
        }

        // right arm
        if (ClientDataHolderVR.getInstance().vrSettings.playerLimbsConnected) {
            positionConnectedLimb(actualRightShoulder, actualRightHand, this.rotInfo.rightArmPos,
                this.rotInfo.rightArmRot, this.rotInfo.rightArmQuat, this.rotInfo.rightElbowPos, false,
                HumanoidArm.RIGHT);

            this.tempM.transform(-offset, 0, 0, this.tempV);
            actualRightHand.x += this.tempV.x;
            actualRightHand.y += this.tempV.y;
            actualRightHand.z += this.tempV.z;
        } else {
            positionSplitLimb(actualRightShoulder, actualRightHand, this.rotInfo.rightArmPos, this.rotInfo.rightArmRot,
                this.rotInfo.rightArmQuat, 0F, -offset, this.rotInfo.rightElbowPos, false, -3F, HumanoidArm.RIGHT);
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
        this.leftHandSleeve.visible = this.leftSleeve.visible;
        this.rightHandSleeve.visible = this.rightSleeve.visible;
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
     * @param lowerRotPoint Z offset to the point the lower body part should be rotated about for the swing animation
     * @param arm arm this is positioning, to check if the swing animation should be applied
     */
    protected void positionSplitLimb(
        ModelPart upper, ModelPart lower, Vector3fc lowerPos, Vector3fc lowerDir, Quaternionfc lowerRot, float lowerXRot,
        float lowerXOffset, Vector3fc jointPos, boolean jointDown, float lowerRotPoint, HumanoidArm arm)
    {
        // place lower directly at the lower point
        ModelUtils.worldToModel(lowerPos, this.rotInfo, this.bodyYaw, this.tempV);
        lower.setPos(this.tempV.x, this.tempV.y, this.tempV.z);

        // jiont estimation
        if (jointPos == null) {
            // point the elbow away from the hand direction
            // arm dir
            this.tempV.sub(upper.x, upper.y, upper.z);

            // hand direction
            ModelUtils.worldToModelDirection(lowerDir, this.bodyYaw, this.tempV2);
            if (!jointDown) {
                this.tempV2.mul(-1F);
            }

            // calculate the vector perpendicular to the arm dir
            float dot = this.tempV.dot(this.tempV2) / this.tempV.dot(this.tempV);
            this.tempV.mul(dot);
            this.tempV2.sub(this.tempV, this.tempV).normalize();
            this.tempM.transform(MathUtils.RIGHT, this.tempV).mul(lowerXOffset);
            MathUtils.RIGHT.mul(lowerXOffset, this.tempV);
            ModelUtils.estimateJoint(
                upper.x, upper.y, upper.z,
                lower.x + this.tempV.x, lower.y + this.tempV.y, lower.z + this.tempV.z,
                this.tempV2, 12.0F, this.tempV);
        } else {
            ModelUtils.worldToModel(jointPos, this.rotInfo, this.bodyYaw, this.tempV);
        }

        // upper position and rotation
        ModelUtils.pointModelAtModel(upper, this.tempV.x, this.tempV.y, this.tempV.z,
            this.tempV, this.tempV2, this.tempM);

        this.tempM.rotateLocalX(-this.xRot);
        ModelUtils.setRotation(upper, this.tempM, this.tempV);

        // lower rotation
        ModelUtils.toModelDir(this.bodyYaw, lowerRot, this.tempM);

        armSwing(lower, arm, lowerRotPoint, false);

        this.tempM.rotateLocalX(-this.xRot + lowerXRot);
        ModelUtils.setRotation(lower, this.tempM, this.tempV);
    }

    /**
     * positions the hand/foot and shoulder/thigh to point at the elbow/knee
     * @param upper upper body part (shoulder/thigh)
     * @param lower lower body part (hand/foot)
     * @param lowerPos player space position the lower body part should be at
     * @param lowerDir player space direction the lower body part should account for
     * @param lowerRot direction the lower body part should face
     * @param jointPos elbow/knee position, if {@code null} a middle point will be estimated
     * @param jointDown if the estimated joint should prefer up/forward or down/back
     * @param arm arm this is positioning, to check if the swing animation should be applied
     */
    protected void positionConnectedLimb(
        ModelPart upper, ModelPart lower, Vector3fc lowerPos, Vector3fc lowerDir, Quaternionfc lowerRot,
        Vector3fc jointPos, boolean jointDown, HumanoidArm arm)
    {
        // position lower
        ModelUtils.worldToModel(lowerPos, this.rotInfo, this.bodyYaw, this.tempV);
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

        // hand direction
        ModelUtils.worldToModelDirection(lowerDir, this.bodyYaw, this.tempV2);
        if (!jointDown) {
            this.tempV2.mul(-1F);
        }

        // calculate the vector perpendicular to the arm dir
        float dot = this.tempV.dot(this.tempV2) / this.tempV.dot(this.tempV);
        this.tempV.mul(dot);
        this.tempV2.sub(this.tempV, this.tempV).normalize();

        float jointDirX = this.tempV.x;
        float jointDirY = this.tempV.y;
        float jointDirZ = this.tempV.z;

        // get joint
        if (jointPos == null) {
            ModelUtils.estimateJoint(
                upper.x, upper.y, upper.z,
                lower.x, lower.y, lower.z,
                this.tempV, armLength, this.tempV2);
        } else {
            ModelUtils.worldToModel(jointPos, this.rotInfo, this.bodyYaw, this.tempV2);
        }
        float jointX = this.tempV2.x;
        float jointY = this.tempV2.y;
        float jointZ = this.tempV2.z;

        // upper part rotation
        ModelUtils.pointModelAtModel(upper, jointX, jointY, jointZ, this.tempV, this.tempV2, this.tempM);
        // dir
        this.tempV.set(jointX - upper.x, jointY - upper.y, jointZ - upper.z);
        // up
        if (length > armLength) {
            this.tempV2.set(jointDirX, jointDirY, jointDirZ);
        } else {
            this.tempV2.set(jointX - lower.x, jointY - lower.y, jointZ - lower.z);
        }

        ModelUtils.pointAtModel(this.tempV, this.tempV2, this.tempM);
        this.tempM.rotateLocalX(-this.xRot);
        ModelUtils.setModelRotation(upper, this.tempM, this.tempV);

        // upper part rotation
        // dir
        this.tempV.set(lower.x - jointX, lower.y - jointY, lower.z - jointZ);
        // up
        if (length > armLength) {
            this.tempV2.set(jointDirX, jointDirY, jointDirZ);
        } else {
            this.tempV2.set(jointX - upper.x, jointY - upper.y, jointZ - upper.z);
        }

        ModelUtils.pointAtModel(this.tempV, this.tempV2, this.tempM);

        armSwing(lower, arm, -armLength * 0.5F, true);

        this.tempM.rotateLocalX(-this.xRot);
        ModelUtils.setModelRotation(lower, this.tempM, this.tempV);
    }

    /**
     *
     * @param lower
     * @param arm
     * @param offset
     * @param modelSpace
     */
    private void armSwing(ModelPart lower, HumanoidArm arm, float offset, boolean modelSpace) {
        if (arm != null && this.attackArm == arm) {
            // need to get the pre and post rotation point, to offset the modelPart correctly
            this.tempM.transform(0,  offset, 0, this.tempV2);

            //TODO FBT invert x and Y rot for modelspace
            ModelUtils.swingAnimation(arm, this.attackTime, this.isMainPlayer, this.tempM, this.tempV);
            lower.x -= this.tempV.x;
            lower.y -= this.tempV.y;
            lower.z -= this.tempV.z * (modelSpace ? 1F : -1F);
            this.tempM.transform(0,  offset, 0, this.tempV);

            // apply the offset
            lower.x += this.tempV2.x - this.tempV.x;
            lower.y += this.tempV2.y - this.tempV.y;
            lower.z += (this.tempV2.z - this.tempV.z) * (modelSpace ? 1F : -1F);
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
