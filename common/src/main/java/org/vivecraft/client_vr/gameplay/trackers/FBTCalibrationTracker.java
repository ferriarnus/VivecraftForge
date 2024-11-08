package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.settings.AutoCalibration;

public class FBTCalibrationTracker extends Tracker {
    private static final float distanceThreshold = 0.2F;

    private Vector3f[] oldOffsets;
    private Quaternionf[] oldRotations;


    public boolean calibrationActive = false;
    public boolean leftHandAtPosition = false;
    public boolean rightHandAtPosition = false;
    public boolean ready = false;

    public FBTCalibrationTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    public void startCalibration() {
        this.calibrationActive = true;

        this.oldOffsets = this.dh.vrSettings.fbtOffsets;
        this.oldRotations = this.dh.vrSettings.fbtRotations;

        this.dh.vrSettings.fbtOffsets = this.dh.vrSettings.getFbtOffsetDefault();
        this.dh.vrSettings.fbtRotations = this.dh.vrSettings.getFbtRotationsDefault();
    }

    public void calibrate() {
        AutoCalibration.calibrateManual();
        this.dh.vr.calibrateFBT();
        this.dh.vrSettings.saveOptions();
        this.calibrationActive = false;
    }

    public void cancelCalibration() {
        this.dh.vrSettings.fbtOffsets = this.oldOffsets;
        this.dh.vrSettings.fbtRotations = this.oldRotations;
        this.calibrationActive = false;
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        return this.calibrationActive;
    }

    @Override
    public void reset(LocalPlayer player) {
        this.leftHandAtPosition = this.rightHandAtPosition = this.ready = false;
    }

    @Override
    public void doProcess(LocalPlayer player) {
        float hmdYaw = this.dh.vrPlayer.vrdata_room_post.hmd.getYawRad();
        Vector3f hmdPosAvg = this.dh.vr.hmdPivotHistory.averagePosition(0.5D);

        float height = hmdPosAvg.y / AutoCalibration.DEFAULT_HEIGHT;
        float scale = height * 0.9375F * this.dh.vrPlayer.getVRDataWorld().worldScale;

        boolean rightHandNew = this.dh.vrPlayer.vrdata_room_post.getController(0).getPositionF()
            .sub(hmdPosAvg.x , 0.0F, hmdPosAvg.z)
            .rotateY(hmdYaw)
            .distance(-scale + distanceThreshold, scale * 1.375F, 0) < distanceThreshold;
        boolean leftHandNew = this.dh.vrPlayer.vrdata_room_post.getController(1).getPositionF()
            .sub(hmdPosAvg.x , 0.0F, hmdPosAvg.z)
            .rotateY(hmdYaw)
            .distance(scale - distanceThreshold, scale * 1.375F, 0) < distanceThreshold;

        if (!this.rightHandAtPosition && rightHandNew) {
            this.dh.vr.triggerHapticPulse(ControllerType.RIGHT, 0.01F, 100, 1F);
        }
        if (!this.leftHandAtPosition && leftHandNew) {
            this.dh.vr.triggerHapticPulse(ControllerType.LEFT, 0.01F, 100, 1F);
        }
        this.rightHandAtPosition = rightHandNew;
        this.leftHandAtPosition = leftHandNew;
        this.ready = this.rightHandAtPosition && this.leftHandAtPosition;
    }

    @Override
    public EntryPoint getEntryPoint() {
        return EntryPoint.SPECIAL_ITEMS;
    }
}
