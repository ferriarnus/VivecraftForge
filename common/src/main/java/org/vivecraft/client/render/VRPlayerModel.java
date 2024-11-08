package org.vivecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.*;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client.utils.ModelUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.MinecraftExtension;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.common.network.FBTMode;
import org.vivecraft.common.utils.MathUtils;

import java.lang.Math;

public class VRPlayerModel<T extends LivingEntity> extends PlayerModel<T> {
    public final ModelPart vrHMD;
    private final ModelPart cloak;
    protected VRPlayersClient.RotInfo rotInfo;
    protected float bodyYaw;
    protected boolean laying;
    public float layAmount;
    protected HumanoidArm attackArm = null;
    protected boolean isMainPlayer;
    private final Matrix3f bodyRot = new Matrix3f();

    // temp vec for most math
    protected final Vector3f tempV = new Vector3f();
    protected final Vector3f tempV2 = new Vector3f();
    // temp mat3 for rotations
    protected final Matrix3f tempM = new Matrix3f();

    public VRPlayerModel(ModelPart modelPart, boolean isSlim) {
        super(modelPart, isSlim);
        this.vrHMD = modelPart.getChild("vrHMD");
        this.cloak = modelPart.getChild("cloak");
    }

    public static MeshDefinition createMesh(CubeDeformation cubeDeformation, boolean slim) {
        MeshDefinition meshDefinition = PlayerModel.createMesh(cubeDeformation, slim);
        PartDefinition root = meshDefinition.getRoot();
        root.addOrReplaceChild("vrHMD", CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-3.5F, -6.0F, -7.5F,
                    7.0F, 4.0F, 5.0F, cubeDeformation),
            PartPose.ZERO);

