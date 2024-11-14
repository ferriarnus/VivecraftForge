package org.vivecraft.client.render.armor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;
import org.vivecraft.client.render.VRPlayerModel_WithArmsLegs;
import org.vivecraft.client.render.models.FeetModel;
import org.vivecraft.client.utils.ModelUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;

public class VRArmorModel_WithArmsLegs<T extends LivingEntity> extends VRArmorModel_WithArms<T> implements FeetModel {
    public final ModelPart leftFoot;
    public final ModelPart rightFoot;

    public VRArmorModel_WithArmsLegs(ModelPart root) {
        super(root);
        this.leftFoot = root.getChild("left_foot");
        this.rightFoot = root.getChild("right_foot");
        ModelUtils.textureHackUpper(this.leftLeg, this.leftFoot);
        ModelUtils.textureHackUpper(this.rightLeg, this.rightFoot);
    }

    public static MeshDefinition createBodyLayer(CubeDeformation cubeDeformation) {
        MeshDefinition meshDefinition = VRArmorModel_WithArms.createBodyLayer(cubeDeformation);
        PartDefinition partDefinition = meshDefinition.getRoot();

        boolean connected = ClientDataHolderVR.getInstance().vrSettings.playerLimbsConnected;
        int upperExtension = connected ? VRPlayerModel_WithArmsLegs.UPPER_EXTENSION : 0;
        int lowerExtension = connected ? VRPlayerModel_WithArmsLegs.LOWER_EXTENSION : 0;
        float lowerShrinkage = connected ? -0.05F : 0F;

        partDefinition.addOrReplaceChild("left_foot", CubeListBuilder.create()
                .texOffs(0, 23 - lowerExtension).mirror()
                .addBox(-2.0F, -5.0F - lowerExtension, -2.0F, 4.0F, 5.0F + lowerExtension, 4.0F, cubeDeformation.extend(lowerShrinkage - 0.1F)),
            PartPose.offset(1.9F, 24.0F, 0.0F));
        partDefinition.addOrReplaceChild("right_foot", CubeListBuilder.create()
                .texOffs(0, 23 - lowerExtension)
                .addBox(-2.0F, -5.0F - lowerExtension, -2.0F, 4.0F, 5.0F + lowerExtension, 4.0F, cubeDeformation.extend(lowerShrinkage - 0.1F)),
            PartPose.offset(-1.9F, 24.0F, 0.0F));

        partDefinition.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 16).mirror()
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F + upperExtension, 4.0F, cubeDeformation.extend(-0.1F)),
            PartPose.offset(1.9F, 12.0F, 0.0F));
        partDefinition.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 16)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F + upperExtension, 4.0F, cubeDeformation.extend(-0.1F)),
            PartPose.offset(-1.9F, 12.0F, 0.0F));
        return meshDefinition;
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return Iterables.concat(super.bodyParts(), ImmutableList.of(this.leftFoot, this.rightFoot));
    }

    @Override
    public ModelPart getLeftFoot() {
        return this.leftFoot;
    }

    @Override
    public ModelPart getRightFoot() {
        return this.rightFoot;
    }

    @Override
    public void setAllVisible(boolean visible) {
        super.setAllVisible(visible);

        this.leftFoot.visible = visible;
        this.rightFoot.visible = visible;
    }
}
