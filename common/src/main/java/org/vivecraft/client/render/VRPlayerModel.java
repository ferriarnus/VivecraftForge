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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.vivecraft.client.ClientVRPlayers;
import org.vivecraft.client.utils.ClientUtils;
import org.vivecraft.client.utils.ModelUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.api.data.FBTMode;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.mod_compat_vr.immersiveportals.ImmersivePortalsHelper;
import org.vivecraft.mod_compat_vr.mca.MCAHelper;
import org.vivecraft.mod_compat_vr.shaders.ShadersHelper;

public class VRPlayerModel<T extends LivingEntity> extends PlayerModel<T> {
    public ModelPart vrHMD;

    protected ClientVRPlayers.RotInfo rotInfo;
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

    // temp vec for most math
    protected final Vector3f tempV = new Vector3f();
    protected final Vector3f tempV2 = new Vector3f();
    // temp mat3 for rotations
    protected final Matrix3f tempM = new Matrix3f();

    public VRPlayerModel(ModelPart root, boolean isSlim) {
        super(root, isSlim);
        this.vrHMD = root.getChild("vrHMD");
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
        this.vrHMD.visible = true;
        this.vrHMD.copyFrom(this.head);
    }

    public static void animateVRModel(
        PlayerModel<LivingEntity> model, LivingEntity player, float limbSwing, float limbSwingAmount, Vector3f tempV,
        Vector3f tempV2, Matrix3f tempM)
    {
        ClientVRPlayers.RotInfo rotInfo = null;

        // don't do any animations for dummy players
        if (player.getClass() == LocalPlayer.class || player.getClass() == RemotePlayer.class) {
            rotInfo = ClientVRPlayers.getInstance().getRotationsForPlayer(player.getUUID());
        }

        if (rotInfo == null) {
            // not a vr player
            if (model instanceof VRPlayerModel<LivingEntity> vrModel) {
                vrModel.rotInfo = null;
            }
            return;
        }

        float partialTick = ClientUtils.getCurrentPartialTick();
        boolean isMainPlayer = VRState.VR_RUNNING && player == Minecraft.getInstance().player;

        HumanoidArm mainArm = rotInfo.leftHanded ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        HumanoidArm attackArm = null;

        if (model.attackTime > 0F) {
            // we ignore the vanilla main arm setting
            attackArm = player.swingingArm == InteractionHand.MAIN_HAND ?
                HumanoidArm.RIGHT : HumanoidArm.LEFT;
            if (rotInfo.leftHanded) {
                attackArm = attackArm.getOpposite();
            }
        }

        float bodyYaw;

        if (isMainPlayer) {
            bodyYaw = ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().getBodyYawRad();
        } else {
            bodyYaw = rotInfo.getBodyYawRad();
        }

        boolean laying = model.swimAmount > 0.0F || player.isFallFlying();
        float layAmount = player.isFallFlying() ? 1F : model.swimAmount;

        boolean swimming = (laying && player.isInWater()) || player.isFallFlying();
        boolean noLowerBodyAnimation = swimming || rotInfo.fbtMode == FBTMode.ARMS_ONLY;

        float bodyScale = 1F;
        float armScale = 1F;
        float legScale = 1F;

        if (isMainPlayer && ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
            !(ImmersivePortalsHelper.isLoaded() && ImmersivePortalsHelper.isRenderingPortal()) &&
            (!ShadersHelper.isRenderingShadows() &&
                RenderPass.isFirstPerson(ClientDataHolderVR.getInstance().currentPass)
            ) ||
            (ShadersHelper.isRenderingShadows() && ClientDataHolderVR.getInstance().vrSettings.shaderFullSizeShadowLimbs
            ))
        {
            bodyScale = ClientDataHolderVR.getInstance().vrSettings.playerModelBodyScale;
            armScale = ClientDataHolderVR.getInstance().vrSettings.playerModelArmsScale;
            legScale = ClientDataHolderVR.getInstance().vrSettings.playerModelLegScale;
        }

        // scale the offset with the body and arm scale, to keep them attached
        float sideOffset = 4F * bodyScale + armScale;

        float xRot;

        if (swimming) {
            // in water also rotate around the view vector
            xRot = layAmount * (-Mth.HALF_PI - Mth.DEG_TO_RAD * player.getViewXRot(partialTick));
        } else {
            xRot = layAmount * -Mth.HALF_PI;
        }

        // head pivot
        if (!swimming) {
            rotInfo.headQuat.transform(0F, -0.2F, 0.1F, tempV2);
            if (isMainPlayer) {
                tempV2.mul(rotInfo.worldScale);
            }
            tempV2.mul(rotInfo.heightScale);
        } else {
            // no pivot offset when swimming
            tempV2.zero();
        }
        tempV2.add(rotInfo.headPos);

        float progress = ModelUtils.getBendProgress(player, rotInfo, tempV2);
        float heightOffset = 22F * progress;

        // rotate head
        tempM.set(rotInfo.headQuat)
            .rotateLocalY(bodyYaw + Mth.PI)
            .rotateLocalX(-xRot);
        ModelUtils.setRotation(model.head, tempM, tempV);
        ModelUtils.worldToModel(player, tempV2, rotInfo, bodyYaw, isMainPlayer, tempV);

        if (swimming) {
            // move the head in front of the body when swimming
            tempV.z += 3F;
        }

        // move head and body with bend
        model.head.setPos(tempV.x, tempV.y, tempV.z);
        model.body.setPos(model.head.x, model.head.y, model.head.z);

        // rotate body
        if (model.riding) {
            // when riding, rotate body to sitting position
            ModelUtils.pointModelAtModelForward(model.body, 0F, 14F, 2F + heightOffset, tempV, tempV2, tempM);
            tempM.rotateLocalX(-xRot);
            ModelUtils.setRotation(model.body, tempM, tempV);
        } else if (noLowerBodyAnimation) {
            // with only arms simply rotate the body in place
            model.body.setRotation(
                Mth.PI * Math.max(0F, model.body.y / 22F) * (model instanceof VRPlayerModel_WithArmsLegs ? 0.5F : 1F), 0F, 0F);
            if (laying) {
                float bodyXRot;
                if (swimming) {
                    bodyXRot = -xRot;
                } else {
                    float aboveGround = (heightOffset - 11F) / 11F;
                    bodyXRot = progress * (Mth.PI - Mth.HALF_PI * (1F + 0.3F * (1F - aboveGround)));
                }
                // lerp body rotation when swimming, to keep the model connected
                model.body.xRot = Mth.lerp(layAmount, model.body.xRot, bodyXRot);
                model.head.y -= 2F * layAmount;
                model.body.y -= 2F * layAmount;
            }
        } else {
            // body/arm position with waist tracker
            // if there is a waist tracker, align the body to that
            ModelUtils.pointModelAtLocal(player, model.body, rotInfo.waistPos, rotInfo.waistQuat, rotInfo,
                bodyYaw, isMainPlayer, tempV, tempV2, tempM);

            // offset arms
            tempM.transform(sideOffset, 2F, 0F, tempV2);
            model.leftArm.x = model.body.x + tempV2.x;
            model.leftArm.y = model.body.y + tempV2.y;
            model.leftArm.z = model.body.z - tempV2.z;

            tempM.transform(-sideOffset, 2F, 0F, tempV2);
            model.rightArm.x = model.body.x + tempV2.x;
            model.rightArm.y = model.body.y + tempV2.y;
            model.rightArm.z = model.body.z - tempV2.z;

            tempM.rotateLocalX(-xRot);
            ModelUtils.setRotation(model.body, tempM, tempV);
        }

        float cosBodyRot = Mth.cos(model.body.xRot);

        if (model.riding || noLowerBodyAnimation) {
            // offset arms with body rotation
            model.leftArm.x = model.body.x + sideOffset;
            model.rightArm.x = model.body.x - sideOffset;
            model.leftArm.y = 2F * cosBodyRot + model.body.y;
            model.leftArm.z = model.body.z;

            model.rightArm.y = model.leftArm.y;
            model.rightArm.z = model.leftArm.z;
        }

        model.leftLeg.x = 1.9F;
        model.rightLeg.x = -1.9F;

        if (model.riding) {
            model.leftLeg.z = heightOffset;
            model.rightLeg.z = model.leftLeg.z;
        } else if (laying && noLowerBodyAnimation) {
            // adjust legs
            if (swimming) {
                tempV.set(0, 12, 0);
                tempV.rotateX(-xRot);
                model.leftLeg.y = model.body.y + tempV.y;
                model.leftLeg.z = model.body.z + tempV.z;
            } else {
                // move legs with bend
                float cosBodyRot2 = cosBodyRot * cosBodyRot;
                model.leftLeg.y += 10.25F - 2F * cosBodyRot2;
                model.leftLeg.z = model.body.z + 13F - cosBodyRot2 * 8F;
            }
            model.leftLeg.x += model.body.x;
            model.rightLeg.x += model.body.x;

            model.rightLeg.y = model.leftLeg.y;
            model.rightLeg.z = model.leftLeg.z;
        } else if (rotInfo.fbtMode != FBTMode.ARMS_ONLY) {
            // fbt leg position
            ModelUtils.worldToModel(player, rotInfo.waistPos, rotInfo, bodyYaw, isMainPlayer, tempV);

            tempV2.set(-1.9F, -2F, 0F);
            rotInfo.waistQuat.transform(tempV2);
            ModelUtils.worldToModelDirection(tempV2, bodyYaw, tempV2);
            model.leftLeg.setPos(
                tempV.x + tempV2.x,
                tempV.y + tempV2.y,
                tempV.z + tempV2.z);

            tempV2.set(1.9F, -2F, 0F);
            rotInfo.waistQuat.transform(tempV2);
            ModelUtils.worldToModelDirection(tempV2, bodyYaw, tempV2);
            model.rightLeg.setPos(
                tempV.x + tempV2.x,
                tempV.y + tempV2.y,
                tempV.z + tempV2.z);
        } else {
            model.leftLeg.x += model.body.x;
            model.rightLeg.x += model.body.x;
        }

        // regular positioning
        if (!model.riding && layAmount < 1.0F && rotInfo.fbtMode == FBTMode.ARMS_ONLY) {
            // move legs back with bend
            float newLegY = 12F + Math.min(model.body.y, 0F);
            float newLegZ = model.body.z + 10F * Mth.sin(model.body.xRot);
            if (model instanceof VRPlayerModel_WithArmsLegs) {
                newLegY += 10F * Mth.sin(model.body.xRot);
            }

            model.leftLeg.y = Mth.lerp(layAmount, newLegY, model.leftLeg.y);
            model.leftLeg.z = Mth.lerp(layAmount, newLegZ, model.leftLeg.z);

            model.rightLeg.y = model.leftLeg.y;
            model.rightLeg.z = model.leftLeg.z;
        }

        // arms/legs only when standing
        if (!rotInfo.seated || isMainPlayer) {
            // arms only when not a split arms model
            if (!(model instanceof VRPlayerModel_WithArms) &&
                rotInfo.offHandPos.distanceSquared(rotInfo.mainHandPos) > 0.0F)
            {
                ModelPart offHand = rotInfo.leftHanded ? model.rightArm : model.leftArm;
                ModelPart mainHand = rotInfo.leftHanded ? model.leftArm : model.rightArm;

                // rotation offset, since the rotation point isn't in the center.
                // this rotates the arm 0.5 or 1 pixels at full arm distance, so that the hand matches up with the center
                float offset = (rotInfo.leftHanded ? -1F : 1f) * (model.slim ? 0.016F : 0.032F) * Mth.PI * armScale;

                // main hand
                ModelUtils.pointModelAtLocal(player, mainHand, rotInfo.mainHandPos, rotInfo.mainHandQuat,
                    rotInfo, bodyYaw, isMainPlayer, tempV, tempV2, tempM);

                float controllerDist = tempV.length();

                if (!ClientDataHolderVR.getInstance().vrSettings.playerLimbsLimit && controllerDist > 10F) {
                    tempV.normalize().mul(controllerDist - 10F);
                    mainHand.x += tempV.x;
                    mainHand.y += tempV.y;
                    mainHand.z += tempV.z;
                    tempM.rotateZ(-offset);
                } else {
                    // reduce correction angle with distance
                    tempM.rotateZ(-offset * Math.min(10F / controllerDist, 1F));
                }

                if (ClientDataHolderVR.getInstance().vrSettings.playerArmAnim && attackArm == mainArm) {
                    ModelUtils.swingAnimation(attackArm, model.attackTime, isMainPlayer, tempM,
                        tempV);
                    mainHand.x -= tempV.x;
                    mainHand.y -= tempV.y;
                    mainHand.z += tempV.z;
                }
                tempM.rotateLocalX(-xRot);
                ModelUtils.setRotation(mainHand, tempM, tempV);

                // offhand
                ModelUtils.pointModelAtLocal(player, offHand, rotInfo.offHandPos, rotInfo.offHandQuat,
                    rotInfo, bodyYaw, isMainPlayer, tempV, tempV2, tempM);

                controllerDist = tempV.length();

                if (!ClientDataHolderVR.getInstance().vrSettings.playerLimbsLimit && controllerDist > 10F) {
                    tempV.normalize().mul(controllerDist - 10F);
                    offHand.x += tempV.x;
                    offHand.y += tempV.y;
                    offHand.z += tempV.z;
                    tempM.rotateZ(offset);
                } else {
                    // reduce correction angle with distance
                    tempM.rotateZ(offset * Math.min(10F / controllerDist, 1F));
                }

                if (ClientDataHolderVR.getInstance().vrSettings.playerArmAnim && attackArm != mainArm) {
                    ModelUtils.swingAnimation(attackArm, model.attackTime, isMainPlayer, tempM,
                        tempV);
                    offHand.x -= tempV.x;
                    offHand.y -= tempV.y;
                    offHand.z += tempV.z;
                }

                if (isMainPlayer && ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
                    ClientDataHolderVR.getInstance().vrSettings.modelArmsMode != VRSettings.ModelArmsMode.OFF)
                {
                    GuiHandler.GUI_ROTATION_PLAYER_MODEL.set3x3(tempM);
                    // ModelParts are rotated 90°
                    GuiHandler.GUI_ROTATION_PLAYER_MODEL.rotateX(-Mth.HALF_PI);
                    // undo body yaw
                    GuiHandler.GUI_ROTATION_PLAYER_MODEL.rotateLocalY(-bodyYaw - Mth.PI);

                    // arm vector
                    GuiHandler.GUI_ROTATION_PLAYER_MODEL.transformDirection(MathUtils.BACK, tempV)
                        .mul(0.584F * rotInfo.worldScale);

                    ModelUtils.modelToWorld(player, offHand.x, offHand.y, offHand.z, rotInfo, bodyYaw, true,
                        isMainPlayer, tempV2);
                    if (MCAHelper.isLoaded()) {
                        MCAHelper.applyPlayerScale(player, tempV);
                    }

                    tempV2.add(tempV);

                    GuiHandler.GUI_POS_PLAYER_MODEL = player.getPosition(ClientUtils.getCurrentPartialTick())
                        .add(tempV2.x, tempV2.y, tempV2.z);
                }
                tempM.rotateLocalX(-xRot);
                ModelUtils.setRotation(offHand, tempM, tempV);
            }

            // legs only when not sitting
            if (!model.riding && !noLowerBodyAnimation && !(model instanceof VRPlayerModel_WithArmsLegs)) {
                float limbRotation = 0F;
                if (ClientDataHolderVR.getInstance().vrSettings.playerWalkAnim) {
                    // vanilla walking animation on top
                    limbRotation = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
                }

                ModelUtils.pointModelAtLocal(player, model.rightLeg, rotInfo.rightFootPos,
                    rotInfo.rightFootQuat, rotInfo, bodyYaw, isMainPlayer, tempV,
                    tempV2, tempM);
                tempM.rotateLocalX(limbRotation - xRot);
                ModelUtils.setRotation(model.rightLeg, tempM, tempV);

                ModelUtils.pointModelAtLocal(player, model.leftLeg, rotInfo.leftFootPos, rotInfo.leftFootQuat,
                    rotInfo, bodyYaw, isMainPlayer, tempV, tempV2, tempM);
                tempM.rotateLocalX(-limbRotation - xRot);
                ModelUtils.setRotation(model.leftLeg, tempM, tempV);
            }
        }

        if (layAmount > 0F) {
            if (noLowerBodyAnimation) {
                // with a waist tracker the rotation is already done before
                model.body.xRot += xRot;
            }

            if (model instanceof VRPlayerModel_WithArmsLegs) {
                ModelUtils.applySwimRotationOffset(player, xRot, tempV, tempV2,
                    model.head, model.body);
            } else if (model instanceof VRPlayerModel_WithArms) {
                ModelUtils.applySwimRotationOffset(player, xRot, tempV, tempV2,
                    model.head, model.body,
                    model.leftLeg, model.rightLeg);
            } else {
                ModelUtils.applySwimRotationOffset(player, xRot, tempV, tempV2,
                    model.head, model.body,
                    model.leftArm, model.rightArm,
                    model.leftLeg, model.rightLeg);
            }
        }

        model.leftArm.xScale = model.leftArm.zScale = model.rightArm.xScale = model.rightArm.zScale = armScale;
        model.body.xScale = model.body.zScale = bodyScale;
        model.leftLeg.xScale = model.leftLeg.zScale = model.rightLeg.xScale = model.rightLeg.zScale = legScale;

        // spin attack moves the model one block up
        if (player.isAutoSpinAttack()) {
            spinOffset(model.head, model.body);
            if (!(model instanceof VRPlayerModel_WithArms)) {
                spinOffset(model.leftArm, model.rightArm);
            }
            if (!(model instanceof VRPlayerModel_WithArmsLegs)) {
                spinOffset(model.leftLeg, model.rightLeg);
            }
        }

        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);

