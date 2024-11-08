package org.vivecraft.client_vr.provider.nullvr;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.*;
import org.lwjgl.glfw.GLFW;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.utils.ClientUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.MethodHolder;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.VRRenderer;
import org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction;
import org.vivecraft.client_vr.render.MirrorNotification;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.network.FBTMode;
import org.vivecraft.common.utils.MathUtils;

import java.lang.Math;
import java.util.List;

/**
 * MCVR implementation that does not interact with any runtime.
 */
public class NullVR extends MCVR {
    private static final float IPD = 0.1F;

    protected static NullVR OME;

    private boolean vrActive = true;

    protected static final int HEAD_TRACKER = CAMERA_TRACKER;

    private BodyPart currentBodyPart = BodyPart.HEAD;
    private FBTMode fbtMode = FBTMode.ARMS_ONLY;

    private ControllerTransform controllerType = ControllerTransform.NULL;

    private final Vector3f[] deviceOffsets = new Vector3f[trackableDeviceCount];
    private final Quaternionf[] deviceRotations = new Quaternionf[trackableDeviceCount];

    public NullVR(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh, VivecraftVRMod.INSTANCE);
        OME = this;
        this.hapticScheduler = new NullVRHapticScheduler();

        this.deviceOffsets[MAIN_CONTROLLER] = new Vector3f(0.3F, 1.25F, -0.5F);
        this.deviceOffsets[OFFHAND_CONTROLLER] = this.deviceOffsets[MAIN_CONTROLLER]
            .mul(-1, 1, 1, new Vector3f());

        // use the camera Tracker for the head
        this.deviceOffsets[HEAD_TRACKER] = new Vector3f(0.0F, 1.62F, 0.0F);

        this.deviceOffsets[WAIST_TRACKER] = new Vector3f(0.0F, 0.85F, 0.0F);

        this.deviceOffsets[RIGHT_FOOT_TRACKER] = new Vector3f(0.15F, 0.05F, 0.0F);
        this.deviceOffsets[LEFT_FOOT_TRACKER] = this.deviceOffsets[RIGHT_FOOT_TRACKER]
            .mul(-1, 1, 1, new Vector3f());
        this.deviceOffsets[RIGHT_ELBOW_TRACKER] = new Vector3f(0.3F, 1.25F, -0.1F);
        this.deviceOffsets[LEFT_ELBOW_TRACKER] = this.deviceOffsets[RIGHT_ELBOW_TRACKER]
            .mul(-1,1,1, new Vector3f());
        this.deviceOffsets[RIGHT_KNEE_TRACKER] = new Vector3f(0.15F, 0.5F, 0.0F);
        this.deviceOffsets[LEFT_KNEE_TRACKER] = this.deviceOffsets[RIGHT_KNEE_TRACKER]
            .mul(-1,1,1, new Vector3f());

        for(int i = 0; i < trackableDeviceCount; i++) {
            this.deviceRotations[i] = new Quaternionf();
        }
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
            //this.dh.vrSettings.seated = true;

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

            boolean swapHands = this.dh.vrSettings.reverseHands;

            this.controllerPose[MAIN_CONTROLLER] = this.controllerType.tipR.invert(new Matrix4f());
            this.controllerPose[MAIN_CONTROLLER]
                .rotate(this.deviceRotations[swapHands ? OFFHAND_CONTROLLER : MAIN_CONTROLLER]);
            this.controllerPose[MAIN_CONTROLLER]
                .setTranslation(this.deviceOffsets[swapHands ? OFFHAND_CONTROLLER : MAIN_CONTROLLER]);

            this.controllerPose[OFFHAND_CONTROLLER] = this.controllerType.tipL.invert(new Matrix4f());
            this.controllerPose[OFFHAND_CONTROLLER]
                .rotate(this.deviceRotations[swapHands ? MAIN_CONTROLLER : OFFHAND_CONTROLLER]);
            this.controllerPose[OFFHAND_CONTROLLER]
                .setTranslation(this.deviceOffsets[swapHands ? MAIN_CONTROLLER : OFFHAND_CONTROLLER]);

            // waist
            this.controllerPose[WAIST_TRACKER].rotation(this.deviceRotations[WAIST_TRACKER]);
            this.controllerPose[WAIST_TRACKER].setTranslation(this.deviceOffsets[WAIST_TRACKER]);

            this.hmdPose.rotation(this.deviceRotations[HEAD_TRACKER]);
            this.hmdPose.setTranslation(this.deviceOffsets[HEAD_TRACKER]);

            // fbt trackers index 4-9
            for (int i = 4; i < trackableDeviceCount; i++) {
                this.controllerPose[i].rotation(this.deviceRotations[i]);
                this.controllerPose[i].setTranslation(this.deviceOffsets[i]);
            }

            this.dh.vrSettings.xSensitivity = xSens;
            this.dh.vrSettings.keyholeX = xKey;

