package org.vivecraft.client_vr.gameplay.screenhandlers;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.phys.*;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.vivecraft.client.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.MethodHolder;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.WindowExtension;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.HandedKeyBinding;
import org.vivecraft.client_vr.provider.InputSimulator;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.openvr_lwjgl.OpenVRUtil;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.helpers.RenderHelper;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.utils.math.Matrix4f;
import org.vivecraft.common.utils.math.Quaternion;
import org.vivecraft.common.utils.math.Vector3;

public class GuiHandler {
    public static final Minecraft MC = Minecraft.getInstance();
    public static final ClientDataHolderVR DH = ClientDataHolderVR.getInstance();

    // For mouse menu emulation
    private static double CONTROLLER_MOUSE_X = -1.0D;
    private static double CONTROLLER_MOUSE_Y = -1.0D;
    private static boolean CONTROLLER_MOUSE_VALID;

    private static boolean LAST_PRESSED_LEFT_CLICK;
    private static boolean LAST_PRESSED_RIGHT_CLICK;
    private static boolean LAST_PRESSED_MIDDLE_CLICK;
    private static boolean LAST_PRESSED_SHIFT;
    private static boolean LAST_PRESSED_CRTL;
    private static boolean LAST_PRESSED_ALT;

    public static final KeyMapping KEY_LEFT_CLICK = new KeyMapping("vivecraft.key.guiLeftClick", -1, "vivecraft.key.category.gui");
    public static final KeyMapping KEY_RIGHT_CLICK = new KeyMapping("vivecraft.key.guiRightClick", -1, "vivecraft.key.category.gui");
    public static final KeyMapping KEY_MIDDLE_CLICK = new KeyMapping("vivecraft.key.guiMiddleClick", -1, "vivecraft.key.category.gui");
    public static final KeyMapping KEY_SHIFT = new KeyMapping("vivecraft.key.guiShift", -1, "vivecraft.key.category.gui");
    public static final KeyMapping KEY_CTRL = new KeyMapping("vivecraft.key.guiCtrl", -1, "vivecraft.key.category.gui");
    public static final KeyMapping KEY_ALT = new KeyMapping("vivecraft.key.guiAlt", -1, "vivecraft.key.category.gui");
    public static final KeyMapping KEY_SCROLL_UP = new KeyMapping("vivecraft.key.guiScrollUp", -1, "vivecraft.key.category.gui");
    public static final KeyMapping KEY_SCROLL_DOWN = new KeyMapping("vivecraft.key.guiScrollDown", -1, "vivecraft.key.category.gui");
    public static final KeyMapping KEY_SCROLL_AXIS = new KeyMapping("vivecraft.key.guiScrollAxis", -1, "vivecraft.key.category.gui");
    public static final HandedKeyBinding KEY_KEYBOARD_CLICK = new HandedKeyBinding("vivecraft.key.keyboardClick", -1, "vivecraft.key.category.keyboard") {
        @Override
        public boolean isPriorityOnController(ControllerType type) {
            if (KeyboardHandler.SHOWING && !GuiHandler.DH.vrSettings.physicalKeyboard) {
                return KeyboardHandler.isUsingController(type);
            } else {
                return RadialHandler.isShowing() && RadialHandler.isUsingController(type);
            }
        }
    };
    public static final HandedKeyBinding KEY_KEYBOARD_SHIFT = new HandedKeyBinding("vivecraft.key.keyboardShift", -1, "vivecraft.key.category.keyboard") {
        @Override
        public boolean isPriorityOnController(ControllerType type) {
            if (KeyboardHandler.SHOWING) {
                return GuiHandler.DH.vrSettings.physicalKeyboard || KeyboardHandler.isUsingController(type);
            } else {
                return RadialHandler.isShowing() && RadialHandler.isUsingController(type);
            }
        }
    };

    public static RenderTarget GUI_FRAMEBUFFER = null;

