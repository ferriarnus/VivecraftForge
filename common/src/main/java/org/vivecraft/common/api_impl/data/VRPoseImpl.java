package org.vivecraft.common.api_impl.data;

import org.vivecraft.api.data.FBTMode;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.api.data.VRBodyPartData;

import javax.annotation.Nullable;

public record VRPoseImpl(VRBodyPartData hmd, VRBodyPartData c0, VRBodyPartData c1,
                         VRBodyPartData rightFoot, VRBodyPartData leftFoot,
                         VRBodyPartData waist,
                         VRBodyPartData rightKnee, VRBodyPartData leftKnee,
                         VRBodyPartData rightElbow, VRBodyPartData leftElbow,
                         boolean isSeated, boolean isLeftHanded, FBTMode fbtMode) implements VRPose {

    @Override
    @Nullable
    public VRBodyPartData getBodyPartData(VRBodyPart vrBodyPart) {
        if (vrBodyPart == null) {
            throw new IllegalArgumentException("Cannot get a null body part's data!");
        }
        return switch (vrBodyPart) {
            case HMD -> this.hmd;
            case MAIN_HAND -> this.c0;
            case OFF_HAND -> this.c1;
            case RIGHT_FOOT -> this.rightFoot;
            case LEFT_FOOT -> this.leftFoot;
            case WAIST -> this.waist;
            case RIGHT_KNEE -> this.rightKnee;
            case LEFT_KNEE -> this.leftKnee;
            case RIGHT_ELBOW -> this.rightElbow;
            case LEFT_ELBOW -> this.leftElbow;
        };
    }

    @Override
    public FBTMode getFBTMode() {
        return this.fbtMode;
    }

    @Override
    public String toString() {
        return "HMD: " + getHMD() + "\nController 0: " + getMainHand() + "\nController 1: " + getOffHand();
    }
}
