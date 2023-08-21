package org.vivecraft.api.client;

import com.google.common.annotations.Beta;
import net.minecraft.world.InteractionHand;
import org.vivecraft.api.client.data.VRPoseHistory;
import org.vivecraft.api.data.VRData;
import org.vivecraft.client.api_impl.ClientAPIImpl;
import org.vivecraft.client_vr.render.RenderPass;

import javax.annotation.Nullable;

public interface VivecraftClientAPI {

    static VivecraftClientAPI getInstance() {
        return ClientAPIImpl.INSTANCE;
    }

    /**
     * Registers the tracker to the list of all trackers to be run for the local player. See the documentation for
     * {@link Tracker} for more information on what a tracker is.
     *
     * @param tracker Tracker to register.
     */
    void registerTracker(Tracker tracker);

    /**
     * Gets data representing the devices as they exist in the room before the game tick.
     * Note that this data is gathered BEFORE mod loaders' pre-tick events.
     *
     * @return Data representing the devices in the room pre-tick, or null if the local player isn't in VR.
     */
    @Nullable
    VRData getPreTickRoomData();

    /**
     * Gets data representing the devices as they exist in the room after the game tick.
     * Note that this data is gathered AFTER mod loaders' post-tick events.
     *
     * @return Data representing the devices in the room post-tick, or null if the local player isn't in VR.
     */
    @Nullable
    VRData getPostTickRoomData();

    /**
     * Gets data representing the devices as they exist in Minecraft coordinates before the game tick.
     * This is the same as {@link #getPreTickRoomData()} with translation to Minecraft's coordinates as of the last
     * tick, and is the main data source used by Vivecraft. If you're unsure which {@link VRData} method to use, you
     * likely want to use this one.
     * Note that this data is gathered BEFORE mod loaders' pre-tick events.
     *
     * @return Data representing the devices in Minecraft space pre-tick, or null if the local player isn't in VR.
     */
    @Nullable
    VRData getPreTickWorldData();

    /**
     * Gets data representing the devices as they exist in Minecraft coordinates after the game tick.
     * This is the data sent to the server, and also used to calculate the data in {@link #getWorldRenderData()}.
     * Note that this data is gathered AFTER mod loaders' post-tick events.
     *
     * @return Data representing the devices in Minecraft space post-tick, or null if the local player isn't in VR.
     */
    @Nullable
    VRData getPostTickWorldData();

    /**
     * Gets data representing the devices as they exist in Minecraft coordinates after the game tick interpolated for
     * rendering.
     * This is the same data as {@link #getPostTickWorldData()}, however it is interpolated for rendering.
     *
     * @return Data representing the devices in Minecraft space post-tick interpolated for rendering, or null if the
     * local player isn't in VR.
     */
    @Nullable
    VRData getWorldRenderData();

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
     * @return Whether the client player is currently in seated mode.
     */
    boolean isSeated();

    /**
     * @return Whether the client player is using reversed hands.
     */
    boolean usingReversedHands();

    /**
     * @return Whether VR support is initialized.
     */
    boolean isVrInitialized();

    /**
     * @return Whether the client is actively in VR.
     */
    boolean isVrActive();

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
    VRPoseHistory getHistoricalVRHMDPoses();

    /**
     * Returns the history of VR poses for the player for a controller. Will return null if the player isn't
     * in VR.
     *
     * @param controller The controller number to get, with 0 being the primary controller.
     * @return The historical VR data for the player's controller, or null if the player isn't in VR.
     */
    @Nullable
    VRPoseHistory getHistoricalVRControllerPoses(int controller);

    /**
     * Returns the history of VR poses for the player for a controller. Will return null if the player isn't
     * in VR.
     *
     * @param hand The hand to get controller history for.
     * @return The historical VR data for the player's controller, or null if the player isn't in VR.
     */
    @Nullable
    default VRPoseHistory getHistoricalVRControllerPoses(InteractionHand hand) {
        return getHistoricalVRControllerPoses(hand.ordinal());
    }

    /**
     * Returns the history of VR poses for the player for the primary controller. Will return null if the
     * player isn't in VR.
     *
     * @return The historical VR data for the player's primary controller, or null if the player isn't in VR.
     */
    @Nullable
    default VRPoseHistory getHistoricalVRController0Poses() {
        return getHistoricalVRControllerPoses(0);
    }

    /**
     * Returns the history of VR poses for the player for the secondary controller. Will return null if the
     * player isn't in VR.
     *
     * @return The historical VR data for the player's secondary controller, or null if the player isn't in VR.
     */
    @Nullable
    default VRPoseHistory getHistoricalVRController1Poses() {
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

    /**
     * @return Whether the current render pass is a vanilla render pass.
     */
    @Beta
    boolean isVanillaRenderPass();

    /**
     * @return The current render pass Vivecraft is performing.
     */
    @Beta
    RenderPass getCurrentRenderPass();

    /**
     * @return Whether the current render pass is the first one it performed for this render cycle.
     */
    @Beta
    boolean isFirstRenderPass();
}
