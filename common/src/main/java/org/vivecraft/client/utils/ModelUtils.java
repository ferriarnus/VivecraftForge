package org.vivecraft.client.utils;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.*;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client.Xplat;
import org.vivecraft.client.render.VRPlayerModel;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.render.VRFirstPersonArmSwing;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.mod_compat_vr.pehkui.PehkuiHelper;

import java.lang.Math;

public class ModelUtils {
    /**
     * calculates how far down the player is bending over
     * @param entity Player entity to check for
     * @param rotInfo RotInfo for the given player
     * @return 0-1 of how far the player is bending over
     */
    public static float getBendProgress(LivingEntity entity, VRPlayersClient.RotInfo rotInfo) {
        // no bending when spinning
        if (entity.isAutoSpinAttack()) return 0.0F;

        float eyeHeight = entity.getEyeHeight(Pose.STANDING) * rotInfo.worldScale;
        if (Xplat.isModLoaded("pehkui")) {
            // remove pehkui scale from that, since the whole entity is scaled
            eyeHeight /= PehkuiHelper.getEntityEyeHeightScale(entity, Minecraft.getInstance().getFrameTime());
        }
        float heightOffset = Mth.clamp(rotInfo.headPos.y() - eyeHeight * rotInfo.heightScale * 0.95F, -eyeHeight, 0F);

        float progress = heightOffset / -eyeHeight;

        if (entity.isCrouching()) {
            progress = Math.max(progress, 0.125F);
        }
        if (entity.isPassenger()) {
            // don't go below sitting position
            progress = Math.min(progress, 0.5F);
        }
        return progress;
    }

    /**
     * converts a player local point to player model space
     * @param position Position to convert
     * @param rotInfo player VR info
     * @param bodyYaw players Y rotation
     * @param out Vector3f to store the result in
     */
    public static void worldToModel(Vector3fc position, VRPlayersClient.RotInfo rotInfo, float bodyYaw, Vector3f out) {
        out.set(position);
        if (Xplat.isModLoaded("pehkui")) {
            // remove pehkui scale from that, since the whole entity is scaled
            // TODO FBT which of those is correct
            //out.div(PehkuiHelper.getEntityEyeHeightScale(Minecraft.getInstance().player, Minecraft.getInstance().getFrameTime()));
            out.div(rotInfo.worldScale);
        }
        final float scale = 0.9375F * rotInfo.heightScale;
        out.sub(0.0F, 1.501F * scale, 0.0F) // move to player center
            .rotateY(-Mth.PI + bodyYaw) // apply player rotation
            .mul(16.0F / scale)
            .mul(-1, -1, 1); // scale to player space
    }

    /**
     * converts a world direction to player model space
     * @param direction direction to convert
     * @param bodyYaw players Y rotation
     * @param out Vector3f to store the result in
     */
    public static void worldToModelDirection(Vector3fc direction, float bodyYaw, Vector3f out) {
        direction.rotateY(-Mth.PI + bodyYaw, out);
        out.set(-direction.x(), -direction.y(), direction.z());
    }

    /**
     * converts a player model direction to world space
     * @param direction direction to convert
     * @param bodyYaw players Y rotation
     * @param out Vector3f to store the result in
     */
    public static void modelToWorldDirection(Vector3fc direction, float bodyYaw, Vector3f out) {
        out.set(-direction.x(), -direction.y(), direction.z())
            .rotateY(Mth.PI - bodyYaw);
    }

    /**
     * converts a player model space point to player local space
     * @param modelPosition source point in model space
     * @param rotInfo player VR info
     * @param bodyYaw players Y rotation
     * @param out Vector3f to store the result in
     * @return {@code out} vector
     */
    public static Vector3f modelToWorld(
        Vector3fc modelPosition, VRPlayersClient.RotInfo rotInfo, float bodyYaw, Vector3f out)
    {
        return modelToWorld(modelPosition.x(), modelPosition.y(), modelPosition.z(), rotInfo, bodyYaw, out);
    }

    /**
     * converts a player model space point to player local space
     * @param x x coordinate of the source point
     * @param y y coordinate of the source point
     * @param z z coordinate of the source point
     * @param rotInfo player VR info
     * @param bodyYaw players Y rotation
     * @param out Vector3f to store the result in
     * @return {@code out} vector
     */
    public static Vector3f modelToWorld(
        float x, float y, float z, VRPlayersClient.RotInfo rotInfo, float bodyYaw, Vector3f out)
    {
        final float scale = 0.9375F * rotInfo.heightScale;
        out.set(-x, -y, z)
            .mul(scale / 16.0F)
            .rotateY(Mth.PI - bodyYaw)
            .add(0.0F, 1.501F * scale, 0.0F);
        if (Xplat.isModLoaded("pehkui")) {
            // remove pehkui scale from that, since the whole entity is scaled
            // TODO FBT which of those is correct
            //out.div(PehkuiHelper.getEntityEyeHeightScale(Minecraft.getInstance().player, Minecraft.getInstance().getFrameTime()));
            out.mul(rotInfo.worldScale);
        }
        return out;
    }

