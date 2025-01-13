package org.vivecraft.api.data;

import net.minecraft.world.InteractionHand;

/**
 * Represents the pose of the VR player. In other words, the position and rotation data of all tracked body parts of
 * the VR player.
 */
public interface VRPose {

    /**
     * @return Pose data for the HMD.
     */
    VRBodyPart getHMD();

    /**
     * Gets the pose data for a given controller.
     *
     * @param controller The controller number to get, with 0 being the primary controller.
     * @return The specified controller's pose data.
     */
    VRBodyPart getController(int controller);

    /**
     * @return Whether the player is currently in seated mode.
     */
    boolean isSeated();

    /**
     * @return Whether the player is playing with left-handed controls.
     */
    boolean isLeftHanded();

    /**
     * Gets the pose for a given controller.
     *
     * @param hand The interaction hand to get controller data for.
     * @return The specified controller's pose data.
     */
    default VRBodyPart getController(InteractionHand hand) {
        return getController(hand.ordinal());
    }

    /**
     * Gets the pose for the primary controller.
     *
     * @return The main controller's pose data.
     */
    default VRBodyPart getController0() {
        return getController(0);
    }

    /**
     * Gets the pose for the secondary controller.
     *
     * @return The main controller's pose data.
     */
    default VRBodyPart getController1() {
        return getController(1);
    }
}
