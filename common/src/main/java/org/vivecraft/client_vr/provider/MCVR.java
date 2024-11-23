package org.vivecraft.client_vr.provider;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.utils.Utils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.QuaternionfHistory;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.Vec3History;
import org.vivecraft.client_vr.extensions.GuiExtension;
import org.vivecraft.client_vr.extensions.WindowExtension;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.provider.control.TrackpadSwipeSampler;
import org.vivecraft.client_vr.provider.control.VRInputAction;
import org.vivecraft.client_vr.provider.control.VRInputActionSet;
import org.vivecraft.client_vr.provider.control.VivecraftMovementInput;
import org.vivecraft.client_vr.provider.openxr.DeviceCompat;
import org.vivecraft.client_vr.render.RenderConfigException;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRHotkeys;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassManager;
import org.vivecraft.common.utils.lwjgl.Matrix4f;
import org.vivecraft.common.utils.lwjgl.Vector3f;
import org.vivecraft.common.utils.math.Quaternion;
import org.vivecraft.common.utils.math.Vector2;
import org.vivecraft.common.utils.math.Vector3;
import org.vivecraft.mod_compat_vr.ShadersHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class MCVR {
    protected Minecraft mc;
    protected ClientDataHolderVR dh;
    protected static MCVR me;
    protected static VivecraftVRMod mod;
    protected org.vivecraft.common.utils.math.Matrix4f hmdPose = new org.vivecraft.common.utils.math.Matrix4f();
    public org.vivecraft.common.utils.math.Matrix4f hmdRotation = new org.vivecraft.common.utils.math.Matrix4f();
    public HardwareType detectedHardware = HardwareType.VIVE;
    protected org.vivecraft.common.utils.math.Matrix4f hmdPoseLeftEye = new org.vivecraft.common.utils.math.Matrix4f();
    protected org.vivecraft.common.utils.math.Matrix4f hmdPoseRightEye = new org.vivecraft.common.utils.math.Matrix4f();
    public Vec3History hmdHistory = new Vec3History();
    public Vec3History hmdPivotHistory = new Vec3History();
    public QuaternionfHistory hmdRotHistory = new QuaternionfHistory();
    protected boolean headIsTracking;
    protected org.vivecraft.common.utils.math.Matrix4f[] controllerPose = new org.vivecraft.common.utils.math.Matrix4f[3];
    protected org.vivecraft.common.utils.math.Matrix4f[] controllerRotation = new org.vivecraft.common.utils.math.Matrix4f[3];
    protected boolean[] controllerTracking = new boolean[3];
    protected org.vivecraft.common.utils.math.Matrix4f[] handRotation = new org.vivecraft.common.utils.math.Matrix4f[3];
    public Vec3History[] controllerHistory = new Vec3History[]{new Vec3History(), new Vec3History()};
    public Vec3History[] controllerForwardHistory = new Vec3History[]{new Vec3History(), new Vec3History()};
    public Vec3History[] controllerUpHistory = new Vec3History[]{new Vec3History(), new Vec3History()};
    protected double gunAngle = 0.0D;
    protected boolean gunStyle;
    public boolean initialized;
    public String initStatus;
    public boolean initSuccess;
    protected org.vivecraft.common.utils.math.Matrix4f[] poseMatrices;
    protected Vec3[] deviceVelocity;
    protected Vec3[] aimSource = new Vec3[3];
    public Vector3 forward = new Vector3(0.0F, 0.0F, -1.0F);
    public Vector3 up = new Vector3(0.0F, 1.0F, 0.0F);
    public int hmdAvgLength = 90;
    public LinkedList<Vec3> hmdPosSamples = new LinkedList<>();
    public LinkedList<Float> hmdYawSamples = new LinkedList<>();
    protected float hmdYawTotal;
    protected float hmdYawLast;
    protected boolean trigger;
    public boolean mrMovingCamActive;
    public Vec3 mrControllerPos = Vec3.ZERO;
    public float mrControllerPitch;
    public float mrControllerYaw;
    public float mrControllerRoll;
    protected HapticScheduler hapticScheduler;
    public float seatedRot;
    public float aimPitch = 0.0F;
    protected final org.vivecraft.common.utils.math.Matrix4f Neutral_HMD = new org.vivecraft.common.utils.math.Matrix4f(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.62F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
    protected final org.vivecraft.common.utils.math.Matrix4f TPose_Left = new org.vivecraft.common.utils.math.Matrix4f(1.0F, 0.0F, 0.0F, 0.25F, 0.0F, 1.0F, 0.0F, 1.62F, 0.0F, 0.0F, 1.0F, 0.25F, 0.0F, 0.0F, 0.0F, 1.0F);
    protected final org.vivecraft.common.utils.math.Matrix4f TPose_Right = new org.vivecraft.common.utils.math.Matrix4f(1.0F, 0.0F, 0.0F, 0.75F, 0.0F, 1.0F, 0.0F, 1.62F, 0.0F, 0.0F, 1.0F, 0.75F, 0.0F, 0.0F, 0.0F, 1.0F);
    protected boolean TPose = false;
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
    protected final Map<String, TrackpadSwipeSampler> trackpadSwipeSamplers = new HashMap<>();
    protected boolean inputInitialized;
    public final DeviceCompat device;

    public MCVR(Minecraft mc, ClientDataHolderVR dh, VivecraftVRMod vrMod) {
        this.mc = mc;
        this.dh = dh;
        mod = vrMod;
        this.device = DeviceCompat.detectDevice();
        me = this;

        for (int i = 0; i < 3; ++i) {
            this.aimSource[i] = new Vec3(0.0D, 0.0D, 0.0D);
            this.controllerPose[i] = new org.vivecraft.common.utils.math.Matrix4f();
            this.controllerRotation[i] = new org.vivecraft.common.utils.math.Matrix4f();
            this.handRotation[i] = new org.vivecraft.common.utils.math.Matrix4f();
        }
    }

    public static MCVR get() {
        return me;
    }

    public abstract String getName();

    public abstract String getID();

    public void processInputs() {
        if (!this.dh.vrSettings.seated && !ClientDataHolderVR.viewonly && this.inputInitialized) {
            for (VRInputAction vrinputaction : this.inputActions.values()) {
                if (vrinputaction.isHanded()) {
                    for (ControllerType controllertype : ControllerType.values()) {
                        vrinputaction.setCurrentHand(controllertype);
                        this.processInputAction(vrinputaction);
                    }
                } else {
                    this.processInputAction(vrinputaction);
                }
            }

            this.processScrollInput(GuiHandler.keyScrollAxis, () ->
            {
                InputSimulator.scrollMouse(0.0D, 1.0D);
            }, () ->
            {
                InputSimulator.scrollMouse(0.0D, -1.0D);
            });
            this.processScrollInput(VivecraftVRMod.INSTANCE.keyHotbarScroll, () ->
            {
                this.changeHotbar(-1);
            }, () ->
            {
                this.changeHotbar(1);
            });
            this.processSwipeInput(VivecraftVRMod.INSTANCE.keyHotbarSwipeX, () ->
            {
                this.changeHotbar(1);
            }, () ->
            {
                this.changeHotbar(-1);
            }, null, null);
            this.processSwipeInput(VivecraftVRMod.INSTANCE.keyHotbarSwipeY, null, null, () ->
            {
                this.changeHotbar(-1);
            }, () ->
            {
                this.changeHotbar(1);
            });
            this.ignorePressesNextFrame = false;
        }
    }

    public abstract void destroy();

    public double getGunAngle() {
        return this.gunAngle;
    }

    public org.vivecraft.common.utils.math.Matrix4f getAimRotation(int controller) {
        return this.controllerRotation[controller];
    }

    public Vec3 getAimSource(int controller) {
        Vec3 vec3 = new Vec3(this.aimSource[controller].x, this.aimSource[controller].y, this.aimSource[controller].z);

        if (!this.dh.vrSettings.seated && this.dh.vrSettings.allowStandingOriginOffset) {
            if (this.dh.vr.isHMDTracking()) {
                vec3 = vec3.add(this.dh.vrSettings.originOffset.getX(), this.dh.vrSettings.originOffset.getY(), this.dh.vrSettings.originOffset.getZ());
            }
        }

        return vec3;
    }

    public Vec3 getAimVector(int controller) {
        Vector3 vector3 = this.controllerRotation[controller].transform(this.forward);
        return vector3.toVector3d();
    }

    public void triggerHapticPulse(ControllerType controller, float durationSeconds, float frequency, float amplitude) {
        this.triggerHapticPulse(controller, durationSeconds, frequency, amplitude, 0.0F);
    }

    public void triggerHapticPulse(ControllerType controller, float durationSeconds, float frequency, float amplitude, float delaySeconds) {
        if (!this.dh.vrSettings.seated) {
            if (this.dh.vrSettings.reverseHands) {
                if (controller == ControllerType.RIGHT) {
                    controller = ControllerType.LEFT;
                } else {
                    controller = ControllerType.RIGHT;
                }
            }

            this.hapticScheduler.queueHapticPulse(controller, durationSeconds, frequency, amplitude, delaySeconds);
        }
    }

    @Deprecated
    public void triggerHapticPulse(ControllerType controller, int strength) {
        if (strength >= 1) {
            this.triggerHapticPulse(controller, (float) strength / 1000000.0F, 160.0F, 1.0F);
        }
    }

    @Deprecated
    public void triggerHapticPulse(int controller, int strength) {
        if (controller >= 0 && controller < ControllerType.values().length) {
            this.triggerHapticPulse(ControllerType.values()[controller], strength);
        }
    }

    public org.vivecraft.common.utils.math.Matrix4f getHandRotation(int controller) {
        return this.handRotation[controller];
    }

    public Vec3 getHandVector(int controller) {
        Vector3 vector3 = new Vector3(0.0F, 0.0F, -1.0F);
        org.vivecraft.common.utils.math.Matrix4f matrix4f = this.handRotation[controller];
        Vector3 vector31 = matrix4f.transform(vector3);
        return vector31.toVector3d();
    }

    public Vec3 getCenterEyePosition() {
        Vector3 vector3 = Utils.convertMatrix4ftoTranslationVector(this.hmdPose);

        if (this.dh.vrSettings.seated || this.dh.vrSettings.allowStandingOriginOffset) {
            if (this.dh.vr.isHMDTracking()) {
                vector3 = vector3.add(this.dh.vrSettings.originOffset);
            }
        }

        return vector3.toVector3d();
    }

    public Vec3 getEyePosition(RenderPass eye) {
        org.vivecraft.common.utils.math.Matrix4f matrix4f = this.hmdPoseRightEye;

        if (eye == RenderPass.LEFT) {
            matrix4f = this.hmdPoseLeftEye;
        } else if (eye == RenderPass.RIGHT) {
            matrix4f = this.hmdPoseRightEye;
        } else {
            matrix4f = null;
        }

        if (matrix4f == null) {
            org.vivecraft.common.utils.math.Matrix4f matrix4f2 = this.hmdPose;
            Vector3 vector31 = Utils.convertMatrix4ftoTranslationVector(matrix4f2);

            if (this.dh.vrSettings.seated || this.dh.vrSettings.allowStandingOriginOffset) {
                if (this.dh.vr.isHMDTracking()) {
                    vector31 = vector31.add(this.dh.vrSettings.originOffset);
                }
            }

            return vector31.toVector3d();
        } else {
            org.vivecraft.common.utils.math.Matrix4f matrix4f1 = org.vivecraft.common.utils.math.Matrix4f.multiply(this.hmdPose, matrix4f);
            Vector3 vector3 = Utils.convertMatrix4ftoTranslationVector(matrix4f1);

            if (this.dh.vrSettings.seated || this.dh.vrSettings.allowStandingOriginOffset) {
                if (this.dh.vr.isHMDTracking()) {
                    vector3 = vector3.add(this.dh.vrSettings.originOffset);
                }
            }

            return vector3.toVector3d();
        }
    }

    public HardwareType getHardwareType() {
        return this.dh.vrSettings.forceHardwareDetection > 0 ? HardwareType.values()[this.dh.vrSettings.forceHardwareDetection - 1] : this.detectedHardware;
    }

    public Vec3 getHmdVector() {
        Vector3 vector3 = this.hmdRotation.transform(this.forward);
        return vector3.toVector3d();
    }

    public org.vivecraft.common.utils.math.Matrix4f getEyeRotation(RenderPass eye) {
        org.vivecraft.common.utils.math.Matrix4f matrix4f;

        if (eye == RenderPass.LEFT) {
            matrix4f = this.hmdPoseLeftEye;
        } else if (eye == RenderPass.RIGHT) {
            matrix4f = this.hmdPoseRightEye;
        } else {
            matrix4f = null;
        }

        if (matrix4f != null) {
            org.vivecraft.common.utils.math.Matrix4f matrix4f1 = new org.vivecraft.common.utils.math.Matrix4f();
            matrix4f1.M[0][0] = matrix4f.M[0][0];
            matrix4f1.M[0][1] = matrix4f.M[0][1];
            matrix4f1.M[0][2] = matrix4f.M[0][2];
            matrix4f1.M[0][3] = 0.0F;
            matrix4f1.M[1][0] = matrix4f.M[1][0];
            matrix4f1.M[1][1] = matrix4f.M[1][1];
            matrix4f1.M[1][2] = matrix4f.M[1][2];
            matrix4f1.M[1][3] = 0.0F;
            matrix4f1.M[2][0] = matrix4f.M[2][0];
            matrix4f1.M[2][1] = matrix4f.M[2][1];
            matrix4f1.M[2][2] = matrix4f.M[2][2];
            matrix4f1.M[2][3] = 0.0F;
            matrix4f1.M[3][0] = 0.0F;
            matrix4f1.M[3][1] = 0.0F;
            matrix4f1.M[3][2] = 0.0F;
            matrix4f1.M[3][3] = 1.0F;
            return org.vivecraft.common.utils.math.Matrix4f.multiply(this.hmdRotation, matrix4f1);
        } else {
            return this.hmdRotation;
        }
    }

    public VRInputAction getInputAction(String keyBindingDesc) {
        return this.inputActionsByKeyBinding.get(keyBindingDesc);
    }

    public VRInputAction getInputActionByName(String name) {
        return this.inputActions.get(name);
    }

    public Collection<VRInputAction> getInputActions() {
        return Collections.unmodifiableCollection(this.inputActions.values());
    }

    public VRInputAction getInputAction(KeyMapping keyBinding) {
        return this.getInputAction(keyBinding.getName());
    }

    public Collection<VRInputAction> getInputActionsInSet(VRInputActionSet set) {
        return Collections.unmodifiableCollection(this.inputActions.values().stream().filter((action) ->
        {
            return action.actionSet == set;
        }).collect(Collectors.toList()));
    }

    public boolean isControllerTracking(ControllerType controller) {
        return this.isControllerTracking(controller.ordinal());
    }

    public boolean isControllerTracking(int controller) {
        return this.controllerTracking[controller];
    }

    public void resetPosition() {
        Vec3 vec3 = this.getCenterEyePosition().scale(-1.0D).add(this.dh.vrSettings.originOffset.getX(), this.dh.vrSettings.originOffset.getY(), this.dh.vrSettings.originOffset.getZ());
        this.dh.vrSettings.originOffset = new Vector3((float) vec3.x, (float) vec3.y + 1.62F, (float) vec3.z);
    }

    public void clearOffset() {
        this.dh.vrSettings.originOffset = new Vector3(0.0F, 0.0F, 0.0F);
    }

    public boolean isHMDTracking() {
        return this.headIsTracking;
    }

    protected void processHotbar() {
        int previousSlot = this.dh.interactTracker.hotbar;
        this.dh.interactTracker.hotbar = -1;
        if (mc.player == null) {
            return;
        }
        if (mc.player.getInventory() == null) {
            return;
        }

        if (dh.climbTracker.isGrabbingLadder() &&
            dh.climbTracker.isClaws(mc.player.getMainHandItem())) {
            return;
        }
        if (!dh.interactTracker.isActive(mc.player)) {
            return;
        }

        Vec3 main = this.getAimSource(0);
        Vec3 off = this.getAimSource(1);
        Vec3 barStartPos = null, barEndPos = null;

        int i = 1;
        if (this.dh.vrSettings.reverseHands) {
            i = -1;
        }

        if (this.dh.vrSettings.vrHudLockMode == VRSettings.HUDLock.WRIST) {
            barStartPos = this.getAimRotation(1).transform(new Vector3((float) i * 0.02F, 0.05F, 0.26F)).toVector3d();
            barEndPos = this.getAimRotation(1).transform(new Vector3((float) i * 0.02F, 0.05F, 0.01F)).toVector3d();
        } else if (this.dh.vrSettings.vrHudLockMode == VRSettings.HUDLock.HAND) {
            barStartPos = this.getAimRotation(1).transform(new Vector3((float) i * -0.18F, 0.08F, -0.01F)).toVector3d();
            barEndPos = this.getAimRotation(1).transform(new Vector3((float) i * 0.19F, 0.04F, -0.08F)).toVector3d();
        } else {
            return; //how did u get here
        }

        float guiScaleFactor = (float) this.mc.getWindow().getGuiScale() / GuiHandler.guiScaleFactorMax;

        Vec3 barMidPos = barStartPos.add(barEndPos).scale(0.5);

        Vec3 barStart = off.add(
            Mth.lerp(guiScaleFactor, barMidPos.x, barStartPos.x),
            Mth.lerp(guiScaleFactor, barMidPos.y, barStartPos.y),
            Mth.lerp(guiScaleFactor, barMidPos.z, barStartPos.z));
        Vec3 barEnd = off.add(
            Mth.lerp(guiScaleFactor, barMidPos.x, barEndPos.x),
            Mth.lerp(guiScaleFactor, barMidPos.y, barEndPos.y),
            Mth.lerp(guiScaleFactor, barMidPos.z, barEndPos.z));


        Vec3 u = barStart.subtract(barEnd);
        Vec3 pq = barStart.subtract(main);
        float dist = (float) (pq.cross(u).length() / u.length());

        if (dist > 0.06) {
            return;
        }

        float fact = (float) (pq.dot(u) / (u.x * u.x + u.y * u.y + u.z * u.z));

        if (fact < -1) {
            return;
        }

        Vec3 w2 = u.scale(fact).subtract(pq);

        Vec3 point = main.subtract(w2);
        float linelen = (float) u.length();
        float ilen = (float) barStart.subtract(point).length();
        if (fact < 0) {
            ilen *= -1;
        }
        float pos = ilen / linelen * 9;

        if (dh.vrSettings.reverseHands) {
            pos = 9 - pos;
        }

        int box = (int) Math.floor(pos);

        if (box > 8) {
            return;
        }
        if (box < 0) {
            if (pos <= -0.5 && pos >= -1.5) //TODO fix reversed hands situation.
            {
                box = 9;
            } else {
                return;
            }
        }
        //all that maths for this.
        dh.interactTracker.hotbar = box;
        if (previousSlot != dh.interactTracker.hotbar) {
            triggerHapticPulse(0, 750);
        }
    }

    protected KeyMapping findKeyBinding(String name) {
        return Stream.concat(Arrays.stream(this.mc.options.keyMappings), mod.getHiddenKeyBindings().stream()).filter((kb) ->
        {
            return name.equals(kb.getName());
        }).findFirst().orElse(null);
    }

    protected void hmdSampling() {
        if (this.hmdPosSamples.size() == this.hmdAvgLength) {
            this.hmdPosSamples.removeFirst();
        }

        if (this.hmdYawSamples.size() == this.hmdAvgLength) {
            this.hmdYawSamples.removeFirst();
        }

        float f = this.dh.vrPlayer.vrdata_room_pre.hmd.getYaw();

        if (f < 0.0F) {
            f += 360.0F;
        }

        this.hmdYawTotal += Utils.angleDiff(f, this.hmdYawLast);
        this.hmdYawLast = f;

        if (Math.abs(Utils.angleNormalize(this.hmdYawTotal) - this.hmdYawLast) > 1.0F || this.hmdYawTotal > 100000.0F) {
            this.hmdYawTotal = this.hmdYawLast;
            System.out.println("HMD yaw desync/overflow corrected");
        }

        this.hmdPosSamples.add(this.dh.vrPlayer.vrdata_room_pre.hmd.getPosition());
        float f1 = 0.0F;

        if (this.hmdYawSamples.size() > 0) {
            for (float f2 : this.hmdYawSamples) {
                f1 += f2;
            }

            f1 /= (float) this.hmdYawSamples.size();
        }

        if (Math.abs(this.hmdYawTotal - f1) > 20.0F) {
            this.trigger = true;
        }

        if (Math.abs(this.hmdYawTotal - f1) < 1.0F) {
            this.trigger = false;
        }

        if (this.trigger || this.hmdYawSamples.isEmpty()) {
            this.hmdYawSamples.add(this.hmdYawTotal);
        }
    }

    protected void updateAim() {
        RenderPassManager.setGUIRenderPass();


        if (this.mc != null) {
            this.hmdRotation.M[0][0] = this.hmdPose.M[0][0];
            this.hmdRotation.M[0][1] = this.hmdPose.M[0][1];
            this.hmdRotation.M[0][2] = this.hmdPose.M[0][2];
            this.hmdRotation.M[0][3] = 0.0F;
            this.hmdRotation.M[1][0] = this.hmdPose.M[1][0];
            this.hmdRotation.M[1][1] = this.hmdPose.M[1][1];
            this.hmdRotation.M[1][2] = this.hmdPose.M[1][2];
            this.hmdRotation.M[1][3] = 0.0F;
            this.hmdRotation.M[2][0] = this.hmdPose.M[2][0];
            this.hmdRotation.M[2][1] = this.hmdPose.M[2][1];
            this.hmdRotation.M[2][2] = this.hmdPose.M[2][2];
            this.hmdRotation.M[2][3] = 0.0F;
            this.hmdRotation.M[3][0] = 0.0F;
            this.hmdRotation.M[3][1] = 0.0F;
            this.hmdRotation.M[3][2] = 0.0F;
            this.hmdRotation.M[3][3] = 1.0F;
            Vec3 vec3 = this.getCenterEyePosition();
            this.hmdHistory.add(vec3);
            Vector3 vector3 = this.hmdRotation.transform(new Vector3(0.0F, -0.1F, 0.1F));
            this.hmdPivotHistory.add(new Vec3((double) vector3.getX() + vec3.x, (double) vector3.getY() + vec3.y, (double) vector3.getZ() + vec3.z));
            hmdRotHistory.add(new Quaternionf().setFromNormalized(hmdRotation.transposed().toMCMatrix().rotateY((float) -Math.toRadians(this.dh.vrSettings.worldRotation))));

            if (this.dh.vrSettings.seated) {
                this.controllerPose[0] = this.hmdPose.inverted().inverted();
                this.controllerPose[1] = this.hmdPose.inverted().inverted();
            }

            org.vivecraft.common.utils.math.Matrix4f[] amatrix4f = new org.vivecraft.common.utils.math.Matrix4f[]{new org.vivecraft.common.utils.math.Matrix4f(), new org.vivecraft.common.utils.math.Matrix4f()};
            org.vivecraft.common.utils.math.Matrix4f[] amatrix4f1 = new org.vivecraft.common.utils.math.Matrix4f[]{new org.vivecraft.common.utils.math.Matrix4f(), new org.vivecraft.common.utils.math.Matrix4f()};

            if (this.dh.vrSettings.seated) {
                amatrix4f1[0] = this.controllerPose[0];
            } else {
                amatrix4f1[0] = org.vivecraft.common.utils.math.Matrix4f.multiply(this.controllerPose[0], this.getControllerComponentTransform(0, "handgrip"));
            }

            this.handRotation[0].M[0][0] = amatrix4f1[0].M[0][0];
            this.handRotation[0].M[0][1] = amatrix4f1[0].M[0][1];
            this.handRotation[0].M[0][2] = amatrix4f1[0].M[0][2];
            this.handRotation[0].M[0][3] = 0.0F;
            this.handRotation[0].M[1][0] = amatrix4f1[0].M[1][0];
            this.handRotation[0].M[1][1] = amatrix4f1[0].M[1][1];
            this.handRotation[0].M[1][2] = amatrix4f1[0].M[1][2];
            this.handRotation[0].M[1][3] = 0.0F;
            this.handRotation[0].M[2][0] = amatrix4f1[0].M[2][0];
            this.handRotation[0].M[2][1] = amatrix4f1[0].M[2][1];
            this.handRotation[0].M[2][2] = amatrix4f1[0].M[2][2];
            this.handRotation[0].M[2][3] = 0.0F;
            this.handRotation[0].M[3][0] = 0.0F;
            this.handRotation[0].M[3][1] = 0.0F;
            this.handRotation[0].M[3][2] = 0.0F;
            this.handRotation[0].M[3][3] = 1.0F;

            if (this.dh.vrSettings.seated) {
                amatrix4f[0] = this.controllerPose[0];
            } else {
                amatrix4f[0] = org.vivecraft.common.utils.math.Matrix4f.multiply(this.controllerPose[0], this.getControllerComponentTransform(0, "tip"));
            }

            Vector3 vector31 = Utils.convertMatrix4ftoTranslationVector(amatrix4f[0]);
            this.aimSource[0] = vector31.toVector3d();
            this.controllerHistory[0].add(this.getAimSource(0));
            this.controllerRotation[0].M[0][0] = amatrix4f[0].M[0][0];
            this.controllerRotation[0].M[0][1] = amatrix4f[0].M[0][1];
            this.controllerRotation[0].M[0][2] = amatrix4f[0].M[0][2];
            this.controllerRotation[0].M[0][3] = 0.0F;
            this.controllerRotation[0].M[1][0] = amatrix4f[0].M[1][0];
            this.controllerRotation[0].M[1][1] = amatrix4f[0].M[1][1];
            this.controllerRotation[0].M[1][2] = amatrix4f[0].M[1][2];
            this.controllerRotation[0].M[1][3] = 0.0F;
            this.controllerRotation[0].M[2][0] = amatrix4f[0].M[2][0];
            this.controllerRotation[0].M[2][1] = amatrix4f[0].M[2][1];
            this.controllerRotation[0].M[2][2] = amatrix4f[0].M[2][2];
            this.controllerRotation[0].M[2][3] = 0.0F;
            this.controllerRotation[0].M[3][0] = 0.0F;
            this.controllerRotation[0].M[3][1] = 0.0F;
            this.controllerRotation[0].M[3][2] = 0.0F;
            this.controllerRotation[0].M[3][3] = 1.0F;
            Vec3 vec31 = this.getHmdVector();

            if (this.dh.vrSettings.seated && this.mc.screen == null && this.mc.mouseHandler.isMouseGrabbed()) {
                Matrix4f matrix4f = new Matrix4f();
                float f = 110.0F;
                float f1 = 180.0F;
                double d0 = this.mc.mouseHandler.xpos() / (double) this.mc.getWindow().getScreenWidth() * (double) f - (double) (f / 2.0F);
                int i = this.mc.getWindow().getScreenHeight();

                if (i % 2 != 0) {
                    --i;
                }

                double d1 = -this.mc.mouseHandler.ypos() / (double) i * (double) f1 + (double) (f1 / 2.0F);
                double d2 = -d1;

                if (this.mc.isWindowActive()) {
                    float f2 = this.dh.vrSettings.keyholeX;
                    float f3 = 20.0F * this.dh.vrSettings.xSensitivity;
                    int j = (int) ((double) (-f2 + f / 2.0F) * (double) this.mc.getWindow().getScreenWidth() / (double) f) + 1;
                    int k = (int) ((double) (f2 + f / 2.0F) * (double) this.mc.getWindow().getScreenWidth() / (double) f) - 1;
                    float f4 = ((float) Math.abs(d0) - f2) / (f / 2.0F - f2);
                    double d3 = this.mc.mouseHandler.xpos();

                    if (d0 < (double) (-f2)) {
                        this.seatedRot += f3 * f4;
                        this.seatedRot %= 360.0F;
                        this.hmdForwardYaw = (float) Math.toDegrees(Math.atan2(vec31.x, vec31.z));
                        d3 = j;
                        d0 = -f2;
                    } else if (d0 > (double) f2) {
                        this.seatedRot -= f3 * f4;
                        this.seatedRot %= 360.0F;
                        this.hmdForwardYaw = (float) Math.toDegrees(Math.atan2(vec31.x, vec31.z));
                        d3 = k;
                        d0 = f2;
                    }

                    double d4 = 0.5D * (double) this.dh.vrSettings.ySensitivity;
                    d2 = (double) this.aimPitch + d1 * d4;
                    d2 = Mth.clamp(d2, -89.9D, 89.9D);
                    double screenX = d3 * (((WindowExtension) (Object) this.mc.getWindow()).vivecraft$getActualScreenWidth() / (double) this.mc.getWindow().getScreenWidth());
                    double screenY = (i * 0.5F) * (((WindowExtension) (Object) this.mc.getWindow()).vivecraft$getActualScreenHeight() / (double) this.mc.getWindow().getScreenHeight());
                    InputSimulator.setMousePos(screenX, screenY);
                    GLFW.glfwSetCursorPos(this.mc.getWindow().getWindow(), screenX, screenY);
                    matrix4f.rotate((float) Math.toRadians(-d2), new Vector3f(1.0F, 0.0F, 0.0F));
                    matrix4f.rotate((float) Math.toRadians(-180.0D + d0 - (double) this.hmdForwardYaw), new Vector3f(0.0F, 1.0F, 0.0F));
                }

                this.controllerRotation[0].M[0][0] = matrix4f.m00;
                this.controllerRotation[0].M[0][1] = matrix4f.m01;
                this.controllerRotation[0].M[0][2] = matrix4f.m02;
                this.controllerRotation[0].M[1][0] = matrix4f.m10;
                this.controllerRotation[0].M[1][1] = matrix4f.m11;
                this.controllerRotation[0].M[1][2] = matrix4f.m12;
                this.controllerRotation[0].M[2][0] = matrix4f.m20;
                this.controllerRotation[0].M[2][1] = matrix4f.m21;
                this.controllerRotation[0].M[2][2] = matrix4f.m22;

                this.handRotation[0].M[0][0] = matrix4f.m00;
                this.handRotation[0].M[0][1] = matrix4f.m01;
                this.handRotation[0].M[0][2] = matrix4f.m02;
                this.handRotation[0].M[1][0] = matrix4f.m10;
                this.handRotation[0].M[1][1] = matrix4f.m11;
                this.handRotation[0].M[1][2] = matrix4f.m12;
                this.handRotation[0].M[2][0] = matrix4f.m20;
                this.handRotation[0].M[2][1] = matrix4f.m21;
                this.handRotation[0].M[2][2] = matrix4f.m22;
            }

            Vec3 vec32 = this.getAimVector(0);
            this.aimPitch = (float) Math.toDegrees(Math.asin(vec32.y / vec32.length()));
            this.controllerForwardHistory[0].add(vec32);
            Vec3 vec33 = this.controllerRotation[0].transform(this.up).toVector3d();
            this.controllerUpHistory[0].add(vec33);

            if (this.dh.vrSettings.seated) {
                amatrix4f1[1] = this.controllerPose[1];
            } else {
                amatrix4f1[1] = org.vivecraft.common.utils.math.Matrix4f.multiply(this.controllerPose[1], this.getControllerComponentTransform(1, "handgrip"));
            }

            this.handRotation[1].M[0][0] = amatrix4f1[1].M[0][0];
            this.handRotation[1].M[0][1] = amatrix4f1[1].M[0][1];
            this.handRotation[1].M[0][2] = amatrix4f1[1].M[0][2];
            this.handRotation[1].M[0][3] = 0.0F;
            this.handRotation[1].M[1][0] = amatrix4f1[1].M[1][0];
            this.handRotation[1].M[1][1] = amatrix4f1[1].M[1][1];
            this.handRotation[1].M[1][2] = amatrix4f1[1].M[1][2];
            this.handRotation[1].M[1][3] = 0.0F;
            this.handRotation[1].M[2][0] = amatrix4f1[1].M[2][0];
            this.handRotation[1].M[2][1] = amatrix4f1[1].M[2][1];
            this.handRotation[1].M[2][2] = amatrix4f1[1].M[2][2];
            this.handRotation[1].M[2][3] = 0.0F;
            this.handRotation[1].M[3][0] = 0.0F;
            this.handRotation[1].M[3][1] = 0.0F;
            this.handRotation[1].M[3][2] = 0.0F;
            this.handRotation[1].M[3][3] = 1.0F;

            if (this.dh.vrSettings.seated) {
                amatrix4f[1] = this.controllerPose[1];
            } else {
                amatrix4f[1] = org.vivecraft.common.utils.math.Matrix4f.multiply(this.controllerPose[1], this.getControllerComponentTransform(1, "tip"));
            }

            vector31 = Utils.convertMatrix4ftoTranslationVector(amatrix4f[1]);
            this.aimSource[1] = vector31.toVector3d();
            this.controllerHistory[1].add(this.getAimSource(1));
            this.controllerRotation[1].M[0][0] = amatrix4f[1].M[0][0];
            this.controllerRotation[1].M[0][1] = amatrix4f[1].M[0][1];
            this.controllerRotation[1].M[0][2] = amatrix4f[1].M[0][2];
            this.controllerRotation[1].M[0][3] = 0.0F;
            this.controllerRotation[1].M[1][0] = amatrix4f[1].M[1][0];
            this.controllerRotation[1].M[1][1] = amatrix4f[1].M[1][1];
            this.controllerRotation[1].M[1][2] = amatrix4f[1].M[1][2];
            this.controllerRotation[1].M[1][3] = 0.0F;
            this.controllerRotation[1].M[2][0] = amatrix4f[1].M[2][0];
            this.controllerRotation[1].M[2][1] = amatrix4f[1].M[2][1];
            this.controllerRotation[1].M[2][2] = amatrix4f[1].M[2][2];
            this.controllerRotation[1].M[2][3] = 0.0F;
            this.controllerRotation[1].M[3][0] = 0.0F;
            this.controllerRotation[1].M[3][1] = 0.0F;
            this.controllerRotation[1].M[3][2] = 0.0F;
            this.controllerRotation[1].M[3][3] = 1.0F;
            vec31 = this.getAimVector(1);
            this.controllerForwardHistory[1].add(vec31);
            vec32 = this.controllerRotation[1].transform(this.up).toVector3d();
            this.controllerUpHistory[1].add(vec32);

            if (this.dh.vrSettings.seated) {
                this.aimSource[1] = this.getCenterEyePosition();
                this.aimSource[0] = this.getCenterEyePosition();
            }

            boolean flag = false;

            if (flag) {
                this.controllerPose[2] = this.controllerPose[0];
            }

            this.controllerRotation[2].M[0][0] = this.controllerPose[2].M[0][0];
            this.controllerRotation[2].M[0][1] = this.controllerPose[2].M[0][1];
            this.controllerRotation[2].M[0][2] = this.controllerPose[2].M[0][2];
            this.controllerRotation[2].M[0][3] = 0.0F;
            this.controllerRotation[2].M[1][0] = this.controllerPose[2].M[1][0];
            this.controllerRotation[2].M[1][1] = this.controllerPose[2].M[1][1];
            this.controllerRotation[2].M[1][2] = this.controllerPose[2].M[1][2];
            this.controllerRotation[2].M[1][3] = 0.0F;
            this.controllerRotation[2].M[2][0] = this.controllerPose[2].M[2][0];
            this.controllerRotation[2].M[2][1] = this.controllerPose[2].M[2][1];
            this.controllerRotation[2].M[2][2] = this.controllerPose[2].M[2][2];
            this.controllerRotation[2].M[2][3] = 0.0F;
            this.controllerRotation[2].M[3][0] = 0.0F;
            this.controllerRotation[2].M[3][1] = 0.0F;
            this.controllerRotation[2].M[3][2] = 0.0F;
            this.controllerRotation[2].M[3][3] = 1.0F;

            if ((!this.hasThirdController() || this.dh.vrSettings.displayMirrorMode != VRSettings.MirrorMode.MIXED_REALITY && this.dh.vrSettings.displayMirrorMode != VRSettings.MirrorMode.THIRD_PERSON) && !flag) {
                this.mrMovingCamActive = false;
                this.aimSource[2] = new Vec3(this.dh.vrSettings.vrFixedCamposX, this.dh.vrSettings.vrFixedCamposY, this.dh.vrSettings.vrFixedCamposZ);
            } else {
                this.mrMovingCamActive = true;
                Vector3 vector32 = Utils.convertMatrix4ftoTranslationVector(this.controllerPose[2]);
                this.aimSource[2] = vector32.toVector3d();
            }
        }
    }

    public void processBindings() {
        if (!this.inputActions.isEmpty()) {
            boolean flag = this.mc.level != null && this.mc.player != null && this.mc.player.isSleeping();
            boolean flag1 = this.mc.screen != null;
            boolean flag2 = mod.keyToggleMovement.consumeClick();

            if (!this.mc.options.keyPickItem.isDown() && !flag2) {
                this.moveModeSwitchCount = 0;
            } else if (++this.moveModeSwitchCount == 80 || flag2) {
                if (this.dh.vrSettings.seated) {
                    this.dh.vrSettings.seatedFreeMove = !this.dh.vrSettings.seatedFreeMove;
                    this.mc.gui.getChat().addMessage(Component.translatable("vivecraft.messages.movementmodeswitch", this.dh.vrSettings.seatedFreeMove ? Component.translatable("vivecraft.options.freemove") : Component.translatable("vivecraft.options.teleport")));
                } else if (this.dh.vrPlayer.isTeleportSupported()) {
                    this.dh.vrSettings.forceStandingFreeMove = !this.dh.vrSettings.forceStandingFreeMove;
                    this.mc.gui.getChat().addMessage(Component.translatable("vivecraft.messages.movementmodeswitch", this.dh.vrSettings.seatedFreeMove ? Component.translatable("vivecraft.options.freemove") : Component.translatable("vivecraft.options.teleport")));
                } else if (this.dh.vrPlayer.isTeleportOverridden()) {
                    this.dh.vrPlayer.setTeleportOverride(false);
                    this.mc.gui.getChat().addMessage(Component.translatable("vivecraft.messages.teleportdisabled"));
                } else {
                    this.dh.vrPlayer.setTeleportOverride(true);
                    this.mc.gui.getChat().addMessage(Component.translatable("vivecraft.messages.teleportenabled"));
                }
            }

            Vec3 vec3 = this.getAimVector(0);
            Vec3 vec31 = this.getAimVector(1);
            float f = (float) Math.toDegrees(Math.atan2(-vec3.x, vec3.z));
            float f1 = (float) Math.toDegrees(Math.atan2(-vec31.x, vec31.z));

            if (!flag1) {
                if (mod.keyWalkabout.isDown()) {
                    float f2 = f;
                    ControllerType controllertype = this.findActiveBindingControllerType(mod.keyWalkabout);

                    if (controllertype != null && controllertype == ControllerType.LEFT) {
                        f2 = f1;
                    }

                    if (!this.isWalkingAbout) {
                        this.isWalkingAbout = true;
                        this.walkaboutYawStart = this.dh.vrSettings.worldRotation - f2;
                    } else {
                        this.dh.vrSettings.worldRotation = this.walkaboutYawStart + f2;
                        this.dh.vrSettings.worldRotation %= 360.0F;
                    }
                } else {
                    this.isWalkingAbout = false;
                }

                if (mod.keyRotateFree.isDown()) {
                    float f3 = f;
                    ControllerType controllertype5 = this.findActiveBindingControllerType(mod.keyRotateFree);

                    if (controllertype5 != null && controllertype5 == ControllerType.LEFT) {
                        f3 = f1;
                    }

                    if (!this.isFreeRotate) {
                        this.isFreeRotate = true;
                        this.walkaboutYawStart = this.dh.vrSettings.worldRotation + f3;
                    } else {
                        this.dh.vrSettings.worldRotation = this.walkaboutYawStart - f3;
                    }
                } else {
                    this.isFreeRotate = false;
                }
            }

            if (mod.keyHotbarNext.consumeClick()) {
                this.changeHotbar(-1);
                this.triggerBindingHapticPulse(mod.keyHotbarNext, 250);
            }

            if (mod.keyHotbarPrev.consumeClick()) {
                this.changeHotbar(1);
                this.triggerBindingHapticPulse(mod.keyHotbarPrev, 250);
            }

            if (mod.keyQuickTorch.consumeClick() && this.mc.player != null) {
                for (int j = 0; j < 9; ++j) {
                    ItemStack itemstack = this.mc.player.getInventory().getItem(j);

                    if (itemstack.getItem() instanceof BlockItem && ((BlockItem) itemstack.getItem()).getBlock() instanceof TorchBlock && this.mc.screen == null) {
                        this.quickTorchPreviousSlot = this.mc.player.getInventory().selected;
                        this.mc.player.getInventory().selected = j;
                        this.mc.startUseItem();
                        this.mc.player.getInventory().selected = this.quickTorchPreviousSlot;
                        this.quickTorchPreviousSlot = -1;
                        break;
                    }
                }
            }

            if (flag1 && !flag && this.mc.options.keyUp.isDown() && !(this.mc.screen instanceof WinScreen) && this.mc.player != null) {
                this.mc.player.closeContainer();
            }

            if (this.mc.screen instanceof AbstractContainerScreen && this.mc.options.keyInventory.consumeClick() && this.mc.player != null) {
                this.mc.player.closeContainer();
            }

            if (this.mc.screen instanceof ChatScreen && this.mc.options.keyChat.consumeClick()) {
                this.mc.setScreen(null);
            }

            if (this.dh.vrSettings.worldRotationIncrement == 0.0F) {
                float f4 = this.getInputAction(mod.keyRotateAxis).getAxis2DUseTracked().getX();

                if (f4 == 0.0F) {
                    f4 = this.getInputAction(mod.keyFreeMoveRotate).getAxis2DUseTracked().getX();
                }

                if (f4 != 0.0F) {
                    float f8 = 10.0F * f4;
                    this.dh.vrSettings.worldRotation -= f8;
                    this.dh.vrSettings.worldRotation %= 360.0F;
                }
            } else if (mod.keyRotateAxis.consumeClick() || mod.keyFreeMoveRotate.consumeClick()) {
                float f5 = this.getInputAction(mod.keyRotateAxis).getAxis2D(false).getX();

                if (f5 == 0.0F) {
                    f5 = this.getInputAction(mod.keyFreeMoveRotate).getAxis2D(false).getX();
                }

                if (Math.abs(f5) > 0.5F) {
                    this.dh.vrSettings.worldRotation -= this.dh.vrSettings.worldRotationIncrement * Math.signum(f5);
                    this.dh.vrSettings.worldRotation %= 360.0F;
                }
            }

            Vector2 axis = this.getInputAction(mod.keyFlickStick).getAxis2DUseTracked();
            if (axis.getX() != 0F || axis.getY() != 0F) {

                float rotation = (float) Math.toDegrees(Math.atan2(axis.getX(), axis.getY()));
                if (isFlickStick) {
                    this.dh.vrSettings.worldRotation += this.flickStickRot - rotation;
                } else {
                    isFlickStick = true;
                    this.dh.vrSettings.worldRotation -= rotation;
                }

                this.dh.vrSettings.worldRotation %= 360.0F;
                this.flickStickRot = rotation;
            } else {
                this.flickStickRot = 0F;
                isFlickStick = false;
            }

            if (this.dh.vrSettings.worldRotationIncrement == 0.0F) {
                float f6 = VivecraftMovementInput.getMovementAxisValue(mod.keyRotateLeft);

                if (f6 > 0.0F) {
                    float f9 = 5.0F;

                    if (f6 > 0.0F) {
                        f9 = 10.0F * f6;
                    }

                    this.dh.vrSettings.worldRotation += f9;
                    this.dh.vrSettings.worldRotation %= 360.0F;
                }
            } else if (mod.keyRotateLeft.consumeClick()) {
                this.dh.vrSettings.worldRotation += this.dh.vrSettings.worldRotationIncrement;
                this.dh.vrSettings.worldRotation %= 360.0F;
            }

            if (this.dh.vrSettings.worldRotationIncrement == 0.0F) {
                float f7 = VivecraftMovementInput.getMovementAxisValue(mod.keyRotateRight);

                if (f7 > 0.0F) {
                    float f10 = 5.0F;

                    if (f7 > 0.0F) {
                        f10 = 10.0F * f7;
                    }

                    this.dh.vrSettings.worldRotation -= f10;
                    this.dh.vrSettings.worldRotation %= 360.0F;
                }
            } else if (mod.keyRotateRight.consumeClick()) {
                this.dh.vrSettings.worldRotation -= this.dh.vrSettings.worldRotationIncrement;
                this.dh.vrSettings.worldRotation %= 360.0F;
            }

            this.seatedRot = this.dh.vrSettings.worldRotation;

            if (mod.keyRadialMenu.consumeClick() && !flag1) {
                ControllerType controllertype1 = this.findActiveBindingControllerType(mod.keyRadialMenu);

                if (controllertype1 != null) {
                    RadialHandler.setOverlayShowing(!RadialHandler.isShowing(), controllertype1);
                }
            }

            if (mod.keySwapMirrorView.consumeClick()) {
                if (this.dh.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON) {
                    this.dh.vrSettings.displayMirrorMode = VRSettings.MirrorMode.FIRST_PERSON;
                } else if (this.dh.vrSettings.displayMirrorMode == VRSettings.MirrorMode.FIRST_PERSON) {
                    this.dh.vrSettings.displayMirrorMode = VRSettings.MirrorMode.THIRD_PERSON;
                }

                this.dh.vrRenderer.reinitWithoutShaders("Mirror Setting Changed");
            }

            if (mod.keyToggleKeyboard.consumeClick()) {
                KeyboardHandler.setOverlayShowing(!KeyboardHandler.Showing);
            }

            if (mod.keyMoveThirdPersonCam.consumeClick() && !ClientDataHolderVR.kiosk && !this.dh.vrSettings.seated && (this.dh.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY || this.dh.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON)) {
                ControllerType controllertype2 = this.findActiveBindingControllerType(mod.keyMoveThirdPersonCam);

                if (controllertype2 != null) {
                    VRHotkeys.startMovingThirdPersonCam(controllertype2.ordinal(), VRHotkeys.Triggerer.BINDING);
                }
            }

            if (!mod.keyMoveThirdPersonCam.isDown() && VRHotkeys.isMovingThirdPersonCam() && VRHotkeys.getMovingThirdPersonCamTriggerer() == VRHotkeys.Triggerer.BINDING) {
                VRHotkeys.stopMovingThirdPersonCam();
                this.dh.vrSettings.saveOptions();
            }

            if (VRHotkeys.isMovingThirdPersonCam() && VRHotkeys.getMovingThirdPersonCamTriggerer() == VRHotkeys.Triggerer.MENUBUTTON && mod.keyMenuButton.consumeClick()) {
                VRHotkeys.stopMovingThirdPersonCam();
                this.dh.vrSettings.saveOptions();
            }

            if (KeyboardHandler.Showing && this.mc.screen == null && mod.keyMenuButton.consumeClick()) {
                KeyboardHandler.setOverlayShowing(false);
            }

            if (RadialHandler.isShowing() && mod.keyMenuButton.consumeClick()) {
                RadialHandler.setOverlayShowing(false, null);
            }

            if (mod.keyMenuButton.consumeClick()) {
                if (!flag1) {
                    if (!ClientDataHolderVR.kiosk) {
                        this.mc.pauseGame(false);
                    }
                } else {
                    InputSimulator.pressKey(256);
                    InputSimulator.releaseKey(256);
                }

                KeyboardHandler.setOverlayShowing(false);
            }

            if (mod.keyTogglePlayerList.consumeClick()) {
                ((GuiExtension) this.mc.gui).vivecraft$setShowPlayerList(!((GuiExtension) this.mc.gui).vivecraft$getShowPlayerList());
            }

            if (mod.keyToggleHandheldCam.consumeClick() && this.mc.player != null) {
                this.dh.cameraTracker.toggleVisibility();

                if (this.dh.cameraTracker.isVisible()) {
                    ControllerType controllertype3 = this.findActiveBindingControllerType(mod.keyToggleHandheldCam);

                    if (controllertype3 == null) {
                        controllertype3 = ControllerType.RIGHT;
                    }

                    VRData.VRDevicePose vrdata$vrdevicepose = this.dh.vrPlayer.vrdata_world_pre.getController(controllertype3.ordinal());
                    this.dh.cameraTracker.setPosition(vrdata$vrdevicepose.getPosition());
                    this.dh.cameraTracker.setRotation(new Quaternion(vrdata$vrdevicepose.getMatrix().transposed()));
                }
            }

            if (mod.keyQuickHandheldCam.consumeClick() && this.mc.player != null) {
                if (!this.dh.cameraTracker.isVisible()) {
                    this.dh.cameraTracker.toggleVisibility();
                }

                ControllerType controllertype4 = this.findActiveBindingControllerType(mod.keyQuickHandheldCam);

                if (controllertype4 == null) {
                    controllertype4 = ControllerType.RIGHT;
                }

                VRData.VRDevicePose vrdata$vrdevicepose1 = this.dh.vrPlayer.vrdata_world_pre.getController(controllertype4.ordinal());
                this.dh.cameraTracker.setPosition(vrdata$vrdevicepose1.getPosition());
                this.dh.cameraTracker.setRotation(new Quaternion(vrdata$vrdevicepose1.getMatrix().transposed()));
                this.dh.cameraTracker.startMoving(controllertype4.ordinal(), true);
            }

            if (!mod.keyQuickHandheldCam.isDown() && this.dh.cameraTracker.isMoving() && this.dh.cameraTracker.isQuickMode() && this.mc.player != null) {
                this.dh.cameraTracker.stopMoving();
                this.dh.grabScreenShot = true;
            }

            GuiHandler.processBindingsGui();
            RadialHandler.processBindings();
            KeyboardHandler.processBindings();
            this.dh.interactTracker.processBindings();
        }
    }

    public void populateInputActions() {
        Map<String, ActionParams> map = this.getSpecialActionParams();

        // iterate over all minecraft keys, and our hidden keys
        for (KeyMapping keymapping : Stream.concat(Arrays.stream(this.mc.options.keyMappings), mod.getHiddenKeyBindings().stream()).toList()) {
            ActionParams actionparams = map.getOrDefault(keymapping.getName(), new ActionParams("optional", "boolean", null));
            VRInputAction vrinputaction = new VRInputAction(keymapping, actionparams.requirement, actionparams.type, actionparams.actionSetOverride);
            this.inputActions.put(vrinputaction.name, vrinputaction);
        }

        for (VRInputAction vrinputaction1 : this.inputActions.values()) {
            this.inputActionsByKeyBinding.put(vrinputaction1.keyBinding.getName(), vrinputaction1);
        }

        this.getInputAction(mod.keyVRInteract).setPriority(5).setEnabled(false);
        this.getInputAction(mod.keyClimbeyGrab).setPriority(10).setEnabled(false);
        this.getInputAction(mod.keyClimbeyJump).setEnabled(false);
        this.getInputAction(GuiHandler.keyKeyboardClick).setPriority(50);
        this.getInputAction(GuiHandler.keyKeyboardShift).setPriority(50);
    }

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
        this.addActionParams(map, mod.keyHotbarScroll, "optional", "vector2", null);
        this.addActionParams(map, mod.keyHotbarSwipeX, "optional", "vector2", null);
        this.addActionParams(map, mod.keyHotbarSwipeY, "optional", "vector2", null);
        this.addActionParams(map, mod.keyMenuButton, "suggested", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, mod.keyTeleportFallback, "suggested", "vector1", null);
        this.addActionParams(map, mod.keyFreeMoveRotate, "optional", "vector2", null);
        this.addActionParams(map, mod.keyFreeMoveStrafe, "optional", "vector2", null);
        this.addActionParams(map, mod.keyRotateLeft, "optional", "vector1", null);
        this.addActionParams(map, mod.keyRotateRight, "optional", "vector1", null);
        this.addActionParams(map, mod.keyRotateAxis, "optional", "vector2", null);
        this.addActionParams(map, mod.keyFlickStick, "optional", "vector2", null);
        this.addActionParams(map, mod.keyRadialMenu, "suggested", "boolean", null);
        this.addActionParams(map, mod.keySwapMirrorView, "optional", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, mod.keyToggleKeyboard, "optional", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, mod.keyMoveThirdPersonCam, "optional", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, mod.keyToggleHandheldCam, "optional", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, mod.keyQuickHandheldCam, "optional", "boolean", VRInputActionSet.GLOBAL);
        this.addActionParams(map, mod.keyTrackpadTouch, "optional", "boolean", VRInputActionSet.TECHNICAL);
        this.addActionParams(map, mod.keyVRInteract, "suggested", "boolean", VRInputActionSet.CONTEXTUAL);
        this.addActionParams(map, mod.keyClimbeyGrab, "suggested", "boolean", null);
        this.addActionParams(map, mod.keyClimbeyJump, "suggested", "boolean", null);
        this.addActionParams(map, GuiHandler.keyLeftClick, "suggested", "boolean", null);
        this.addActionParams(map, GuiHandler.keyScrollAxis, "optional", "vector2", null);
        this.addActionParams(map, GuiHandler.keyRightClick, "suggested", "boolean", null);
        this.addActionParams(map, GuiHandler.keyShift, "suggested", "boolean", null);
        this.addActionParams(map, GuiHandler.keyKeyboardClick, "suggested", "boolean", null);
        this.addActionParams(map, GuiHandler.keyKeyboardShift, "suggested", "boolean", null);
        File file1 = new File("customactionsets.txt");

        if (file1.exists()) {
            System.out.println("Loading custom action set definitions...");
            String s;

            try (BufferedReader bufferedreader = new BufferedReader(new FileReader(file1))) {
                while ((s = bufferedreader.readLine()) != null) {
                    String[] astring = s.split(":", 2);

                    if (astring.length < 2) {
                        System.out.println("Invalid tokens: " + s);
                    } else {
                        KeyMapping keymapping = this.findKeyBinding(astring[0]);

                        if (keymapping == null) {
                            System.out.println("Unknown key binding: " + astring[0]);
                        } else if (mod.getAllKeyBindings().contains(keymapping)) {
                            System.out.println("NO! Don't touch Vivecraft bindings!");
                        } else {
                            VRInputActionSet vrinputactionset = null;
                            String s1 = astring[1].toLowerCase();

                            switch (s1) {
                                case "ingame":
                                    vrinputactionset = VRInputActionSet.INGAME;
                                    break;

                                case "gui":
                                    vrinputactionset = VRInputActionSet.GUI;
                                    break;

                                case "global":
                                    vrinputactionset = VRInputActionSet.GLOBAL;
                            }

                            if (vrinputactionset == null) {
                                System.out.println("Unknown action set: " + astring[1]);
                            } else {
                                this.addActionParams(map, keymapping, "optional", "boolean", vrinputactionset);
                            }
                        }
                    }
                }
            } catch (IOException ioexception) {
                ioexception.printStackTrace();
            }
        }

        return map;
    }

    protected void changeHotbar(int dir) {
        if (this.mc.player != null && (!this.dh.climbTracker.isGrabbingLadder() || !this.dh.climbTracker.isClaws(this.mc.player.getMainHandItem()))) {
            if (this.mc.screen == null) {
                InputSimulator.scrollMouse(0.0D, dir * 4);
            } else {
                this.mc.player.getInventory().swapPaint(dir);
            }
        }
    }

    protected void processInputAction(VRInputAction action) {
        if (action.isActive() && action.isEnabledRaw()
            // try to prevent double left clicks
            && (!ClientDataHolderVR.getInstance().vrSettings.ingameBindingsInGui
            || !(action.actionSet == VRInputActionSet.INGAME && action.keyBinding.key.getType() == InputConstants.Type.MOUSE && action.keyBinding.key.getValue() == 0 && mc.screen != null))) {
            if (action.isButtonChanged()) {
                if (action.isButtonPressed() && action.isEnabled()) {
                    if (!this.ignorePressesNextFrame) {
                        action.pressBinding();
                    }
                } else {
                    action.unpressBinding();
                }
            }
        } else {
            action.unpressBinding();
        }
    }

    protected void processScrollInput(KeyMapping keyBinding, Runnable upCallback, Runnable downCallback) {
        VRInputAction vrinputaction = this.getInputAction(keyBinding);

        if (vrinputaction.isEnabled() && vrinputaction.getLastOrigin() != 0L && vrinputaction.getAxis2D(true).getY() != 0.0F) {
            float f = vrinputaction.getAxis2D(false).getY();

            if (f > 0.0F) {
                upCallback.run();
            } else if (f < 0.0F) {
                downCallback.run();
            }
        }
    }

    protected void processSwipeInput(KeyMapping keyBinding, Runnable leftCallback, Runnable rightCallback, Runnable upCallback, Runnable downCallback) {
        VRInputAction vrinputaction = this.getInputAction(keyBinding);

        if (vrinputaction.isEnabled() && vrinputaction.getLastOrigin() != 0L) {
            ControllerType controllertype = this.findActiveBindingControllerType(keyBinding);

            if (controllertype != null) {
                if (!this.trackpadSwipeSamplers.containsKey(keyBinding.getName())) {
                    this.trackpadSwipeSamplers.put(keyBinding.getName(), new TrackpadSwipeSampler());
                }

                TrackpadSwipeSampler trackpadswipesampler = this.trackpadSwipeSamplers.get(keyBinding.getName());
                trackpadswipesampler.update(controllertype, vrinputaction.getAxis2D(false));

                if (trackpadswipesampler.isSwipedUp() && upCallback != null) {
                    this.triggerHapticPulse(controllertype, 0.001F, 400.0F, 0.5F);
                    upCallback.run();
                }

                if (trackpadswipesampler.isSwipedDown() && downCallback != null) {
                    this.triggerHapticPulse(controllertype, 0.001F, 400.0F, 0.5F);
                    downCallback.run();
                }

                if (trackpadswipesampler.isSwipedLeft() && leftCallback != null) {
                    this.triggerHapticPulse(controllertype, 0.001F, 400.0F, 0.5F);
                    leftCallback.run();
                }

                if (trackpadswipesampler.isSwipedRight() && rightCallback != null) {
                    this.triggerHapticPulse(controllertype, 0.001F, 400.0F, 0.5F);
                    rightCallback.run();
                }
            }
        }
    }

    private void addActionParams(Map<String, ActionParams> map, KeyMapping keyBinding, String requirement, String type, VRInputActionSet actionSetOverride) {
        ActionParams actionparams = new ActionParams(requirement, type, actionSetOverride);
        map.put(keyBinding.getName(), actionparams);
    }

    protected abstract void triggerBindingHapticPulse(KeyMapping var1, int var2);

    protected abstract ControllerType findActiveBindingControllerType(KeyMapping var1);

    public abstract void poll(long var1);

    public abstract Vector2f getPlayAreaSize();

    public abstract boolean init();

    public abstract boolean postinit() throws RenderConfigException;

    public abstract org.vivecraft.common.utils.math.Matrix4f getControllerComponentTransform(int var1, String var2);

    public abstract boolean hasThirdController();

    public abstract List<Long> getOrigins(VRInputAction var1);

    public abstract String getOriginName(long l);

    public abstract VRRenderer createVRRenderer();

    public abstract boolean isActive();

    public abstract ControllerType getOriginControllerType(long i);
    public boolean capFPS() {
        return false;
    }

    /**
     * @return the ipd in meters
     */
    public abstract float getIPD();

    public abstract String getRuntimeName();
}
