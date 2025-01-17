package org.vivecraft.api.data;

/**
 * The mode used for full-body tracking, denoting which body parts are being tracked.
 */
public enum FBTMode {
    /**
     * Only HMD and hands are available.
     */
    ARMS_ONLY,
    /**
     * HMD, hands, waist, and feet trackers are available.
     */
    ARMS_LEGS,
    /**
     * HMD, hands, waist, feet, elbow, and knee trackers are available.
     */
    WITH_JOINTS;

    /**
     * Whether the provided body part is available in this full-body tracking mode.
     * @param bodyPart The body part to see if data is available for in this mode.
     * @return Whether the provided body part is available in this mode.
     */
    public boolean bodyPartAvailable(VRBodyPart bodyPart) {
        return bodyPart.availableInMode(this);
    }
}