            // point head in cursor direction
            if (this.dh.vrSettings.seated) {
                this.hmdRotation.set3x3(this.handRotation[0]);
            }

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
        return switch (componentName) {
            case "tip" -> controllerIndex == MAIN_CONTROLLER ? this.controllerType.tipR : this.controllerType.tipL;
            case "handgrip" ->
                controllerIndex == MAIN_CONTROLLER ? this.controllerType.handGripR : this.controllerType.handGripL;
            default -> new Matrix4f();
        };
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
    public boolean hasFBT() {
        return this.fbtMode != FBTMode.ARMS_ONLY;
    }

    @Override
    public boolean hasExtendedFBT() {
        return this.fbtMode == FBTMode.WITH_JOINTS;
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
        boolean triggered = false;
        if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            if (action == GLFW.GLFW_PRESS) {
                if (key == GLFW.GLFW_KEY_F6) {
                    this.vrActive = !this.vrActive;
                    return true;
                }

                int offset = MethodHolder.isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT) ? -1 : 1;

                if (key == GLFW.GLFW_KEY_F9) {
                    this.controllerType = ClientUtils.getNextEnum(this.controllerType, offset);
                    Vector3f tipForward = this.controllerType.tipR.transformDirection(MathUtils.BACK, new Vector3f());
                    Vector3f handForward = this.controllerType.handGripR.transformDirection(MathUtils.BACK,
                        new Vector3f());
                    this.gunAngle = (float) Math.toDegrees(Math.acos(Math.abs(tipForward.dot(handForward))));
                    this.gunStyle = this.gunAngle > 10.0F;
                    MirrorNotification.notify("Changed to controller: " + this.currentBodyPart, false, 1000);
                    triggered = true;
                } else if (key == GLFW.GLFW_KEY_KP_5) {
                    // toggle body part
                    this.currentBodyPart = ClientUtils.getNextEnum(this.currentBodyPart, offset);
                    MirrorNotification.notify("Changed selected body part to: " + this.currentBodyPart, false, 1000);
                    triggered = true;
                } else if (key == GLFW.GLFW_KEY_KP_1) {
                    // toggle fbt mode
                    this.fbtMode = ClientUtils.getNextEnum(this.fbtMode, offset);
                    MirrorNotification.notify("Changed fbt mode to: " + this.fbtMode, false, 1000);
                    triggered = true;
                }
            }

            if (action != GLFW.GLFW_RELEASE) {
                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT)) {
                    // rotate current bodypart
                    if (key == GLFW.GLFW_KEY_KP_8) {
                        rotateBody(-Mth.PI/18.0F, MathUtils.RIGHT);
                        triggered = true;
                    }
                    if (key == GLFW.GLFW_KEY_KP_2) {
                        rotateBody(Mth.PI/18.0F, MathUtils.RIGHT);
                        triggered = true;
                    }
                    if (key == GLFW.GLFW_KEY_KP_4) {
                        rotateBody(Mth.PI/18.0F, MathUtils.UP);
                        triggered = true;
                    }
                    if (key == GLFW.GLFW_KEY_KP_6) {
                        rotateBody(-Mth.PI/18.0F, MathUtils.UP);
                        triggered = true;
                    }
                    if (key == GLFW.GLFW_KEY_KP_9) {
                        rotateBody(Mth.PI/18.0F, MathUtils.BACK);
                        triggered = true;
                    }
                    if (key == GLFW.GLFW_KEY_KP_7) {
                        rotateBody(-Mth.PI/18.0F, MathUtils.BACK);
                        triggered = true;
                    }
                } else {
                    // move current bodypart
                    if (key == GLFW.GLFW_KEY_KP_8) {
                        translateBody(0F,0F,-0.01F);
                        triggered = true;
                    }
                    if (key == GLFW.GLFW_KEY_KP_2) {
                        translateBody(0F,0F,0.01F);
                        triggered = true;
                    }
                    if (key == GLFW.GLFW_KEY_KP_4) {
                        translateBody(-0.01F, 0F, 0F);
                        triggered = true;
                    }
                    if (key == GLFW.GLFW_KEY_KP_6) {
                        translateBody(0.01F, 0F, 0F);
                        triggered = true;
                    }
                    if (key == GLFW.GLFW_KEY_KP_9) {
                        translateBody(0F, 0.01F, 0F);
                        triggered = true;
                    }
                    if (key == GLFW.GLFW_KEY_KP_3) {
                        translateBody(0F, -0.01F, 0F);
                        triggered = true;
                    }
                }
            }
        }

        return triggered;
    }

    private void rotateBody(float angle, Vector3fc axis) {
        this.deviceRotations[this.currentBodyPart.rightIndex].rotateAxis(angle, axis);
        if (this.currentBodyPart.leftIndex != -1) {
            if (axis == MathUtils.RIGHT) {
                this.deviceRotations[this.currentBodyPart.leftIndex].rotateAxis(angle, axis);
            } else {
                this.deviceRotations[this.currentBodyPart.leftIndex].rotateAxis(-angle, axis);
            }
        }
    }
    private void translateBody(float x, float y, float z) {
        this.deviceOffsets[this.currentBodyPart.rightIndex].add(x, y, z);
        if (this.currentBodyPart.leftIndex != -1) {
            this.deviceOffsets[this.currentBodyPart.leftIndex].add(-x, y, z);
        }
    }
}
