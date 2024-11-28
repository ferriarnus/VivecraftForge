package org.vivecraft.client_vr.gameplay.screenhandlers;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.vivecraft.client.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gui.GuiKeyboard;
import org.vivecraft.client_vr.gui.PhysicalKeyboard;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.common.utils.lwjgl.Matrix4f;
import org.vivecraft.common.utils.lwjgl.Vector3f;

public class KeyboardHandler {
    public static final Minecraft MC = Minecraft.getInstance();
    public static final ClientDataHolderVR DH = ClientDataHolderVR.getInstance();

    public static boolean KEYBOARD_FOR_GUI;
    public static GuiKeyboard UI = new GuiKeyboard();
    public static RenderTarget FRAMEBUFFER = null;
    public static PhysicalKeyboard PHYSICAL_KEYBOARD = new PhysicalKeyboard();

    public static Vec3 POS_ROOM = new Vec3(0.0D, 0.0D, 0.0D);
    public static org.vivecraft.common.utils.math.Matrix4f ROTATION_ROOM = new org.vivecraft.common.utils.math.Matrix4f();

    public static boolean SHOWING = false;

    private static boolean POINTED_L;
    private static boolean POINTED_R;

    private static boolean LAST_PRESSED_CLICK_L;
    private static boolean LAST_PRESSED_CLICK_R;
    private static boolean LAST_PRESSED_SHIFT;

    public static boolean setOverlayShowing(boolean showingState) {
        if (ClientDataHolderVR.KIOSK) return false;
        if (DH.vrSettings.seated) {
            showingState = false;
        }

        if (showingState) {
            if (DH.vrSettings.physicalKeyboard) {
                PHYSICAL_KEYBOARD.show();
            } else {
                UI.init(Minecraft.getInstance(), GuiHandler.SCALED_WIDTH_MAX, GuiHandler.SCALED_HEIGHT_MAX);
            }

            SHOWING = true;
            orientOverlay(MC.screen != null);
            RadialHandler.setOverlayShowing(false, null);

            if (DH.vrSettings.physicalKeyboard && MC.screen != null) {
                GuiHandler.onScreenChanged(MC.screen, MC.screen, false);
            }
        } else {
            SHOWING = false;
            if (DH.vrSettings.physicalKeyboard) {
                PHYSICAL_KEYBOARD.unpressAllKeys();
            }
        }

        return SHOWING;
    }

    public static void processGui() {
        POINTED_L = false;
        POINTED_R = false;

        if (!SHOWING) return;
        if (DH.vrSettings.seated) return;
        if (ROTATION_ROOM == null) return;

        if (DH.vrSettings.physicalKeyboard) {
            PHYSICAL_KEYBOARD.process();
            // Skip the rest of this
            return;
        }

        // process cursors
        POINTED_R = UI.processCursor(POS_ROOM, ROTATION_ROOM, false);
        POINTED_L = UI.processCursor(POS_ROOM, ROTATION_ROOM, true);
    }

