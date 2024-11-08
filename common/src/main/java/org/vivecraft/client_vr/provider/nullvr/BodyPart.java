package org.vivecraft.client_vr.provider.nullvr;

import org.vivecraft.client_vr.provider.MCVR;

public enum BodyPart {
    HAND(MCVR.MAIN_CONTROLLER, MCVR.OFFHAND_CONTROLLER),
    FOOT(MCVR.RIGHT_FOOT_TRACKER, MCVR.LEFT_FOOT_TRACKER),
    ELBOW(MCVR.RIGHT_ELBOW_TRACKER, MCVR.LEFT_ELBOW_TRACKER),
    KNEE(MCVR.RIGHT_KNEE_TRACKER, MCVR.LEFT_KNEE_TRACKER),
    WAIST(MCVR.WAIST_TRACKER, -1),
    HEAD(NullVR.HEAD_TRACKER, -1);
    public final int rightIndex;
    public final int leftIndex;

    BodyPart(int rightIndex, int leftIndex) {
        this.rightIndex = rightIndex;
        this.leftIndex = leftIndex;
    }
}