    // for gui positioning
    public static boolean GUI_APPEAR_OVER_BLOCK_ACTIVE = false;
    public static float GUI_SCALE = 1.0F;
    public static float GUI_SCALE_APPLIED = 1.0F;
    public static Vec3 GUI_POS_ROOM = null;
    public static Matrix4f GUI_ROTATION_ROOM = null;

    // for GUI scale override
    public static int GUI_WIDTH = 1280;
    public static int GUI_HEIGHT = 720;
    public static int GUI_SCALE_FACTOR_MAX;
    public static int GUI_SCALE_FACTOR = calculateScale(0, false, GUI_WIDTH, GUI_HEIGHT);
    public static int SCALED_WIDTH;
    public static int SCALED_HEIGHT;
    public static int SCALED_WIDTH_MAX;
    public static int SCALED_HEIGHT_MAX;
    private static int PREVIOUS_GUI_SCALE = -1;

    /**
     * copy of the vanilla method to calculate gui resolution and max scale
     */
    public static int calculateScale(int scaleIn, boolean forceUnicode, int framebufferWidth, int framebufferHeight) {
        int scale = 1;
        int maxScale = 1;

        while (maxScale < framebufferWidth &&
            maxScale < framebufferHeight &&
            framebufferWidth / (maxScale + 1) >= 320 &&
            framebufferHeight / (maxScale + 1) >= 240) {
            if (scale < scaleIn || scaleIn == 0) {
                scale++;
            }
            maxScale++;
        }

        if (forceUnicode) {
            if (scale % 2 != 0) {
                scale++;
            }
            if (maxScale % 2 != 0) {
                maxScale++;
            }
        }

        GUI_SCALE_FACTOR_MAX = maxScale;

        SCALED_WIDTH = Mth.ceil(framebufferWidth / (float) scale);
        SCALED_WIDTH_MAX = Mth.ceil(framebufferWidth / (float) maxScale);

        SCALED_HEIGHT = Mth.ceil(framebufferHeight / (float) scale);
        SCALED_HEIGHT_MAX = Mth.ceil(framebufferHeight / (float) maxScale);

        return scale;
    }

    /**
     * updates the gui resolution, and scales the cursor position
     * @return if the gui scale/size changed
     */
    public static boolean updateResolution() {
        int oldWidth = GUI_WIDTH;
        int oldHeight = GUI_HEIGHT;
        int oldGuiScale = GUI_SCALE_FACTOR;
        GUI_WIDTH = DH.vrSettings.doubleGUIResolution ? 2560 : 1280;
        GUI_HEIGHT = DH.vrSettings.doubleGUIResolution ? 1440 : 720;

        int newGuiScale = DH.vrSettings.doubleGUIResolution ?
            DH.vrSettings.guiScale : (int) Math.ceil(DH.vrSettings.guiScale * 0.5f);

        if (oldWidth != GUI_WIDTH || PREVIOUS_GUI_SCALE != newGuiScale) {
            // only recalculate when scale or size changed
            GUI_SCALE_FACTOR = calculateScale(newGuiScale, false, GUI_WIDTH, GUI_HEIGHT);
            PREVIOUS_GUI_SCALE = newGuiScale;
        }
        if (oldWidth != GUI_WIDTH) {
            // move cursor to right position
            InputSimulator.setMousePos(
                MC.mouseHandler.xpos() * ((WindowExtension) (Object) MC.getWindow()).vivecraft$getActualScreenWidth() / oldWidth,
                MC.mouseHandler.ypos() * ((WindowExtension) (Object) MC.getWindow()).vivecraft$getActualScreenHeight() / oldHeight);
            CONTROLLER_MOUSE_X *= (double) GUI_WIDTH / oldWidth;
            CONTROLLER_MOUSE_Y *= (double) GUI_HEIGHT / oldHeight;
            return true;
        } else {
            return oldGuiScale != GUI_SCALE_FACTOR;
        }
    }

