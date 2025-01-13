package org.vivecraft.common.api_impl.data;

import org.vivecraft.api.data.VRPose;
import org.vivecraft.api.data.VRBodyPart;

public class VRPoseImpl implements VRPose {

    private final VRBodyPart hmd;
    private final VRBodyPart c0;
    private final VRBodyPart c1;
    private final boolean isSeated;
    private final boolean isLeftHanded;

    public VRPoseImpl(VRBodyPart hmd, VRBodyPart c0, VRBodyPart c1, boolean isSeated, boolean isLeftHanded) {
        this.hmd = hmd;
        this.c0 = c0;
        this.c1 = c1;
        this.isSeated = isSeated;
        this.isLeftHanded = isLeftHanded;
    }

    @Override
    public VRBodyPart getHMD() {
        return this.hmd;
    }

    @Override
    public VRBodyPart getController(int controller) {
        if (controller != 0 && controller != 1) {
            throw new IllegalArgumentException("Controller number must be controller 0 or controller 1.");
        }
        return controller == 0 ? this.c0 : this.c1;
    }

    @Override
    public boolean isSeated() {
        return this.isSeated;
    }

    @Override
    public boolean isLeftHanded() {
        return this.isLeftHanded;
    }

    @Override
    public String toString() {
        return "HMD: " + getHMD() + "\nController 0: " + getController0() + "\nController 1: " + getController1();
    }
}
