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
        return getBodyPartData(VRBodyPart.HEAD);
    }

    /**
     * Gets the body part data for a given controller.
     *
     * @param controller The controller number to get, with 0 being the primary controller.
     * @return The specified controller's pose data.
     */
    default VRBodyPartData getController(int controller) {
        if (controller != 0 && controller != 1) {
            throw new IllegalArgumentException("Controller number must be controller 0 or controller 1.");
        }
        return controller == 0 ? getBodyPartData(VRBodyPart.MAIN_HAND) : getBodyPartData(VRBodyPart.OFF_HAND);
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
     * Gets the pose for a given controller.
     *
     * @param hand The interaction hand to get controller data for.
     * @return The specified controller's pose data.
     */
    default VRBodyPartData getController(InteractionHand hand) {
        return getController(hand.ordinal());
    }

    /**
     * Gets the pose for the primary controller.
     *
     * @return The main controller's pose data.
     */
    default VRBodyPartData getController0() {
        return getController(0);
    }

    /**
     * Gets the pose for the secondary controller.
     *
     * @return The main controller's pose data.
     */
    default VRBodyPartData getController1() {
        return getController(1);
    }
}