    /**
     * calculates and sets the cursor position
     */
    public static void processGui() {
        if (GUI_ROTATION_ROOM == null) return;
        if (DH.vrSettings.seated) return;
        if (!MCVR.get().isControllerTracking(0)) return;
        // some mods ungrab the mouse when there is no screen
        if (MC.screen == null && MC.mouseHandler.isMouseGrabbed()) return;

        Vec2 tex = getTexCoordsForCursor(GUI_POS_ROOM, GUI_ROTATION_ROOM, GUI_SCALE, DH.vrPlayer.vrdata_room_pre.getController(0));
        float u = tex.x;
        float v = tex.y;

        if (u < 0 || v < 0 || u > 1 || v > 1) {
            // offscreen
            CONTROLLER_MOUSE_X = -1.0f;
            CONTROLLER_MOUSE_Y = -1.0f;
            CONTROLLER_MOUSE_VALID = false;
        } else if (!CONTROLLER_MOUSE_VALID) {
            CONTROLLER_MOUSE_X = (int) (u * MC.getWindow().getWidth());
            CONTROLLER_MOUSE_Y = (int) (v * MC.getWindow().getHeight());
            CONTROLLER_MOUSE_VALID = true;
        } else {
            // apply some smoothing between mouse positions
            float newX = (int) (u * MC.getWindow().getWidth());
            float newY = (int) (v * MC.getWindow().getHeight());
            CONTROLLER_MOUSE_X = CONTROLLER_MOUSE_X * 0.7f + newX * 0.3f;
            CONTROLLER_MOUSE_Y = CONTROLLER_MOUSE_Y * 0.7f + newY * 0.3f;
            CONTROLLER_MOUSE_VALID = true;
        }

        if (CONTROLLER_MOUSE_VALID) {
            // mouse on screen
            InputSimulator.setMousePos(
                CONTROLLER_MOUSE_X * (((WindowExtension) (Object) MC.getWindow()).vivecraft$getActualScreenWidth() / (double) MC.getWindow().getScreenWidth()),
                CONTROLLER_MOUSE_Y * (((WindowExtension) (Object) MC.getWindow()).vivecraft$getActualScreenHeight() / (double) MC.getWindow().getScreenHeight()));
        }
    }

    /**
     * calculates the relative cursor position on the gui
     * @param guiPos_room position of the gui
     * @param guiRotation_room orientation of the gui
     * @param guiScale size of the gui layer
     * @param controller device pose to get the cursor for
     * @return relative position on the gui, anchored top left.<br>
     *  If offscreen returns Vec2(-1,-1)
     */
    public static Vec2 getTexCoordsForCursor(Vec3 guiPos_room, Matrix4f guiRotation_room, float guiScale, VRData.VRDevicePose controller) {
        Vec3 con = controller.getPosition();
        Vector3 controllerPos = new Vector3(con);
        Vec3 conDir = controller.getDirection();
        Vector3 controllerDir = new Vector3((float) conDir.x, (float) conDir.y, (float) conDir.z);
        Vector3 forward = new Vector3(0.0F, 0.0F, 1.0F);
        Vector3 guiNormal = guiRotation_room.transform(forward);
        Vector3 guiRight = guiRotation_room.transform(new Vector3(1.0F, 0.0F, 0.0F));
        Vector3 guiUp = guiRotation_room.transform(new Vector3(0.0F, 1.0F, 0.0F));
        float guiDotController = guiNormal.dot(controllerDir);

        if (Math.abs(guiDotController) > 1.0E-5F) {
            // pointed normal to the GUI
            float guiWidth = 1.0F;
            float guiHalfWidth = guiWidth * 0.5F;
            float guiHeight = 1.0F;
            float guiHalfHeight = guiHeight * 0.5F;
            Vector3 guiPos = new Vector3();
            guiPos.setX((float) guiPos_room.x);
            guiPos.setY((float) guiPos_room.y);
            guiPos.setZ((float) guiPos_room.z);

            Vector3 guiTopLeft = guiPos.subtract(guiUp.multiply(guiHalfHeight)).subtract(guiRight.multiply(guiHalfWidth));

            float intersectDist = -guiNormal.dot(controllerPos.subtract(guiTopLeft)) / guiDotController;

            if (intersectDist > 0.0F) {
                Vector3 pointOnPlane = controllerPos.add(controllerDir.multiply(intersectDist));

                Vector3 relativePoint = pointOnPlane.subtract(guiTopLeft);
                float u = relativePoint.dot(guiRight.multiply(guiWidth));
                float v = relativePoint.dot(guiUp.multiply(guiWidth));

                float aspect = (float) MC.getWindow().getGuiScaledHeight() / (float) MC.getWindow().getGuiScaledWidth();
                u = (u - 0.5F) / 1.5F / guiScale + 0.5F;
                v = (v - 0.5F) / aspect / 1.5F / guiScale + 0.5F;
                v = 1.0F - v;
                return new Vec2(u, v);
            }
        }

        return new Vec2(-1.0F, -1.0F);
    }

