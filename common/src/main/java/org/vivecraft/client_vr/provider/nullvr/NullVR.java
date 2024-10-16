package org.vivecraft.client_vr.provider.nullvr;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.joml.*;
import org.lwjgl.glfw.GLFW;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.MethodHolder;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.VRRenderer;
import org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction;
import org.vivecraft.client_vr.settings.VRSettings;

import java.util.List;

/**
 * MCVR implementation that does not interact with any runtime.
 */
public class NullVR extends MCVR {
    private static final float IPD = 0.1F;

    protected static NullVR OME;

    private boolean vrActive = true;

    public NullVR(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh, VivecraftVRMod.INSTANCE);
        OME = this;
        this.hapticScheduler = new NullVRHapticScheduler();
    }

    public static NullVR get() {
        return OME;
    }

    @Override
    public void destroy() {
        this.initialized = false;
    }

    @Override
    public String getName() {
        return "nullDriver";
    }

    @Override
    public Vector2fc getPlayAreaSize() {
        return new Vector2f(2);
    }

    @Override
    public boolean init() {
        if (!this.initialized) {
            this.mc = Minecraft.getInstance();

            // only supports seated mode
            VRSettings.LOGGER.info("Vivecraft: NullDriver. Forcing seated mode.");
            this.dh.vrSettings.seated = true;

            this.headIsTracking = true;
            this.hmdPose.identity();
            this.hmdPose.m31(1.62F);

            // eye offset, 10cm total distance
            this.hmdPoseLeftEye.m30(-IPD * 0.5F);
            this.hmdPoseRightEye.m30(IPD * 0.5F);

            this.populateInputActions();

            this.initialized = true;
            this.initSuccess = true;
        }

        return this.initialized;
    }

    @Override
    public void poll(long frameIndex) {
        if (this.initialized) {

            this.mc.getProfiler().push("updatePose");

            // don't permanently change the sensitivity
            float xSens = this.dh.vrSettings.xSensitivity;
            float xKey = this.dh.vrSettings.keyholeX;

            this.dh.vrSettings.xSensitivity = this.dh.vrSettings.ySensitivity * 1.636F *
                ((float) this.mc.getWindow().getScreenWidth() / (float) this.mc.getWindow().getScreenHeight());
            this.dh.vrSettings.keyholeX = 1;

            this.updateAim();

            this.controllerPose[0].setTranslation(
                this.dh.vrSettings.reverseHands ? -0.3F : 0.3F,
                1.2F,
                -0.5F);

            this.controllerPose[1].setTranslation(
                this.dh.vrSettings.reverseHands ? 0.3F : -0.3F,
                1.2F,
                -0.5F);

            this.dh.vrSettings.xSensitivity = xSens;
            this.dh.vrSettings.keyholeX = xKey;


            // point head in cursor direction
            this.hmdRotation.set3x3(this.handRotation[0]);

            if (GuiHandler.GUI_ROTATION_ROOM != null) {
                // look at screen, so that it's centered
                this.hmdRotation.set3x3(GuiHandler.GUI_ROTATION_ROOM);
            }
            this.mc.getProfiler().popPush("hmdSampling");
            this.hmdSampling();

            this.mc.getProfiler().pop();
        }
    }

    @Override
    public void processInputs() {}

    @Override
    protected ControllerType findActiveBindingControllerType(KeyMapping keyMapping) {
        return null;
    }

    @Override
    public Matrix4fc getControllerComponentTransform(int controllerIndex, String componentName) {
        return new Matrix4f();
    }

    @Override
    public String getOriginName(long origin) {
        return "NullDriver";
    }

    @Override
    public boolean hasCameraTracker() {
        return false;
    }

    @Override
    public List<Long> getOrigins(VRInputAction action) {
        return null;
    }

    @Override
    public VRRenderer createVRRenderer() {
        return new NullVRStereoRenderer(this);
    }

    @Override
    public boolean isActive() {
        return this.vrActive;
    }

    @Override
    public boolean capFPS() {
        return true;
    }

    @Override
    public float getIPD() {
        return IPD;
    }

    @Override
    public String getRuntimeName() {
        return "Null";
    }

    @Override
    public boolean handleKeyboardInputs(int key, int scanCode, int action, int modifiers) {
        if (action == GLFW.GLFW_PRESS && key == GLFW.GLFW_KEY_F6 && MethodHolder.isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            this.vrActive = !this.vrActive;
            return true;
        }
        return false;
    }
}