        if (model instanceof VRPlayerModel vrModel) {
            vrModel.isMainPlayer = isMainPlayer;
            vrModel.rotInfo = rotInfo;
            vrModel.mainArm = mainArm;
            vrModel.attackArm = attackArm;
            vrModel.bodyYaw = bodyYaw;
            vrModel.laying = laying;
            vrModel.layAmount = layAmount;
            vrModel.bodyScale = bodyScale;
            vrModel.armScale = armScale;
            vrModel.legScale = legScale;
            vrModel.xRot = xRot;
        }
    }

    public void renderHMD(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay) {
        this.vrHMD.render(poseStack, vertexConsumer, packedLight, packedOverlay);
    }

    public void hideLeftArm(boolean completeArm) {
        this.leftArm.visible = false;
        this.leftSleeve.visible = false;
    }

    public void hideRightArm(boolean onlyHand) {
        this.rightArm.visible = false;
        this.rightSleeve.visible = false;
    }

    protected static void spinOffset(ModelPart... parts) {
        for (ModelPart part : parts) {
            part.y += 24F;
        }
    }

    @Override
    public void translateToHand(HumanoidArm side, PoseStack poseStack) {
        // can't call super, because, the vanilla slim offset doesn't work with rotations
        this.getArm(side).translateAndRotate(poseStack);

        if (this.slim) {
            poseStack.translate(side == HumanoidArm.RIGHT ? 0.03125F : -0.03125F, 0.0F, 0.0F);
        }

        if (side == this.attackArm) {
            poseStack.translate(0.0F, 0.5F, 0.0F);
            poseStack.mulPose(Axis.XP.rotation(Mth.sin(this.attackTime * Mth.PI)));
            poseStack.translate(0.0F, -0.5F, 0.0F);
        }
    }
}
