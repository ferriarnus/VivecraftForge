package org.vivecraft.client_vr.gameplay.screenhandlers;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.gui.GuiRadial;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.common.utils.math.Matrix4f;

public class RadialHandler {
    public static final ClientDataHolderVR DH = ClientDataHolderVR.getInstance();

    public static GuiRadial UI = new GuiRadial();
    public static RenderTarget FRAMEBUFFER = null;

    public static Vec3 POS_ROOM = new Vec3(0.0D, 0.0D, 0.0D);
    public static Matrix4f ROTATION_ROOM = new Matrix4f();

    private static boolean SHOWING = false;

    private static boolean POINTED_L;
    private static boolean POINTED_R;

    private static ControllerType ACTIVE_CONTROLLER;
    private static boolean LAST_PRESSED_CLICK_L;
    private static boolean LAST_PRESSED_CLICK_R;
    private static boolean LAST_PRESSED_SHIFT_L;
    private static boolean LAST_PRESSED_SHIFT_R;

    public static boolean setOverlayShowing(boolean showingState, ControllerType controller) {
        if (ClientDataHolderVR.KIOSK) {
            return false;
        } else {
            if (DH.vrSettings.seated) {
                showingState = false;
            }

            if (showingState) {
                UI.init(Minecraft.getInstance(), GuiHandler.SCALED_WIDTH_MAX, GuiHandler.SCALED_HEIGHT_MAX);
                SHOWING = true;
                ACTIVE_CONTROLLER = controller;
                orientOverlay(ACTIVE_CONTROLLER);
            } else {
                SHOWING = false;
                ACTIVE_CONTROLLER = null;
            }

            return isShowing();
        }
    }

    public static void processGui() {
        POINTED_L = false;
        POINTED_R = false;

        if (!SHOWING) return;
        if (DH.vrSettings.seated) return;
        if (ROTATION_ROOM == null) return;

        // process cursors
        POINTED_R = UI.processCursor(POS_ROOM, ROTATION_ROOM, false);
        POINTED_L = UI.processCursor(POS_ROOM, ROTATION_ROOM, true);
    }

    public static void orientOverlay(ControllerType controller) {
        if (!isShowing()) return;

        VRData.VRDevicePose pose = DH.vrPlayer.vrdata_room_pre.hmd; //normal menu.
        float distance = 2.0F;
        int id = 0;

        if (controller == ControllerType.LEFT) {
            id = 1;
        }

        if (DH.vrSettings.radialModeHold) {
            // open with controller centered, consistent motions.
            pose = DH.vrPlayer.vrdata_room_pre.getController(id);
            distance = 1.2F;
        }

        Vec3 position = pose.getPosition();
        Vec3 offset = pose.getDirection().scale(distance * 0.5F);

        POS_ROOM = position.add(offset);

        float pitch = (float) Math.asin(offset.y / offset.length());
        float yaw = (float) (Math.PI + Math.atan2(offset.x, offset.z));

        ROTATION_ROOM = Matrix4f.rotationY(yaw);
        Matrix4f tilt = MathUtils.rotationXMatrix(pitch);
        ROTATION_ROOM = Matrix4f.multiply(ROTATION_ROOM, tilt);
    }

    public static void processBindings() {
        if (!isShowing()) return;

        // TODO: this is the cause for issue https://github.com/Vivecraft/VivecraftMod/issues/240

        if (POINTED_L && GuiHandler.KEY_KEYBOARD_SHIFT.consumeClick(ControllerType.LEFT)) {
            UI.setShift(true);
            LAST_PRESSED_SHIFT_L = true;
        }

        if (!GuiHandler.KEY_KEYBOARD_SHIFT.isDown(ControllerType.LEFT) && LAST_PRESSED_SHIFT_L) {
            UI.setShift(false);
            LAST_PRESSED_SHIFT_L = false;
        }

        if (POINTED_R && GuiHandler.KEY_KEYBOARD_SHIFT.consumeClick(ControllerType.RIGHT)) {
            UI.setShift(true);
            LAST_PRESSED_SHIFT_R = true;
        }

        if (!GuiHandler.KEY_KEYBOARD_SHIFT.isDown(ControllerType.RIGHT) && LAST_PRESSED_SHIFT_R) {
            UI.setShift(false);
            LAST_PRESSED_SHIFT_R = false;
        }

        // scale virtual cursor coords to actual screen coords
        float uiScaleX = (float) UI.width / (float) GuiHandler.GUI_WIDTH;
        float uiScaleY = (float) UI.height / (float) GuiHandler.GUI_HEIGHT;

        int x1 = (int) (Math.min(Math.max((int) UI.cursorX1, 0), GuiHandler.GUI_WIDTH) * uiScaleX);
        int y1 = (int) (Math.min(Math.max((int) UI.cursorY1, 0), GuiHandler.GUI_HEIGHT) * uiScaleY);
        int x2 = (int) (Math.min(Math.max((int) UI.cursorX2, 0), GuiHandler.GUI_WIDTH) * uiScaleX);
        int y2 = (int) (Math.min(Math.max((int) UI.cursorY2, 0), GuiHandler.GUI_HEIGHT) * uiScaleY);

        if (DH.vrSettings.radialModeHold) {
            if (ACTIVE_CONTROLLER == null) {
                return;
            }

            if (!VivecraftVRMod.INSTANCE.keyRadialMenu.isDown()) {
                if (ACTIVE_CONTROLLER == ControllerType.LEFT) {
                    UI.mouseClicked(x1, y1, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                } else {
                    UI.mouseClicked(x2, y2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                }

                setOverlayShowing(false, null);
            }
        } else {
            if (POINTED_L && GuiHandler.KEY_KEYBOARD_CLICK.consumeClick(ControllerType.LEFT)) {
                UI.mouseClicked(x1, y1, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                LAST_PRESSED_CLICK_L = true;
            }

            if (!GuiHandler.KEY_KEYBOARD_CLICK.isDown(ControllerType.LEFT) && LAST_PRESSED_CLICK_L) {
                UI.mouseReleased(x1, y1, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                LAST_PRESSED_CLICK_L = false;
            }

            if (POINTED_R && GuiHandler.KEY_KEYBOARD_CLICK.consumeClick(ControllerType.RIGHT)) {
                UI.mouseClicked(x2, y2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                LAST_PRESSED_CLICK_R = true;
            }

            if (!GuiHandler.KEY_KEYBOARD_CLICK.isDown(ControllerType.RIGHT) && LAST_PRESSED_CLICK_R) {
                UI.mouseReleased(x2, y2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                LAST_PRESSED_CLICK_R = false;
            }
        }
    }

    public static boolean isShowing() {
        return SHOWING;
    }

    public static boolean isUsingController(ControllerType controller) {
        return controller == ControllerType.LEFT ? POINTED_L : POINTED_R;
    }
}