    /**
     * processes key presses for the GUI
     */
    public static void processBindingsGui() {
        // only click mouse keys, when cursor is on screen
        boolean mouseValid = CONTROLLER_MOUSE_X >= 0.0D && CONTROLLER_MOUSE_X < MC.getWindow().getScreenWidth() &&
            CONTROLLER_MOUSE_Y >= 0.0D && CONTROLLER_MOUSE_Y < MC.getWindow().getScreenWidth();

        // LMB
        if (KEY_LEFT_CLICK.consumeClick() && MC.screen != null && mouseValid) {
            InputSimulator.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            LAST_PRESSED_LEFT_CLICK = true;
        }
        if (!KEY_LEFT_CLICK.isDown() && LAST_PRESSED_LEFT_CLICK) {
            InputSimulator.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            LAST_PRESSED_LEFT_CLICK = false;
        }

        // RMB
        if (KEY_RIGHT_CLICK.consumeClick() && MC.screen != null && mouseValid) {
            InputSimulator.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            LAST_PRESSED_RIGHT_CLICK = true;
        }
        if (!KEY_RIGHT_CLICK.isDown() && LAST_PRESSED_RIGHT_CLICK) {
            InputSimulator.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            LAST_PRESSED_RIGHT_CLICK = false;
        }

        // MMB
        if (KEY_MIDDLE_CLICK.consumeClick() && MC.screen != null && mouseValid) {
            InputSimulator.pressMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            LAST_PRESSED_MIDDLE_CLICK = true;
        }
        if (!KEY_MIDDLE_CLICK.isDown() && LAST_PRESSED_MIDDLE_CLICK) {
            InputSimulator.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            LAST_PRESSED_MIDDLE_CLICK = false;
        }

        // Shift
        if (KEY_SHIFT.consumeClick() && MC.screen != null) {
            InputSimulator.pressKey(GLFW.GLFW_KEY_LEFT_SHIFT);
            LAST_PRESSED_SHIFT = true;
        }
        if (!KEY_SHIFT.isDown() && LAST_PRESSED_SHIFT) {
            InputSimulator.releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
            LAST_PRESSED_SHIFT = false;
        }

        // Crtl
        if (KEY_CTRL.consumeClick() && MC.screen != null) {
            InputSimulator.pressKey(GLFW.GLFW_KEY_LEFT_CONTROL);
            LAST_PRESSED_CRTL = true;
        }
        if (!KEY_CTRL.isDown() && LAST_PRESSED_CRTL) {
            InputSimulator.releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL);
            LAST_PRESSED_CRTL = false;
        }

        // Alt
        if (KEY_ALT.consumeClick() && MC.screen != null) {
            InputSimulator.pressKey(GLFW.GLFW_KEY_LEFT_ALT);
            LAST_PRESSED_ALT = true;
        }
        if (!KEY_ALT.isDown() && LAST_PRESSED_ALT) {
            InputSimulator.releaseKey(GLFW.GLFW_KEY_LEFT_ALT);
            LAST_PRESSED_ALT = false;
        }

