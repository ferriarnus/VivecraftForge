package org.vivecraft.client.utils;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.*;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.render.VRFirstPersonArmSwing;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;
import org.vivecraft.mod_compat_vr.sodium.SodiumHelper;

import javax.annotation.Nullable;
import java.lang.Math;

public class ModelUtils {
    /**
     * copies the bottom face texture from the {@code source} ModelPart to the top/bottom face of the {@code target} ModelPart
     * @param source ModelPart to copy the top/bottom face from
     * @param target ModelPart to copy the top/bottom face to
     */
    public static void textureHack(ModelPart source, ModelPart target) {
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
     * copies the bottom face texture from the {@code source} ModelPart to the {@code target} ModelPart,
     * and replaces the bottom {@code source} face, and the top {@code target} face with the top {@code source} face
     * @param source ModelPart to copy the top/bottom face from
     * @param target ModelPart to copy the top/bottom face to
     */
    public static void textureHackUpper(ModelPart source, ModelPart target) {
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
    private static void copyUV(ModelPart.Polygon source, ModelPart.Polygon target) {
        for (int i = 0; i < source.vertices.length; i++) {
            ModelPart.Vertex newVertex = new ModelPart.Vertex(target.vertices[i].pos, source.vertices[i].u, source.vertices[i].v);
            // Optifine has custom internal polygon data which also needs to be modified
            if (OptifineHelper.isOptifineLoaded()) {
                OptifineHelper.copyRenderPositions(target.vertices[i], newVertex);
            }
            target.vertices[i] = newVertex;
        }
    }

    /**
     * calculates how far down the player is bending over
     * @param entity Player entity to check for
     * @param rotInfo RotInfo for the given player
     * @return 0-1 of how far the player is bending over
     */
    public static float getBendProgress(LivingEntity entity, VRPlayersClient.RotInfo rotInfo, Vector3fc headPivot) {
        // no bending when spinning
        if (entity.isAutoSpinAttack()) return 0.0F;

        //default player eye height, -0.2 neck offset
        float eyeHeight = 1.42F * rotInfo.worldScale;

        float heightOffset = Mth.clamp(headPivot.y() - eyeHeight * rotInfo.heightScale, -eyeHeight, 0F);

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
     * @param player player this position is from
     * @param position Position to convert
     * @param rotInfo player VR info
     * @param bodyYaw players Y rotation
     * @param useWorldScale when set will cancel out the worldScale, instead of entity scale
     * @param out Vector3f to store the result in
     */
    public static void worldToModel(
        LivingEntity player, Vector3fc position, VRPlayersClient.RotInfo rotInfo, float bodyYaw,
        boolean useWorldScale, Vector3f out)
    {
        out.set(position);

        if (player.isAutoSpinAttack()) {
            out.y += 1F;
        }

        if (useWorldScale) {
            // the main player has the entity scale in its world scale
            out.div(rotInfo.worldScale);
        } else {
            out.div(ScaleHelper.getEntityEyeHeightScale(player, ClientUtils.getCurrentPartialTick()));
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
     * @param player player this position is from
     * @param modelPosition source point in model space
     * @param rotInfo player VR info
     * @param bodyYaw players Y rotation
     * @param applyScale if the woldScale/entity scale should be applied
     * @param useWorldScale when set will apply the worldScale, instead of entity scale
     * @param out Vector3f to store the result in
     * @return {@code out} vector
     */
    public static Vector3f modelToWorld(
        LivingEntity player, Vector3fc modelPosition, VRPlayersClient.RotInfo rotInfo, float bodyYaw,
        boolean applyScale, boolean useWorldScale, Vector3f out)
    {
        return modelToWorld(player, modelPosition.x(), modelPosition.y(), modelPosition.z(), rotInfo, bodyYaw,
            applyScale, useWorldScale, out);
    }

    /**
     * converts a player model space point to player local space
     * @param player player this position is from
     * @param x x coordinate of the source point
     * @param y y coordinate of the source point
     * @param z z coordinate of the source point
     * @param rotInfo player VR info
     * @param bodyYaw players Y rotation
     * @param applyScale if the woldScale/entity scale should be applied
     * @param useWorldScale when set will apply the worldScale, instead of entity scale
     * @param out Vector3f to store the result in
     * @return {@code out} vector
     */
    public static Vector3f modelToWorld(
        LivingEntity player, float x, float y, float z, VRPlayersClient.RotInfo rotInfo, float bodyYaw,
        boolean applyScale, boolean useWorldScale, Vector3f out)
    {
        final float scale = 0.9375F * rotInfo.heightScale;
        out.set(-x, -y, z)
            .mul(scale / 16.0F)
            .rotateY(Mth.PI - bodyYaw)
            .add(0.0F, 1.501F * scale, 0.0F);

        if (applyScale) {
            if (useWorldScale) {
                // the main player has the entity scale in its world scale
                out.mul(rotInfo.worldScale);
            } else {
                out.mul(ScaleHelper.getEntityEyeHeightScale(player, ClientUtils.getCurrentPartialTick()));
            }
        }

        return out;
    }

    /**
     * sets the matrix {@code tempM} so that the ModelPart {@code part} points at the given player local world space point
     * @param player player this position is from
     * @param part ModelPart to use as the pivot point
     * @param target target point the {@code part} should face, player local in world space
     * @param targetRot target rotation the {@code part} should respect
     * @param rotInfo players data
     * @param bodyYaw players Y rotation
     * @param useWorldScale when set will apply the worldScale, instead of entity scale
     * @param tempVDir Vector3f object to work with, contains the direction after the call, in model space
     * @param tempVUp second Vector3f object to work with, contains the up direction after the call
     * @param tempM Matrix3f object to work with, contains the rotation after the call
     */
    public static void pointModelAtLocal(
        LivingEntity player, ModelPart part, Vector3fc target, Quaternionfc targetRot, VRPlayersClient.RotInfo rotInfo,
        float bodyYaw, boolean useWorldScale, Vector3f tempVDir, Vector3f tempVUp, Matrix3f tempM)
    {
        // convert target to model
        worldToModel(player, target, rotInfo, bodyYaw, useWorldScale, tempVDir);

        // calculate direction
        tempVDir.sub(part.x, part.y, part.z);

        // get the up vector the ModelPart should face
        targetRot.transform(MathUtils.RIGHT, tempVUp);
        worldToModelDirection(tempVUp, bodyYaw, tempVUp);

        tempVDir.cross(tempVUp, tempVUp);

        // rotate model
        pointAtModel(tempVDir, tempVUp, tempM);
    }

    /**
     * sets the matrix {@code tempM} so that the ModelPart {@code part} points at the given model space point, while facing forward
     * @param part ModelPart to use as the pivot point
     * @param targetX x coordinate of the target point the {@code part} should face, in model space
     * @param targetY y coordinate of the target point the {@code part} should face, in model space
     * @param targetZ z coordinate of the target point the {@code part} should face, in model space
     * @param tempVDir Vector3f object to work with, contains the direction vector after the call
     * @param tempVUp second Vector3f object to work with, contains the up vector after the call
     * @param tempM Matrix3f object to work with, contains the rotation after the call
     */
    public static void pointModelAtModelForward(
        ModelPart part, float targetX, float targetY, float targetZ, Vector3f tempVDir,
        Vector3f tempVUp, Matrix3f tempM)
    {
        // calculate direction
        tempVDir.set(targetX - part.x, targetY - part.y, targetZ - part.z);

        // get the up vector the ModelPart should face
        tempVDir.cross(MathUtils.LEFT, tempVUp);

        // rotate model
        pointAtModel(tempVDir, tempVUp, tempM);
    }

    /**
     * sets the matrix {@code tempM} so that the ModelPart {@code part} points at the given model space point, while facing forward
     * @param part ModelPart to use as the pivot point
     * @param targetX x coordinate of the target point the {@code part} should face, in model space
     * @param targetY y coordinate of the target point the {@code part} should face, in model space
     * @param targetZ z coordinate of the target point the {@code part} should face, in model space
     * @param up up vector the ModelPart should face
     * @param tempVDir Vector3f object to work with, contains the direction vector after the call
     * @param tempM Matrix3f object to work with, contains the rotation after the call
     */
    public static void pointModelAtModelWithUp(
        ModelPart part, float targetX, float targetY, float targetZ, Vector3fc up, Vector3f tempVDir, Matrix3f tempM)
    {
        // calculate direction
        tempVDir.set(targetX - part.x, targetY - part.y, targetZ - part.z);

        // rotate model
        pointAtModel(tempVDir, up, tempM);
    }

    /**
     * rotates the given Matrix3f to point in the {@code dir} model direction
     * @param dir direction Vector the matrix should look at
     * @param upDir up direction for the look matrix
     * @param tempM Matrix3f object to work with, contains the rotation after the call
     */
    public static void pointAtModel(Vector3fc dir, Vector3fc upDir, Matrix3f tempM) {
        tempM.setLookAlong(
            -dir.x(), -dir.y(), dir.z(),
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
     * estimates the direction the limb joint should be in
     * @param upper upper body part
     * @param lower lower body part
     * @param lowerRot rotation of lower body part
     * @param bodyYaw players Y rotation
     * @param jointDown if the joint should go up or down
     * @param jointPos available joint position, can be {@code null}
     * @param player player the {@code jointPos} is from
     * @param rotInfo player VR info
     * @param useWorldScale when set will cancel out the worldScale, instead of entity scale
     * @param tempV Vector3f object to work with, contains the joint direction after the call
     * @param tempV2 Vector3f object to work with
     */
    public static void estimateJointDir(
        ModelPart upper, ModelPart lower, Quaternionfc lowerRot, float bodyYaw, boolean jointDown,
        @Nullable Vector3fc jointPos, LivingEntity player, VRPlayersClient.RotInfo rotInfo, boolean useWorldScale,
        Vector3f tempV, Vector3f tempV2)
    {
        if (jointPos != null) {
            // use mid arm point to joint direction
            tempV.set(upper.x + lower.x, upper.y + lower.y, upper.z + lower.z)
                .mul(0.5F);
            ModelUtils.worldToModel(player, jointPos, rotInfo, bodyYaw, useWorldScale, tempV2);
            tempV2.sub(tempV, tempV);
        } else {
            // point the elbow away from the hand direction
            // hand direction, up forward/down back
            lowerRot.transform(0F, jointDown ? -1F : 1F, jointDown ? 1F : -1F, tempV);
            ModelUtils.worldToModelDirection(tempV, bodyYaw, tempV);
        }
        // arm dir
        tempV2.set(lower.x - upper.x, lower.y - upper.y, lower.z - upper.z);

        // calculate the vector perpendicular to the arm dir
        float dot = tempV2.dot(tempV) / tempV2.dot(tempV2);
        tempV2.mul(dot);
        tempV.sub(tempV2).normalize();
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
