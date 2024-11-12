package org.vivecraft.client.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.*;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client.Xplat;
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
        out.set(-out.x(), -out.y(), out.z());
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
     * @param tempVDir Vector3f object to work with, contains the euler angles after the call
     * @param tempVUp second Vector3f object to work with, contains the euler angles after the call
     * @param tempM Matrix3f object to work with, contains the rotation after the call
     */
    public static void pointModelAtLocal(
        ModelPart part, Vector3fc target, Quaternionfc targetRot, VRPlayersClient.RotInfo rotInfo, float bodyYaw,
        Vector3f tempVDir, Vector3f tempVUp, Matrix3f tempM)
    {
        // concert model to world space
        modelToWorld(part.x, part.y, part.z, rotInfo, bodyYaw, tempVDir);
        // calculate direction
        target.sub(tempVDir, tempVDir);

        // get the up vector the ModelPart should face
        targetRot.transform(MathUtils.RIGHT, tempVUp);
        tempVDir.cross(tempVUp, tempVUp);

        // rotate model
        pointAt(bodyYaw, tempVDir, tempVUp, tempM);
    }

    /**
     * rotates the ModelPart {@code part} to point at the given model space point
     * @param part ModelPart to rotate
     * @param targetX x coordinate of the target point the {@code part} should face, ine model space
     * @param targetY y coordinate of the target point the {@code part} should face, ine model space
     * @param targetZ z coordinate of the target point the {@code part} should face, ine model space
     * @param targetRot target rotation the {@code part} should respect
     * @param bodyYaw players Y rotation
     * @param tempVDir Vector3f object to work with, contains the euler angles after the call
     * @param tempVUp second Vector3f object to work with, contains the up vector after the call
     * @param tempM Matrix3f object to work with, contains the rotation after the call
     */
    public static void pointModelAtModel(
        ModelPart part, float targetX, float targetY, float targetZ, Quaternionfc targetRot, float bodyYaw,
        Vector3f tempVDir, Vector3f tempVUp, Matrix3f tempM)
    {
        // TODO FBT remove unnecessary bodyYaw
        // calculate direction
        tempVDir.set(targetX - part.x, targetY - part.y, targetZ - part.z);

        // convert to world space
        modelToWorldDirection(tempVDir, bodyYaw, tempVDir);

        // get the up vector the ModelPart should face
        targetRot.transform(MathUtils.RIGHT, tempVUp);
        tempVDir.cross(tempVUp, tempVUp);

        // rotate model
        pointAt(bodyYaw, tempVDir, tempVUp, tempM);
    }

    /**
     * rotates the ModelPart {@code part} to point at the given model space point, while facing forward
     * @param part ModelPart to rotate
     * @param targetX x coordinate of the target point the {@code part} should face, ine model space
     * @param targetY y coordinate of the target point the {@code part} should face, ine model space
     * @param targetZ z coordinate of the target point the {@code part} should face, ine model space
     * @param tempVDir Vector3f object to work with, contains the euler angles after the call
     * @param tempVUp second Vector3f object to work with, contains the up vector after the call
     * @param tempM Matrix3f object to work with, contains the rotation after the call
     */
    public static void pointModelAtModel(
        ModelPart part, float targetX, float targetY, float targetZ,
        Vector3f tempVDir, Vector3f tempVUp, Matrix3f tempM)
    {
        // calculate direction
        tempVDir.set(targetX - part.x, targetY - part.y, targetZ - part.z);

        // convert to world space
        tempVDir.set(tempVDir.x, tempVDir.y, tempVDir.z);

        tempVDir.cross(MathUtils.LEFT, tempVUp);

        // rotate model
        pointAtModel(tempVDir, tempVUp, tempM);
    }

    /**
     * rotates the given Matrix3f to point in the {@code tempDir} world direction
     * @param bodyYaw players Y rotation
     * @param upDir   target direction the {@code part} should respect
     * @param tempDir direction Vector to work with, contains the euler angles after the call
     * @param tempM   Matrix3f object to work with, contains the rotation after the call
     */
    public static void pointAt(float bodyYaw, Vector3f tempDir, Vector3fc upDir, Matrix3f tempM) {
        tempM.setLookAlong(tempDir, upDir).transpose();
        // undo body yaw
        tempM.rotateLocalY(bodyYaw + Mth.PI);
        // ModelParts are rotated 90°
        tempM.rotateX(Mth.HALF_PI);
    }

    /**
     * rotates the given Matrix3f to point in the {@code tempDir} model direction
     * @param upDir target direction the {@code part} should respect
     * @param tempDir direction Vector to work with, contains the euler angles after the call
     * @param tempM Matrix3f object to work with, contains the rotation after the call
     */
    public static void pointAtModel(Vector3f tempDir, Vector3fc upDir, Matrix3f tempM) {
        tempM.setLookAlong(
            -tempDir.x(), -tempDir.y(), tempDir.z(),
            -upDir.x(), -upDir.y(), upDir.z()).transpose();
        // ModelParts are rotated 90°
        tempM.rotateX(Mth.HALF_PI);
    }

    /**
     * rotates the given Matrix3f to point in the {@code direction} world direction
     * @param bodyYaw players Y rotation
     * @param direction direction quat to transform to model space
     * @param tempM Matrix3f object to work with, contains the rotation after the call
     */
    public static void toModelDir(float bodyYaw, Quaternionfc direction, Matrix3f tempM) {
        tempM.set(direction);
        // undo body yaw
        tempM.rotateLocalY(bodyYaw + Mth.PI);
        // ModelParts are rotated 90°
        tempM.rotateX(Mth.HALF_PI);
    }

    /**
     * sets the rotation of the ModelPart to be equal to the given Matrix
     * @param part ModelPart to set the rotation of
     * @param rotation Matrix holding the worldspace rotation
     * @param tempV Vector3f object to work with, contains the euler angles after the call
     */
    public static void setRotation(ModelPart part, Matrix3fc rotation, Vector3f tempV) {
        rotation.getEulerAnglesZYX(tempV);
        // ModelPart x and y axes are flipped
        // this can be nan when it is perfectly aligned with pointing left. 0 isn't right here, but beter than nan
        part.setRotation(-tempV.x, Float.isNaN(tempV.y) ? 0F : -tempV.y, tempV.z);
    }

    /**
     * estimates a point between start and end so that the total length is {@code limbLength}
     * @param startX x position of the start point
     * @param startY y position of the start point
     * @param startZ z position of the start point
     * @param endX x position of the end point
     * @param endY y position of the end point
     * @param endZ z position of the end point
     * @param preferredDirection preferred direction he joint should be at
     * @param limbLength length of the limb
     * @param tempV Vector3f object to work with, contains the estimated joint point after the call
     */
    public static void estimateJoint(
        float startX, float startY, float startZ, float endX, float endY, float endZ, Vector3fc preferredDirection,
        float limbLength, Vector3f tempV)
    {
        tempV.set(startX, startY, startZ);
        float distance = tempV.distance(endX, endY, endZ);
        tempV.add(endX, endY, endZ).mul(0.5F);
        if (distance < limbLength) {
            // move the mid point outwards so that the limb length is reached
            float offsetDistance = (float) Math.sqrt((limbLength * limbLength - distance * distance) * 0.25F);
            tempV.add(preferredDirection.x() * offsetDistance,
                preferredDirection.y() * offsetDistance,
                preferredDirection.z() * offsetDistance);
        }
    }

    /**
     * applies the attack animation, and applies rotation changes to the provided matrix
     * @param arm player arm to apply the animation to
     * @param attackTime progress of the attack animation 0-1
     * @param isMainPlayer if the ModelPart is from the main player
     * @param tempM rotation of the arm in world space, this matrix will be modified
     * @param tempV Vector3f object to work with, contains the world space offset after the call
     */
    public static void swingAnimation(
        HumanoidArm arm, float attackTime, boolean isMainPlayer, Matrix3f tempM, Vector3f tempV)
    {
        // zero it always, since it's supposed to have the offset at the end
        tempV.zero();
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
                        tempM.transform(MathUtils.DOWN, tempV).mul((1F + movement) * 1.6F);
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
     * applies the attack animation with an offset rotation point, and applies rotation changes to the provided matrix
     * @param part ModelPart ot rotate/offset
     * @param arm player arm to apply the animation to
     * @param offset offset for the model rotation
     * @param attackTime progress of the attack animation 0-1
     * @param isMainPlayer if the ModelPart is from the main player
     * @param tempM rotation of the arm in world space, this matrix will be modified
     * @param tempV Vector3f object to work with
     * @param tempV2 Vector3f object to work with
     */
    public static void swingAnimation(
        ModelPart part, HumanoidArm arm, float offset, float attackTime, boolean isMainPlayer, Matrix3f tempM,
        Vector3f tempV, Vector3f tempV2)
    {
        if (attackTime > 0.0F) {
            // need to get the pre and post rotation point, to offset the modelPart correctly
            tempM.transform(0,  offset, 0, tempV2);

            swingAnimation(arm, attackTime, isMainPlayer, tempM, tempV);
            // apply offset from the animation
            part.x -= tempV.x;
            part.y -= tempV.y;
            part.z += tempV.z;

            tempM.transform(0,  offset, 0, tempV);

            // apply the offset from the rotation point
            part.x += tempV2.x - tempV.x;
            part.y += tempV2.y - tempV.y;
            part.z -= tempV2.z - tempV.z;
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
