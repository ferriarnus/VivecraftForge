package org.vivecraft.client_vr;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.client.ClientVRPlayers;
import org.vivecraft.client.gui.screens.FBTCalibrationScreen;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.network.FBTMode;
import org.vivecraft.common.utils.MathUtils;

import javax.annotation.Nullable;
import java.lang.Math;
import org.joml.Quaternionf;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.common.api_impl.data.VRPoseImpl;
import org.vivecraft.common.api_impl.data.VRBodyPartImpl;

public class VRData {
    // headset center
    public VRDevicePose hmd;
    // left eye
    public VRDevicePose eye0;
    // right eye
    public VRDevicePose eye1;
    // main controller aim
    public VRDevicePose c0;
    // offhand controller aim
    public VRDevicePose c1;
    // third person camera
    public VRDevicePose c2;

    // main controller hand
    public VRDevicePose h0;
    // offhand controller hand
    public VRDevicePose h1;

    // main controller telescope
    public VRDevicePose t0;
    // offhand controller telescope
    public VRDevicePose t1;

    // screenshot camera
    public VRDevicePose cam;

    // fbt trackers
    public VRDevicePose waist;
    public VRDevicePose foot_left;
    public VRDevicePose foot_right;
    public VRDevicePose knee_left;
    public VRDevicePose knee_right;
    public VRDevicePose elbow_left;
    public VRDevicePose elbow_right;

    public FBTMode fbtMode = FBTMode.ARMS_ONLY;

    // room origin, all VRDevicePose are relative to that
    public Vec3 origin;
    // room rotation rotated around the origin
    public float rotation_radians;
    // pose positions get scaled by that
    public float worldScale;

