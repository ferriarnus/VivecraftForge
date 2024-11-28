package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.common.utils.math.Matrix4f;
import org.vivecraft.common.utils.math.Quaternion;
import org.vivecraft.common.utils.math.Vector3;

public class CameraTracker extends Tracker {
    public static final ModelResourceLocation CAMERA_MODEL = new ModelResourceLocation("vivecraft", "camera", "");
    public static final ModelResourceLocation CAMERA_DISPLAY_MODEL = new ModelResourceLocation("vivecraft", "camera_display", "");

    private boolean visible = false;
    private Vec3 position = new Vec3(0.0D, 0.0D, 0.0D);
    private Quaternion rotation = new Quaternion();

    private int startController;
    private VRData.VRDevicePose startControllerPose;
    private Vec3 startPosition;
    private Quaternion startRotation;
    private boolean quickMode;

    public CameraTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        if (this.mc.gameMode == null) {
            return false;
        } else if (this.dh.vrSettings.seated) {
            return false;
        } else {
            return this.isVisible();
        }
    }

    @Override
    public void doProcess(LocalPlayer player) {
        if (this.startControllerPose != null) {
            VRData.VRDevicePose controllerPose = this.dh.vrPlayer.vrdata_world_render.getController(this.startController);
            Vec3 startPos = this.startControllerPose.getPosition();
            Vec3 deltaPos = controllerPose.getPosition().subtract(startPos);

            Matrix4f deltaMatrix = Matrix4f.multiply(controllerPose.getMatrix(), this.startControllerPose.getMatrix().inverted());
            Vector3 offset = new Vector3(
                (float) this.startPosition.x - (float) startPos.x,
                (float) this.startPosition.y - (float) startPos.y,
                (float) this.startPosition.z - (float) startPos.z);
            Vector3 offsetRotated = deltaMatrix.transform(offset);

            this.position = new Vec3(
                this.startPosition.x + deltaPos.x + offsetRotated.getX() - offset.getX(),
                this.startPosition.y + deltaPos.y + offsetRotated.getY() - offset.getY(),
                this.startPosition.z + deltaPos.z + offsetRotated.getZ() - offset.getZ());
            this.rotation = this.startRotation.multiply(new Quaternion(MathUtils.convertOVRMatrix(deltaMatrix)));
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

    public Vec3 getPosition() {
        return this.position;
    }

    public void setPosition(Vec3 position) {
        this.position = position;
    }

    public Quaternion getRotation() {
        return this.rotation;
    }

    public void setRotation(Quaternion rotation) {
        this.rotation = rotation;
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
        this.startRotation = this.rotation.copy();
        this.quickMode = quickMode;
    }

    public void startMoving(int controller) {
        this.startMoving(controller, false);
    }

    public void stopMoving() {
        this.startControllerPose = null;
    }
}