    /**
     * rotates the ModelPart {@code part} to point at the given player local world space point
     * @param part ModelPart to rotate
     * @param target target point the {@code part} should face, player local in world space
     * @param targetRot target rotation the {@code part} should respect
     * @param rotInfo players data
     * @param bodyYaw players Y rotation
     * @param applyRotation if the rotation should be directly applied to the ModelPart
     * @param tempV Vector3f objet to work with, contains the euler angles after the call
     * @param tempM Matrix3f objet to work with, contains the rotation after the call
     */
    public static void pointModelAtLocal(
        ModelPart part, Vector3fc target, Quaternionfc targetRot, VRPlayersClient.RotInfo rotInfo, float bodyYaw,
        boolean applyRotation, Vector3f tempV, Matrix3f tempM)
    {
        // concert model to world space
        modelToWorld(part.x, part.y, part.z, rotInfo, bodyYaw, tempV);
        // calculate direction
        target.sub(tempV, tempV);
        // rotate model
        pointAt(part, targetRot, bodyYaw, applyRotation, tempV, tempM);
    }

    /**
     * rotates the ModelPart {@code part} to point at the given model space point
     * @param part ModelPart to rotate
     * @param targetX x coordinate of the target point the {@code part} should face, ine model space
     * @param targetY y coordinate of the target point the {@code part} should face, ine model space
     * @param targetZ z coordinate of the target point the {@code part} should face, ine model space
     * @param targetRot target rotation the {@code part} should respect
     * @param bodyYaw players Y rotation
     * @param applyRotation if the rotation should be directly applied to the ModelPart
     * @param tempV Vector3f objet to work with, contains the euler angles after the call
     * @param tempM Matrix3f objet to work with, contains the rotation after the call
     */
    public static void pointModelAtModel(
        ModelPart part, float targetX, float targetY, float targetZ, Quaternionfc targetRot, float bodyYaw,
        boolean applyRotation, Vector3f tempV, Matrix3f tempM)
    {
        // calculate direction
        tempV.set(targetX - part.x, targetY - part.y, targetZ - part.z);

        // convert to world space
        modelToWorldDirection(tempV, bodyYaw, tempV);

        // rotate model
        pointAt(part, targetRot, bodyYaw, applyRotation, tempV, tempM);
    }

    public static void positionAndRotateModelToLocal(
        ModelPart part, Vector3fc targetPos, Quaternionfc targetRot, VRPlayersClient.RotInfo rotInfo, float bodyYaw,
        Vector3f tempV)
    {
        ModelUtils.worldToModel(targetPos, rotInfo, bodyYaw, tempV);
        part.setPos(tempV.x, tempV.y, tempV.z);

        Quaternionf rot = new Quaternionf(targetRot);

        // undo body yaw
        rot.rotateLocalY(bodyYaw + Mth.PI);
        // ModelParts are rotated 90°
        rot.rotateX(Mth.HALF_PI);
        //rot.rotateZ(Mth.HALF_PI);
        MathUtils.getEulerAnglesZYX(rot, tempV);
        // ModelPart x and y axes are flipped
        part.setRotation(-tempV.x, -tempV.y, tempV.z);

    }

   /**
     * rotates the ModelPart {@code part} to point in the {@code tempDir} direction
     * @param part ModelPart to rotate
     * @param targetRot target rotation the {@code part} should respect
     * @param bodyYaw players Y rotation
     * @param applyRotation if the rotation should be directly applied to the ModelPart
     * @param tempDir direction Vector to work with, contains the euler angles after the call
     * @param tempM Matrix3f objet to work with, contains the rotation after the call
     */
    public static void pointAt(
        ModelPart part, Quaternionfc targetRot, float bodyYaw, boolean applyRotation, Vector3f tempDir, Matrix3f tempM)
    {
        // get the up vector the ModelPart should face
        Vector3f temp2 = targetRot.transform(MathUtils.RIGHT, new Vector3f());
        tempDir.cross(temp2, temp2);

        tempM.setLookAlong(tempDir, temp2).transpose();
        // undo body yaw
        tempM.rotateLocalY(bodyYaw + Mth.PI);
        // ModelParts are rotated 90°
        tempM.rotateX(Mth.HALF_PI);
        if (applyRotation) {
            setRotation(part, tempM, tempDir);
        }
    }

    /**
     * sets the rotation of the ModelPart to be equal to the given Matrix
     * @param part ModelPart to set the rotation of
     * @param rotation Matrix holding the worldspace rotation
     * @param tempV Vector3f objet to work with, contains the euler angles after the call
     */
    public static void setRotation(ModelPart part, Matrix3fc rotation, Vector3f tempV) {
        rotation.getEulerAnglesZYX(tempV);
        // ModelPart x and y axes are flipped
        part.setRotation(-tempV.x, -tempV.y, tempV.z);
    }

    /**
     * sets the rotation of the ModelPart to be equal to the given Quaternion
     * @param part ModelPart to set the rotation of
     * @param rotation Quaternion holding the worldspace rotation
     * @param tempV Vector3f objet to work with, contains the euler angles after the call
     */
    public static void setRotation(ModelPart part, Quaternionfc rotation, Vector3f tempV) {
        MathUtils.getEulerAnglesZYX(rotation, tempV);
        // ModelPart x and y axes are flipped
        part.setRotation(-tempV.x, -tempV.y, tempV.z);
    }

