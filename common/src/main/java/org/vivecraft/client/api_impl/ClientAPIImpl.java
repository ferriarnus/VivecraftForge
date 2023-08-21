package org.vivecraft.client.api_impl;

import org.jetbrains.annotations.Nullable;
import org.vivecraft.api.client.Tracker;
import org.vivecraft.api.client.data.VRPoseHistory;
import org.vivecraft.api.client.VivecraftClientAPI;
import org.vivecraft.api.data.VRData;
import org.vivecraft.client.api_impl.data.VRPoseHistoryImpl;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassType;

public final class ClientAPIImpl implements VivecraftClientAPI {

    public static final ClientAPIImpl INSTANCE = new ClientAPIImpl();

    private final VRPoseHistoryImpl hmdHistory = new VRPoseHistoryImpl();
    private VRPoseHistoryImpl c0History = new VRPoseHistoryImpl();
    private VRPoseHistoryImpl c1History = new VRPoseHistoryImpl();

    private ClientAPIImpl() {
    }

    public void clearHistories() {
        this.hmdHistory.clear();
        this.c0History.clear();
        this.c1History.clear();
    }

    public void addPosesToHistory(VRData data) {
        this.hmdHistory.addPose(data.getHMD());
        this.c0History.addPose(data.getController0());
        this.c1History.addPose(data.getController1());
    }

    @Nullable
    @Override
    public VRData getPreTickRoomData() {
        if (!isVrActive()) {
            return null;
        }
        return ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_pre.asVRData();
    }

    @Nullable
    @Override
    public VRData getPostTickRoomData() {
        if (!isVrActive()) {
            return null;
        }
        return ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_post.asVRData();
    }

    @Nullable
    @Override
    public VRData getPreTickWorldData() {
        if (!isVrActive()) {
            return null;
        }
        return ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_pre.asVRData();
    }

    @Nullable
    @Override
    public VRData getPostTickWorldData() {
        if (!isVrActive()) {
            return null;
        }
        return ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_post.asVRData();
    }

    @Nullable
    @Override
    public VRData getWorldRenderData() {
        if (!isVrActive()) {
            return null;
        }
        return ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.asVRData();
    }

    @Override
    public void triggerHapticPulse(int controllerNum, float duration, float frequency, float amplitude, float delay) {
        if (controllerNum != 0 && controllerNum != 1) {
            throw new IllegalArgumentException("Can only trigger a haptic pulse for controllers 0 and 1.");
        }
        if (amplitude < 0F || amplitude > 1F) {
            throw new IllegalArgumentException("The amplitude of a haptic pulse must be between 0 and 1.");
        }
        if (isVrActive() && !isSeated()) {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(
                ControllerType.values()[controllerNum],
                duration,
                frequency,
                amplitude,
                delay
            );
        }
    }

    @Override
    public boolean isSeated() {
        return ClientDataHolderVR.getInstance().vrSettings.seated;
    }

    @Override
    public boolean usingReversedHands() {
        return ClientDataHolderVR.getInstance().vrSettings.reverseHands;
    }

    @Override
    public boolean isVrInitialized() {
        return VRState.VR_INITIALIZED;
    }

    @Override
    public boolean isVrActive() {
        return VRState.VR_RUNNING;
    }

    @Override
    public float getWorldScale() {
        if (isVrActive()) {
            return ClientDataHolderVR.getInstance().vrPlayer.worldScale;
        } else {
            return 1f;
        }
    }

    @Override
    public void registerTracker(Tracker tracker) {
        ClientDataHolderVR.getInstance().registerTracker(tracker);
    }

    @Nullable
    @Override
    public VRPoseHistory getHistoricalVRHMDPoses() {
        if (!isVrActive()) {
            return null;
        }
        return this.hmdHistory;
    }

    @Nullable
    @Override
    public VRPoseHistory getHistoricalVRControllerPoses(int controller) {
        if (controller != 0 && controller != 1) {
            throw new IllegalArgumentException("Historical VR controller data only available for controllers 0 and 1.");
        } else if (!isVrActive()) {
            return null;
        }
        return controller == 0 ? this.c0History : this.c1History;
    }

    @Override
    public boolean setKeyboardState(boolean isNowOpen) {
        if (isVrActive()) {
            return KeyboardHandler.setOverlayShowing(isNowOpen);
        }
        return false;
    }

    @Override
    public boolean isVanillaRenderPass() {
        return RenderPassType.isVanilla();
    }

    @Override
    public RenderPass getCurrentRenderPass() {
        return ClientDataHolderVR.getInstance().currentPass;
    }

    @Override
    public boolean isFirstRenderPass() {
        return ClientDataHolderVR.getInstance().isFirstPass;
    }
}