    public VRData(Vec3 origin, float walkMul, float worldScale, float rotation) {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();
        MCVR mcVR = dataHolder.vr;

        this.origin = origin;
        this.worldScale = worldScale;
        this.rotation_radians = rotation;

        Vector3f hmd_raw = mcVR.getEyePosition(RenderPass.CENTER);
        Vector3f scaledPos = new Vector3f(hmd_raw.x * walkMul, hmd_raw.y, hmd_raw.z * walkMul);

        Vector3f scaleOffset = new Vector3f(scaledPos.x - hmd_raw.x, 0.0F, scaledPos.z - hmd_raw.z);

        // headset
        this.hmd = new VRDevicePose(this, mcVR.hmdRotation, scaledPos, mcVR.getHmdVector());

        this.eye0 = new VRDevicePose(this,
            mcVR.getEyeRotation(RenderPass.LEFT),
            mcVR.getEyePosition(RenderPass.LEFT).add(scaleOffset),
            mcVR.getHmdVector());

        this.eye1 = new VRDevicePose(this,
            mcVR.getEyeRotation(RenderPass.RIGHT),
            mcVR.getEyePosition(RenderPass.RIGHT).add(scaleOffset),
            mcVR.getHmdVector());

        // controllers
        Vector3fc mainAimSource = mcVR.getAimSource(0).add(scaleOffset, new Vector3f());
        Vector3fc offAimSource = mcVR.getAimSource(1).add(scaleOffset, new Vector3f());
        this.c0 = new VRDevicePose(this,
            mcVR.getAimRotation(0),
            mainAimSource,
            mcVR.getAimVector(0));
        this.c1 = new VRDevicePose(this,
            mcVR.getAimRotation(1),
            offAimSource,
            mcVR.getAimVector(1));

        this.h0 = new VRDevicePose(this,
            mcVR.getHandRotation(0),
            mainAimSource,
            mcVR.getHandVector(0));
        this.h1 = new VRDevicePose(this,
            mcVR.getHandRotation(1),
            offAimSource,
            mcVR.getHandVector(1));

        // telescopes
        if (dataHolder.vrSettings.seated) {
            this.t0 = this.eye0;
            this.t1 = this.eye1;
        } else {
            Matrix4f scopeMain = this.getSmoothedRotation(0, 0.2F);
            Matrix4f scopeOff = this.getSmoothedRotation(1, 0.2F);
            this.t0 = new VRDevicePose(this,
                scopeMain,
                mainAimSource,
                scopeMain.transformDirection(MathUtils.BACK, new Vector3f()));
            this.t1 = new VRDevicePose(this,
                scopeOff,
                offAimSource,
                scopeOff.transformDirection(MathUtils.BACK, new Vector3f()));
        }

        // screenshot camera
        Matrix4f camRot = new Matrix4f().rotationY(-rotation)
            .mul(dataHolder.cameraTracker.getRotation().get(new Matrix4f()));
        this.cam = new VRData.VRDevicePose(this,
            camRot,
            dataHolder.cameraTracker.getRoomPosition(origin)
                .rotateY(-rotation).div(worldScale).add(scaleOffset),
            camRot.transformDirection(MathUtils.BACK, new Vector3f()));

        // third person camera
        if (mcVR.mrMovingCamActive) {
            this.c2 = new VRDevicePose(this,
                mcVR.getAimRotation(2),
                mcVR.getAimSource(2).add(scaleOffset, new Vector3f()),
                mcVR.getAimVector(2));
        } else {
            VRSettings vrsettings = dataHolder.vrSettings;
            Matrix4f rot = vrsettings.vrFixedCamrotQuat.get(new Matrix4f());
            Vector3f pos = new Vector3f(vrsettings.vrFixedCampos);
            Vector3f dir = rot.transformDirection(MathUtils.BACK, new Vector3f());
            this.c2 = new VRDevicePose(this,
                rot,
                pos.add(scaleOffset),
                dir);
        }

        // fbt
        if (mcVR.hasFBT() && dataHolder.vrSettings.fbtCalibrated &&
            !(Minecraft.getInstance().screen instanceof FBTCalibrationScreen))
        {
            this.fbtMode = FBTMode.ARMS_LEGS;
            this.waist = new VRDevicePose(this,
                mcVR.getAimRotation(MCVR.WAIST_TRACKER),
                mcVR.getAimSource(MCVR.WAIST_TRACKER).add(scaleOffset, new Vector3f()),
                mcVR.getAimVector(MCVR.WAIST_TRACKER));
            this.foot_left = new VRDevicePose(this,
                mcVR.getAimRotation(MCVR.LEFT_FOOT_TRACKER),
                mcVR.getAimSource(MCVR.LEFT_FOOT_TRACKER).add(scaleOffset, new Vector3f()),
                mcVR.getAimVector(MCVR.LEFT_FOOT_TRACKER));
            this.foot_right = new VRDevicePose(this,
                mcVR.getAimRotation(MCVR.RIGHT_FOOT_TRACKER),
                mcVR.getAimSource(MCVR.RIGHT_FOOT_TRACKER).add(scaleOffset, new Vector3f()),
                mcVR.getAimVector(MCVR.RIGHT_FOOT_TRACKER));
            if (mcVR.hasExtendedFBT() && dataHolder.vrSettings.fbtExtendedCalibrated) {
                this.fbtMode = FBTMode.WITH_JOINTS;
                this.knee_left = new VRDevicePose(this,
                    mcVR.getAimRotation(MCVR.LEFT_KNEE_TRACKER),
                    mcVR.getAimSource(MCVR.LEFT_KNEE_TRACKER).add(scaleOffset, new Vector3f()),
                    mcVR.getAimVector(MCVR.LEFT_KNEE_TRACKER));
                this.knee_right = new VRDevicePose(this,
                    mcVR.getAimRotation(MCVR.RIGHT_KNEE_TRACKER),
                    mcVR.getAimSource(MCVR.RIGHT_KNEE_TRACKER).add(scaleOffset, new Vector3f()),
                    mcVR.getAimVector(MCVR.RIGHT_KNEE_TRACKER));
                this.elbow_left = new VRDevicePose(this,
                    mcVR.getAimRotation(MCVR.LEFT_ELBOW_TRACKER),
                    mcVR.getAimSource(MCVR.LEFT_ELBOW_TRACKER).add(scaleOffset, new Vector3f()),
                    mcVR.getAimVector(MCVR.LEFT_ELBOW_TRACKER));
                this.elbow_right = new VRDevicePose(this,
                    mcVR.getAimRotation(MCVR.RIGHT_ELBOW_TRACKER),
                    mcVR.getAimSource(MCVR.RIGHT_ELBOW_TRACKER).add(scaleOffset, new Vector3f()),
                    mcVR.getAimVector(MCVR.RIGHT_ELBOW_TRACKER));
            }
        }
    }