    /**
     * estimates a point between start and end so that the total length is {@code distance}
     * @param startX x position of the start point
     * @param startY y position of the start point
     * @param startZ z position of the start point
     * @param endX x position of the end point
     * @param endY y position of the end point
     * @param endZ z position of the end point
     * @param direction
     * @param distance length of the total line
     * @param tempV Vector3f objet to work with, contains the estimated joint point after the call
     */
    public static void estimateJoint(
        float startX, float startY, float startZ, float endX, float endY, float endZ, Vector3fc direction,
        float distance, Vector3f tempV)
    {
        tempV.set(startX - endX, startY - endY, startZ - endZ);
        float length = tempV.length();
        if (length >= distance) {
            // return the midpoint
            tempV.set(startX + endX, startY + endY, startZ + endZ).mul(0.5F);
        } else {
            length *= 0.5F;
            // offset the midpoint in the given direction
            tempV.cross(direction).normalize();
            tempV.mul((float) Math.sqrt(distance * distance * 0.25F - length * length));
            tempV.add((startX + endX) * 0.5F, (startY + endY) * 0.5F, (startZ + endZ) * 0.5F);
        }
    }

    /**
     * applies the attack animation, and applies rotation changes to the provided matrix, if attack arm equals {@code armCheck}
     * @param part ModelPart to modify
     * @param arm player arm to apply the animation to
     * @param attackTime progress of the attack animation 0-1
     * @param isMainPlayer if the ModelPart is from the main player
     * @param tempM rotation of the arm in world space, this matrix will be modified
     * @param tempV Vector3f object to work with, contains the world space offset after the call
     */
    public static void attackAnimation(ModelPart part, HumanoidArm arm, float attackTime, boolean isMainPlayer, Matrix3f tempM, Vector3f tempV) {
        if (attackTime > 0.0F) {
            if (!isMainPlayer || ClientDataHolderVR.getInstance().swingType == VRFirstPersonArmSwing.Attack) {
                // arm swing animation
                float rotation;
                if (attackTime > 0.5F) {
                    rotation = Mth.sin(attackTime * Mth.PI + Mth.PI);
                } else {
                    rotation = Mth.sin((attackTime * 3.0F) * Mth.PI);
                }

                tempM.rotateX(rotation * 30.0F * Mth.DEG_TO_RAD);
            } else {
                switch (ClientDataHolderVR.getInstance().swingType) {
                    case Use -> {
                        // hand forward animation
                        float movement;
                        if (attackTime > 0.25F) {
                            movement = Mth.sin(attackTime * Mth.HALF_PI + Mth.PI);
                        } else {
                            movement = Mth.sin(attackTime * Mth.TWO_PI);
                        }
                        tempM.transform(MathUtils.DOWN, tempV).mul(movement);
                        part.x -= tempV.x;
                        part.y -= tempV.y;
                        part.z += tempV.z;
                    }
                    case Interact -> {
                        // arm rotation animation
                        float rotation;
                        if (attackTime > 0.5F) {
                            rotation = Mth.sin(attackTime * Mth.PI + Mth.PI);
                        } else {
                            rotation = Mth.sin(attackTime * 3.0F * Mth.PI);
                        }

                        tempM.rotateY((arm == HumanoidArm.RIGHT ? -40.0F : 40.0F) * rotation * Mth.DEG_TO_RAD);
                    }
                }
            }
        }
    }

    /**
     * applies the swimming rotation offset to the provided ModelParts
     * @param player Player that is swimming
     * @param xRot rotation of the player, in radians
     * @param tempV first Vector3f object to work with
     * @param tempV2 second Vector3f object to work with, contains the global player offset after the call
     * @param parts list of ModelParts to modify
     */
    public static void applySwimRotationOffset(
        LivingEntity player, float xRot, Vector3f tempV, Vector3f tempV2, ModelPart... parts)
    {
        // fetch those once to not have to calculate it fore each part
        float sin = Mth.sin(xRot);
        float cos = Mth.cos(xRot);

        // calculate rotation offset, since the player model is offset while swimming
        if (player.isVisuallySwimming() && !player.isAutoSpinAttack() && !player.isFallFlying()) {
            tempV2.set(0.0F, 17.06125F, 5.125F);
            //tempV2.rotateX(-xRot);
            MathUtils.rotateX(tempV2, -sin, cos);
            tempV2.y += 2;
        } else {
            // make sure this one is empty
            tempV2.set(0,0,0);
        }

        for (ModelPart part : parts) {
            tempV.set(part.x, part.y, part.z);

            tempV.sub(tempV2);

            // apply swimming rotation to the offset
            tempV.y -= 24F;
            //tempV.rotateX(xRot);
            MathUtils.rotateX(tempV, sin, cos);
            tempV.y += 24F;
            part.setPos(tempV.x, tempV.y, tempV.z);
        }
    }
}
