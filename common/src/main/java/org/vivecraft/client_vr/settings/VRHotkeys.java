package org.vivecraft.client_vr.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.vivecraft.client.utils.LangHelper;
import org.vivecraft.client.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.MethodHolder;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.render.MirrorNotification;
import org.vivecraft.common.utils.math.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class VRHotkeys {
    static final boolean DEBUG = false;
    private static int START_CONTROLLER;
    private static VRData.VRDevicePose START_CONTROLLER_POSE;
    private static float START_CAMPOS_X;
    private static float START_CAMPOS_Y;
    private static float START_CAMPOS_Z;
    private static Quaternion START_CAMROT_QUAT;
    private static Triggerer CAM_TRIGGERER;

    /**
     * process debug keys
     * @param key GLFW key that got pressed
     * @param scanCode GLFW scancode of the key
     * @param action GLFW key action (pressed/released)
     * @param modifiers GLFW key modifier
     * @return if a key was processed
     */
    public static boolean handleKeyboardInputs(int key, int scanCode, int action, int modifiers) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

        // Capture Minecrift key events
        boolean gotKey = false;

        if (action == GLFW.GLFW_PRESS) {
            // control key combinations
            if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                if (VRState.VR_INITIALIZED) {
                    // Debug aim
                    if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                        dataHolder.vrSettings.storeDebugAim = true;
                        minecraft.gui.getChat().addMessage(Component.translatable("vivecraft.messages.showaim"));
                        gotKey = true;
                    }

                    // Walk up blocks
                    if (key == GLFW.GLFW_KEY_B) {
                        dataHolder.vrSettings.walkUpBlocks = !dataHolder.vrSettings.walkUpBlocks;
                        minecraft.gui.getChat().addMessage(Component.translatable("vivecraft.messages.walkupblocks",
                            dataHolder.vrSettings.walkUpBlocks ? LangHelper.getYes() : LangHelper.getNo()));
                        gotKey = true;
                    }

                    // Player inertia
                    if (key == GLFW.GLFW_KEY_I) {
                        dataHolder.vrSettings.inertiaFactor = dataHolder.vrSettings.inertiaFactor.getNext();
                        minecraft.gui.getChat().addMessage(Component.translatable("vivecraft.messages.playerinertia",
                            Component.translatable(dataHolder.vrSettings.inertiaFactor.getLangKey())));

                        gotKey = true;
                    }

                    // for testing restricted client mode
                    if (key == GLFW.GLFW_KEY_R) {
                        if (dataHolder.vrPlayer.isTeleportOverridden()) {
                            dataHolder.vrPlayer.setTeleportOverride(false);
                            minecraft.gui.getChat()
                                .addMessage(Component.translatable("vivecraft.messages.teleportdisabled"));
                        } else {
                            dataHolder.vrPlayer.setTeleportOverride(true);
                            minecraft.gui.getChat()
                                .addMessage(Component.translatable("vivecraft.messages.teleportenabled"));
                        }

                        gotKey = true;
                    }
                }

                // toggle VR with a keyboard shortcut
                if (key == GLFW.GLFW_KEY_F7) {
                    VRState.VR_ENABLED = !VRState.VR_ENABLED;
                    ClientDataHolderVR.getInstance().vrSettings.vrEnabled = VRState.VR_ENABLED;
                    gotKey = true;
                }
            }

            if (key == GLFW.GLFW_KEY_F12 && DEBUG) {
                Screen current = minecraft.screen;
                minecraft.setScreen(new WinScreen(false, () -> minecraft.setScreen(current)));
                gotKey = true;
            }

            // toggle mirror mode
            if (key == GLFW.GLFW_KEY_F5 && (minecraft.level == null || minecraft.screen != null) &&
                VRState.VR_INITIALIZED)
            {
                dataHolder.vrSettings.setOptionValue(VRSettings.VrOptions.MIRROR_DISPLAY);
                MirrorNotification.notify(
                    dataHolder.vrSettings.getButtonDisplayString(VRSettings.VrOptions.MIRROR_DISPLAY), false, 3000);
                gotKey = true;
            }
        }

        if (VRState.VR_INITIALIZED) {
            gotKey |= dataHolder.vr.handleKeyboardInputs(key, scanCode, action, modifiers);

            if (dataHolder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY ||
                dataHolder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON)
            {
                gotKey |= VRHotkeys.handleMRKeys();
            }
        }

        if (gotKey) {
            dataHolder.vrSettings.saveOptions();
        }

        return gotKey;
    }

    /**
     * move third person camera with keys
     * @return if a key was processed
     */
    public static boolean handleMRKeys() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();
        boolean gotKey = false;
        if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT)) {
                // with shift do rotation
                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_UP)) {
                    adjustCamRot(Axis.PITCH, 0.5F);
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_DOWN)) {
                    adjustCamRot(Axis.PITCH, -0.5F);
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_LEFT)) {
                    adjustCamRot(Axis.YAW, 0.5F);
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_RIGHT)) {
                    adjustCamRot(Axis.YAW, -0.5F);
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_PAGE_UP)) {
                    adjustCamRot(Axis.ROLL, 0.5F);
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_PAGE_DOWN)) {
                    adjustCamRot(Axis.ROLL, -0.5F);
                    gotKey = true;
                }
            } else {
                // without shift do position
                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_LEFT)) {
                    adjustCamPos(new Vector3(-0.01F, 0.0F, 0.0F));
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_RIGHT)) {
                    adjustCamPos(new Vector3(0.01F, 0.0F, 0.0F));
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_UP)) {
                    adjustCamPos(new Vector3(0.0F, 0.0F, -0.01F));
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_DOWN)) {
                    adjustCamPos(new Vector3(0.0F, 0.0F, 0.01F));
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_PAGE_UP)) {
                    adjustCamPos(new Vector3(0.0F, 0.01F, 0.0F));
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_PAGE_DOWN)) {
                    adjustCamPos(new Vector3(0.0F, -0.01F, 0.0F));
                    gotKey = true;
                }

                // snap third person cam
                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_HOME)) {
                    snapMRCam(0);
                    gotKey = true;
                }
            }

            // change fov
            if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT)) {
                // third person fov
                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_INSERT)) {
                    dataHolder.vrSettings.mixedRealityFov++;
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_DELETE)) {
                    dataHolder.vrSettings.mixedRealityFov--;
                    gotKey = true;
                }
            } else {
                // first person fov
                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_INSERT)) {
                    minecraft.options.fov().set(minecraft.options.fov().get() + 1);
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_DELETE)) {
                    minecraft.options.fov().set(minecraft.options.fov().get() - 1);
                    gotKey = true;
                }
            }
        }

        if (gotKey) {
            dataHolder.vrSettings.saveOptions();

            if (dataHolder.vr.mrMovingCamActive) {
                minecraft.gui.getChat().addMessage(
                    Component.translatable("vivecraft.messages.coords",
                        dataHolder.vrSettings.mrMovingCamOffsetX,
                        dataHolder.vrSettings.mrMovingCamOffsetY,
                        dataHolder.vrSettings.mrMovingCamOffsetZ));
                Angle angle = dataHolder.vrSettings.mrMovingCamOffsetRotQuat.toEuler();
                minecraft.gui.getChat().addMessage(
                    Component.translatable("vivecraft.messages.angles",
                        angle.getPitch(), angle.getYaw(), angle.getRoll()));
            } else {
                minecraft.gui.getChat().addMessage(
                    Component.translatable("vivecraft.messages.coords",
                        dataHolder.vrSettings.vrFixedCamposX,
                        dataHolder.vrSettings.vrFixedCamposY,
                        dataHolder.vrSettings.vrFixedCamposZ));
                Angle angle1 = dataHolder.vrSettings.vrFixedCamrotQuat.toEuler();
                minecraft.gui.getChat().addMessage(
                    Component.translatable("vivecraft.messages.angles",
                        angle1.getPitch(), angle1.getYaw(), angle1.getRoll()));
            }
        }

        return gotKey;
    }

    /**
     * moves the camera position
     * @param offset offset to move the camera to, local to the camera
     */
    private static void adjustCamPos(Vector3 offset) {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

        if (dataHolder.vr.mrMovingCamActive) {
            offset = dataHolder.vrSettings.mrMovingCamOffsetRotQuat.multiply(offset);
            dataHolder.vrSettings.mrMovingCamOffsetX += offset.getX();
            dataHolder.vrSettings.mrMovingCamOffsetY += offset.getY();
            dataHolder.vrSettings.mrMovingCamOffsetZ += offset.getZ();
        } else {
            offset = dataHolder.vrSettings.vrFixedCamrotQuat.inverse().multiply(offset);
            dataHolder.vrSettings.vrFixedCamposX += offset.getX();
            dataHolder.vrSettings.vrFixedCamposY += offset.getY();
            dataHolder.vrSettings.vrFixedCamposZ += offset.getZ();
        }
    }

    /**
     * rotate the camera
     * @param axis camera local axis
     * @param degrees degree amount to rotate around {@code axis}
     */
    private static void adjustCamRot(Axis axis, float degrees) {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

        if (dataHolder.vr.mrMovingCamActive) {
            dataHolder.vrSettings.mrMovingCamOffsetRotQuat.set(dataHolder.vrSettings.mrMovingCamOffsetRotQuat.rotate(axis, degrees, true));
        } else {
            dataHolder.vrSettings.vrFixedCamrotQuat.set(dataHolder.vrSettings.vrFixedCamrotQuat.rotate(axis, degrees, false));
        }
    }

    /**
     * snaps the camera to the given controller
     * @param controller index of the controller to snap to
     */
    public static void snapMRCam(int controller) {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();
        Vec3 pos = dataHolder.vrPlayer.vrdata_room_pre.getController(controller).getPosition();
        dataHolder.vrSettings.vrFixedCamposX = (float) pos.x;
        dataHolder.vrSettings.vrFixedCamposY = (float) pos.y;
        dataHolder.vrSettings.vrFixedCamposZ = (float) pos.z;
        Quaternion quat = new Quaternion(
            MathUtils.convertOVRMatrix(dataHolder.vrPlayer.vrdata_room_pre.getController(controller).getMatrix()));
        dataHolder.vrSettings.vrFixedCamrotQuat.set(quat);
    }

    /**
     *
     */
    public static void updateMovingThirdPersonCam() {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

        if (START_CONTROLLER_POSE != null) {
            VRData.VRDevicePose controllerPose = dataHolder.vrPlayer.vrdata_room_pre.getController(START_CONTROLLER);
            Vec3 startPos = START_CONTROLLER_POSE.getPosition();
            Vec3 deltaPos = controllerPose.getPosition().subtract(startPos);

            Matrix4f deltaMatrix = Matrix4f.multiply(controllerPose.getMatrix(), START_CONTROLLER_POSE.getMatrix().inverted());
            Vector3 offset = new Vector3(
                START_CAMPOS_X - (float) startPos.x,
                START_CAMPOS_Y - (float) startPos.y,
                START_CAMPOS_Z - (float) startPos.z);
            Vector3 offsetRotated = deltaMatrix.transform(offset);

            dataHolder.vrSettings.vrFixedCamposX = START_CAMPOS_X + (float) deltaPos.x + (offsetRotated.getX() - offset.getX());
            dataHolder.vrSettings.vrFixedCamposY = START_CAMPOS_Y + (float) deltaPos.y + (offsetRotated.getY() - offset.getY());
            dataHolder.vrSettings.vrFixedCamposZ = START_CAMPOS_Z + (float) deltaPos.z + (offsetRotated.getZ() - offset.getZ());
            dataHolder.vrSettings.vrFixedCamrotQuat.set(START_CAMROT_QUAT.multiply(new Quaternion(MathUtils.convertOVRMatrix(deltaMatrix))));
        }
    }

    /**
     * starts moving the third person camera, stores the initial position
     * @param controller which controller moves the camera
     * @param triggerer what type of input caused the moving
     */
    public static void startMovingThirdPersonCam(int controller, Triggerer triggerer) {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

        START_CONTROLLER = controller;
        START_CONTROLLER_POSE = dataHolder.vrPlayer.vrdata_room_pre.getController(controller);
        START_CAMPOS_X = dataHolder.vrSettings.vrFixedCamposX;
        START_CAMPOS_Y = dataHolder.vrSettings.vrFixedCamposY;
        START_CAMPOS_Z = dataHolder.vrSettings.vrFixedCamposZ;
        START_CAMROT_QUAT = dataHolder.vrSettings.vrFixedCamrotQuat.copy();
        CAM_TRIGGERER = triggerer;
    }

    /**
     * stops moving the third person camera
     */
    public static void stopMovingThirdPersonCam() {
        START_CONTROLLER_POSE = null;
    }

    /**
     * @return if the third person camera is currently being moved
     */
    public static boolean isMovingThirdPersonCam() {
        return START_CONTROLLER_POSE != null;
    }

    /**
     * @return which controller is moving the third person camera
     */
    public static int getMovingThirdPersonCamController() {
        return START_CONTROLLER;
    }

    /**
     * @return what caused the third person camera movement
     */
    public static Triggerer getMovingThirdPersonCamTriggerer() {
        return CAM_TRIGGERER;
    }

    /**
     * read camera config file and set the position/rotation
     */
    public static void loadExternalCameraConfig(VRSettings vrSettings) {
        File file = new File("ExternalCamera.cfg");

        if (file.exists()) {
            float x = 0.0F, y = 0.0F, z = 0.0F;
            float rx = 0.0F, ry = 0.0F, rz = 0.0F;
            float fov = 40.0F;

            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] tokens = line.split("=", 2);

                    switch (tokens[0]) {
                        case "x" -> x = Float.parseFloat(tokens[1]);
                        case "y" -> y = Float.parseFloat(tokens[1]);
                        case "z" -> z = Float.parseFloat(tokens[1]);
                        case "rx" -> rx = Float.parseFloat(tokens[1]);
                        case "ry" -> ry = Float.parseFloat(tokens[1]);
                        case "rz" -> rz = Float.parseFloat(tokens[1]);
                        case "fov" -> fov = Float.parseFloat(tokens[1]);
                    }
                }
            } catch (Exception exception) {
                VRSettings.LOGGER.error("Vivecraft: error reading camera config:", exception);
                return;
            }

            // Eh just set everything, the fixed pos is overridden by the moving cam anyways
            Quaternion quaternion = new Quaternion(rx, ry, rz, vrSettings.externalCameraAngleOrder);
            vrSettings.mrMovingCamOffsetX = x;
            vrSettings.mrMovingCamOffsetY = y;
            vrSettings.mrMovingCamOffsetZ = z;
            vrSettings.mrMovingCamOffsetRotQuat.set(quaternion);
            vrSettings.vrFixedCamposX = x;
            vrSettings.vrFixedCamposY = y;
            vrSettings.vrFixedCamposZ = z;
            vrSettings.vrFixedCamrotQuat.set(quaternion);
            vrSettings.mixedRealityFov = fov;
        }
    }

    /**
     * @return if the user has a camera config file
     */
    public static boolean hasExternalCameraConfig() {
        return (new File("ExternalCamera.cfg")).exists();
    }

    public enum Triggerer {
        BINDING,
        MENUBUTTON,
        INTERACTION
    }
}
