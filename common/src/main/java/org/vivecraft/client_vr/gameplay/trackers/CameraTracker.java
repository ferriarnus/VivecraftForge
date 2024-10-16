package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.vivecraft.client.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.render.RenderPass;

public class CameraTracker extends Tracker {
    public static final ModelResourceLocation CAMERA_MODEL = new ModelResourceLocation("vivecraft", "camera", "");
    public static final ModelResourceLocation CAMERA_DISPLAY_MODEL = new ModelResourceLocation("vivecraft", "camera_display", "");

    private boolean visible = false;
    private Vec3 position = new Vec3(0.0D, 0.0D, 0.0D);
    private Quaternionf rotation = new Quaternionf();

    private int startController;
    private VRData.VRDevicePose startControllerPose;
    private Vec3 startPosition;
    private Quaternionf startRotation;
    private boolean quickMode;

    public CameraTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        if (this.mc.gameMode == null) {
            return false;
        /*} else if (this.dh.vrSettings.seated) {
            return false;*/
        } else {
            return this.isVisible();
        }
    }

    @Override
    public void doProcess(LocalPlayer player) {
        if (this.startControllerPose != null) {
            VRData.VRDevicePose controllerPose = this.dh.vrPlayer.vrdata_world_render.getController(this.startController);
            Vec3 startPos = this.startControllerPose.getPosition();
            Vector3f deltaPos = MathUtils.subtractToVector3f(controllerPose.getPosition(), startPos);

            Matrix4f deltaMatrix = controllerPose.getMatrix().mul(this.startControllerPose.getMatrix().invert());
            Vector3f offset = MathUtils.subtractToVector3f(this.startPosition, startPos);
            Vector3f offsetRotated = deltaMatrix.transformPosition(offset, new Vector3f());

            this.position = new Vec3(
                this.startPosition.x + deltaPos.x + offsetRotated.x() - offset.x(),
                this.startPosition.y + deltaPos.y + offsetRotated.y() - offset.y(),
                this.startPosition.z + deltaPos.z + offsetRotated.z() - offset.z());
            Quaternionf tempQuat = deltaMatrix.getNormalizedRotation(new Quaternionf());
            this.rotation = tempQuat.mul(this.startRotation, tempQuat);
        }

        if (this.quickMode && !this.isMoving() && !this.dh.grabScreenShot) {
            this.visible = false;
        }

        // chunk renderer gets angry if we're really far away, force hide when >3/4 render distance
        if (this.dh.vrPlayer.vrdata_world_render.getEye(RenderPass.CENTER).getPosition().distanceTo(this.position) > this.mc.options.getEffectiveRenderDistance() * 12) {
            this.visible = false;
        }
    }

    @Override
    public void reset(LocalPlayer player) {
        this.visible = false;
        this.quickMode = false;
        this.stopMoving();
    }

    @Override
    public EntryPoint getEntryPoint() {
        return EntryPoint.SPECIAL_ITEMS; // smoother camera movement
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void toggleVisibility() {
        this.visible = !this.visible;
    }

    /**
     * @return camera postion in world space
     */
    public Vec3 getPosition() {
        return this.position;
    }

    /**
     * calculates the room local position of the camera
     * @param roomOrigin room origin, if it is Vec3.ZERO it is ignored and the VRPlayers room origin is used instead
     * @return position relative to the room origin
     */
    public Vector3f getRoomPosition(Vec3 roomOrigin) {
        if (roomOrigin == Vec3.ZERO && this.dh.vrPlayer != null) {
            return MathUtils.subtractToVector3f(this.position, this.dh.vrPlayer.roomOrigin);
        } else {
            return MathUtils.subtractToVector3f(this.position, roomOrigin);
        }
    }

    /**
     * set camera postion in world space
     */
    public void setPosition(Vec3 position) {
        this.position = position;
    }

    public Quaternionf getRotation() {
        return this.rotation;
    }

    public void setRotation(Quaternionf rotation) {
        this.rotation = rotation;
    }

    public void setRotation(Matrix4fc rotationMat) {
        rotationMat.getNormalizedRotation(this.rotation);
    }

    public boolean isMoving() {
        return this.startControllerPose != null;
    }

    public int getMovingController() {
        return this.startController;
    }

    public boolean isQuickMode() {
        return this.quickMode;
    }

    public void startMoving(int controller, boolean quickMode) {
        this.startController = controller;
        this.startControllerPose = this.dh.vrPlayer.vrdata_world_pre.getController(controller);
        this.startPosition = this.position;
        this.startRotation = new Quaternionf(this.rotation);
        this.quickMode = quickMode;
    }

    public void startMoving(int controller) {
        this.startMoving(controller, false);
    }

    public void stopMoving() {
        this.startControllerPose = null;
    }
}
