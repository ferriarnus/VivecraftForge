package org.vivecraft.client_vr.provider;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.*;
import org.lwjgl.glfw.GLFW;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client_vr.settings.AutoCalibration;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.QuaternionfHistory;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.Vector3fHistory;
import org.vivecraft.client_vr.extensions.GuiExtension;
import org.vivecraft.client_vr.extensions.WindowExtension;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.gameplay.trackers.ClimbTracker;
import org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction;
import org.vivecraft.client_vr.provider.openvr_lwjgl.control.VRInputActionSet;
import org.vivecraft.client_vr.render.RenderConfigException;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRHotkeys;
import org.vivecraft.client_vr.settings.VRSettings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.Math;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class MCVR {
    public static final int MAIN_CONTROLLER = 0;
    public static final int OFFHAND_CONTROLLER = 1;

    public static final int RIGHT_CONTROLLER = 0;
    public static final int LEFT_CONTROLLER = 1;
    public static final int CAMERA_TRACKER = 2;
    public static final int WAIST_TRACKER = 3;
    public static final int RIGHT_FOOT_TRACKER = 4;
    public static final int LEFT_FOOT_TRACKER = 5;
    public static final int RIGHT_ELBOW_TRACKER = 6;
    public static final int LEFT_ELBOW_TRACKER = 7;
    public static final int RIGHT_KNEE_TRACKER = 8;
    public static final int LEFT_KNEE_TRACKER = 9;

    public static final int trackableDeviceCount = 10;

    protected Minecraft mc;
    protected ClientDataHolderVR dh;
    protected static MCVR ME;
    protected static VivecraftVRMod MOD;

    protected HardwareType detectedHardware = HardwareType.VIVE;

    // position/orientation of headset and eye offsets
    protected Matrix4f hmdPose = new Matrix4f();
    public Matrix4f hmdRotation = new Matrix4f();
    protected Matrix4f hmdPoseLeftEye = new Matrix4f();
    protected Matrix4f hmdPoseRightEye = new Matrix4f();

    public Vector3fHistory hmdHistory = new Vector3fHistory();
    public Vector3fHistory hmdPivotHistory = new Vector3fHistory();
    public QuaternionfHistory hmdRotHistory = new QuaternionfHistory();
    protected boolean headIsTracking;
    protected Matrix4f[] controllerPose = new Matrix4f[trackableDeviceCount];
    protected Matrix4f[] controllerRotation = new Matrix4f[trackableDeviceCount];
    protected boolean[] controllerTracking = new boolean[trackableDeviceCount];
    protected Matrix4f[] handRotation = new Matrix4f[trackableDeviceCount];
    public Vector3fHistory[] controllerHistory = new Vector3fHistory[]{new Vector3fHistory(), new Vector3fHistory()};
    public Vector3fHistory[] controllerForwardHistory = new Vector3fHistory[]{new Vector3fHistory(), new Vector3fHistory()};
    public Vector3fHistory[] controllerUpHistory = new Vector3fHistory[]{new Vector3fHistory(), new Vector3fHistory()};
    public Vector3fHistory[] feetHistory = new Vector3fHistory[]{new Vector3fHistory(), new Vector3fHistory()};
    protected float gunAngle = 0.0F;
    protected boolean gunStyle;
    public boolean initialized;
    public String initStatus;
    public boolean initSuccess;
    protected Matrix4f[] poseMatrices;
    protected Vector3f[] deviceVelocity;
    protected Vector3f[] aimSource = new Vector3f[trackableDeviceCount];

    //hmd sampling
    private static final int HMD_AVG_MAX_SAMPLES = 90;
    public LinkedList<Vector3f> hmdPosSamples = new LinkedList<>();
    public LinkedList<Float> hmdYawSamples = new LinkedList<>();
    protected float hmdYawTotal;
    protected float hmdYawLast;
    protected boolean trigger;
    public boolean mrMovingCamActive;
    protected HapticScheduler hapticScheduler;

    //seated
    public float seatedRot;
    public float aimPitch = 0.0F;
    //
    public boolean hudPopup = true;
    protected int moveModeSwitchCount = 0;
    public boolean isWalkingAbout;
    protected boolean isFreeRotate;
    protected boolean isFlickStick;
    protected float flickStickRot;
    protected ControllerType walkaboutController;
    protected ControllerType freeRotateController;
    protected float walkaboutYawStart;
    protected float hmdForwardYaw = 180;
    public boolean ignorePressesNextFrame = false;
    protected int quickTorchPreviousSlot;
    protected Map<String, VRInputAction> inputActions = new HashMap<>();
    protected Map<String, VRInputAction> inputActionsByKeyBinding = new HashMap<>();

    protected static final Vector3fc[] fbtReferencePositions = new Vector3fc[] {
        new Vector3f(0F, 0.875F, 0F), // waist
        new Vector3f(0.125F, 0F, 0F), // right foot
        new Vector3f(-0.125F, 0F, 0F), // left foot
        new Vector3f(0.625F, 1.375F, 0F), // right elbow
        new Vector3f(-0.625F, 1.375F, 0F), // left elbow
        new Vector3f(0.125F, 0.375F, 0F), // right knee
        new Vector3f(-0.125F, 0.375F, 0F) // left knee
    };

    /**
     * creates the MCVR instance
     * @param mc instance of Minecraft to use
     * @param dh instance of ClientDataHolderVR to use
     * @param vrMod instance of VivecraftVRMod to use
     */
    public MCVR(Minecraft mc, ClientDataHolderVR dh, VivecraftVRMod vrMod) {
        this.mc = mc;
        this.dh = dh;
        MOD = vrMod;
        ME = this;

        // initialize all controller/tracker fields
        for (int c = 0; c < trackableDeviceCount; c++) {
            this.aimSource[c] = new Vector3f();
            this.controllerPose[c] = new Matrix4f();
            this.controllerRotation[c] = new Matrix4f();
            this.handRotation[c] = new Matrix4f();
        }
    }

    /**
     * @return the current active MCVR implementation
     */
    public static MCVR get() {
        return ME;
    }

    /**
     * initializes the api connection, and sets everything up.
     * @return if init was successful
     * @throws RenderConfigException if there was a critical error
     */
    public abstract boolean init() throws RenderConfigException;

    /**
     * stops the api connection and releases any allocated objects
     */
    public abstract void destroy();

    /**
     * triggers a haptic pulse on the give controller, as soon as possible
     * @param controller controller to trigger on
     * @param durationSeconds duration in seconds
     * @param frequency frequency in Hz
     * @param amplitude strength 0.0 - 1.0
     */
    public void triggerHapticPulse(ControllerType controller, float durationSeconds, float frequency, float amplitude) {
        this.triggerHapticPulse(controller, durationSeconds, frequency, amplitude, 0.0F);
    }

    /**
     * triggers a haptic pulse on the give controller, after the specified delay
     * @param controller controller to trigger on
     * @param durationSeconds duration in seconds
     * @param frequency frequency in Hz
     * @param amplitude strength 0.0 - 1.0
     * @param delaySeconds delay for when to trigger in seconds
     */
    public void triggerHapticPulse(ControllerType controller, float durationSeconds, float frequency, float amplitude, float delaySeconds) {
        if (this.dh.vrSettings.seated) return;
        if (this.dh.vrSettings.reverseHands) {
            controller = controller == ControllerType.RIGHT ? ControllerType.LEFT : ControllerType.RIGHT;
        }
        this.hapticScheduler.queueHapticPulse(controller, durationSeconds, frequency, amplitude, delaySeconds);
    }

    /**
     * triggers a haptic pulse on the give controller
     * uses a fixed frequency and amplitude, just changes duration
     * legacy method for simplicity
     * @param controller controller to trigger on
     * @param strength how long to trigger in microseconds
     */
    @Deprecated
    public void triggerHapticPulse(ControllerType controller, int strength) {
        if (strength >= 1) {
            // Through careful analysis of the haptics in the legacy API (read: I put the controller to
            // my ear, listened to the vibration, and reproduced the frequency in Audacity), I have determined
            // that the old haptics used 160Hz. So, these parameters will match the "feel" of the old haptics.
            this.triggerHapticPulse(controller, (float) strength / 1000000.0F, 160.0F, 1.0F);
        }
    }

    /**
     * triggers a haptic pulse on the give controller
     * uses a fixed frequency and amplitude, just changes duration
     * legacy method for simplicity
     * @param controller controller to trigger on
     * @param strength how long to trigger in microseconds
     */
    @Deprecated
    public void triggerHapticPulse(int controller, int strength) {
        if (controller >= 0 && controller < ControllerType.values().length) {
            this.triggerHapticPulse(ControllerType.values()[controller], strength);
        }
    }

    /**
     * finds the controller that has the given KeyMapping bound, and triggers a haptic there
     * legacy method for simplicity
     * @param keyMapping the KeyMapping to trigger for
     * @param strength how long to trigger in microseconds
     */
    @Deprecated
    protected void triggerBindingHapticPulse(KeyMapping keyMapping, int strength) {
        ControllerType controller = this.findActiveBindingControllerType(keyMapping);
        if (controller != null) {
            this.triggerHapticPulse(controller, strength);
        }
    }

    /**
     * @return the angle at which stuff is hold in the hand
     */
    public float getGunAngle() {
        return this.gunAngle;
    }

    /**
     * @param controller controller/tracker to get the aim rotation for
     * @return aim rotation of the specified controller/tracker in room space
     */
    public Matrix4fc getAimRotation(int controller) {
        return this.controllerRotation[controller];
    }

    /**
     * @param controller controller/tracker to get the aim position for
     * @return aim position of the specified controller/tracker in room space
     */
    public Vector3fc getAimSource(int controller) {
        Vector3fc out = this.aimSource[controller];

        if (!this.dh.vrSettings.seated && this.dh.vrSettings.allowStandingOriginOffset) {
            if (this.dh.vr.isHMDTracking()) {
                return out.add(this.dh.vrSettings.originOffset, new Vector3f());
            }
        }
        return out;
    }

    /**
     * @param controller controller/tracker to get the aim direction vector for
     * @return forward aim direction of the specified controller/tracker in room space
     */
    public Vector3f getAimVector(int controller) {
        return this.controllerRotation[controller].transformDirection(MathUtils.BACK, new Vector3f());
    }

    /**
     * @param controller controller/tracker to get the visual hand rotation for
     * @return visual hand rotation of the specified controller/tracker in room space
     */
    public Matrix4fc getHandRotation(int controller) {
        return this.handRotation[controller];
    }

    /**
     * @param controller controller/tracker to get the visual hand direction vector for
     * @return visual hand forward direction of the specified controller/tracker in room space
     */
    public Vector3f getHandVector(int controller) {
        return this.handRotation[controller].transformDirection(MathUtils.BACK, new Vector3f());
    }

    /**
     * @param eye LEFT, RIGHT or CENTER eye
     * @return position of the given eye, in room space
     */
    public Vector3f getEyePosition(RenderPass eye) {
        Matrix4f pose = new Matrix4f(this.hmdPose);
        switch (eye) {
            case LEFT -> pose.mul(this.hmdPoseLeftEye);
            case RIGHT -> pose.mul(this.hmdPoseRightEye);
            default -> {}
        };

        Vector3f pos = pose.getTranslation(new Vector3f());

        if (this.dh.vrSettings.seated || this.dh.vrSettings.allowStandingOriginOffset) {
            if (this.dh.vr.isHMDTracking()) {
                pos = pos.add(this.dh.vrSettings.originOffset);
            }
        }

        return pos;
    }

    /**
     * @param eye LEFT, RIGHT or CENTER eye
     * @return rotation of the given eye, in room space
     */
    public Matrix4fc getEyeRotation(RenderPass eye) {
        Matrix4f hmdToEye = switch (eye) {
            case LEFT -> this.hmdPoseLeftEye;
            case RIGHT -> this.hmdPoseRightEye;
            default -> null;
        };

        if (hmdToEye != null) {
            Matrix4f eyeRot = new Matrix4f().set3x3(hmdToEye);
            return this.hmdRotation.mul(eyeRot, eyeRot);
        } else {
            return this.hmdRotation;
        }
    }

    /**
     * @return forward vector of the headset, in room space
     */
    public Vector3f getHmdVector() {
        return this.hmdRotation.transformDirection(MathUtils.BACK, new Vector3f());
    }

    /**
     * @param keyMapping KeyMapping to get the VRInputAction for
     * @return VRInputAction that is linked to the given KeyMapping
     */
    public VRInputAction getInputAction(KeyMapping keyMapping) {
        return this.getInputAction(keyMapping.getName());
    }

    /**
     * @param name name of the KeyMapping to get the VRInputAction for
     * @return VRInputAction that is linked to the given KeyMapping
     */
    public VRInputAction getInputAction(String name) {
        return this.inputActionsByKeyBinding.get(name);
    }

    /**
     * gets the VRInputAction by name, a VRInputAction name is built like "(action set)/in/(keyMapping name)"
     * @param name name of the VRInputAction to get
     * @return VRInputAction that is linked to the given action name
     */
    public VRInputAction getInputActionByName(String name) {
        return this.inputActions.get(name);
    }

    /**
     * @return unmodifiable collection of all loaded VRInputActions
     */
    public Collection<VRInputAction> getInputActions() {
        return Collections.unmodifiableCollection(this.inputActions.values());
    }

    /**
     * @param set VRInputActionSet to get the VRInputActions for
     * @return unmodifiable collection of all VRInputActions in the given set
     */
    public Collection<VRInputAction> getInputActionsInSet(VRInputActionSet set) {
        return Collections.unmodifiableCollection(this.inputActions.values().stream().filter((action) ->
            action.actionSet == set).collect(Collectors.toList()));
    }

    /**
     * @param controller controller to check
     * @return if the controller is currently tracking
     */
    public boolean isControllerTracking(ControllerType controller) {
        return this.isControllerTracking(controller.ordinal());
    }

    /**
     * @param controller controller/tracker to check
     * @return if the controller/tracker is currently tracking
     */
    public boolean isControllerTracking(int controller) {
        return this.controllerTracking[controller];
    }

    /**
     * @return if the headset is currently tracking
     */
    public boolean isHMDTracking() {
        return this.headIsTracking;
    }

    /**
     * sets the room origin to the current headset position. assumes a 1.62 meter headset height
     */
    public void resetPosition() {
        // get the center position, and remove the old origin offset from it
        this.dh.vrSettings.originOffset.sub(this.getEyePosition(RenderPass.CENTER));
        this.dh.vrSettings.originOffset.y += 1.62F;
    }

    /**
     * clears the room origin offset
     */
    public void clearOffset() {
        this.dh.vrSettings.originOffset.zero();
    }

    /**
     * changes teh selected hotbar slot in the given direction.
     * @param dir direction to change to, negative is right, positive is left
     */
    protected void changeHotbar(int dir) {
        if (this.mc.player != null &&
            // never let go, jack.
            (!this.dh.climbTracker.isGrabbingLadder() || !ClimbTracker.isClaws(this.mc.player.getMainHandItem())))
        {
            if (this.mc.screen == null) {
                InputSimulator.scrollMouse(0.0D, dir * 4);
            } else {
                this.mc.player.getInventory().swapPaint(dir);
            }
        }
    }

    /**
     * processes the interactive hotbar
     */
    protected void processHotbar() {
        int previousSlot = this.dh.interactTracker.hotbar;
        this.dh.interactTracker.hotbar = -1;

        if (this.mc.player == null) return;
        // this shouldn't happen, it's final
        if (this.mc.player.getInventory() == null) return;
        if (this.dh.climbTracker.isGrabbingLadder() && ClimbTracker.isClaws(this.mc.player.getMainHandItem())) return;
        if (!this.dh.interactTracker.isActive(this.mc.player)) return;
        if (GuiHandler.GUI_POS_WORLD == Vec3.ZERO) return;

        Vector3fc main = this.getAimSource(MAIN_CONTROLLER);

        // TODO this is one frame behind, does it matter?

        Vector3f tempV = new Vector3f();
        VRData worldData = this.dh.vrPlayer.getVRDataWorld();
        Vector3f guiPos = MathUtils.subtractToVector3f(GuiHandler.GUI_POS_WORLD, worldData.origin);

        float scale =
            GuiHandler.GUI_SCALE_APPLIED * (float) this.mc.getWindow().getGuiScale() / GuiHandler.GUI_SCALE_FACTOR_MAX;
        // offset from center to the left of the hotbar
        GuiHandler.GUI_OFFSET_WORLD.add(-0.32F * scale, -0.38F * GuiHandler.GUI_SCALE_APPLIED, 0, tempV);

        Vector3f barStart = guiPos.add(GuiHandler.GUI_ROTATION_WORLD.transformDirection(tempV), new Vector3f());
        Vector3f barEnd = barStart.add(
            GuiHandler.GUI_ROTATION_WORLD.transformDirection(MathUtils.LEFT, tempV).mul(0.64F * scale), new Vector3f());

        barStart.div(worldData.worldScale);
        barEnd.div(worldData.worldScale);

        barStart.rotateY(-worldData.rotation_radians);
        barEnd.rotateY(-worldData.rotation_radians);

        Vector3fc barLine = barStart.sub(barEnd, new Vector3f());
        Vector3fc handToBar = barStart.sub(main, new Vector3f());

        // check if the hand is close enough
        float dist = handToBar.cross(barLine, new Vector3f()).length() / barLine.length();
        if (dist > 0.06) return;

        // check that the controller is to the right of the offhand slot, and how far it's to the right
        float fact = handToBar.dot(barLine) / barLine.lengthSquared();
        if (fact < -1) return;

        // get the closest point from the hand to the hotbar
        Vector3f point = barLine.mul(fact, new Vector3f()).sub(handToBar);
        // subtract and store in point
        main.sub(point, point);

        float barSize = barLine.length();
        float ilen = barStart.distance(point);
        if (fact < 0) {
            ilen *= -1;
        }
        float pos = ilen / barSize * 9;

        if (this.dh.vrSettings.reverseHands) {
            pos = 9 - pos;
        }

        // actual slot that is selected
        int box = (int) Math.floor(pos);

        if (box > 8) {
            if (this.mc.player.getMainArm().getOpposite() == HumanoidArm.RIGHT && pos >= 9.5 && pos <= 10.5) {
                box = 9;
            } else {
                return;
            }
        } else if (box < 0) {
            if (this.mc.player.getMainArm().getOpposite() == HumanoidArm.LEFT && pos <= -0.5 && pos >= -1.5) {
                box = 9;
            } else {
                return;
            }
        }

        // all that maths for this.
        this.dh.interactTracker.hotbar = box;
        if (previousSlot != this.dh.interactTracker.hotbar) {
            triggerHapticPulse(0, 750);
        }
    }

    /**
     * searches a KeyMapping by name
     * @param name name to search the KeyMapping for
     * @return found KeyMapping or null if none was found
     */
    protected KeyMapping findKeyBinding(String name) {
        return Stream.concat(Arrays.stream(this.mc.options.keyMappings), MOD.getHiddenKeyBindings().stream())
            .filter((kb) -> name.equals(kb.getName())).findFirst().orElse(null);
    }

    /**
     * manages the HMD position and rotation average
     */
    protected void hmdSampling() {
        if (this.hmdPosSamples.size() == HMD_AVG_MAX_SAMPLES) {
            this.hmdPosSamples.removeFirst();
        }

        if (this.hmdYawSamples.size() == HMD_AVG_MAX_SAMPLES) {
            this.hmdYawSamples.removeFirst();
        }

        // position samples are taken always
        this.hmdPosSamples.add(this.dh.vrPlayer.vrdata_room_pre.hmd.getPositionF());

        // yaw sampling below
        float yaw = this.dh.vrPlayer.vrdata_room_pre.hmd.getYaw();

        if (yaw < 0.0F) {
            yaw += 360.0F;
        }

        this.hmdYawTotal += MathUtils.angleDiff(yaw, this.hmdYawLast);
        this.hmdYawLast = yaw;

        if (Math.abs(MathUtils.angleNormalize(this.hmdYawTotal) - this.hmdYawLast) > 1.0F || this.hmdYawTotal > 100000.0F) {
            this.hmdYawTotal = this.hmdYawLast;
            VRSettings.LOGGER.info("Vivecraft: HMD yaw desync/overflow corrected");
        }

        float yawAvg = 0.0F;

        // avoid division by 0
        if (!this.hmdYawSamples.isEmpty()) {
            for (float sample : this.hmdYawSamples) {
                yawAvg += sample;
            }
            yawAvg /= (float) this.hmdYawSamples.size();
        }

        // only count this sample, if the headset moved enough, and is not still
        // this is like that, to make the menu not move constantly in seated/follow mode
        if (Math.abs(this.hmdYawTotal - yawAvg) > 20.0F) {
            this.trigger = true;
        }

        if (Math.abs(this.hmdYawTotal - yawAvg) < 1.0F) {
            this.trigger = false;
        }

        // only add the current yaw if it's the first one, or if the head moved significantly
        if (this.trigger || this.hmdYawSamples.isEmpty()) {
            this.hmdYawSamples.add(this.hmdYawTotal);
        }
    }

    /**
     * updates headset and controller matrices, also does seated controller override
     */
    protected void updateAim() {
        // hmd
        this.hmdRotation.identity();
        this.hmdRotation.set3x3(this.hmdPose);

        Vector3fc eye = this.getEyePosition(RenderPass.CENTER);
        this.hmdHistory.add(eye);

        Vector3fc pivot = this.hmdRotation.transformPosition(new Vector3f(0.0F, -0.1F, 0.1F)).add(eye);
        this.hmdPivotHistory.add(pivot);

        // conjugate, because camera matrices need to be transposed
        this.hmdRotHistory.add(new Quaternionf().setFromNormalized(this.hmdRotation).conjugate().rotateY((float) -Math.toRadians(this.dh.vrSettings.worldRotation)));


        // controllers
        for (int c = 0; c < 2; c++) {
            Matrix4f controllerPoseTip;
            Matrix4f controllerPoseHand;

            if (this.dh.vrSettings.seated) {
                // seated: use the hmd orientation for the controllers
                this.controllerPose[c] = new Matrix4f(this.hmdPose);
                controllerPoseHand = this.controllerPose[c];
                controllerPoseTip = this.controllerPose[c];
            } else {
                // just parse the controllers as is
                controllerPoseHand = this.controllerPose[c]
                    .mul(this.getControllerComponentTransform(c, "handgrip"), new Matrix4f());
                controllerPoseTip = this.controllerPose[c]
                    .mul(this.getControllerComponentTransform(c, "tip"), new Matrix4f());
            }

            this.handRotation[c].identity();
            this.handRotation[c].set3x3(controllerPoseHand);

            // grab controller position in tracker space, scaled to minecraft units
            controllerPoseTip.getTranslation(this.aimSource[c]);
            this.controllerHistory[c].add(new Vector3f(this.getAimSource(c)));

            // build matrix describing controller rotation
            this.controllerRotation[c].identity();
            this.controllerRotation[c].set3x3(controllerPoseTip);

            // special case for seated main controller
            if (c == MAIN_CONTROLLER && this.dh.vrSettings.seated && this.mc.screen == null &&
                this.mc.mouseHandler.isMouseGrabbed())
            {
                Matrix4f temp = new Matrix4f();
                if (this.mc.isWindowActive()) {
                    final float hRange = 110.0F;
                    final float vRange = 180.0F;

                    int screenWidth = this.mc.getWindow().getScreenWidth();
                    int screenHeight = this.mc.getWindow().getScreenHeight();

                    if (screenHeight % 2 != 0) {
                        // fix drifting vertical mouse.
                        screenHeight--;
                    }

                    float hPos = (float) this.mc.mouseHandler.xpos() / (float) screenWidth * hRange - (hRange * 0.5F);
                    float vPos = (float) -this.mc.mouseHandler.ypos() / (float) screenHeight * vRange + (vRange * 0.5F);

                    float rotStart = this.dh.vrSettings.keyholeX;
                    float rotSpeed = 20.0F * this.dh.vrSettings.xSensitivity;
                    int leftEdge = (int) ((-rotStart + hRange * 0.5F) * (float) screenWidth / hRange) + 1;
                    int rightEdge = (int) ((rotStart + hRange * 0.5F) * (float) screenWidth / hRange) - 1;

                    // Scaled 0...1 from rotStart to FOV edge
                    float rotMul = (Math.abs(hPos) - rotStart) / (hRange * 0.5F - rotStart);
                    double xPos = this.mc.mouseHandler.xpos();

                    Vector3f hmdDir = this.getHmdVector();

                    if (hPos < -rotStart) {
                        this.seatedRot += rotSpeed * rotMul;
                        this.seatedRot %= 360.0F;
                        this.hmdForwardYaw = (float) Math.toDegrees(Math.atan2(-hmdDir.x, hmdDir.z));
                        xPos = leftEdge;
                        hPos = -rotStart;
                    } else if (hPos > rotStart) {
                        this.seatedRot -= rotSpeed * rotMul;
                        this.seatedRot %= 360.0F;
                        this.hmdForwardYaw = (float) Math.toDegrees(Math.atan2(-hmdDir.x, hmdDir.z));
                        xPos = rightEdge;
                        hPos = rotStart;
                    }

                    float ySpeed = 0.5F * this.dh.vrSettings.ySensitivity;

                    this.aimPitch = Mth.clamp(this.aimPitch + vPos * ySpeed, -89.9F, 89.9F);

                    double screenX = xPos *
                        (((WindowExtension) (Object) this.mc.getWindow()).vivecraft$getActualScreenWidth() /
                            (double) screenWidth
                        );
                    double screenY = (screenHeight * 0.5F) *
                        (((WindowExtension) (Object) this.mc.getWindow()).vivecraft$getActualScreenHeight() /
                            (double) this.mc.getWindow().getScreenHeight()
                        );

                    InputSimulator.setMousePos(screenX, screenY);
                    GLFW.glfwSetCursorPos(this.mc.getWindow().getWindow(), screenX, screenY);

                    temp.rotationY(Mth.DEG_TO_RAD * (-180.0F - hPos - this.hmdForwardYaw));
                    temp.rotateX(Mth.DEG_TO_RAD * this.aimPitch);
                } else {
                    this.aimPitch = 0.0F;
                }

                this.handRotation[c].set(this.controllerRotation[c].set3x3(temp));
            } else if (c == MAIN_CONTROLLER) {
                this.aimPitch = 0.0F;
            }

            this.controllerForwardHistory[c].add(this.getAimVector(c));
            this.controllerUpHistory[c].add(this.controllerRotation[c].transformDirection(MathUtils.UP, new Vector3f()));
        }


        if (this.dh.vrSettings.seated) {
            // seated uses head as aim source
            this.aimSource[MAIN_CONTROLLER] = this.getEyePosition(RenderPass.CENTER);
            this.aimSource[OFFHAND_CONTROLLER].set(this.aimSource[MAIN_CONTROLLER]);
        }

        // trackers
        if (this.dh.vrSettings.debugCameraTracker) {
            this.controllerPose[CAMERA_TRACKER].set(this.controllerPose[MAIN_CONTROLLER]);
        }

        this.controllerRotation[CAMERA_TRACKER].identity();
        this.controllerRotation[CAMERA_TRACKER].set3x3(this.controllerPose[CAMERA_TRACKER]);

        if ((this.dh.vrSettings.debugCameraTracker || hasCameraTracker()) &&
            (this.dh.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY ||
                this.dh.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON
            ))
        {
            this.mrMovingCamActive = true;
            this.controllerPose[CAMERA_TRACKER].getTranslation(this.aimSource[CAMERA_TRACKER]);
        } else {
            this.mrMovingCamActive = false;
            this.aimSource[CAMERA_TRACKER].set(this.dh.vrSettings.vrFixedCampos);
        }

        if (this.hasFBT()) {
            int trackers = this.hasExtendedFBT() ? trackableDeviceCount : 6;
            boolean calibrated = this.dh.vrSettings.fbtCalibrated &&
                (!this.hasExtendedFBT() || this.dh.vrSettings.fbtExtendedCalibrated);
            for (int i = 3; i < trackers; i++) {
                this.controllerRotation[i].set3x3(this.controllerPose[i]);
                this.controllerPose[i].getTranslation(this.aimSource[i]);
                if (calibrated) {
                    this.controllerRotation[i].rotate(this.dh.vrSettings.fbtRotations[i - 3]);
                    this.aimSource[i].add(
                        this.controllerRotation[i].transformDirection(this.dh.vrSettings.fbtOffsets[i - 3],
                            new Vector3f()));
                }
            }
            this.feetHistory[0].add(new Vector3f(this.getAimSource(RIGHT_FOOT_TRACKER)));
            this.feetHistory[1].add(new Vector3f(this.getAimSource(LEFT_FOOT_TRACKER)));
        }
    }

    /**
     * processes vr specific keys
     */
    public void processBindings() {
        //if (this.inputActions.isEmpty()) return;

        boolean sleeping = this.mc.level != null && this.mc.player != null && this.mc.player.isSleeping();
        boolean gui = this.mc.screen != null;
        boolean toggleMovementPressed = MOD.keyToggleMovement.consumeClick();

        // allow movement switching with long pressing pick block
        if (this.mc.options.keyPickItem.isDown() || toggleMovementPressed) {
            if (++this.moveModeSwitchCount == 80 || toggleMovementPressed) {
                if (this.dh.vrSettings.seated) {
                    this.dh.vrSettings.seatedFreeMove = !this.dh.vrSettings.seatedFreeMove;
                    this.mc.gui.getChat().addMessage(Component.translatable("vivecraft.messages.movementmodeswitch",
                        this.dh.vrSettings.seatedFreeMove ? Component.translatable("vivecraft.options.freemove") :
                            Component.translatable("vivecraft.options.teleport")));
                } else if (this.dh.vrPlayer.isTeleportSupported()) {
                    this.dh.vrSettings.forceStandingFreeMove = !this.dh.vrSettings.forceStandingFreeMove;
                    this.mc.gui.getChat().addMessage(Component.translatable("vivecraft.messages.movementmodeswitch",
                        this.dh.vrSettings.seatedFreeMove ? Component.translatable("vivecraft.options.freemove") :
                            Component.translatable("vivecraft.options.teleport")));
                } else if (this.dh.vrPlayer.isTeleportOverridden()) {
                    this.dh.vrPlayer.setTeleportOverride(false);
                    this.mc.gui.getChat().addMessage(Component.translatable("vivecraft.messages.teleportdisabled"));
                } else {
                    this.dh.vrPlayer.setTeleportOverride(true);
                    this.mc.gui.getChat().addMessage(Component.translatable("vivecraft.messages.teleportenabled"));
                }
            }
        } else {
            this.moveModeSwitchCount = 0;
        }

        Vector3f main = this.getAimVector(MAIN_CONTROLLER);
        Vector3f off = this.getAimVector(OFFHAND_CONTROLLER);

        float mainYaw = (float) Math.toDegrees(Math.atan2(-main.x, main.z));
        float offYaw = (float) Math.toDegrees(Math.atan2(-off.x, off.z));

        if (!gui) {
            // world rotation
            if (MOD.keyWalkabout.isDown()) {
                ControllerType controller = this.findActiveBindingControllerType(MOD.keyWalkabout);

                float yaw = controller == ControllerType.LEFT ? offYaw : mainYaw;

                if (!this.isWalkingAbout) {
                    this.isWalkingAbout = true;
                    this.walkaboutYawStart = this.dh.vrSettings.worldRotation - yaw;
                } else {
                    this.dh.vrSettings.worldRotation = this.walkaboutYawStart + yaw;
                    // Prevent stupidly large values (can they even happen here?)
                    this.dh.vrSettings.worldRotation %= 360.0F;
                }
            } else {
                this.isWalkingAbout = false;
            }

            if (MOD.keyRotateFree.isDown()) {
                ControllerType controller = this.findActiveBindingControllerType(MOD.keyRotateFree);

                float yaw = controller == ControllerType.LEFT ? offYaw : mainYaw;

                if (!this.isFreeRotate) {
                    this.isFreeRotate = true;
                    this.walkaboutYawStart = this.dh.vrSettings.worldRotation + yaw;
                } else {
                    this.dh.vrSettings.worldRotation = this.walkaboutYawStart - yaw;
                }
            } else {
                this.isFreeRotate = false;
            }
        }

        if (this.dh.vrSettings.worldRotationIncrement == 0.0F) {
            // smooth rotation
            float ax = this.getInputAction(MOD.keyRotateAxis).getAxis2DUseTracked().x();

            if (ax == 0.0F) {
                ax = this.getInputAction(MOD.keyFreeMoveRotate).getAxis2DUseTracked().x();
            }

            // single direction keys
            ax -= Math.abs(this.getInputAction(MOD.keyRotateLeft).getAxis1DUseTracked());
            ax += Math.abs(this.getInputAction(MOD.keyRotateRight).getAxis1DUseTracked());

            if (ax != 0.0F) {
                float analogRotSpeed = 10.0F * ax;
                this.dh.vrSettings.worldRotation -= analogRotSpeed;
                this.dh.vrSettings.worldRotation %= 360.0F;
            }
        } else if (MOD.keyRotateAxis.consumeClick() || MOD.keyFreeMoveRotate.consumeClick()) {
            // axis snap turning
            float ax = this.getInputAction(MOD.keyRotateAxis).getAxis1D(false);

            if (ax == 0.0F) {
                ax = this.getInputAction(MOD.keyFreeMoveRotate).getAxis1D(false);
            }

            // dead zone
            if (Math.abs(ax) > 0.5F) {
                this.dh.vrSettings.worldRotation -= this.dh.vrSettings.worldRotationIncrement * Math.signum(ax);
                this.dh.vrSettings.worldRotation %= 360.0F;
            }
        } else if (MOD.keyRotateLeft.consumeClick()){
            // button snap turning
            this.dh.vrSettings.worldRotation += this.dh.vrSettings.worldRotationIncrement;
            this.dh.vrSettings.worldRotation %= 360.0F;
        } else if (MOD.keyRotateRight.consumeClick()){
            this.dh.vrSettings.worldRotation -= this.dh.vrSettings.worldRotationIncrement;
            this.dh.vrSettings.worldRotation %= 360.0F;
        }

        Vector2fc axis = this.getInputAction(MOD.keyFlickStick).getAxis2DUseTracked();
        if (axis.x() != 0F || axis.y() != 0F) {
            float rotation = (float) Math.toDegrees(Math.atan2(axis.x(), axis.y()));
            if (this.isFlickStick) {
                this.dh.vrSettings.worldRotation += this.flickStickRot - rotation;
            } else {
                this.isFlickStick = true;
                this.dh.vrSettings.worldRotation -= rotation;
            }

            this.dh.vrSettings.worldRotation %= 360.0F;
            this.flickStickRot = rotation;
        } else {
            this.flickStickRot = 0F;
            this.isFlickStick = false;
        }

        this.seatedRot = this.dh.vrSettings.worldRotation;

        if (MOD.keyHotbarNext.consumeClick()) {
            this.changeHotbar(-1);
            this.triggerBindingHapticPulse(MOD.keyHotbarNext, 250);
        }

        if (MOD.keyHotbarPrev.consumeClick()) {
            this.changeHotbar(1);
            this.triggerBindingHapticPulse(MOD.keyHotbarPrev, 250);
        }

        // quick torch, checks for a torch in the hotbar, and places it
        if (MOD.keyQuickTorch.consumeClick() && this.mc.player != null && this.mc.screen == null) {
            for (int slot = 0; slot < 9; slot++) {
                ItemStack itemstack = this.mc.player.getInventory().getItem(slot);

                if (itemstack.getItem() instanceof BlockItem item && item.getBlock() instanceof TorchBlock) {
                    this.quickTorchPreviousSlot = this.mc.player.getInventory().selected;
                    this.mc.player.getInventory().selected = slot;
                    this.mc.startUseItem();
                    // switch back immediately
                    this.mc.player.getInventory().selected = this.quickTorchPreviousSlot;
                    this.quickTorchPreviousSlot = -1;
                    break;
                }
            }
        }

        // if you start moving, close any UI
        if (gui && !sleeping && this.mc.options.keyUp.isDown() && !(this.mc.screen instanceof WinScreen) && this.mc.player != null) {
            this.mc.player.closeContainer();
        }

        // containers only listens directly to the keyboard to close.
        if (this.mc.screen instanceof AbstractContainerScreen && this.mc.options.keyInventory.consumeClick() && this.mc.player != null) {
            this.mc.player.closeContainer();
        }

        // allow toggling chat window with chat keybind
        if (this.mc.screen instanceof ChatScreen && this.mc.options.keyChat.consumeClick()) {
            this.mc.setScreen(null);
        }

        // swap slow mirror between Third and First Person
        if (MOD.keySwapMirrorView.consumeClick()) {
            if (this.dh.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON) {
                this.dh.vrSettings.displayMirrorMode = VRSettings.MirrorMode.FIRST_PERSON;
            } else if (this.dh.vrSettings.displayMirrorMode == VRSettings.MirrorMode.FIRST_PERSON) {
                this.dh.vrSettings.displayMirrorMode = VRSettings.MirrorMode.THIRD_PERSON;
            }
            this.dh.vrRenderer.reinitWithoutShaders("Mirror Setting Changed");
        }

        // start third person cam movement
        if (MOD.keyMoveThirdPersonCam.consumeClick() && !ClientDataHolderVR.KIOSK && !this.dh.vrSettings.seated && (this.dh.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY || this.dh.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON)) {
            ControllerType controller = this.findActiveBindingControllerType(MOD.keyMoveThirdPersonCam);
            if (controller != null) {
                VRHotkeys.startMovingThirdPersonCam(controller.ordinal(), VRHotkeys.Triggerer.BINDING);
            }
        }

        // stop third person cam movement
        if (VRHotkeys.isMovingThirdPersonCam()) {
            VRHotkeys.Triggerer trigger = VRHotkeys.getMovingThirdPersonCamTriggerer();
            // check type first, to not consume unrelated clicks
            if ((trigger == VRHotkeys.Triggerer.MENUBUTTON && MOD.keyMenuButton.consumeClick()) ||
                (trigger == VRHotkeys.Triggerer.BINDING && !MOD.keyMoveThirdPersonCam.isDown()))
            {
                VRHotkeys.stopMovingThirdPersonCam();
                this.dh.vrSettings.saveOptions();
            }
        }

        // keyboard
        if (MOD.keyToggleKeyboard.consumeClick()) {
            KeyboardHandler.setOverlayShowing(!KeyboardHandler.SHOWING);
        }

        // close keyboard with ESC
        if (KeyboardHandler.SHOWING && this.mc.screen == null && MOD.keyMenuButton.consumeClick()) {
            KeyboardHandler.setOverlayShowing(false);
        }

        // radial menu
        if (MOD.keyRadialMenu.consumeClick() && !gui) {
            ControllerType controller = this.findActiveBindingControllerType(MOD.keyRadialMenu);
            if (controller != null) {
                RadialHandler.setOverlayShowing(!RadialHandler.isShowing(), controller);
            }
        }

        // close radial with ESC when not hold mode
        if (RadialHandler.isShowing() && MOD.keyMenuButton.consumeClick()) {
            RadialHandler.setOverlayShowing(false, null);
        }

        if (MOD.keyMenuButton.consumeClick()) {
            // handle menu directly
            if (!gui) {
                if (!ClientDataHolderVR.KIOSK) {
                    this.mc.pauseGame(false);
                }
            } else {
                InputSimulator.pressKey(GLFW.GLFW_KEY_ESCAPE);
                InputSimulator.releaseKey(GLFW.GLFW_KEY_ESCAPE);
            }

            KeyboardHandler.setOverlayShowing(false);
        }

        // player list
        if (MOD.keyTogglePlayerList.consumeClick()) {
            ((GuiExtension) this.mc.gui).vivecraft$setShowPlayerList(!((GuiExtension) this.mc.gui).vivecraft$getShowPlayerList());
        }

        // screenshot cam
        boolean toggleCam = MOD.keyToggleHandheldCam.consumeClick();
        boolean quickCam = MOD.keyQuickHandheldCam.consumeClick();
        if (this.mc.player != null && (toggleCam || quickCam)) {
            if (toggleCam || !this.dh.cameraTracker.isVisible()) {
                this.dh.cameraTracker.toggleVisibility();
            }

            // if the cam is now visible position it
            if (this.dh.cameraTracker.isVisible()) {
                ControllerType hand = this.findActiveBindingControllerType(
                    toggleCam ? MOD.keyToggleHandheldCam : MOD.keyQuickHandheldCam);

                if (hand == null) {
                    hand = ControllerType.RIGHT;
                }

                VRData.VRDevicePose handPose = this.dh.vrPlayer.vrdata_world_pre.getController(hand.ordinal());
                this.dh.cameraTracker.setPosition(handPose.getPosition());
                this.dh.cameraTracker.setRotation(handPose.getMatrix());

                if (quickCam) {
                    // start moving
                    this.dh.cameraTracker.startMoving(hand.ordinal(), true);
                }
            }
        }

        // stop quick cam
        if (!MOD.keyQuickHandheldCam.isDown() && this.dh.cameraTracker.isMoving() && this.dh.cameraTracker.isQuickMode() && this.mc.player != null) {
            this.dh.cameraTracker.stopMoving();
            this.dh.grabScreenShot = true;
        }

        GuiHandler.processBindingsGui();
        RadialHandler.processBindings();
        KeyboardHandler.processBindings();
        this.dh.interactTracker.processBindings();
    }

    /**
     * creates VRInputActions for all registered keyMappings, should be called in {@link #init}
     */
    public void populateInputActions() {
        Map<String, ActionParams> actionParams = this.getSpecialActionParams();

        // iterate over all minecraft keys, and our hidden keys
        for (KeyMapping keyMapping : Stream.concat(Arrays.stream(this.mc.options.keyMappings), MOD.getHiddenKeyBindings().stream()).toList()) {
            ActionParams params = actionParams.getOrDefault(keyMapping.getName(), ActionParams.DEFAULT);
            VRInputAction action = new VRInputAction(keyMapping, params.requirement(), params.type(),
                params.actionSetOverride());

            this.inputActions.put(action.name, action);
            this.inputActionsByKeyBinding.put(action.keyBinding.getName(), action);
        }

        this.getInputAction(MOD.keyVRInteract).setPriority(5).setEnabled(false);
        this.getInputAction(MOD.keyClimbeyGrab).setPriority(10).setEnabled(false);
        this.getInputAction(MOD.keyClimbeyJump).setEnabled(false);
        this.getInputAction(GuiHandler.KEY_KEYBOARD_CLICK).setPriority(50);
        this.getInputAction(GuiHandler.KEY_KEYBOARD_SHIFT).setPriority(50);
    }

    /**
     * This is for bindings with specific requirement/type params, anything not listed will default to optional and boolean <br>
     * See OpenVR docs for valid values: <a href="https://github.com/ValveSoftware/openvr/wiki/Action-manifest#actions">Action-manifest#actions</a>
     * @return map of Keymappings with non default ActionParameters
     */

    public Map<String, ActionParams> getSpecialActionParams() {
        Map<String, ActionParams> map = new HashMap<>();

        this.addActionParams(map, this.mc.options.keyUp, "optional", "vector1", null);
        this.addActionParams(map, this.mc.options.keyDown, "optional", "vector1", null);
        this.addActionParams(map, this.mc.options.keyLeft, "optional", "vector1", null);
        this.addActionParams(map, this.mc.options.keyRight, "optional", "vector1", null);
        this.addActionParams(map, this.mc.options.keyInventory, "suggested", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, this.mc.options.keyAttack, "suggested", "boolean", null);
        this.addActionParams(map, this.mc.options.keyUse, "suggested", "boolean", null);
        this.addActionParams(map, this.mc.options.keyChat, "optional", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, MOD.keyHotbarScroll, "optional", "vector2", null);
        this.addActionParams(map, MOD.keyHotbarSwipeX, "optional", "vector2", null);
        this.addActionParams(map, MOD.keyHotbarSwipeY, "optional", "vector2", null);
        this.addActionParams(map, MOD.keyMenuButton, "suggested", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, MOD.keyTeleportFallback, "suggested", "vector1", null);
        this.addActionParams(map, MOD.keyFreeMoveRotate, "optional", "vector2", null);
        this.addActionParams(map, MOD.keyFreeMoveStrafe, "optional", "vector2", null);
        this.addActionParams(map, MOD.keyRotateLeft, "optional", "vector1", null);
        this.addActionParams(map, MOD.keyRotateRight, "optional", "vector1", null);
        this.addActionParams(map, MOD.keyRotateAxis, "optional", "vector2", null);
        this.addActionParams(map, MOD.keyFlickStick, "optional", "vector2", null);
        this.addActionParams(map, MOD.keyRadialMenu, "suggested", "boolean", null);
        this.addActionParams(map, MOD.keySwapMirrorView, "optional", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, MOD.keyToggleKeyboard, "optional", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, MOD.keyMoveThirdPersonCam, "optional", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, MOD.keyToggleHandheldCam, "optional", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, MOD.keyQuickHandheldCam, "optional", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, MOD.keyTrackpadTouch, "optional", "boolean", VRInputActionSet.TECHNICAL);
        this.addActionParams(map, MOD.keyVRInteract, "suggested", "boolean", VRInputActionSet.CONTEXTUAL);
        this.addActionParams(map, MOD.keyClimbeyGrab, "suggested", "boolean", null);
        this.addActionParams(map, MOD.keyClimbeyJump, "suggested", "boolean", null);
        this.addActionParams(map, GuiHandler.KEY_LEFT_CLICK, "suggested", "boolean", null);
        this.addActionParams(map, GuiHandler.KEY_SCROLL_AXIS, "optional", "vector2", null);
        this.addActionParams(map, GuiHandler.KEY_RIGHT_CLICK, "suggested", "boolean", null);
        this.addActionParams(map, GuiHandler.KEY_SHIFT, "suggested", "boolean", null);
        this.addActionParams(map, GuiHandler.KEY_KEYBOARD_CLICK, "suggested", "boolean", null);
        this.addActionParams(map, GuiHandler.KEY_KEYBOARD_SHIFT, "suggested", "boolean", null);

        // users can provide their own action parameters if they want
        // this allows them to split mod KeyMappings into GUI, INGAME and GLOBAL categories
        File file = new File("customactionsets.txt");

        if (file.exists()) {
            VRSettings.LOGGER.info("Vivecraft: Loading custom action set definitions...");
            String line;

            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
                while ((line = bufferedReader.readLine()) != null) {
                    String[] tokens = line.split(":", 2);

                    if (tokens.length < 2) {
                        VRSettings.LOGGER.warn("Vivecraft: Invalid tokens: {}", line);
                        continue;
                    }
                    KeyMapping keyMapping = this.findKeyBinding(tokens[0]);

                    if (keyMapping == null) {
                        VRSettings.LOGGER.warn("Vivecraft: Unknown key binding: {}", tokens[0]);
                    } else if (MOD.getAllKeyBindings().contains(keyMapping)) {
                        VRSettings.LOGGER.warn("Vivecraft: NO! Don't touch Vivecraft bindings!: {}", keyMapping.getName());
                    } else {
                        VRInputActionSet actionSet = switch (tokens[1].toLowerCase()) {
                            case "ingame" -> VRInputActionSet.INGAME;
                            case "gui" -> VRInputActionSet.GUI;
                            case "global" -> VRInputActionSet.GLOBAL;
                            default -> null;
                        };

                        if (actionSet == null) {
                            VRSettings.LOGGER.warn("Vivecraft: Unknown action set: {}", tokens[1]);
                        } else {
                            this.addActionParams(map, keyMapping, "optional", "boolean", actionSet);
                        }
                    }
                }
            } catch (IOException e) {
                VRSettings.LOGGER.error("Vivecraft: Failed to read customactionsets.txt: ", e);
            }
        }

        return map;
    }

    /**
     * convenience method to create an ActionParam and add to the map
     * @param map Map to add the ActionParam to
     * @param keyMapping KeyMapping the ActionParam belongs to
     * @param requirement requirement of the action. See {@link ActionParams#requirement()}
     * @param type input type of the action. See {@link ActionParams#type()}
     * @param actionSetOverride actionset this should be in. See {@link ActionParams#actionSetOverride()}
     */
    private void addActionParams(Map<String, ActionParams> map, KeyMapping keyMapping, String requirement, String type, VRInputActionSet actionSetOverride) {
        ActionParams actionparams = new ActionParams(requirement, type, actionSetOverride);
        map.put(keyMapping.getName(), actionparams);
    }

    /**
     * handles any keyboard inputs that are specific to this MCVR implementation
     * @param key GLFW key that is handled
     * @param scanCode scanCode of the handled key
     * @param action if the key was pressed, released or repeated
     * @param modifiers key modifiers that are active
     * @return true if a key was handled
     */
    public boolean handleKeyboardInputs(int key, int scanCode, int action, int modifiers) {
        return false;
    }

    /**
     * calculates the fbt trackers rotation and position offsets from the current state, to where they should be
     */
    public void calibrateFBT(float headsetYaw) {
        Vector3f tempV = new Vector3f();
        float scale = (AutoCalibration.getPlayerHeight() / AutoCalibration.DEFAULT_HEIGHT) * 0.9375F;

        Vector3f posAvg = this.hmdPivotHistory.averagePosition(0.5D);

        for (int t = 3; t < (hasExtendedFBT() ? trackableDeviceCount : 6); t++) {
            Matrix4f rotationOffset = this.controllerPose[t].rotateLocalY(headsetYaw, new Matrix4f());
            rotationOffset.getUnnormalizedRotation(this.dh.vrSettings.fbtRotations[t - 3]).conjugate();
        }

        for (int t = 3; t < (hasExtendedFBT() ? trackableDeviceCount : 6); t++) {
            Vector3f offset = fbtReferencePositions[t - 3].mul(scale, new Vector3f()).sub(
                this.controllerPose[t].getTranslation(tempV)
                .sub(posAvg.x, 0F, posAvg.z) // center around headset
                .rotateY(headsetYaw)); // remove body rotation
            this.dh.vrSettings.fbtOffsets[t - 3].set(offset);
        }

        if (hasFBT()) {
            this.dh.vrSettings.fbtCalibrated = true;
        }
        if (hasExtendedFBT()) {
            this.dh.vrSettings.fbtExtendedCalibrated = true;
        }
    }

    /**
     * @return List of trackers, with device type and poseMatrix
     */
    public List<Pair<Integer, Matrix4fc>> getTrackers() {
        List<Pair<Integer, Matrix4fc>> poses = new ArrayList<>();
        if (hasFBT()) {
            for(int i = 3; i < (hasExtendedFBT() ? trackableDeviceCount : 6); i++) {
                poses.add(Pair.of(i, this.controllerPose[i]));
            }
        }
        return poses;
    }

    /**
     * @return the name of this MCVR implementation
     */
    public abstract String getName();

    /**
     * processes the fetched inputs from the VR runtime, and maps them to the ingame keys
     */
    public abstract void processInputs();

    /**
     * @param keyMapping KeyMapping to check where it is bound at
     * @return controller this Keymapping is mapped on, null if it isn't mapped
     */
    protected abstract ControllerType findActiveBindingControllerType(KeyMapping keyMapping);

    /**
     * polls VR events, and fetches new device poses and inputs
     * @param frameIndex index of the current VR frame. Some VR runtimes need that
     */
    public abstract void poll(long frameIndex);

    /**
     * @return size of the play area or null if not available
     */
    public abstract Vector2fc getPlayAreaSize();

    /**
     * @param controllerIndex index of the controller to get the transform for
     * @param componentName name of the transform. `tip` or `handgrip`
     * @return the controller transform with the given name, that was fetched during {@link MCVR#poll}
     */
    public abstract Matrix4fc getControllerComponentTransform(int controllerIndex, String componentName);

    /**
     * @return if there is a tracker for the camera
     */
    public abstract boolean hasCameraTracker();

    /**
     * @return if feet and waist trackers are available
     */
    public abstract boolean hasFBT();

    /**
     * @return if elbow and knee trackers are available
     */
    public abstract boolean hasExtendedFBT();

    /**
     * @param action VRInputAction to query origins for
     * @return a list containing all currently active origin handles for that action
     */
    public abstract List<Long> getOrigins(VRInputAction action);

    /**
     * @param origin the origin handle of an input action
     * @return String describing what button/input the given origin is pointing to
     */
    public abstract String getOriginName(long origin);

    /**
     * @return the VRRenderer that corresponds to this MCVR
     */
    public abstract VRRenderer createVRRenderer();

    /**
     * @return if the headset is active and the game should be in VR, when this returns false, the game will switch to NONVR
     */
    public abstract boolean isActive();

    /**
     * determines if the vanilla framecap should still be applied,
     * by default this returns false, since the VR runtime should handle any frame caps
     * @return if the game should still apply the vanilla framecap
     */
    public boolean capFPS() {
        return false;
    }

    /**
     * @return the ipd in meters
     */
    public abstract float getIPD();

    /**
     * @return the name of the VR runtime
     */
    public abstract String getRuntimeName();
}