        // scroll mouse
        if (KEY_SCROLL_UP.consumeClick() && MC.screen != null) {
            InputSimulator.scrollMouse(0.0D, 4.0D);
        }

        if (KEY_SCROLL_DOWN.consumeClick() && MC.screen != null) {
            InputSimulator.scrollMouse(0.0D, -4.0D);
        }
    }

    public static void onScreenChanged(Screen previousGuiScreen, Screen newScreen, boolean unpressKeys) {
        onScreenChanged(previousGuiScreen, newScreen, unpressKeys, false);
    }

    public static void onScreenChanged(Screen previousGuiScreen, Screen newScreen, boolean unpressKeys, boolean infrontOfHand) {
        if (!VRState.VR_RUNNING) {
            return;
        }

        if (unpressKeys) {
            DH.vr.ignorePressesNextFrame = true;
        }

        if (newScreen == null) {
            // just insurance
            GUI_POS_ROOM = null;
            GUI_ROTATION_ROOM = null;
            GUI_SCALE = 1.0F;

            if (KeyboardHandler.KEYBOARD_FOR_GUI && DH.vrSettings.autoCloseKeyboard) {
                KeyboardHandler.setOverlayShowing(false);
            }
        } else {
            RadialHandler.setOverlayShowing(false, null);
        }

        if (MC.level == null || newScreen instanceof WinScreen) {
            DH.vrSettings.worldRotationCached = DH.vrSettings.worldRotation;
            DH.vrSettings.worldRotation = 0.0F;
        } else {
            // these dont update when screen open.
            if (DH.vrSettings.worldRotationCached != 0.0F) {
                DH.vrSettings.worldRotation = DH.vrSettings.worldRotationCached;
                DH.vrSettings.worldRotationCached = 0.0F;
            }
        }

        // check if the new screen is meant to show the MenuRoom, instead of the current screen
        boolean staticScreen = MethodHolder.willBeInMenuRoom(newScreen);
        staticScreen &= !DH.vrSettings.seated && !DH.vrSettings.menuAlwaysFollowFace;

        if (staticScreen) {
            GUI_SCALE = 2.0F;
            Vector2f playArea = MCVR.get().getPlayAreaSize();
            // slight offset to center of the room, to prevent z fighting
            GUI_POS_ROOM = new Vec3(0.02D, 1.3F, -Math.max(playArea != null ? playArea.y / 2.0F : 0.0F, 1.5F));
            GUI_ROTATION_ROOM = new Matrix4f();
            return;
        }
        if ((previousGuiScreen == null && newScreen != null) ||
            newScreen instanceof ChatScreen ||
            newScreen instanceof BookEditScreen ||
            newScreen instanceof AbstractSignEditScreen)
        {
            // check if screen is a container screen
            // and if the pointed at block is the same that was last interacted with
            boolean isBlockScreen = newScreen instanceof AbstractContainerScreen &&
                MC.hitResult != null &&
                MC.hitResult.getType() == HitResult.Type.BLOCK;

            // check if screen is a container screen
            // and if the pointed at entity is the same that was last interacted with
            boolean isEntityScreen = newScreen instanceof AbstractContainerScreen &&
                MC.hitResult instanceof EntityHitResult &&
                ((EntityHitResult) MC.hitResult).getEntity() instanceof ContainerEntity;

            VRData.VRDevicePose facingDevice = infrontOfHand ? DH.vrPlayer.vrdata_room_pre.getController(0) : DH.vrPlayer.vrdata_room_pre.hmd;

            if (GUI_APPEAR_OVER_BLOCK_ACTIVE && (isBlockScreen || isEntityScreen) && DH.vrSettings.guiAppearOverBlock) {
                // appear over block / entity
                Vec3 sourcePos;
                if (isEntityScreen) {
                    EntityHitResult entityHitResult = (EntityHitResult) MC.hitResult;
                    sourcePos = new Vec3(entityHitResult.getEntity().getX(), entityHitResult.getEntity().getY(), entityHitResult.getEntity().getZ());
                } else {
                    BlockHitResult blockHitResult = (BlockHitResult) MC.hitResult;
                    sourcePos = new Vec3(((float) blockHitResult.getBlockPos().getX() + 0.5F), blockHitResult.getBlockPos().getY(), ((float) blockHitResult.getBlockPos().getZ() + 0.5F));
                }

                Vec3 roomPos = VRPlayer.world_to_room_pos(sourcePos, DH.vrPlayer.vrdata_world_pre);
                Vec3 hmdPos = DH.vrPlayer.vrdata_room_pre.hmd.getPosition();
                double distance = roomPos.subtract(hmdPos).length();
                GUI_SCALE = (float) Math.sqrt(distance);
                Vec3 sourcePosWorld = new Vec3(sourcePos.x, sourcePos.y + 1.1D + (double) (0.5F * GUI_SCALE / 2.0F), sourcePos.z);
                GUI_POS_ROOM = VRPlayer.world_to_room_pos(sourcePosWorld, DH.vrPlayer.vrdata_world_pre);
            } else {
                // static screens like menu, inventory, and dead.
                Vec3 offset = new Vec3(0.0D, 0.0D, -2.0D);

                if (newScreen instanceof ChatScreen) {
                    offset = new Vec3(0.0D, 0.5D, -2.0D);
                } else if (newScreen instanceof BookEditScreen || newScreen instanceof AbstractSignEditScreen) {
                    offset = new Vec3(0.0D, 0.25D, -2.0D);
                }

                Vec3 hmdPos = facingDevice.getPosition();
                Vec3 look = facingDevice.getCustomVector(offset);
                GUI_POS_ROOM = new Vec3(
                    look.x / 2.0D + hmdPos.x,
                    look.y / 2.0D + hmdPos.y,
                    look.z / 2.0D + hmdPos.z);

                if (DH.vrSettings.physicalKeyboard && KeyboardHandler.SHOWING && GUI_POS_ROOM.y < hmdPos.y + 0.2D) {
                    GUI_POS_ROOM = new Vec3(GUI_POS_ROOM.x, hmdPos.y + 0.2D, GUI_POS_ROOM.z);
                }
            }

            // orient screen
            Vec3 hmdPos = facingDevice.getPosition();
            Vector3 look = new Vector3();
            look.setX((float) (GUI_POS_ROOM.x - hmdPos.x));
            look.setY((float) (GUI_POS_ROOM.y - hmdPos.y));
            look.setZ((float) (GUI_POS_ROOM.z - hmdPos.z));
            float pitch = (float) Math.asin((look.getY() / look.length()));
            float yaw = (float) (Math.PI + Math.atan2(look.getX(), look.getZ()));
            GUI_ROTATION_ROOM = Matrix4f.rotationY(yaw);
            Matrix4f tilt = MathUtils.rotationXMatrix(pitch);
            GUI_ROTATION_ROOM = Matrix4f.multiply(GUI_ROTATION_ROOM, tilt);
        }

        KeyboardHandler.orientOverlay(newScreen != null);
    }

    public static Vec3 applyGUIModelView(RenderPass currentPass, PoseStack pMatrixStack) {
        MC.getProfiler().push("applyGUIModelView");

        if (MC.screen != null && GUI_POS_ROOM == null) {
            //naughty mods!
            onScreenChanged(null, MC.screen, false);
        } else if (MC.screen == null && !MC.mouseHandler.isMouseGrabbed()) {
            // some mod want's to do a mouse selection overlay
            if (GUI_POS_ROOM == null) {
                onScreenChanged(null, new Screen(Component.empty()) {
                }, false, true);
            }
        } else if (MC.screen == null && GUI_POS_ROOM != null) {
            //even naughtier mods!
            // someone canceled the setScreen, so guiPos didn't get reset
            onScreenChanged(null, null, false);
        }

        Vec3 guipos = GUI_POS_ROOM;
        Matrix4f guirot = GUI_ROTATION_ROOM;
        Vec3 guilocal = new Vec3(0.0D, 0.0D, 0.0D);
        float scale = GUI_SCALE;

        if (guipos == null) {
            guirot = null;
            scale = 1.0F;

            if (MC.level != null && (MC.screen == null || !DH.vrSettings.floatInventory)) {
                // HUD view - attach to head or controller
                int i = 1;

                if (DH.vrSettings.reverseHands) {
                    i = -1;
                }

                if (DH.vrSettings.seated || DH.vrSettings.vrHudLockMode == VRSettings.HUDLock.HEAD) {
                    Matrix4f rot = Matrix4f.rotationY(DH.vrPlayer.vrdata_world_render.rotation_radians);
                    Matrix4f max = Matrix4f.multiply(rot, DH.vr.hmdRotation);

                    Vec3 position = DH.vrPlayer.vrdata_world_render.hmd.getPosition();
                    Vec3 direction = DH.vrPlayer.vrdata_world_render.hmd.getDirection();

                    if (DH.vrSettings.seated && DH.vrSettings.seatedHudAltMode) {
                        direction = DH.vrPlayer.vrdata_world_render.getController(0).getDirection();
                        max = Matrix4f.multiply(rot, DH.vr.getAimRotation(0));
                    }

                    guipos = new Vec3(
                        position.x + direction.x * DH.vrPlayer.vrdata_world_render.worldScale * DH.vrSettings.hudDistance,
                        position.y + direction.y * DH.vrPlayer.vrdata_world_render.worldScale * DH.vrSettings.hudDistance,
                        position.z + direction.z * DH.vrPlayer.vrdata_world_render.worldScale * DH.vrSettings.hudDistance);

                    Quaternion orientationQuat = OpenVRUtil.convertMatrix4ftoRotationQuat(max);
                    guirot = new Matrix4f(orientationQuat);
                    scale = DH.vrSettings.hudScale;
                } else {
                    if (DH.vrSettings.vrHudLockMode == VRSettings.HUDLock.HAND) {
                        // hud on hand
                        Matrix4f out = DH.vr.getAimRotation(1);
                        Matrix4f rot = Matrix4f.rotationY(DH.vrPlayer.vrdata_world_render.rotation_radians);
                        Matrix4f guiRotationPose = Matrix4f.multiply(rot, out);
                        guirot = Matrix4f.multiply(guiRotationPose, MathUtils.rotationXMatrix(((float) Math.PI * -0.2F)));
                        guirot = Matrix4f.multiply(guirot, Matrix4f.rotationY((float) Math.PI * 0.1F * i));
                        scale = 0.58823526F;

                        guilocal = new Vec3(guilocal.x, 0.32D * DH.vrPlayer.vrdata_world_render.worldScale, guilocal.z);

                        guipos = RenderHelper.getControllerRenderPos(1);

                        DH.vr.hudPopup = true;
                    } else if (DH.vrSettings.vrHudLockMode == VRSettings.HUDLock.WRIST) {
                        // hud on wrist
                        Matrix4f out = DH.vr.getAimRotation(1);
                        Matrix4f rot = Matrix4f.rotationY(DH.vrPlayer.vrdata_world_render.rotation_radians);
                        guirot = Matrix4f.multiply(rot, out);

                        guirot = Matrix4f.multiply(guirot, MathUtils.rotationZMatrix((float) Math.PI * 0.5F * i));
                        guirot = Matrix4f.multiply(guirot, Matrix4f.rotationY((float) Math.PI * 0.3F * i));

                        guipos = RenderHelper.getControllerRenderPos(1);
                        DH.vr.hudPopup = true;

                        boolean slim = MC.player.getSkin().model().id().equals("slim");
                        scale = 0.4F;
                        float offset = MC.player.getMainArm().getOpposite() == (DH.vrSettings.reverseHands ? HumanoidArm.LEFT : HumanoidArm.RIGHT) ? -0.166F : -0.136F;
                        guilocal = new Vec3(
                            i * offset * DH.vrPlayer.vrdata_world_render.worldScale,
                            (slim ? 0.13D : 0.12D) * DH.vrPlayer.vrdata_world_render.worldScale,
                            0.06D * DH.vrPlayer.vrdata_world_render.worldScale);
                        guirot = Matrix4f.multiply(guirot, Matrix4f.rotationY((float) Math.PI * 0.2F * i));
                    }
                }
            }
        } else {
            // convert previously calculated coords to world coords
            guipos = VRPlayer.room_to_world_pos(guipos, DH.vrPlayer.vrdata_world_render);
            Matrix4f rot = Matrix4f.rotationY(DH.vrPlayer.vrdata_world_render.rotation_radians);
            guirot = Matrix4f.multiply(rot, guirot);
        }

        if ((DH.vrSettings.seated || DH.vrSettings.menuAlwaysFollowFace) && MethodHolder.isInMenuRoom()) {
            // main menu slow yaw tracking thing
            scale = 2.0F;
            Vec3 posAvg = new Vec3(0.0D, 0.0D, 0.0D);

            for (Vec3 sample : DH.vr.hmdPosSamples) {
                posAvg = posAvg.add(sample);
            }

            posAvg = new Vec3(
                posAvg.x / DH.vr.hmdPosSamples.size(),
                posAvg.y / DH.vr.hmdPosSamples.size(),
                posAvg.z / DH.vr.hmdPosSamples.size());

            float yawAvg = 0.0F;

            for (float sample : DH.vr.hmdYawSamples) {
                yawAvg += sample;
            }

            yawAvg /= DH.vr.hmdYawSamples.size();
            yawAvg = (float) Math.toRadians(yawAvg);

            Vec3 dir = new Vec3(-Math.sin(yawAvg), 0.0D, Math.cos(yawAvg));
            float dist = MethodHolder.isInMenuRoom() ?
                2.5F * DH.vrPlayer.vrdata_world_render.worldScale : DH.vrSettings.hudDistance;

            Vec3 pos = posAvg.add(new Vec3(dir.x * dist, dir.y * dist, dir.z * dist));

            Matrix4f guiRotation = Matrix4f.rotationY((float) Math.PI - yawAvg);
            guirot = Matrix4f.multiply(guiRotation, Matrix4f.rotationY(DH.vrPlayer.vrdata_world_render.rotation_radians));
            guipos = VRPlayer.room_to_world_pos(pos, DH.vrPlayer.vrdata_world_render);

            // for mouse control
            GUI_ROTATION_ROOM = guiRotation;
            GUI_SCALE = 2.0F;
            GUI_POS_ROOM = pos;
        }

        if (guipos == null) {
            VRSettings.LOGGER.error("Vivecraft: guipos was null, how did that happen. vrRunning: {}: ", VRState.VR_RUNNING, new RuntimeException());
            GUI_POS_ROOM = new Vec3(0, 0, 0);
            guipos = VRPlayer.room_to_world_pos(GUI_POS_ROOM, DH.vrPlayer.vrdata_world_render);
            GUI_ROTATION_ROOM = new Matrix4f();
            guirot = new Matrix4f();
            GUI_SCALE = 1.0F;
        }

        Vec3 eye = RenderHelper.getSmoothCameraPosition(currentPass, DH.vrPlayer.vrdata_world_render);

        Vec3 translation = guipos.subtract(eye);
        pMatrixStack.translate(translation.x, translation.y, translation.z);

        // offset from eye to gui pos
        pMatrixStack.mulPoseMatrix(guirot.toMCMatrix());
        pMatrixStack.translate(guilocal.x, guilocal.y, guilocal.z);

        float thescale = scale * DH.vrPlayer.vrdata_world_render.worldScale;
        pMatrixStack.scale(thescale, thescale, thescale);

        GUI_SCALE_APPLIED = thescale;

        MC.getProfiler().pop();

        return guipos;
    }
}
