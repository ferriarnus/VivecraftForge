package org.vivecraft.api.data;

public enum VRBodyPart {
    MAIN_HAND,
    OFF_HAND,
    RIGHT_FOOT,
    LEFT_FOOT,
    WAIST,
    RIGHT_KNEE,
    LEFT_KNEE,
    RIGHT_ELBOW,
    LEFT_ELBOW,
    HMD;

    /**
     * @return the opposite limb
     */
    public VRBodyPart opposite() {
        return switch (this) {
            case MAIN_HAND -> OFF_HAND;
            case OFF_HAND -> MAIN_HAND;
            case RIGHT_FOOT -> LEFT_FOOT;
            case LEFT_FOOT -> RIGHT_FOOT;
            case RIGHT_KNEE -> LEFT_KNEE;
            case LEFT_KNEE -> RIGHT_KNEE;
            case RIGHT_ELBOW -> LEFT_ELBOW;
            case LEFT_ELBOW -> RIGHT_ELBOW;
            default -> this;
        };
    }

    /**
     * Whether this body part type is available in the provided full-body tracking mode.
     * @param fbtMode The full-body tracking mode to check.
     * @return Whether this body part has available data in the provided mode.
     */
    public boolean availableInMode(FBTMode fbtMode) {
        return switch (this) {
            case HMD, MAIN_HAND, OFF_HAND -> true;
            case RIGHT_FOOT, LEFT_FOOT, WAIST -> fbtMode != FBTMode.ARMS_ONLY;
            case RIGHT_KNEE, LEFT_KNEE, RIGHT_ELBOW, LEFT_ELBOW -> fbtMode == FBTMode.WITH_JOINTS;
        };
    }

    /**
     * @return Whether this body part is a foot.
     */
    public boolean isFoot() {
        return this == RIGHT_FOOT || this == LEFT_FOOT;
    }

    /**
     * @return Whether this body part is a hand.
     */
    public boolean isHand() {
        return this == MAIN_HAND || this == OFF_HAND;
    }
}
