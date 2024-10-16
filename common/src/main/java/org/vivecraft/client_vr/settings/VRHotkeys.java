package org.vivecraft.client_vr.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
    private static Vector3f START_CAMPOS;
    private static Quaternionf START_CAMROT_QUAT;
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
                    adjustCamPos(-0.01F, 0.0F, 0.0F);
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_RIGHT)) {
                    adjustCamPos(0.01F, 0.0F, 0.0F);
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_UP)) {
                    adjustCamPos(0.0F, 0.0F, -0.01F);
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_DOWN)) {
                    adjustCamPos(0.0F, 0.0F, 0.01F);
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_PAGE_UP)) {
                    adjustCamPos(0.0F, 0.01F, 0.0F);
                    gotKey = true;
                }

                if (MethodHolder.isKeyDown(GLFW.GLFW_KEY_PAGE_DOWN)) {
                    adjustCamPos(0.0F, -0.01F, 0.0F);
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
                logPositionRotation(dataHolder.vrSettings.mrMovingCamOffset, dataHolder.vrSettings.mrMovingCamOffsetRotQuat);
            } else {
                logPositionRotation(dataHolder.vrSettings.vrFixedCampos, dataHolder.vrSettings.vrFixedCamrotQuat);
            }
        }

        return gotKey;
    }

    private static void logPositionRotation(Vector3f position, Quaternionf rotation) {
        Minecraft.getInstance().gui.getChat().addMessage(
            Component.translatable("vivecraft.messages.coords",
                "%.2f".formatted(position.x),
                "%.2f".formatted(position.y),
                "%.2f".formatted(position.z)));
        Vector3f angle = MathUtils.getEulerAnglesYZX(rotation);
        Minecraft.getInstance().gui.getChat().addMessage(
            Component.translatable("vivecraft.messages.angles",
                "%.1f".formatted(Math.toDegrees(angle.x)),
                "%.1f".formatted(Math.toDegrees(angle.y)),
                "%.1f".formatted(Math.toDegrees(angle.z))));
    }

    /**
     * moves the camera position
     * @param offsetX offset to move the camera to along the X axis, local to the camera
     * @param offsetY offset to move the camera to along the Y axis, local to the camera
     * @param offsetZ offset to move the camera to along the Z axis, local to the camera
     */
    private static void adjustCamPos(float offsetX, float offsetY, float offsetZ) {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

        if (dataHolder.vr.mrMovingCamActive) {
            Vector3f offset = dataHolder.vrSettings.mrMovingCamOffsetRotQuat
                .transform(offsetX, offsetY, offsetZ, new Vector3f());
            dataHolder.vrSettings.mrMovingCamOffset.add(offset);
        } else {
            Vector3f offset = dataHolder.vrSettings.vrFixedCamrotQuat
                .transform(offsetX, offsetY, offsetZ, new Vector3f());
            dataHolder.vrSettings.vrFixedCampos.add(offset);
        }
    }

    /**
     * rotate the camera
     * @param axis camera local axis
     * @param degrees degree amount to rotate around {@code axis}
     */
    private static void adjustCamRot(Axis axis, float degrees) {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

        float rad = (float) Math.toRadians(degrees);

        if (dataHolder.vr.mrMovingCamActive) {
            switch (axis) {
                case PITCH -> dataHolder.vrSettings.mrMovingCamOffsetRotQuat.rotateLocalX(rad);
                case YAW -> dataHolder.vrSettings.mrMovingCamOffsetRotQuat.rotateLocalY(rad);
                case ROLL -> dataHolder.vrSettings.mrMovingCamOffsetRotQuat.rotateLocalZ(rad);
            }
        } else {
            dataHolder.vrSettings.vrFixedCamrotQuat.rotateAxis(
                rad,
                axis.getVector()
            );
        }
    }

    /**
     * snaps the camera to the given controller
     * @param controller index of the controller to snap to
     */
    public static void snapMRCam(int controller) {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();
        Vector3f pos = dataHolder.vrPlayer.vrdata_room_pre.getController(controller).getPositionF();
        dataHolder.vrSettings.vrFixedCampos.set(pos);

        dataHolder.vrSettings.vrFixedCamrotQuat
            .setFromNormalized(dataHolder.vrPlayer.vrdata_room_pre.getController(controller).getMatrix());
    }

    /**
     *
     */
    public static void updateMovingThirdPersonCam() {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

        if (START_CONTROLLER_POSE != null) {
            VRData.VRDevicePose controllerPose = dataHolder.vrPlayer.vrdata_room_pre.getController(START_CONTROLLER);
            Vector3f startPos = START_CONTROLLER_POSE.getPositionF();
            Vector3f deltaPos = controllerPose.getPositionF().sub(startPos);

            Matrix4f deltaMatrix = controllerPose.getMatrix().mul(START_CONTROLLER_POSE.getMatrix().invert());
            Vector3f offset = START_CAMPOS.sub(startPos, new Vector3f());
            Vector3f offsetRotated = deltaMatrix.transformPosition(offset, new Vector3f());

            dataHolder.vrSettings.vrFixedCampos.set(START_CAMPOS).add(deltaPos).add(offsetRotated).sub(offset);

            dataHolder.vrSettings.vrFixedCamrotQuat = deltaMatrix.getNormalizedRotation(new Quaternionf())
                .mul(START_CAMROT_QUAT, dataHolder.vrSettings.vrFixedCamrotQuat);
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
        START_CAMPOS = new Vector3f(dataHolder.vrSettings.vrFixedCampos);
        START_CAMROT_QUAT = new Quaternionf(dataHolder.vrSettings.vrFixedCamrotQuat);
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
            Quaternionf quaternion = vrSettings.externalCameraAngleOrder
                .getRotation((float) Math.toRadians(rx), (float) Math.toRadians(ry), (float) Math.toRadians(rz))
                .conjugate();

            vrSettings.mrMovingCamOffset.set(x, y, z);
            vrSettings.mrMovingCamOffsetRotQuat.set(quaternion);

            vrSettings.vrFixedCampos.set(x, y, z);
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
