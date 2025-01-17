package org.vivecraft.api.data;

import net.minecraft.world.InteractionHand;

import javax.annotation.Nullable;

/**
 * Represents the pose of the VR player. In other words, the position and rotation data of all tracked body parts of
 * the VR player.
 */
public interface VRPose {

    /**
     * Gets the pose data for a body part.
     *
     * @param vrBodyPart The body part to get the pose data for.
     * @return The specified body part's pose data, or null if that body part is not being tracked.
     */
    @Nullable
    VRBodyPartData getBodyPartData(VRBodyPart vrBodyPart);

    /**
     * @return Body part pose data for the HMD.
     */
    default VRBodyPartData getHMD() {
        return getBodyPartData(VRBodyPart.HMD);
    }

    /**
     * Gets the body part data for a given hand.
     *
     * @param hand The hand number to get, with 0 being the main-hand and 1 being the off-hand.
     * @return The specified hand's pose data.
     */
    default VRBodyPartData getHand(int hand) {
        if (hand != 0 && hand != 1) {
            throw new IllegalArgumentException("Hand number must be 0 or 1.");
        }
        return hand == 0 ? getBodyPartData(VRBodyPart.MAIN_HAND) : getBodyPartData(VRBodyPart.OFF_HAND);
    }

    /**
     * @return Whether the player is currently in seated mode.
     */
    boolean isSeated();

    /**
     * @return Whether the player is playing with left-handed controls.
     */
    boolean isLeftHanded();

    /**
     * @return The full-body tracking mode currently in-use.
     */
    FBTMode getFBTMode();

    /**
     * Gets the pose for a given hand.
     *
     * @param hand The interaction hand to get hand data for.
     * @return The specified hand's pose data.
     */
    default VRBodyPartData getHand(InteractionHand hand) {
        return getHand(hand.ordinal());
    }

    /**
     * Gets the pose for the main-hand.
     *
     * @return The main-hand's pose data.
     */
    default VRBodyPartData getMainHand() {
        return getHand(0);
    }

    /**
     * Gets the pose for the off-hand.
     *
     * @return The off-hand's pose data.
     */
    default VRBodyPartData getOffHand() {
        return getHand(1);
    }
}