    public static void orientOverlay(boolean guiRelative) {
        KEYBOARD_FOR_GUI = false;

        if (!SHOWING) return;

        KEYBOARD_FOR_GUI = guiRelative;

        if (DH.vrSettings.physicalKeyboard) {
            Vec3 pos = DH.vrPlayer.vrdata_room_pre.hmd.getPosition();
            Vec3 offset = new Vec3(0.0D, -0.5D, 0.3D);
            offset = offset.yRot((float) Math.toRadians(-DH.vrPlayer.vrdata_room_pre.hmd.getYaw()));

            POS_ROOM = pos.add(offset);
            float yaw = (float) Math.PI + (float) Math.toRadians(-DH.vrPlayer.vrdata_room_pre.hmd.getYaw());

            ROTATION_ROOM = org.vivecraft.common.utils.math.Matrix4f.rotationY(yaw);
            ROTATION_ROOM = org.vivecraft.common.utils.math.Matrix4f.multiply(
                ROTATION_ROOM, MathUtils.rotationXMatrix((float)Math.PI * 0.8f));
        } else if (guiRelative && GuiHandler.GUI_ROTATION_ROOM != null) {
            // put the keyboard below the current screen
            Matrix4f guiRot = MathUtils.convertOVRMatrix(GuiHandler.GUI_ROTATION_ROOM);
            Vec3 guiUp = new Vec3(guiRot.m10, guiRot.m11, guiRot.m12);
            Vec3 guiFwd = (new Vec3(guiRot.m20, guiRot.m21, guiRot.m22)).scale(0.25D * GuiHandler.GUI_SCALE);
            guiUp = guiUp.scale(0.8F);

            Matrix4f roomRotation = new Matrix4f();
            roomRotation.translate(new Vector3f((float) (GuiHandler.GUI_POS_ROOM.x - guiUp.x), (float) (GuiHandler.GUI_POS_ROOM.y - guiUp.y), (float) (GuiHandler.GUI_POS_ROOM.z - guiUp.z)));
            roomRotation.translate(new Vector3f((float) guiFwd.x, (float) guiFwd.y, (float) guiFwd.z));
            Matrix4f.mul(roomRotation, guiRot, roomRotation);
            roomRotation.rotate((float) Math.toRadians(30.0D), new Vector3f(-1.0F, 0.0F, 0.0F));

            ROTATION_ROOM = MathUtils.convertToOVRMatrix(roomRotation);
            POS_ROOM = new Vec3(ROTATION_ROOM.M[0][3], ROTATION_ROOM.M[1][3], ROTATION_ROOM.M[2][3]);
            ROTATION_ROOM.M[0][3] = 0.0F;
            ROTATION_ROOM.M[1][3] = 0.0F;
            ROTATION_ROOM.M[2][3] = 0.0F;
        } else {
            // copy from GuiHandler.onScreenChanged for static screens
            Vec3 offset = new Vec3(0.0D, -0.5D, -2.0D);

            Vec3 hmdPos = DH.vrPlayer.vrdata_room_pre.hmd.getPosition();
            Vec3 look = DH.vrPlayer.vrdata_room_pre.hmd.getCustomVector(offset).scale(0.5F);

            POS_ROOM = look.add(hmdPos);

            // orient screen
            float yaw = (float) (Math.PI + Math.atan2(look.x, look.z));
            ROTATION_ROOM = org.vivecraft.common.utils.math.Matrix4f.rotationY(yaw);
        }
    }

    public static void processBindings() {
        if (!SHOWING) return;

        if (DH.vrSettings.physicalKeyboard) {
            PHYSICAL_KEYBOARD.processBindings();
            return;
        }

        // scale virtual cursor coords to actual screen coords
        float uiScaleX = (float) UI.width / (float) GuiHandler.GUI_WIDTH;
        float uiScaleY = (float) UI.height / (float) GuiHandler.GUI_HEIGHT;

        int x1 = (int) (Math.min(Math.max((int) UI.cursorX1, 0), GuiHandler.GUI_WIDTH) * uiScaleX);
        int y1 = (int) (Math.min(Math.max((int) UI.cursorY1, 0), GuiHandler.GUI_HEIGHT) * uiScaleY);
        int x2 = (int) (Math.min(Math.max((int) UI.cursorX2, 0), GuiHandler.GUI_WIDTH) * uiScaleX);
        int y2 = (int) (Math.min(Math.max((int) UI.cursorY2, 0), GuiHandler.GUI_HEIGHT) * uiScaleY);

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

        if (GuiHandler.KEY_KEYBOARD_SHIFT.consumeClick()) {
            UI.setShift(true);
            LAST_PRESSED_SHIFT = true;
        }

        if (!GuiHandler.KEY_KEYBOARD_SHIFT.isDown() && LAST_PRESSED_SHIFT) {
            UI.setShift(false);
            LAST_PRESSED_SHIFT = false;
        }
    }

    /**
     * checks if the given controller points at the keyboard
     * @param type controller to check
     */
    public static boolean isUsingController(ControllerType type) {
        return type == ControllerType.LEFT ? POINTED_L : POINTED_R;
    }
}
