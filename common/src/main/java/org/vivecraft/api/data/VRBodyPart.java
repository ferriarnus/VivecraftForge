package org.vivecraft.api.data;

/**
 * The device tracking a specific body part.
 */
public enum VRBodyPart {
    HMD,
    MAIN_HAND,
    OFF_HAND,
    RIGHT_FOOT,
    LEFT_FOOT,
    WAIST,
    RIGHT_KNEE,
    LEFT_KNEE,
    RIGHT_ELBOW,
    LEFT_ELBOW;

    /**
     * @return The opposite body part to this one, or the same body part if it has no opposite.
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
     * @param mode The full-body tracking mode to check.
     * @return Whether this body part has available data in the provided mode.
     */
    public boolean availableInMode(FBTMode mode) {
        return switch (this) {
            case HMD, MAIN_HAND, OFF_HAND -> true;
            case RIGHT_FOOT, LEFT_FOOT, WAIST -> mode != FBTMode.ARMS_ONLY;
            case RIGHT_KNEE, LEFT_KNEE, RIGHT_ELBOW, LEFT_ELBOW -> mode == FBTMode.WITH_JOINTS;
        };
    }
}