    /**
     * gets the smoothed rotation matrix of the specified controller
     *
     * @param c      controller to get
     * @param lenSec time period in seconds
     * @return smoothed rotation
     */
    private Matrix4f getSmoothedRotation(int c, float lenSec) {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

        Vector3f forward = dataHolder.vr.controllerForwardHistory[c].averagePosition(lenSec);
        Vector3f up = dataHolder.vr.controllerUpHistory[c].averagePosition(lenSec);
        Vector3f right = forward.cross(up, new Vector3f());

        return new Matrix4f(new Matrix3f(right, forward, up));
    }

    /**
     * @param c controller index
     * @return the device pose for the specified controller
     */
    public VRDevicePose getController(int c) {
        return c == 1 ? this.c1 : (c == 2 ? this.c2 : this.c0);
    }

    /**
     * @param c device index
     * @return the hand device pose for the specified device, if the specified device doesn't have a hand post the regular pose is returned
     */
    @Nullable
    public VRDevicePose getHand(int c) {
        return c > 1 ? getDevice(c) : c == 0 ? this.h0 : this.h1;
    }

    /**
     * @param d tracked device index
     * @return the device pose for the specified device, if the device is not available {@code null} is returned
     */
    @Nullable
    public VRDevicePose getDevice(int d) {
        return switch (d) {
            case MCVR.OFFHAND_CONTROLLER -> this.c1;
            case MCVR.CAMERA_TRACKER -> this.c2;
            case MCVR.WAIST_TRACKER -> this.waist;
            case MCVR.LEFT_FOOT_TRACKER -> this.foot_left;
            case MCVR.RIGHT_FOOT_TRACKER -> this.foot_right;
            case MCVR.LEFT_KNEE_TRACKER -> this.knee_left;
            case MCVR.RIGHT_KNEE_TRACKER -> this.knee_right;
            case MCVR.LEFT_ELBOW_TRACKER -> this.elbow_left;
            case MCVR.RIGHT_ELBOW_TRACKER -> this.elbow_right;
            default -> this.c0;
        };
    }

    /**
     * @return the yaw direction the player body is facing, in degrees
     */
    public float getBodyYaw() {
        return Mth.RAD_TO_DEG * getBodyYawRad();
    }

    /**
     * IMPORTANT!!! when changing this, also change {@link ClientVRPlayers.RotInfo#getBodyYawRad()}
     *
     * @return the yaw direction the player body is facing, in radians
     */
    public float getBodyYawRad() {
        if (ClientDataHolderVR.getInstance().vrSettings.seated) {
            // in seated use the head direction
            return this.hmd.getYawRad();
        } else if (this.fbtMode != FBTMode.ARMS_ONLY) {
            // use average of head and waist
            Vector3f dir = this.waist.getDirection().lerp(this.hmd.getDirection(), 0.5F);
            return (float) Math.atan2(-dir.x, dir.z);
        } else {
            if (!ClientDataHolderVR.getInstance().vr.isControllerTracking(0) ||
                !ClientDataHolderVR.getInstance().vr.isControllerTracking(1))
            {
                return this.hmd.getYawRad();
            }

            Vec3 headPos = this.hmd.getPosition();

            Vector3f c1Pos = this.c1.getPosition().subtract(headPos).toVector3f();
            Vector3f c0Pos = this.c0.getPosition().subtract(headPos).toVector3f();
            Vector3f head = this.hmd.getDirection();

            return MathUtils.bodyYawRad(c0Pos, c1Pos, head);
        }
    }

