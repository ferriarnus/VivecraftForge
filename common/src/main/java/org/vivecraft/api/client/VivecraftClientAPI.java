package org.vivecraft.api.client;

import net.minecraft.world.InteractionHand;
import org.vivecraft.api.client.data.VRBodyPartHistory;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.client.api_impl.VivecraftClientAPIImpl;

import javax.annotation.Nullable;

/**
 * The main interface for interacting with Vivecraft from client code. For rendering, one should use
 * {@link VivecraftRenderingAPI}.
 */
public interface VivecraftClientAPI {

    static VivecraftClientAPI getInstance() {
        return VivecraftClientAPIImpl.INSTANCE;
    }

    /**
     * Registers the tracker to the list of all trackers to be run for the local player. See the documentation for
     * {@link Tracker} for more information on what a tracker is.
     *
     * @param tracker Tracker to register.
     */
    void registerTracker(Tracker tracker);

    /**
     * Gets the VR pose representing the player in the room before the game tick.
     * Note that this pose is gathered BEFORE mod loaders' pre-tick events.
     *
     * @return The VR pose representing the player in the room pre-tick, or null if the local player isn't in VR.
     */
    @Nullable
    VRPose getPreTickRoomPose();

    /**
     * Gets the VR pose representing the player in the room after the game tick.
     * Note that this pose is gathered AFTER mod loaders' post-tick events.
     *
     * @return The VR pose representing the player in the room post-tick, or null if the local player isn't in VR.
     */
    @Nullable
    VRPose getPostTickRoomPose();

    /**
     * Gets the VR pose representing the player in Minecraft world coordinates before the game tick.
     * This is the same as {@link #getPreTickRoomPose()} with translation to in-game world coordinates as of the last
     * tick, and is the main pose source used by Vivecraft for gameplay. If you're unsure which {@link VRPose} method
     * to use, you likely want to use this one.
     * Note that this pose is gathered BEFORE mod loaders' pre-tick events.
     *
     * @return The VR pose representing the player in world space pre-tick, or null if the local player isn't in VR.
     */
    @Nullable
    VRPose getPreTickWorldPose();

    /**
     * Gets the VR pose representing the player in Minecraft world coordinates after the game tick.
     * This is the pose sent to the server, and also used to calculate the pose in {@link #getWorldRenderPose()}.
     * Note that this pose is gathered AFTER mod loaders' post-tick events.
     *
     * @return The VR pose representing the player in Minecraft space post-tick, or null if the local player isn't in VR.
     */
    @Nullable
    VRPose getPostTickWorldPose();

    /**
     * Gets the VR pose representing the player in Minecraft world coordinates after the game tick interpolated for
     * rendering.
     * This is the same pose as {@link #getPostTickWorldPose()}, however it is interpolated for rendering.
     *
     * @return The VR pose representing the player in Minecraft space post-tick interpolated for rendering, or null if
     * the local player isn't in VR.
     */
    @Nullable
    VRPose getWorldRenderPose();

    /**
     * Causes a haptic pulse (vibration/rumble) for the specified controller.
     * This function silently fails if called for players not in VR or players who are in seated mode.
     *
     * @param controllerNum The controller number to trigger a haptic pulse. 0 is the primary controller, while 1 is
     *                      the secondary controller.
     * @param duration      The duration of the haptic pulse in seconds. Note that this number is passed to the
     *                      underlying VR API used by Vivecraft, and may act with a shorter length than expected beyond
     *                      very short pulses.
     * @param frequency     The frequency of the haptic pulse in Hz. 160 is a safe bet for this number, with Vivecraft's codebase
     *                      using anywhere from 160F for actions such as a bite on a fishing line, to 1000F for things such
     *                      as a chat notification.
     * @param amplitude     The amplitude of the haptic pulse. This should be kept between 0F and 1F.
     * @param delay         An amount of time to delay until creating the haptic pulse. The majority of the time, one should use 0F here.
     */
    void triggerHapticPulse(int controllerNum, float duration, float frequency, float amplitude, float delay);

    /**
     * Causes a haptic pulse (vibration/rumble) for the specified controller.
     * This function silently fails if called for players not in VR or players who are in seated mode.
     *
     * @param controllerNum The controller number to trigger a haptic pulse. 0 is the primary controller, while 1 is
     *                      the secondary controller.
     * @param duration      The duration of the haptic pulse in seconds. Note that this number is passed to the
     *                      underlying VR API used by Vivecraft, and may act with a shorter length than expected beyond
     *                      very short pulses.
     */
    default void triggerHapticPulse(int controllerNum, float duration) {
        triggerHapticPulse(controllerNum, duration, 160F, 1F, 0F);
    }

    /**
     * @return Whether the local player is currently in seated mode.
     */
    boolean isSeated();

    /**
     * @return Whether the local player is playing with left-handed controls.
     */
    boolean isLeftHanded();

    /**
     * @return Whether VR support is initialized.
     */
    boolean isVRInitialized();

    /**
     * @return Whether the client is actively in VR.
     */
    boolean isVRActive();

    /**
     * @return The currently active world scale.
     */
    float getWorldScale();

    /**
     * Returns the history of VR poses for the player for the HMD. Will return null if the player isn't
     * in VR.
     *
     * @return The historical VR data for the player's HMD, or null if the player isn't in VR.
     */
    @Nullable
    VRBodyPartHistory getHistoricalVRHMDPoses();

    /**
     * Returns the history of VR poses for the player for a controller. Will return null if the player isn't
     * in VR.
     *
     * @param controller The controller number to get, with 0 being the primary controller.
     * @return The historical VR data for the player's controller, or null if the player isn't in VR.
     */
    @Nullable
    VRBodyPartHistory getHistoricalVRControllerPoses(int controller);

    /**
     * Returns the history of VR poses for the player for a controller. Will return null if the player isn't
     * in VR.
     *
     * @param hand The hand to get controller history for.
     * @return The historical VR data for the player's controller, or null if the player isn't in VR.
     */
    @Nullable
    default VRBodyPartHistory getHistoricalVRControllerPoses(InteractionHand hand) {
        return getHistoricalVRControllerPoses(hand.ordinal());
    }

    /**
     * Returns the history of VR poses for the player for the primary controller. Will return null if the
     * player isn't in VR.
     *
     * @return The historical VR data for the player's primary controller, or null if the player isn't in VR.
     */
    @Nullable
    default VRBodyPartHistory getHistoricalVRController0Poses() {
        return getHistoricalVRControllerPoses(0);
    }

    /**
     * Returns the history of VR poses for the player for the secondary controller. Will return null if the
     * player isn't in VR.
     *
     * @return The historical VR data for the player's secondary controller, or null if the player isn't in VR.
     */
    @Nullable
    default VRBodyPartHistory getHistoricalVRController1Poses() {
        return getHistoricalVRControllerPoses(1);
    }

    /**
     * Opens or closes Vivecraft's keyboard. Will fail silently if the user isn't in VR or if the keyboard's new state
     * is the same as the old.
     *
     * @param isNowOpen Whether the keyboard should now be open. If false, the keyboard will attempt to close.
     * @return Whether the keyboard is currently showing after attempting to open/close it.
     */
    boolean setKeyboardState(boolean isNowOpen);
}
