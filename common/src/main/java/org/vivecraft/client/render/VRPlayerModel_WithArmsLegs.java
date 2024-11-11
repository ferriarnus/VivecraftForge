package org.vivecraft.client.render;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import org.vivecraft.client.utils.ModelUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.common.utils.MathUtils;

public class VRPlayerModel_WithArmsLegs<T extends LivingEntity> extends VRPlayerModel_WithArms<T> {
    // thighs use the vanilla leg parts
    public ModelPart leftFoot;
    public ModelPart rightFoot;
    public ModelPart leftFootPants;
    public ModelPart rightFootPants;

    private final Vector3f tempV3 = new Vector3f();

    public VRPlayerModel_WithArmsLegs(ModelPart modelPart, boolean isSlim) {
        super(modelPart, isSlim);
        this.leftFoot = modelPart.getChild("left_foot");
        this.rightFoot = modelPart.getChild("right_foot");
        this.leftFootPants = modelPart.getChild("left_foot_pants");
        this.rightFootPants = modelPart.getChild("right_foot_pants");

        // copy textures
        textureHackUpper(this.leftLeg, this.leftFoot);
        textureHackUpper(this.rightLeg, this.rightFoot);
        textureHack(this.rightPants, this.leftFootPants);
        textureHack(this.leftPants, this.rightFootPants);
    }

    public static MeshDefinition createMesh(CubeDeformation cubeDeformation, boolean slim) {
        MeshDefinition meshDefinition = VRPlayerModel_WithArms.createMesh(cubeDeformation, slim);
        PartDefinition partDefinition = meshDefinition.getRoot();

        boolean connected = ClientDataHolderVR.getInstance().vrSettings.playerLimbsConnected;
        int upperExtension = connected ? 2 : 0;
        int lowerExtension = connected ? 2 : 0;
        float lowerShrinkage = connected ? -0.05F : 0F;

        // feet
        partDefinition.addOrReplaceChild("left_foot", CubeListBuilder.create()
                .texOffs(16, 55 - lowerExtension)
                .addBox(-2.0F, -5.0F - lowerExtension, -2.0F, 4.0F, 5.0F + lowerExtension, 4.0F, cubeDeformation.extend(lowerShrinkage)),
            PartPose.offset(1.9F, 19.0F, 0.0F));
        partDefinition.addOrReplaceChild("left_foot_pants", CubeListBuilder.create()
                .texOffs(0, 55 - lowerExtension)
                .addBox(-2.0F, -5.0F - lowerExtension, -2.0F, 4.0F, 5.0F + lowerExtension, 4.0F, cubeDeformation.extend(0.25F + lowerShrinkage)),
            PartPose.offset(1.9F, 19.0F, 0.0F));
        partDefinition.addOrReplaceChild("right_foot", CubeListBuilder.create()
                .texOffs(0, 23 - lowerExtension)
                .addBox(-2.0F, -5.0F - lowerExtension, -2.0F, 4.0F, 5.0F + lowerExtension, 4.0F, cubeDeformation.extend(lowerShrinkage)),
            PartPose.offset(-1.9F, 19.0F, 0.0F));
        partDefinition.addOrReplaceChild("right_foot_pants", CubeListBuilder.create()
                .texOffs(0, 39 - lowerExtension)
                .addBox(-2.0F, -5.0F - lowerExtension, -2.0F, 4.0F, 5.0F + lowerExtension, 4.0F, cubeDeformation.extend(0.25F + lowerShrinkage)),
            PartPose.offset(-1.9F, 19.0F, 0.0F));

        // thighs
        partDefinition.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(16, 48)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F + upperExtension, 4.0F, cubeDeformation),
            PartPose.offset(1.9F, 12.0F, 0.0F));
        partDefinition.addOrReplaceChild("left_pants", CubeListBuilder.create()
                .texOffs(0, 48)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F + upperExtension, 4.0F, cubeDeformation.extend(0.25f)),
            PartPose.offset(1.9F, 12.0F, 0.0F));
        partDefinition.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 16)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F + upperExtension, 4.0F, cubeDeformation),
            PartPose.offset(-1.9F, 12.0F, 0.0F));
        partDefinition.addOrReplaceChild("right_pants", CubeListBuilder.create()
                .texOffs(0, 32)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F + upperExtension, 4.0F, cubeDeformation.extend(0.25f)),
            PartPose.offset(-1.9F, 12.0F, 0.0F));
        return meshDefinition;
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return Iterables.concat(super.bodyParts(),
            ImmutableList.of(this.leftFoot, this.rightFoot, this.leftFootPants, this.rightFootPants));
    }

    @Override
    public void setupAnim(
        T player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        super.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        if (this.rotInfo == null) {
            return;
        }

        // left leg
        this.rotInfo.leftFootQuat.transform(MathUtils.BACK, this.tempV3);
        if (ClientDataHolderVR.getInstance().vrSettings.playerLimbsConnected) {
            positionConnectedLimb(this.leftLeg, this.leftFoot, this.rotInfo.leftFootPos, this.tempV3,
                this.rotInfo.leftFootQuat, this.rotInfo.leftKneePos, true, null);
        } else {
            positionSplitLimb(this.leftLeg, this.leftFoot, this.rotInfo.leftFootPos, this.tempV3,
                this.rotInfo.leftFootQuat, -Mth.HALF_PI, 0F, this.rotInfo.leftKneePos, true, 0F, null);
        }

        // right leg
        this.rotInfo.rightFootQuat.transform(MathUtils.BACK, this.tempV3);
        if (ClientDataHolderVR.getInstance().vrSettings.playerLimbsConnected) {
            positionConnectedLimb(this.rightLeg, this.rightFoot, this.rotInfo.rightFootPos, this.tempV3,
                this.rotInfo.rightFootQuat, this.rotInfo.rightKneePos, true, null);
        } else {
            positionSplitLimb(this.rightLeg, this.rightFoot, this.rotInfo.rightFootPos, this.tempV3,
                this.rotInfo.rightFootQuat, -Mth.HALF_PI, 0F, this.rotInfo.rightKneePos, true, 0F, null);
        }

        if (this.layAmount > 0F) {
            ModelUtils.applySwimRotationOffset(player, this.xRot, this.tempV, this.tempV2,
                this.leftLeg, this.rightLeg,
                this.leftFoot, this.rightFoot);
        }

        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftFootPants.copyFrom(this.leftFoot);
        this.rightFootPants.copyFrom(this.rightFoot);
        this.leftFootPants.visible = this.leftPants.visible;
        this.rightFootPants.visible = this.rightPants.visible;
    }

    @Override
    public void setAllVisible(boolean visible) {
        super.setAllVisible(visible);

        this.leftFoot.visible = visible;
        this.rightFoot.visible = visible;
        this.leftFootPants.visible = visible;
        this.rightFootPants.visible = visible;
    }
}