    /**
     * @return the yaw direction the players hands is facing, in degrees
     */
    public float getFacingYaw() {
        return Mth.RAD_TO_DEG * getFacingYawRad();
    }

    /**
     * @return the yaw direction the players hands is facing, in radians
     */
    public float getFacingYawRad() {
        if (ClientDataHolderVR.getInstance().vrSettings.seated) {
            return this.hmd.getYawRad();
        } else {
            Vector3f arms = MathUtils.subtractToVector3f(this.c1.getPosition(), this.c0.getPosition())
                .normalize().rotateY(-Mth.HALF_PI);
            // with reverseHands c0 is the left hand, not right
            if (ClientDataHolderVR.getInstance().vrSettings.reverseHands) {
                return (float) Math.atan2(arms.x, -arms.z);
            } else {
                return (float) Math.atan2(-arms.x, arms.z);
            }
        }
    }

    /**
     * @return estimated pivot point that the players head rotates around, in world space
     */
    public Vec3 getHeadPivot() {
        Vec3 eye = this.hmd.getPosition();
        // scale pivot point with world scale, to prevent unwanted player movement
        Vector3f headPivotOffset = this.hmd.getMatrix()
            .transformPosition(new Vector3f(0.0F, -0.1F * this.worldScale, 0.1F * this.worldScale));
        return eye.add(headPivotOffset.x, headPivotOffset.y, headPivotOffset.z);
    }

    /**
     * calculates the head pivot estimation with the provided room origin and scale
     *
     * @param newOrigin     new room origin to use instead of the one linked to this VRData
     * @param newWorldScale new world scale to use instead of the one linked to this VRData
     * @return estimated pivot point that the players head rotates around, in world space
     */
    public Vec3 getNewHeadPivot(Vec3 newOrigin, float newWorldScale) {
        Vec3 newEye = this.hmd.getPosition().subtract(this.origin).add(newOrigin);
        // scale pivot point with world scale, to prevent unwanted player movement
        Vector3f headPivotOffset = this.hmd.getMatrix()
            .transformPosition(new Vector3f(0.0F, -0.1F * newWorldScale, 0.1F * newWorldScale));
        return newEye.add(headPivotOffset.x, headPivotOffset.y, headPivotOffset.z);
    }

    /**
     * @return estimated point that is behind the players back, in world space
     */
    public Vec3 getHeadRear() {
        Vec3 eye = this.hmd.getPosition();
        Vector3f headBackOffset = this.hmd.getMatrix()
            .transformPosition(new Vector3f(0.0F, -0.2F * this.worldScale, 0.2F * this.worldScale));
        return eye.add(headBackOffset.x, headBackOffset.y, headBackOffset.z);
    }

    /**
     * @param pass RenderPass to get the VRDevicePose for
     * @return VRDevicePose corresponding to that RenderPass, or HMD if no matching pose is available
     */
    public VRDevicePose getEye(RenderPass pass) {
        return switch (pass) {
            case CENTER -> this.hmd;
            case LEFT -> this.eye0;
            case RIGHT -> this.eye1;
            case THIRD -> this.c2;
            case SCOPER -> this.t0;
            case SCOPEL -> this.t1;
            case CAMERA -> this.cam;
            default -> this.hmd;
        };
    }

    /**
     * @return this data in a manner better-suited for the API
     */
    public VRPose asVRPose() {
        return new VRPoseImpl(
            this.hmd.asVRBodyPart(),
            this.c0.asVRBodyPart(),
            this.c1.asVRBodyPart(),
            ClientDataHolderVR.getInstance().vrSettings.seated,
            ClientDataHolderVR.getInstance().vrSettings.reverseHands
        );
    }

    @Override
    public String toString() {
        return """
            VRData:
                origin: %s
                rotation: %.2f
                scale: %.2f
                hmd: %s
                c0: %s
                c1: %s
                c2: %s
            """
            .formatted(
                this.origin,
                this.rotation_radians,
                this.worldScale,
                this.hmd,
                this.c0,
                this.c1,
                this.c2
            );
    }

