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
import org.vivecraft.client.utils.ClientUtils;
import org.vivecraft.client.utils.ModelUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.network.FBTMode;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.mod_compat_vr.ShadersHelper;

import java.lang.Math;

public class VRPlayerModel<T extends LivingEntity> extends PlayerModel<T> {
    public final ModelPart vrHMD;
    private final ModelPart cloak;

    protected VRPlayersClient.RotInfo rotInfo;
    protected float bodyYaw;
    protected boolean laying;
    protected float xRot;
    protected float layAmount;
    protected HumanoidArm attackArm = null;
    protected HumanoidArm mainArm = HumanoidArm.RIGHT;
    protected boolean isMainPlayer;
    protected float bodyScale;
    protected float armScale;
    protected float legScale;

    private final Matrix3f bodyRot = new Matrix3f();

    // temp vec for most math
    protected final Vector3f tempV = new Vector3f();
    protected final Vector3f tempV2 = new Vector3f();
    // temp mat3 for rotations
    protected final Matrix3f tempM = new Matrix3f();

    public VRPlayerModel(ModelPart root, boolean isSlim) {
        super(root, isSlim);
        this.vrHMD = root.getChild("vrHMD");
        this.cloak = root.getChild("cloak");
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
        float partialTick = ClientUtils.getCurrentPartialTick();
        this.isMainPlayer = VRState.VR_RUNNING && player == Minecraft.getInstance().player;

        this.rotInfo = VRPlayersClient.getInstance().getRotationsForPlayer(player.getUUID());

        if (this.rotInfo == null) {
            return; //how
        }

        this.mainArm = this.rotInfo.reverse ? HumanoidArm.LEFT : HumanoidArm.RIGHT;

        if (this.attackTime > 0F) {
            // we ignore the vanilla main arm setting
            this.attackArm = player.swingingArm == InteractionHand.MAIN_HAND ?
                HumanoidArm.RIGHT : HumanoidArm.LEFT;
            if (this.rotInfo.reverse) {
                this.attackArm = this.attackArm.getOpposite();
            }
        } else {
            this.attackArm = null;
        }

        if (this.isMainPlayer) {
            this.bodyYaw = ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().getBodyYawRad();
        } else {
            this.bodyYaw = this.rotInfo.getBodyYawRad();
        }

        this.laying = this.swimAmount > 0.0F || player.isFallFlying();// && !player.isAutoSpinAttack());
        this.layAmount = player.isFallFlying() ? 1F : this.swimAmount;

        boolean swimming = (this.laying && player.isInWater()) || player.isFallFlying() ;
        boolean noLowerBodyAnimation = swimming || this.rotInfo.fbtMode == FBTMode.ARMS_ONLY;

        this.bodyScale = 1F;
        this.armScale = 1F;
        this.legScale = 1F;

        if (this.isMainPlayer && RenderPass.isFirstPerson(ClientDataHolderVR.getInstance().currentPass) &&
            !ShadersHelper.isRenderingShadows())
        {
            this.bodyScale = ClientDataHolderVR.getInstance().vrSettings.playerModelBodyScale;
            this.armScale = ClientDataHolderVR.getInstance().vrSettings.playerModelArmsScale;
            this.legScale = ClientDataHolderVR.getInstance().vrSettings.playerModelLegScale;
        }

        // scale the offset with the body and arm scale, to keep them attached
        float sideOffset = 4F * this.bodyScale + this.armScale;

        if (swimming) {
            // in water also rotate around the view vector
            this.xRot = this.layAmount * (-Mth.HALF_PI - Mth.DEG_TO_RAD * player.getViewXRot(partialTick));
        } else {
            this.xRot = this.layAmount * -Mth.HALF_PI;
        }

        // head pivot
        if (!swimming) {
            this.rotInfo.headQuat.transform(0F, -0.2F, 0.1F, this.tempV2);
            if (this.isMainPlayer) {
                this.tempV2.mul(this.rotInfo.worldScale);
            }
        } else {
            // no pivot offset when swimming
            this.tempV2.zero();
        }
        this.tempV2.add(this.rotInfo.headPos);

        float progress = ModelUtils.getBendProgress(player, this.rotInfo, this.tempV2);
        float heightOffset = 22F * progress;

        // rotate head
        this.tempM.set(this.rotInfo.headQuat)
            .rotateLocalY(this.bodyYaw + Mth.PI)
            .rotateLocalX(-this.xRot);
        ModelUtils.setRotation(this.head, this.tempM, this.tempV);
        ModelUtils.worldToModel(player, this.tempV2, this.rotInfo, this.bodyYaw, this.isMainPlayer,
            this.tempV);

        if (swimming) {
            // move the head in front of the body when swimming
            this.tempV.z += 3F;
        }

        // move head and body with bend
        this.head.setPos(this.tempV.x, heightOffset, this.tempV.z);
        this.body.setPos(this.head.x, this.head.y, this.head.z);

        // rotate body
        if (this.riding) {
            // when riding, rotate body to sitting position
            ModelUtils.pointModelAtModelForward(this.body, 0F, 14F, 2F + heightOffset, this.tempV, this.tempV2, this.tempM);
            this.tempM.rotateLocalX(-this.xRot);
            ModelUtils.setRotation(this.body, this.tempM, this.tempV);
        } else if (noLowerBodyAnimation) {
            // with only arms simply rotate the body in place
            this.body.setRotation(
                Mth.PI * (this.body.y / 22F) * (this instanceof VRPlayerModel_WithArmsLegs ? 0.5F : 1F), 0F, 0F);
            if (this.laying) {
                float bodyXRot;
                if (swimming) {
                    bodyXRot = -this.xRot;
                } else {
                    float aboveGround = (heightOffset - 11F) / 11F;
                    bodyXRot = progress * (Mth.PI - Mth.HALF_PI * (1F + 0.3F * (1F - aboveGround)));
                }
                // lerp body rotation when swimming, to keep the model connected
                this.body.xRot = Mth.lerp(this.layAmount, this.body.xRot, bodyXRot);
            }
        } else {
            // body/arm position with waist tracker
            // if there is a waist tracker, align the body to that
            ModelUtils.pointModelAtLocal(player, this.body, this.rotInfo.waistPos, this.rotInfo.waistQuat, this.rotInfo,
                this.bodyYaw, this.isMainPlayer, this.tempV, this.tempV2, this.tempM);

            // offset arms
            this.tempM.transform(sideOffset, 2F, 0F, this.tempV2);
            this.leftArm.x = this.body.x + this.tempV2.x;
            this.leftArm.y = this.body.y + this.tempV2.y;
            this.leftArm.z = this.body.z - this.tempV2.z;

            this.tempM.transform(-sideOffset, 2F, 0F, this.tempV2);
            this.rightArm.x = this.body.x + this.tempV2.x;
            this.rightArm.y = this.body.y + this.tempV2.y;
            this.rightArm.z = this.body.z - this.tempV2.z;

            this.tempM.rotateLocalX(-this.xRot);
            ModelUtils.setRotation(this.body, this.tempM, this.tempV);
            this.bodyRot.set(this.tempM);
        }

        float cosBodyRot = Mth.cos(this.body.xRot);

        if (this.riding || noLowerBodyAnimation) {
            // offset arms with body rotation
            this.leftArm.x = this.body.x + sideOffset;
            this.rightArm.x = this.body.x - sideOffset;
            this.leftArm.y = 2F * cosBodyRot + this.body.y;
            this.leftArm.z = this.body.z;

            this.rightArm.y = this.leftArm.y;
            this.rightArm.z = this.leftArm.z;
        }

        this.leftLeg.x = 1.9F;
        this.rightLeg.x = -1.9F;

        if (this.riding) {
            this.leftLeg.z = heightOffset;
            this.rightLeg.z = this.leftLeg.z;
        } else if (this.laying && noLowerBodyAnimation) {
            // adjust legs
            if (swimming) {
                this.tempV.set(0, 12, 0);
                this.tempV.rotateX(-this.xRot);
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
            ModelUtils.worldToModel(player, this.rotInfo.waistPos, this.rotInfo, this.bodyYaw, this.isMainPlayer,
                this.tempV);

            this.tempV2.set(-1.9F, -2F, 0F);
            this.rotInfo.waistQuat.transform(this.tempV2);
            ModelUtils.worldToModelDirection(this.tempV2, this.bodyYaw, this.tempV2);
            this.leftLeg.setPos(
                this.tempV.x + this.tempV2.x,
                this.tempV.y + this.tempV2.y,
                this.tempV.z + this.tempV2.z);

            this.tempV2.set(1.9F, -2F, 0F);
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
        if (!this.riding && this.layAmount < 1.0F && this.rotInfo.fbtMode == FBTMode.ARMS_ONLY) {
            // move legs back with bend
            float newLegY = 12F;
            float newLegZ = this.body.z + 10F * Mth.sin(this.body.xRot);
            if (this instanceof VRPlayerModel_WithArmsLegs) {
                newLegY += 10F * Mth.sin(this.body.xRot);
            }

            this.leftLeg.y = Mth.lerp(this.layAmount, newLegY, this.leftLeg.y);
            this.leftLeg.z = Mth.lerp(this.layAmount, newLegZ, this.leftLeg.z);

            this.rightLeg.y = this.leftLeg.y;
            this.rightLeg.z = this.leftLeg.z;
        }

        // arms/legs only when standing
        if (!this.rotInfo.seated || this.isMainPlayer) {
            if (this.getClass() == VRPlayerModel.class &&
                this.rotInfo.leftArmPos.distanceSquared(this.rotInfo.rightArmPos) > 0.0F)
            {
                ModelPart offHand = this.rotInfo.reverse ? this.rightArm : this.leftArm;
                ModelPart mainHand = this.rotInfo.reverse ? this.leftArm : this.rightArm;

                // rotation offset, since the rotation point isn't in the center.
                // this rotates the arm 0.5 or 1 pixels at full arm distance, so that the hand matches up with the center
                float offset = (this.slim ? Mth.PI * 0.016F : Mth.PI * 0.032F) * this.armScale;

                // main hand
                ModelUtils.pointModelAtLocal(player, mainHand, this.rotInfo.rightArmPos, this.rotInfo.rightArmQuat,
                    this.rotInfo, this.bodyYaw, this.isMainPlayer, this.tempV, this.tempV2, this.tempM);

                float controllerDist = this.tempV.length();

                if (!ClientDataHolderVR.getInstance().vrSettings.playerLimbsLimit && controllerDist > 10F) {
                    this.tempV.normalize().mul(controllerDist - 10F);
                    mainHand.x += this.tempV.x;
                    mainHand.y += this.tempV.y;
                    mainHand.z += this.tempV.z;
                    this.tempM.rotateZ(-offset);
                } else {
                    // reduce correction angle with distance
                    this.tempM.rotateZ(-offset * Math.min(10F / controllerDist, 1F));
                }

                if (ClientDataHolderVR.getInstance().vrSettings.playerArmAnim && this.attackArm == this.mainArm) {
                    ModelUtils.swingAnimation(this.attackArm, this.attackTime, this.isMainPlayer, this.tempM,
                        this.tempV);
                    mainHand.x -= this.tempV.x;
                    mainHand.y -= this.tempV.y;
                    mainHand.z += this.tempV.z;
                }
                this.tempM.rotateLocalX(-this.xRot);
                ModelUtils.setRotation(mainHand, this.tempM, this.tempV);

                // offhand
                ModelUtils.pointModelAtLocal(player, offHand, this.rotInfo.leftArmPos, this.rotInfo.leftArmQuat,
                    this.rotInfo, this.bodyYaw, this.isMainPlayer, this.tempV, this.tempV2, this.tempM);

                controllerDist = this.tempV.length();

                if (!ClientDataHolderVR.getInstance().vrSettings.playerLimbsLimit && controllerDist > 10F) {
                    this.tempV.normalize().mul(controllerDist - 10F);
                    offHand.x += this.tempV.x;
                    offHand.y += this.tempV.y;
                    offHand.z += this.tempV.z;
                    this.tempM.rotateZ(offset);
                } else {
                    // reduce correction angle with distance
                    this.tempM.rotateZ(offset * Math.min(10F / controllerDist, 1F));
                }

                if (ClientDataHolderVR.getInstance().vrSettings.playerArmAnim && this.attackArm != this.mainArm) {
                    ModelUtils.swingAnimation(this.attackArm, this.attackTime, this.isMainPlayer, this.tempM,
                        this.tempV);
                    offHand.x -= this.tempV.x;
                    offHand.y -= this.tempV.y;
                    offHand.z += this.tempV.z;
                }

                if (this.isMainPlayer && ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
                    ClientDataHolderVR.getInstance().vrSettings.modelArmsMode != VRSettings.ModelArmsMode.OFF)
                {
                    GuiHandler.GUI_ROTATION_PLAYER_MODEL.set3x3(this.tempM);
                    // ModelParts are rotated 90°
                    GuiHandler.GUI_ROTATION_PLAYER_MODEL.rotateX(-Mth.HALF_PI);
                    // undo body yaw
                    GuiHandler.GUI_ROTATION_PLAYER_MODEL.rotateLocalY(-this.bodyYaw - Mth.PI);

                    // arm vector
                    GuiHandler.GUI_ROTATION_PLAYER_MODEL.transformDirection(MathUtils.BACK, this.tempV)
                        .mul(0.584F * this.rotInfo.worldScale);

                    ModelUtils.modelToWorld(player, offHand.x, offHand.y, offHand.z, this.rotInfo, this.bodyYaw, true,
                        this.isMainPlayer, this.tempV2);
                    this.tempV2.add(this.tempV);

                    GuiHandler.GUI_POS_PLAYER_MODEL = player.getPosition(ClientUtils.getCurrentPartialTick())
                        .add(this.tempV2.x, this.tempV2.y, this.tempV2.z);
                }
                this.tempM.rotateLocalX(-this.xRot);
                ModelUtils.setRotation(offHand, this.tempM, this.tempV);
            }

            // legs only when not sitting
            if (!this.riding && !noLowerBodyAnimation && !(this instanceof VRPlayerModel_WithArmsLegs)) {
                float limbRotation = 0F;
                if (ClientDataHolderVR.getInstance().vrSettings.playerWalkAnim) {
                    // vanilla walking animation on top
                    limbRotation = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
                }

                ModelUtils.pointModelAtLocal(player, this.rightLeg, this.rotInfo.rightFootPos,
                    this.rotInfo.rightFootQuat, this.rotInfo, this.bodyYaw, this.isMainPlayer, this.tempV,
                    this.tempV2, this.tempM);
                this.tempM.rotateLocalX(limbRotation - this.xRot);
                ModelUtils.setRotation(this.rightLeg, this.tempM, this.tempV);

                ModelUtils.pointModelAtLocal(player, this.leftLeg, this.rotInfo.leftFootPos, this.rotInfo.leftFootQuat,
                    this.rotInfo, this.bodyYaw, this.isMainPlayer, this.tempV, this.tempV2, this.tempM);
                this.tempM.rotateLocalX(-limbRotation - this.xRot);
                ModelUtils.setRotation(this.leftLeg, this.tempM, this.tempV);
            }
        }

        // we do the positioning in CapeLayerMixin
        this.cloak.setPos(0,0,0);

        if (this.layAmount > 0F) {
            if (noLowerBodyAnimation) {
                // with a waist tracker the rotation is already done before
                this.body.xRot += this.xRot;
            }

            if (this.getClass() == VRPlayerModel.class) {
                ModelUtils.applySwimRotationOffset(player, this.xRot, this.tempV, this.tempV2,
                    this.head, this.body,
                    this.leftArm, this.rightArm,
                    this.leftLeg, this.rightLeg);
            } else if (this.getClass() == VRPlayerModel_WithArms.class) {
                ModelUtils.applySwimRotationOffset(player, this.xRot, this.tempV, this.tempV2,
                    this.head, this.body,
                    this.leftLeg, this.rightLeg);
            } else if (this.getClass() == VRPlayerModel_WithArmsLegs.class) {
                ModelUtils.applySwimRotationOffset(player, this.xRot, this.tempV, this.tempV2,
                    this.head, this.body);
            }
        }

        if (this.riding || noLowerBodyAnimation) {
            this.bodyRot.rotationZYX(this.body.zRot, -this.body.yRot, -this.body.xRot);
        }

        this.leftArm.xScale = this.leftArm.zScale = this.rightArm.xScale = this.rightArm.zScale = this.armScale;
        this.body.xScale = this.body.zScale = this.bodyScale;
        this.leftLeg.xScale = this.leftLeg.zScale = this.rightLeg.xScale = this.rightLeg.zScale = this.legScale;

        this.vrHMD.visible = true;

        // spin attack moves the model one block up
        if (player.isAutoSpinAttack()) {
            spinOffset(this.head, this.body);
            if (!(this instanceof VRPlayerModel_WithArms)) {
                spinOffset(this.leftArm, this.rightArm);
            }
            if (!(this instanceof VRPlayerModel_WithArmsLegs)) {
                spinOffset(this.leftLeg, this.rightLeg);
            }
        }

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

    public Matrix3fc getBodyRot() {
        return this.bodyRot;
    }

    public void hideHand(LivingEntity player, InteractionHand hand, boolean completeArm) {
        VRPlayersClient.RotInfo rotInfo = VRPlayersClient.getInstance().getRotationsForPlayer(player.getUUID());
        if (rotInfo != null && rotInfo.reverse) {
            if (hand == InteractionHand.MAIN_HAND) {
                this.hideLeftArm(completeArm);
            } else {
                this.hideRightArm(completeArm);
            }
        } else {
            if (hand == InteractionHand.MAIN_HAND) {
                this.hideRightArm(completeArm);
            } else {
                this.hideLeftArm(completeArm);
            }
        }
    }

    public void hideLeftArm(boolean completeArm) {
        this.leftArm.visible = false;
        this.leftSleeve.visible = false;
    }

    public void hideRightArm(boolean onlyHand) {
        this.rightArm.visible = false;
        this.rightSleeve.visible = false;
    }

    protected void spinOffset(ModelPart... parts) {
        for (ModelPart part : parts) {
            part.y += 24F;
        }
    }

    @Override
    public void translateToHand(HumanoidArm side, PoseStack poseStack) {
        // can't call super, because, the vanilla slim offset doesn't work with rotations
        this.getArm(side).translateAndRotate(poseStack);

        if (this.slim) {
            poseStack.translate(0.5F / 16F * (side == HumanoidArm.RIGHT ? 1F : -1F), 0.0F, 0.0F);
        }
        if (side == this.attackArm) {
            poseStack.translate(0.0F, 0.5F, 0.0F);
            poseStack.mulPose(Axis.XP.rotation((float) Math.sin(this.attackTime * Mth.PI)));
            poseStack.translate(0.0F, -0.5F, 0.0F);
        }
    }
}