        return meshDefinition;
    }

    @Override
    public void setupAnim(
        T player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        super.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        float partialTick = ((MinecraftExtension) Minecraft.getInstance()).vivecraft$getPartialTick();
        if (VRState.VR_RUNNING && player == Minecraft.getInstance().player) {
            this.isMainPlayer = true;
            this.rotInfo = VRPlayersClient.getMainPlayerRotInfo(player, partialTick);
        } else {
            this.rotInfo = VRPlayersClient.getInstance().getRotationsForPlayer(player.getUUID());
        }

        if (this.attackTime > 0F) {
            this.attackArm = player.swingingArm == InteractionHand.MAIN_HAND ?
                player.getMainArm() : player.getMainArm().getOpposite();
        } else {
            this.attackArm = null;
        }

        if (this.rotInfo == null) {
            return; //how
        }

        if (this.isMainPlayer) {
            this.bodyYaw = ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().getBodyYawRad();
        } else {
            this.bodyYaw = this.rotInfo.getBodyYawRad();
        }

        this.laying = this.swimAmount > 0.0F || player.isFallFlying();// && !player.isAutoSpinAttack());
        this.layAmount = player.isFallFlying() ? 1F : this.swimAmount;

        float progress = ModelUtils.getBendProgress(player, this.rotInfo);
        float heightOffset = 24F * progress;

        boolean swimming = (this.laying && player.isInWater()) || player.isFallFlying();
        boolean noLowerBodyAnimation = swimming || this.rotInfo.fbtMode == FBTMode.ARMS_ONLY;

        float xRot;
        if (swimming) {
            // in water also rotate around the view vector
            xRot = this.layAmount * (-Mth.HALF_PI - Mth.DEG_TO_RAD * player.getViewXRot(partialTick));
        } else {
            xRot = this.layAmount * -Mth.HALF_PI;
        }

        Quaternionf forward = new Quaternionf().rotationY(-this.bodyYaw);

        // rotate head
        this.tempM.set(this.rotInfo.headQuat)
            .rotateLocalY(this.bodyYaw + Mth.PI)
            .rotateLocalX(-xRot);
        ModelUtils.setRotation(this.head, this.tempM, this.tempV);
        ModelUtils.worldToModel(this.rotInfo.headPos, this.rotInfo, this.bodyYaw, this.tempV);

        // move head and body with bend
        if (swimming) {
            this.tempV.y = Mth.lerp(this.layAmount, heightOffset * (0.8F + 0.1F * this.layAmount), this.tempV.y);
            this.head.setPos(this.tempV.x, this.tempV.y, this.tempV.z + 3F);
        } else {
            this.head.setPos(this.tempV.x, heightOffset * (0.8F + 0.1F * this.layAmount), this.tempV.z + 3F);
        }

        this.body.setPos(this.head.x, this.head.y, this.head.z);

        // rotate body
        if (this.riding) {
            // when riding, rotate body to sitting position
            ModelUtils.pointModelAtModel(this.body, 0F, 14F, 2F, forward, this.bodyYaw, true, this.tempV,
                this.tempM);
        } else if (noLowerBodyAnimation) {
            // with only arms simply rotate the body in place
            this.body.setRotation(Mth.PI * (this.body.y / 24F), 0F, 0F);
        } else {
            // body/arm position with waist tracker
            // if there is a waist tracker, align the body to that
            ModelUtils.pointModelAtLocal(this.body, this.rotInfo.waistPos, this.rotInfo.waistQuat, this.rotInfo,
                this.bodyYaw, false, this.tempV, this.tempM);
            if (this.laying) {
                this.tempM.rotateLocalX(-xRot);
            }
            ModelUtils.setRotation(this.body, this.tempM, this.tempV);
            this.bodyRot.set(tempM);

            // offset arms
            this.tempM.transform(5F, 2F, 0F, this.tempV2);
            this.leftArm.x = this.body.x + this.tempV2.x;
            this.leftArm.y = this.body.y + this.tempV2.y;
            this.leftArm.z = this.body.z - this.tempV2.z;

            this.tempM.transform(-5F, 2F, 0F, this.tempV2);
            this.rightArm.x = this.body.x + this.tempV2.x;
            this.rightArm.y = this.body.y + this.tempV2.y;
            this.rightArm.z = this.body.z - this.tempV2.z;
        }

        if (this.laying && noLowerBodyAnimation) {
            float bodyXRot;
            if (swimming) {
                bodyXRot = -xRot;
            } else {
                float aboveGround = (heightOffset - 11F) / 13F;
                bodyXRot = progress * (Mth.PI - this.layAmount * Mth.HALF_PI * (1F +  0.3F * (1F - aboveGround)));
            }
            // lerp body rotation when swimming, to keep the model connected
            this.body.xRot = Mth.lerp(this.layAmount, this.body.xRot, bodyXRot);
        }

        float cosBodyRot = Mth.cos(this.body.xRot);

        if (this.riding || noLowerBodyAnimation) {
            // offset arms with body rotation
            this.leftArm.x = this.body.x + 5F;
            this.rightArm.x = this.body.x - 5F;
            this.leftArm.y = 2F * cosBodyRot + this.body.y;
            this.leftArm.z = this.body.z;

            this.rightArm.y = this.leftArm.y;
            this.rightArm.z = this.leftArm.z;
        }

        if (this.laying && noLowerBodyAnimation) {
            // adjust legs
            if (swimming) {
                this.tempV.set(0, 12, 0);
                this.tempV.rotateX(-xRot);
                this.leftLeg.y = this.body.y + this.tempV.y;
                this.leftLeg.z = this.body.z + this.tempV.z;
            } else {
                // move legs with bend
                float cosBodyRot2 = cosBodyRot * cosBodyRot;
                this.leftLeg.y += 10.25F - 2F * cosBodyRot2;
                this.leftLeg.z = this.body.z + 13F - cosBodyRot2 * 8F;
            }
            this.leftLeg.x += this.body.x;
            this.rightLeg.x += this.body.x;

            this.rightLeg.y = this.leftLeg.y;
            this.rightLeg.z = this.leftLeg.z;
        } else if (this.rotInfo.fbtMode != FBTMode.ARMS_ONLY) {
            // fbt leg position
            ModelUtils.worldToModel(this.rotInfo.waistPos, this.rotInfo, this.bodyYaw, this.tempV);

            this.tempV2.set(-1.9, -2, 0);
            this.rotInfo.waistQuat.transform(this.tempV2);
            ModelUtils.worldToModelDirection(this.tempV2, this.bodyYaw, this.tempV2);
            this.leftLeg.setPos(
                this.tempV.x + this.tempV2.x,
                this.tempV.y + this.tempV2.y,
                this.tempV.z + this.tempV2.z);

            this.tempV2.set(1.9, -2, 0);
            this.rotInfo.waistQuat.transform(this.tempV2);
            ModelUtils.worldToModelDirection(this.tempV2, this.bodyYaw, this.tempV2);
            this.rightLeg.setPos(
                this.tempV.x + this.tempV2.x,
                this.tempV.y + this.tempV2.y,
                this.tempV.z + this.tempV2.z);
        } else {
            this.leftLeg.x += this.body.x;
            this.rightLeg.x += this.body.x;
        }

        // regular positioning
        if (this.layAmount < 1.0F) {
            //this.cloak.z = 0.0F;
            //this.cloak.y = 0.0F;

            // move legs back with bend
            float newLegZ;
            if (!this.riding) {
                newLegZ = this.body.z + 10F * Mth.sin(this.body.xRot);
            } else {
                newLegZ = 2F;
            }

            if (this.rotInfo.fbtMode == FBTMode.ARMS_ONLY) {
                this.leftLeg.y = Mth.lerp(this.layAmount, 12F, this.leftLeg.y);
                this.leftLeg.z = Mth.lerp(this.layAmount, newLegZ, this.leftLeg.z);

                this.rightLeg.y = this.leftLeg.y;
                this.rightLeg.z = this.leftLeg.z;
            }
        }

        // arms/legs only when standing
        if (!this.rotInfo.seated) {
            if (this.getClass() == VRPlayerModel.class) {
                ModelPart actualLeftArm = this.leftArm;
                ModelPart actualRightArm = this.rightArm;
                if (this.rotInfo.reverse) {
                    actualLeftArm = this.rightArm;
                    actualRightArm = this.leftArm;
                }

                if (!this.slim) {
                    // offset so that the middle of arm points at the controller
                    this.rightArm.x -= 0.5F;
                    this.leftArm.x += 0.5F;
                }

                // right arm
                ModelUtils.pointModelAtLocal(actualRightArm, this.rotInfo.rightArmPos, this.rotInfo.rightArmQuat,
                    this.rotInfo, this.bodyYaw, false, this.tempV, this.tempM);
                if (this.attackArm == HumanoidArm.RIGHT) {
                    ModelUtils.attackAnimation(actualRightArm, HumanoidArm.RIGHT, this.attackTime, this.isMainPlayer,
                        this.tempM, this.tempV);
                }
                if (this.laying) {
                    this.tempM.rotateLocalX(-xRot);
                }
                ModelUtils.setRotation(actualRightArm, this.tempM, this.tempV);

                // left arm
                ModelUtils.pointModelAtLocal(actualLeftArm, this.rotInfo.leftArmPos, this.rotInfo.leftArmQuat,
                    this.rotInfo, this.bodyYaw, false, this.tempV, this.tempM);

                if (!this.slim) {
                    // undo previous offset, before calculating the gui position
                    this.rightArm.x += 0.5F;
                    this.leftArm.x -= 0.5F;
                }

                if (this.isMainPlayer && ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
                    ClientDataHolderVR.getInstance().vrSettings.shouldRenderModelArms)
                {
                    GuiHandler.guiRotation_playerModel.set3x3(this.tempM);
                    // ModelParts are rotated 90°
                    GuiHandler.guiRotation_playerModel.rotateX(-Mth.HALF_PI);
                    // undo body yaw
                    GuiHandler.guiRotation_playerModel.rotateLocalY(-this.bodyYaw - Mth.PI);

                    // arm vector
                    GuiHandler.guiRotation_playerModel.transformDirection(MathUtils.BACK, this.tempV)
                        .mul(0.75F * this.rotInfo.worldScale);

                    Vector3f shoulder = ModelUtils.modelToWorld(actualLeftArm.x, actualLeftArm.y, actualLeftArm.z,
                        this.rotInfo, this.bodyYaw, new Vector3f());
                    shoulder.add(this.tempV);

                    GuiHandler.guiPos_playerModel = player.getPosition(Minecraft.getInstance().getFrameTime())
                        .add(shoulder.x, shoulder.y, shoulder.z);
                }
                if (this.attackArm == HumanoidArm.LEFT) {
                    ModelUtils.attackAnimation(actualLeftArm, HumanoidArm.LEFT, this.attackTime, this.isMainPlayer,
                        this.tempM, this.tempV);
                }
                if (this.laying) {
                    this.tempM.rotateLocalX(-xRot);
                }
                ModelUtils.setRotation(actualLeftArm, this.tempM, this.tempV);
            }

            // legs only when not sitting
            if (!this.riding && !noLowerBodyAnimation &&
                (this.getClass() == VRPlayerModel.class || this.getClass() == VRPlayerModel_WithArms.class))
            {
                // vanilla walking animation on top
                float limbRotation = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;

                ModelUtils.pointModelAtLocal(this.rightLeg, this.rotInfo.rightFootPos, this.rotInfo.rightFootQuat,
                    this.rotInfo, this.bodyYaw, false, this.tempV, this.tempM);
                if (this.laying) {
                    this.tempM.rotateLocalX(-xRot);
                }
                this.tempM.rotateLocalX(limbRotation);
                ModelUtils.setRotation(this.rightLeg, this.tempM, this.tempV);

                ModelUtils.pointModelAtLocal(this.leftLeg, this.rotInfo.leftFootPos, this.rotInfo.leftFootQuat,
                    this.rotInfo, this.bodyYaw, false, this.tempV, this.tempM);
                if (this.laying) {
                    this.tempM.rotateLocalX(-xRot);
                }
                this.tempM.rotateLocalX(-limbRotation);
                ModelUtils.setRotation(this.leftLeg, this.tempM, this.tempV);
            }
        }

        this.cloak.setPos(0,0,0);

        if (this.layAmount > 0F) {
            if (noLowerBodyAnimation) {
                // with a waist tracker the rotation is already done before
                this.body.xRot += xRot;
            }

            ModelUtils.applySwimRotationOffset(player, xRot, this.tempV, this.tempV2,
                this.head, this.body,
                this.leftArm, this.rightArm,
                this.leftLeg, this.rightLeg);
        }

        if (this.riding || noLowerBodyAnimation) {
            this.bodyRot.rotationZYX(this.body.zRot, -this.body.yRot, -this.body.xRot);
        }

        if (this.isMainPlayer && (ClientDataHolderVR.getInstance().currentPass == RenderPass.LEFT ||
            ClientDataHolderVR.getInstance().currentPass == RenderPass.RIGHT ||
            ClientDataHolderVR.getInstance().currentPass == RenderPass.CENTER
        ))
        {
            this.leftArm.xScale = this.leftArm.zScale = this.rightArm.xScale = this.rightArm.zScale =
                ClientDataHolderVR.getInstance().vrSettings.playerModelArmsScale;
            this.body.xScale = this.body.zScale =
                ClientDataHolderVR.getInstance().vrSettings.playerModelBodyScale;
            this.leftLeg.xScale = this.leftLeg.zScale = this.rightLeg.xScale = this.rightLeg.zScale =
                ClientDataHolderVR.getInstance().vrSettings.playerModelLegScale;

        } else {
            this.leftArm.xScale = this.leftArm.zScale = this.rightArm.xScale = this.rightArm.zScale = 1.0F;
            this.body.xScale = this.body.zScale = 1.0F;
            this.leftLeg.xScale = this.leftLeg.zScale = this.rightLeg.xScale = this.rightLeg.zScale = 1.0F;
        }

        this.vrHMD.visible = true;

        this.vrHMD.copyFrom(this.head);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
    }

    public void renderHMD(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay) {
        this.vrHMD.render(poseStack, vertexConsumer, packedLight, packedOverlay);
    }

    public VRPlayersClient.RotInfo getRotInfo() {
        return this.rotInfo;
    }

    public Matrix3fc getBodyRot() {
        return this.bodyRot;
    }

    @Override
    protected ModelPart getArm(HumanoidArm side) {
        if (this.rotInfo != null && this.rotInfo.reverse) {
            return side == HumanoidArm.RIGHT ? this.leftArm : this.rightArm;
        } else {
            return side == HumanoidArm.LEFT ? this.leftArm : this.rightArm;
        }
    }

    @Override
    public void translateToHand(HumanoidArm side, PoseStack poseStack) {
        super.translateToHand(side, poseStack);

        float offset = 0.0F;
        if (this.rotInfo != null && this.rotInfo.reverse) {
            offset = side == HumanoidArm.RIGHT ? 0.1F : -0.1F;
        }

        poseStack.translate(offset, 0.0F, 0.0F);
        if (side == this.attackArm) {
            poseStack.translate(0.0F, 0.5F, 0.0F);
            poseStack.mulPose(Axis.XP.rotation((float) Math.sin(this.attackTime * Mth.PI)));
            poseStack.translate(0.0F, -0.5F, 0.0F);
        }
    }
}