    public class VRDevicePose {
        // link to the parent, holds the rotation, scale and origin
        private final VRData data;
        // in room position
        private final Vector3fc pos;
        // in room direction
        private final Vector3fc dir;
        // in room orientation
        private final Matrix4fc matrix;

        public VRDevicePose(VRData data, Matrix4fc matrix, Vector3fc pos, Vector3fc dir) {
            this.data = data;
            this.matrix = new Matrix4f(matrix);
            this.pos = pos;
            this.dir = dir;
        }

        /**
         * @return position of this device in world space
         */
        public Vec3 getPosition() {
            Vector3f localPos = this.pos.mul(VRData.this.worldScale, new Vector3f())
                .rotateY(this.data.rotation_radians);
            return this.data.origin.add(localPos.x, localPos.y, localPos.z);
        }

        /**
         * returns the world position as a float Vector, is safe to use for VRData marked as {@code room}
         *
         * @return position of this device in world space
         */
        public Vector3f getPositionF() {
            Vector3f localPos = this.pos.mul(VRData.this.worldScale, new Vector3f())
                .rotateY(this.data.rotation_radians);
            return localPos.add((float) this.data.origin.x, (float) this.data.origin.y, (float) this.data.origin.z);
        }

        /**
         * calculates the difference between the current worldScale and the given {@code newWorldScale}
         * the result of this call is newPos - oldPos
         *
         * @param newWorldScale new world scale
         * @return returns the position offset from changed world scale
         */
        public Vector3f getScalePositionOffset(float newWorldScale) {
            Vector3f oldPos = this.pos.mul(VRData.this.worldScale, new Vector3f())
                .rotateY(this.data.rotation_radians);
            Vector3f newPos = this.pos.mul(newWorldScale, new Vector3f())
                .rotateY(this.data.rotation_radians);
            return newPos.sub(oldPos);
        }

        /**
         * @return direction of this device in world space
         */
        public Vector3f getDirection() {
            return this.dir.rotateY(this.data.rotation_radians, new Vector3f());
        }

        /**
         * transforms the device local vector {@code axis} to world space
         *
         * @param axis local vector to transform
         * @return {@code axis} transformed into world space
         */
        public Vector3f getCustomVector(Vector3fc axis) {
            return this.matrix.transformDirection(axis, new Vector3f())
                .rotateY(this.data.rotation_radians);
        }

        /**
         * @return yaw of the device in world space, in degrees
         */
        public float getYaw() {
            return Mth.RAD_TO_DEG * this.getYawRad();
        }

        /**
         * @return yaw of the device in world space, in radians
         */
        public float getYawRad() {
            Vector3f dir = this.getDirection();
            return (float) Math.atan2(-dir.x, dir.z);
        }

        /**
         * @return pitch of the device in world space, in degrees
         */
        public float getPitch() {
            return Mth.RAD_TO_DEG * this.getPitchRad();
        }

        /**
         * @return pitch of the device in world space, in radians
         */
        public float getPitchRad() {
            Vector3f dir = this.getDirection();
            return (float) Math.asin(dir.y / dir.length());
        }

        /**
         * @return roll of the device in world space, in degrees
         */
        public float getRoll() {
            return Mth.RAD_TO_DEG * this.getRollRad();
        }

        /**
         * @return pitch of the device in world space, in radians
         */
        public float getRollRad() {
            return (float) -Math.atan2(this.matrix.m01(), this.matrix.m11());
        }

        /**
         * @return pose matrix of the device in world space
         */
        public Matrix4f getMatrix() {
            return new Matrix4f().rotationY(VRData.this.rotation_radians).mul(this.matrix);
        }

        public VRBodyPart asVRBodyPart() {
            Quaternionf quat = new Quaternionf();
            quat.setFromUnnormalized(getMatrix());
            return new VRBodyPartImpl(
                getPosition(),
                new Vec3(getDirection()),
                quat
            );
        }

        @Override
        public String toString() {
            return "Device: pos:" + this.getPosition() + ", dir: " + this.getDirection();
        }
    }
}
