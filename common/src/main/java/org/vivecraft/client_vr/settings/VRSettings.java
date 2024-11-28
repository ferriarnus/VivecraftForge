/**
 * Copyright 2013 Mark Browning, StellaArtois
 * Licensed under the LGPL 3.0 or later (See LICENSE.md for details)
 */
package org.vivecraft.client_vr.settings;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivecraft.client.Xplat;
import org.vivecraft.client.utils.LangHelper;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.gui.PhysicalKeyboard;
import org.vivecraft.common.utils.math.Angle;
import org.vivecraft.common.utils.math.Quaternion;
import org.vivecraft.common.utils.math.Vector3;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class VRSettings {
    public static final int VERSION = 2;
    public static final Logger LOGGER = LoggerFactory.getLogger("Vivecraft");
    public static VRSettings INSTANCE;

    public static final int UNKNOWN_VERSION = 0;
    public static final String DEGREE = "\u00b0";

    public enum InertiaFactor implements OptionEnum<InertiaFactor> {
        NONE(1f / 0.01f),
        NORMAL(1f),
        LARGE(1f / 4f),
        MASSIVE(1f / 16f);

        private final float factor;

        InertiaFactor(float factor) {
            this.factor = factor;
        }

        public float getFactor() {
            return this.factor;
        }
    }

    public enum BowMode implements OptionEnum<BowMode> {
        OFF,
        VANILLA,
        ON
    }

    public enum RenderPointerElement implements OptionEnum<RenderPointerElement> {
        ALWAYS,
        WITH_HUD,
        NEVER
    }

    public enum ChatNotifications implements OptionEnum<ChatNotifications> {
        NONE,
        HAPTIC,
        SOUND,
        BOTH
    }

    public enum MirrorMode implements OptionEnum<MirrorMode> {
        OFF,
        CROPPED,
        SINGLE,
        DUAL,
        FIRST_PERSON,
        THIRD_PERSON,
        MIXED_REALITY,
        GUI
    }

    public enum HUDLock implements OptionEnum<HUDLock> {
        WRIST,
        HAND,
        HEAD
    }

    public enum FreeMove implements OptionEnum<FreeMove> {
        CONTROLLER,
        HMD,
        RUN_IN_PLACE,
        ROOM,
        AUTO // only for flying
    }

    public enum RealisticJump implements OptionEnum<RealisticJump> {
        OFF,
        ON,
        AUTO
    }

    public enum MenuWorld implements OptionEnum<MenuWorld> {
        BOTH,
        CUSTOM,
        OFFICIAL,
        NONE
    }

    public enum WeaponCollision implements OptionEnum<WeaponCollision> {
        OFF,
        ON,
        AUTO
    }

    public enum RightClickDelay implements OptionEnum<RightClickDelay> {
        VANILLA,
        SLOW,
        SLOWER,
        SLOWEST
    }

    public enum ShaderGUIRender implements OptionEnum<ShaderGUIRender> {
        BEFORE_TRANSLUCENT_SOLID,
        AFTER_TRANSLUCENT,
        AFTER_SHADER
    }

    public enum VRProvider implements OptionEnum<VRProvider> {
        OPENVR,
        NULLVR
    }

    public enum ChatServerPluginMessage implements OptionEnum<ChatServerPluginMessage> {
        ALWAYS,
        SERVER_ONLY,
        NEVER
    }

    public enum UpdateType implements OptionEnum<UpdateType> {
        RELEASE,
        BETA,
        ALPHA
    }

    public enum AutoOpenKeyboard implements OptionEnum<AutoOpenKeyboard> {
        OFF,
        CHAT,
        ON
    }

    @SettingField
    public int version = UNKNOWN_VERSION;

    @SettingField(VrOptions.VR_PLUGIN)
    public VRProvider stereoProviderPluginID = VRProvider.OPENVR;
    public boolean storeDebugAim = false;
    @SettingField
    public int smoothRunTickCount = 20;
    @SettingField
    public boolean smoothTick = false;
    //Jrbudda's Options

    @SettingField(config = "QUICKCOMMAND", separate = true)
    public String[] vrQuickCommands = getQuickCommandsDefaults();
    @SettingField(config = "RADIAL", separate = true)
    public String[] vrRadialItems = getRadialItemsDefault();
    @SettingField(config = "RADIALALT", separate = true)
    public String[] vrRadialItemsAlt = getRadialItemsAltDefault();

    @SettingField(VrOptions.RADIAL_NUMBER)
    public int vrRadialButtons = 8;
    @SettingField(fixedSize = false)
    public int[] keyboardCodes = getKeyboardCodesDefault();

    //Control
    @SettingField(VrOptions.REVERSE_HANDS)
    public boolean reverseHands = false;
    //@SettingField(VrOptions.REVERSE_BOW)
    public boolean reverseShootingEye = false;
    @SettingField(value = VrOptions.WORLD_SCALE)
    public float worldScale = 1.0f;
    @SettingField(value = VrOptions.WORLD_ROTATION)
    public float worldRotation = 0f;
    public float worldRotationCached;
    @SettingField(value = VrOptions.WORLD_ROTATION_INCREMENT, config = "vrWorldRotationIncrement")
    public float worldRotationIncrement = 45f;
    @SettingField(VrOptions.X_SENSITIVITY)
    public float xSensitivity = 1f;
    @SettingField(VrOptions.Y_SENSITIVITY)
    public float ySensitivity = 1f;
    @SettingField(VrOptions.KEYHOLE)
    public float keyholeX = 15;
    @SettingField
    public double headToHmdLength = 0.10f;
    @SettingField
    public float autoCalibration = -1;
    @SettingField
    public float manualCalibration = -1;
    @SettingField
    public boolean alwaysSimulateKeyboard = false;
    @SettingField(VrOptions.BOW_MODE)
    public BowMode bowMode = BowMode.ON;
    @SettingField
    public String keyboardKeys = "`1234567890-=qwertyuiop[]\\asdfghjkl;':\"zxcvbnm,./?<>";
    @SettingField
    public String keyboardKeysShift = "~!@#$%^&*()_+QWERTYUIOP{}|ASDFGHJKL;':\"ZXCVBNM,./?<>";
    @SettingField(VrOptions.HRTF_SELECTION)
    public int hrtfSelection = 0;
    @SettingField(VrOptions.RIGHT_CLICK_DELAY)
    public RightClickDelay rightclickDelay = RightClickDelay.VANILLA;
    @SettingField(VrOptions.INGAME_BINDINGS_IN_GUI)
    public boolean ingameBindingsInGui = false;
    @SettingField(VrOptions.THIRDPERSON_ITEMTRANSFORMS)
    public boolean thirdPersonItems = false;
    @SettingField(VrOptions.THIRDPERSON_ITEMTRANSFORMS_CUSTOM)
    public boolean thirdPersonItemsCustom = true;
    //

    //Locomotion
    @SettingField(VrOptions.INERTIA_FACTOR)
    public InertiaFactor inertiaFactor = InertiaFactor.NORMAL;
    @SettingField(VrOptions.WALK_UP_BLOCKS)
    public boolean walkUpBlocks = true;     // VIVE default to enable climbing
    @SettingField(VrOptions.SIMULATE_FALLING)
    public boolean simulateFalling = true;  // VIVE if HMD is over empty space, fall
    @SettingField(value = VrOptions.WEAPON_COLLISION, config = "weaponCollisionNew")
    public WeaponCollision weaponCollision = WeaponCollision.AUTO;  // VIVE weapon hand collides with blocks/enemies
    @SettingField(VrOptions.SWORD_BLOCK_COLLISION)
    public boolean swordBlockCollision = true;
    @SettingField(VrOptions.MOVEMENT_MULTIPLIER)
    public float movementSpeedMultiplier = 1.0f;   // VIVE - use full speed by default
    @SettingField(VrOptions.FREEMOVE_MODE)
    public FreeMove vrFreeMoveMode = FreeMove.CONTROLLER;
    @SettingField(VrOptions.FREEMOVE_FLY_MODE)
    public FreeMove vrFreeMoveFlyMode = FreeMove.AUTO;
    @SettingField(value = VrOptions.LIMIT_TELEPORT, config = "limitedTeleport")
    public boolean vrLimitedSurvivalTeleport = true;

    @SettingField(value = VrOptions.TELEPORT_UP_LIMIT, config = "teleportLimitUp")
    public int vrTeleportUpLimit = 1;
    @SettingField(value = VrOptions.TELEPORT_DOWN_LIMIT, config = "teleportLimitDown")
    public int vrTeleportDownLimit = 4;
    @SettingField(value = VrOptions.TELEPORT_HORIZ_LIMIT, config = "teleportLimitHoriz")
    public int vrTeleportHorizLimit = 16;

    @SettingField(VrOptions.PLAY_MODE_SEATED)
    public boolean seated = false;
    @SettingField(value = VrOptions.SEATED_HMD, config = "seatedhmd")
    public boolean seatedUseHMD = false;
    @SettingField
    public float jumpThreshold = 0.05f;
    @SettingField
    public float sneakThreshold = 0.4f;
    @SettingField
    public float crawlThreshold = 0.82f;
    @SettingField(VrOptions.REALISTIC_JUMP)
    public RealisticJump realisticJumpEnabled = RealisticJump.AUTO;
    @SettingField(VrOptions.REALISTIC_SNEAK)
    public boolean realisticSneakEnabled = true;
    @SettingField(VrOptions.REALISTIC_CLIMB)
    public boolean realisticClimbEnabled = true;
    @SettingField(VrOptions.REALISTIC_SWIM)
    public boolean realisticSwimEnabled = true;
    @SettingField(VrOptions.REALISTIC_ROW)
    public boolean realisticRowEnabled = true;
    @SettingField(VrOptions.REALISTIC_DISMOUNT)
    public boolean realisticDismountEnabled = true;
    @SettingField(VrOptions.REALISTIC_BLOCK_INTERACT)
    public boolean realisticBlockInteractEnabled = true;
    @SettingField(VrOptions.REALISTIC_ENTITY_INTERACT)
    public boolean realisticEntityInteractEnabled = true;
    @SettingField(VrOptions.BACKPACK_SWITCH)
    public boolean backpackSwitching = true;
    @SettingField(VrOptions.PHYSICAL_GUI)
    public boolean physicalGuiEnabled = false;
    @SettingField(VrOptions.WALK_MULTIPLIER)
    public float walkMultiplier = 1;
    @SettingField(VrOptions.ALLOW_CRAWLING)
    public boolean allowCrawling = true;
    @SettingField(value = VrOptions.BCB_ON, config = "bcbOn")
    public boolean vrShowBlueCircleBuddy = true;
    @SettingField(VrOptions.VEHICLE_ROTATION)
    public boolean vehicleRotation = true;
    @SettingField(VrOptions.ANALOG_MOVEMENT)
    public boolean analogMovement = true;
    @SettingField(VrOptions.AUTO_SPRINT)
    public boolean autoSprint = true;
    @SettingField(VrOptions.AUTO_SPRINT_THRESHOLD)
    public float autoSprintThreshold = 0.9f;
    @SettingField
    public Vector3 originOffset = new Vector3(0.0F, 0.0F, 0.0F);
    @SettingField(VrOptions.ALLOW_STANDING_ORIGIN_OFFSET)
    public boolean allowStandingOriginOffset = false;
    @SettingField(VrOptions.SEATED_FREE_MOVE)
    public boolean seatedFreeMove = false;
    @SettingField(VrOptions.FORCE_STANDING_FREE_MOVE)
    public boolean forceStandingFreeMove = false;
    //

    //Rendering
    @SettingField(VrOptions.FSAA)
    public boolean useFsaa = false;   // default to off
    @SettingField(value = VrOptions.LOW_HEALTH_INDICATOR, config = "low_health_indicator")
    public boolean lowHealthIndicator = true;   // default to on
    @SettingField(VrOptions.HIT_INDICATOR)
    public boolean hitIndicator = true;   // default to on
    @SettingField(VrOptions.WATER_EFFECT)
    public boolean waterEffect = true;   // default to on
    @SettingField(VrOptions.PORTAL_EFFECT)
    public boolean portalEffect = true;   // default to on
    @SettingField(VrOptions.FREEZE_EFFECT)
    public boolean freezeEffect = true;   // default to on
    @SettingField(VrOptions.PUMPKIN_EFFECT)
    public boolean pumpkinEffect = true;   // default to on
    @SettingField(value = VrOptions.FOV_REDUCTION, config = "fovReduction")
    public boolean useFOVReduction = false;   // default to off
    @SettingField(VrOptions.FOV_REDUCTION_OFFSET)
    public float fovRedutioncOffset = 0.1f; // nice typo
    @SettingField(VrOptions.FOV_REDUCTION_MIN)
    public float fovReductionMin = 0.25f;
    @SettingField(value = VrOptions.STENCIL_ON, config = "stencilOn")
    public boolean vrUseStencil = true;
    @SettingField
    public boolean insideBlockSolidColor = false; //unused
    @SettingField(VrOptions.RENDER_SCALEFACTOR)
    public float renderScaleFactor = 1.0f;
    @SettingField(VrOptions.MIRROR_DISPLAY)
    public MirrorMode displayMirrorMode = MirrorMode.CROPPED;
    @SettingField(VrOptions.MIRROR_CROP)
    public float mirrorCrop = 0.15F;
    @SettingField(VrOptions.MIRROR_EYE)
    public boolean displayMirrorLeftEye = false;
    @SettingField(VrOptions.MIRROR_CENTER_SMOOTH)
    public float displayMirrorCenterSmooth = 0.0F;
    @SettingField(VrOptions.MIRROR_SCREENSHOT_CAMERA)
    public boolean displayMirrorUseScreenshotCamera = false;
    public boolean shouldRenderSelf = false;
    @SettingField(VrOptions.MENU_WORLD_SELECTION)
    public MenuWorld menuWorldSelection = MenuWorld.BOTH;
    @SettingField(VrOptions.MENU_WORLD_FALLBACK)
    public boolean menuWorldFallbackPanorama = true;
    @SettingField
    public boolean renderDebug = false;

    //

    //Mixed Reality
    @SettingField(VrOptions.MIXED_REALITY_KEY_COLOR)
    public Color mixedRealityKeyColor = new Color(0, 0, 0);
    public float mixedRealityAspectRatio = 16F / 9F;
    @SettingField(VrOptions.MIXED_REALITY_RENDER_HANDS)
    public boolean mixedRealityRenderHands = false;
    @SettingField(VrOptions.MIXED_REALITY_UNITY_LIKE)
    public boolean mixedRealityUnityLike = true;
    @SettingField(VrOptions.MIXED_REALITY_UNDISTORTED)
    public boolean mixedRealityUndistorted = true;
    @SettingField(VrOptions.MIXED_REALITY_ALPHA_MASK)
    public boolean mixedRealityAlphaMask = false;
    @SettingField(VrOptions.MIXED_REALITY_FOV)
    public float mixedRealityFov = 40;
    @SettingField
    public float vrFixedCamposX = -1.0f;
    @SettingField
    public float vrFixedCamposY = 2.4f;
    @SettingField
    public float vrFixedCamposZ = 2.7f;
    @SettingField(config = "vrFixedCamrot", separate = true)
    public Quaternion vrFixedCamrotQuat = new Quaternion(.962f, .125f, .239f, .041f);
    @SettingField
    public float mrMovingCamOffsetX = 0;
    @SettingField
    public float mrMovingCamOffsetY = 0;
    @SettingField
    public float mrMovingCamOffsetZ = 0;
    @SettingField(config = "mrMovingCamOffsetRot", separate = true)
    public Quaternion mrMovingCamOffsetRotQuat = new Quaternion();
    @SettingField
    public Angle.Order externalCameraAngleOrder = Angle.Order.XZY;
    @SettingField(VrOptions.HANDHELD_CAMERA_FOV)
    public float handCameraFov = 70;
    @SettingField(VrOptions.HANDHELD_CAMERA_RENDER_SCALE)
    public float handCameraResScale = 1.0f;
    @SettingField(VrOptions.MIXED_REALITY_RENDER_CAMERA_MODEL)
    public boolean mixedRealityRenderCameraModel = true;
    //

    //HUD/GUI
    @SettingField(VrOptions.TOUCH_HOTBAR)
    public boolean vrTouchHotbar = true;
    @SettingField(value = VrOptions.HUD_SCALE, config = "headHudScale")
    public float hudScale = 1.0f;
    @SettingField(VrOptions.HUD_DISTANCE)
    public float hudDistance = 1.25f;
    @SettingField
    public float hudPitchOffset = -2f;
    @SettingField
    public float hudYawOffset = 0.0f;
    public boolean floatInventory = true; //false not working yet, have to account for rotation and tilt in MCOpenVR>processGui()
    @SettingField(VrOptions.MENU_ALWAYS_FOLLOW_FACE)
    public boolean menuAlwaysFollowFace;
    @SettingField(VrOptions.HUD_LOCK_TO)
    public HUDLock vrHudLockMode = HUDLock.WRIST;
    @SettingField(VrOptions.HUD_OCCLUSION)
    public boolean hudOcclusion = true;
    @SettingField(VrOptions.CROSSHAIR_SCALE)
    public float crosshairScale = 1.0f;
    @SettingField(VrOptions.CROSSHAIR_SCALES_WITH_DISTANCE)
    public boolean crosshairScalesWithDistance = false;
    @SettingField(VrOptions.RENDER_CROSSHAIR_MODE)
    public RenderPointerElement renderInGameCrosshairMode = RenderPointerElement.ALWAYS;
    @SettingField(VrOptions.RENDER_BLOCK_OUTLINE_MODE)
    public RenderPointerElement renderBlockOutlineMode = RenderPointerElement.ALWAYS;
    @SettingField(VrOptions.HUD_OPACITY)
    public float hudOpacity = 1f;
    @SettingField(VrOptions.RENDER_MENU_BACKGROUND)
    public boolean menuBackground = false;
    @SettingField(VrOptions.MENU_CROSSHAIR_SCALE)
    public float menuCrosshairScale = 1f;
    @SettingField(VrOptions.CROSSHAIR_OCCLUSION)
    public boolean useCrosshairOcclusion = true;
    @SettingField(VrOptions.SEATED_HUD_XHAIR)
    public boolean seatedHudAltMode = true;
    @SettingField(VrOptions.AUTO_OPEN_KEYBOARD)
    public AutoOpenKeyboard autoOpenKeyboard = AutoOpenKeyboard.OFF;
    @SettingField(VrOptions.AUTO_CLOSE_KEYBOARD)
    public boolean autoCloseKeyboard = true;
    @SettingField
    public int forceHardwareDetection = 0; // 0 = off, 1 = vive, 2 = oculus
    @SettingField(VrOptions.RADIAL_MODE_HOLD)
    public boolean radialModeHold = true;
    @SettingField(VrOptions.PHYSICAL_KEYBOARD)
    public boolean physicalKeyboard = true;
    @SettingField(VrOptions.PHYSICAL_KEYBOARD_SCALE)
    public float physicalKeyboardScale = 1.0f;
    @SettingField(VrOptions.PHYSICAL_KEYBOARD_THEME)
    public PhysicalKeyboard.KeyboardTheme physicalKeyboardTheme = PhysicalKeyboard.KeyboardTheme.DEFAULT;
    @SettingField(VrOptions.KEYBOARD_PRESS_BINDS)
    public boolean keyboardPressBinds = false;
    @SettingField(VrOptions.ALLOW_ADVANCED_BINDINGS)
    public boolean allowAdvancedBindings = false;
    @SettingField(VrOptions.CHAT_NOTIFICATIONS)
    public ChatNotifications chatNotifications = ChatNotifications.NONE; // 0 = off, 1 = haptic, 2 = sound, 3 = both
    @SettingField(VrOptions.CHAT_NOTIFICATION_SOUND)
    public String chatNotificationSound = "block.note_block.bell";
    @SettingField(VrOptions.GUI_APPEAR_OVER_BLOCK)
    public boolean guiAppearOverBlock = true;
    @SettingField(VrOptions.SHADER_GUI_RENDER)
    public ShaderGUIRender shaderGUIRender = ShaderGUIRender.AFTER_SHADER;
    @SettingField(VrOptions.DOUBLE_GUI_RESOLUTION)
    public boolean doubleGUIResolution = false;
    @SettingField(VrOptions.GUI_SCALE)
    public int guiScale = 0;
    @SettingField(VrOptions.HUD_MAX_GUI_SCALE)
    public boolean hudMaxScale = false;
    @SettingField(VrOptions.GUI_MIPMAPS)
    public boolean guiMipmaps;
    @SettingField(VrOptions.SHOW_UPDATES)
    public boolean alwaysShowUpdates = true;
    @SettingField
    public String lastUpdate = "";
    @SettingField(VrOptions.UPDATE_TYPE)
    public UpdateType updateType = UpdateType.RELEASE;
    @SettingField(VrOptions.SHOW_PLUGIN)
    public ChatServerPluginMessage showServerPluginMessage = ChatServerPluginMessage.SERVER_ONLY;
    @SettingField(VrOptions.SHOW_PLUGIN_MISSING)
    public boolean showServerPluginMissingMessageAlways = true;
    @SettingField(VrOptions.CHAT_MESSAGE_STENCIL)
    public boolean showChatMessageStencil = true;
    @SettingField(value = VrOptions.VR_ENABLED, config = "vrEnabled")
    public boolean vrEnabled = false;
    @SettingField(VrOptions.VR_REMEMBER_ENABLED)
    public boolean rememberVr = true;
    @SettingField(VrOptions.VR_HOTSWITCH)
    public boolean vrHotswitchingEnabled = true;
    @SettingField(VrOptions.VR_TOGGLE_BUTTON_VISIBLE)
    public boolean vrToggleButtonEnabled = true;
    @SettingField(VrOptions.VR_SETTINGS_BUTTON_VISIBLE)
    public boolean vrSettingsButtonEnabled = true;
    @SettingField(VrOptions.VR_SETTINGS_BUTTON_POSITION)
    public boolean vrSettingsButtonPositionLeft = true;
    @SettingField(VrOptions.MODIFY_PAUSE_MENU)
    public boolean modifyPauseMenu = true;
    @SettingField
    public boolean disableGarbageCollectorMessage = false;
    @SettingField
    public boolean selfButtSparklesInFirstPerson = false;

    /**
     * This isn't actually used, it's only a dummy field to save the value from vanilla Options.
     */
    @SettingField(VrOptions.HUD_HIDE)
    @Deprecated
    public boolean hideGUI;
    /**
     * This isn't actually used, it's only a dummy field to set the value in vanilla Options.
     */
    @SettingField(VrOptions.MONO_FOV)
    @Deprecated
    public float monoFOV;
    //

    public ServerOverrides overrides = new ServerOverrides();

    private final Map<VrOptions, ConfigEntry> fieldEnumMap = new EnumMap<>(VrOptions.class);
    private final Map<String, ConfigEntry> fieldConfigMap = new HashMap<>();

    // This map is only here to preserve old settings, not intended for general use
    private Map<String, String> preservedSettingMap;

    private final File vrCfgFile;

    // holds the default settings during runtime
    private final Map<String, String> defaultsMap = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public VRSettings() {
        this.vrCfgFile = Xplat.getConfigPath("vivecraft-client-config.json").toFile();

        // Need to do this in the instance because array sizes aren't known until instantiation
        initializeFieldInfo();

        // Assumes GameSettings (and hence optifine's settings) have been read first
        INSTANCE = this;

        // Store our class defaults to a member variable for later use
        storeDefaults();

        // Load settings from the file
        this.loadOptions();

        // load external camera config
        VRHotkeys.loadExternalCameraConfig(this);
    }

    /**
     * checks which settings are defined with the {@link SettingField} annotation
     */
    private void initializeFieldInfo() {
        try {
            for (Field field : VRSettings.class.getFields()) {
                SettingField ann = field.getAnnotation(SettingField.class);
                if (ann != null) {

                    String config = ann.config().isEmpty() ? field.getName() : ann.config();

                    ConfigEntry configEntry = new ConfigEntry(field, ann.value(), config, ann.separate(),
                        ann.fixedSize());
                    if (ann.value() != VrOptions.DUMMY) {
                        if (this.fieldEnumMap.containsKey(ann.value())) {
                            throw new RuntimeException("duplicate enum in setting field: " + field.getName());
                        }
                        this.fieldEnumMap.put(ann.value(), configEntry);
                    }

                    if (ann.separate() && field.getType().isArray()) {
                        int len = Array.getLength(field.get(this));
                        IntStream.range(0, len).forEach(i -> this.fieldConfigMap.put(config + "_" + i, configEntry));
                    } else if (ann.separate() && Quaternion.class.isAssignableFrom(field.getType())) {
                        Stream.of('W', 'X', 'Y', 'Z')
                            .forEach(suffix -> this.fieldConfigMap.put(config + suffix, configEntry));
                    } else if (ann.separate() && Vector3.class.isAssignableFrom(field.getType())) {
                        Stream.of('X', 'Y', 'Z').forEach(suffix -> this.fieldConfigMap.put(config + suffix, configEntry));
                    } else {
                        this.fieldConfigMap.put(config, configEntry);
                    }
                }
            }
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * parses {@code value} into an Object of type {@code type}
     * @param name name of the stored setting, used to correctly parse multi component settings
     * @param value value as a String to parse
     * @param currentValue for multi component settings this holds the object to update
     * @param option option for custom handling
     * @param type target type of the setting
     * @param separate if multi component types are stored in individual strings, or a comma separated string
     * @return parsed setting
     * @throws ReflectiveOperationException if an error happens parsing the value of an Enum type option
     */
    private Object loadOption(
        String name, String value, Object currentValue, VrOptions option, Class<?> type,
        boolean separate) throws ReflectiveOperationException
    {
        // First try to convert the option from a legacy value
        Object obj = option.convertOption(value);
        // If that got nothing, try the custom handler
        if (obj == null) {
            obj = option.loadOption(value);
        }
        if (obj != null) {
            return obj;
        }

        // Generic handlers
        if (type == String.class) {
            return value;
        } else if (type == Boolean.TYPE) {
            return value.equals("true");
        } else if (type == Integer.TYPE) {
            return Integer.parseInt(value);
        } else if (type == Long.TYPE) {
            return Long.parseLong(value);
        } else if (type == Float.TYPE) {
            return Float.parseFloat(value);
        } else if (type == Double.TYPE) {
            return Double.parseDouble(value);
        } else if (type.isEnum()) {
            Method m = type.getMethod("valueOf", String.class);
            return m.invoke(null, value);
        } else if (Quaternion.class.isAssignableFrom(type)) {
            Quaternion quat = ((Quaternion) currentValue).copy();
            if (separate) {
                float f = Float.parseFloat(value);
                switch (name.charAt(name.length() - 1)) {
                    case 'W' -> quat.w = f;
                    case 'X' -> quat.x = f;
                    case 'Y' -> quat.y = f;
                    case 'Z' -> quat.z = f;
                }
            } else {
                String[] split = value.split(",");
                quat.w = Float.parseFloat(split[0]);
                quat.x = Float.parseFloat(split[1]);
                quat.y = Float.parseFloat(split[2]);
                quat.z = Float.parseFloat(split[3]);
            }
            return quat;
        } else if (Vector3.class.isAssignableFrom(type)) {
            Vector3 vec = ((Vector3) currentValue).copy();
            if (separate) {
                float f = Float.parseFloat(value);
                switch (name.charAt(name.length() - 1)) {
                    case 'X' -> vec.x = f;
                    case 'Y' -> vec.y = f;
                    case 'Z' -> vec.z = f;
                }
            } else {
                String[] split = value.split(",");
                vec.x = Float.parseFloat(split[0]);
                vec.y = Float.parseFloat(split[1]);
                vec.z = Float.parseFloat(split[2]);
            }
            return vec;
        }

        // If we get here, the value wasn't interpreted
        LOGGER.warn("Vivecraft: Don't know how to load VR option {} with type {}", name, type.getSimpleName());
        return null;
    }

    /**
     * saves the value of {@code obj} to a string
     * @param name name of the setting to store, is used to identify the component when {@code separate}is true
     * @param obj settings value to store
     * @param option option to check for a custom storage string
     * @param type type of {@code obj}
     * @param separate if multi component settings should be separated by component or one comma separated string
     * @return String to save the value as
     */
    private String saveOption(String name, Object obj, VrOptions option, Class<?> type, boolean separate) {
        // Try the custom handler first
        String value = option.saveOption(obj);
        if (value != null) {
            return value;
        }

        // Generic handlers
        if (type == String.class) {
            return (String) obj;
        } else if (type == Boolean.TYPE || type == Integer.TYPE || type == Long.TYPE || type == Float.TYPE ||
            type == Double.TYPE)
        {
            return obj.toString();
        } else if (type.isEnum()) {
            return ((Enum<?>) obj).name();
        } else if (Quaternion.class.isAssignableFrom(type)) {
            Quaternion quat = (Quaternion) obj;
            if (separate) {
                return Float.toString(switch (name.charAt(name.length() - 1)) {
                    case 'W' -> quat.w;
                    case 'X' -> quat.x;
                    case 'Y' -> quat.y;
                    case 'Z' -> quat.z;
                    default -> 0; // shouldn't happen
                });
            } else {
                return quat.w + "," + quat.x + "," + quat.y + "," + quat.z;
            }
        } else if (Vector3.class.isAssignableFrom(type)) {
            Vector3 vec = (Vector3) obj;
            if (separate) {
                return Float.toString(switch (name.charAt(name.length() - 1)) {
                    case 'X' -> vec.x;
                    case 'Y' -> vec.y;
                    case 'Z' -> vec.z;
                    default -> 0; // shouldn't happen
                });
            } else {
                return vec.x + "," + vec.y + "," + vec.z;
            }
        }

        // If we get here, the object wasn't interpreted
        LOGGER.warn("Vivecraft: Don't know how to save VR option {} with type {}", name, type.getSimpleName());
        return null;
    }

    /**
     * loads the default value of the setting with the given {@code name}
     * @param name name of the setting to load
     * @param value value to load/parse, if null it gets retrieved from the {@code settingsMap}
     * @param option option to check for a custom default value
     * @param type object type of the setting
     * @param separate if multi component types are stored in individual strings, or a comma separated string
     * @param settingsMap map of settings to get the default from
     * @return default value of the setting with the given {@code name}
     * @throws ReflectiveOperationException if an error happens reading the default of an Enum type option
     */
    private Object loadDefault(
        String name, String value, VrOptions option, Class<?> type, boolean separate,
        Map<String, String> settingsMap) throws ReflectiveOperationException
    {
        if (value == null) {
            value = settingsMap.get(name);
        }

        // Try the custom handler first
        Object obj = option.loadOption(value);
        if (obj != null) {
            return obj;
        }

        // Generic handlers
        if (type == String.class) {
            return value;
        } else if (type == Boolean.TYPE) {
            return value.equals("true");
        } else if (type == Integer.TYPE) {
            return Integer.parseInt(value);
        } else if (type == Long.TYPE) {
            return Long.parseLong(value);
        } else if (type == Float.TYPE) {
            return Float.parseFloat(value);
        } else if (type == Double.TYPE) {
            return Double.parseDouble(value);
        } else if (type.isEnum()) {
            Method m = type.getMethod("valueOf", String.class);
            return m.invoke(null, value);
        } else if (Quaternion.class.isAssignableFrom(type)) {
            Quaternion quat = new Quaternion();
            if (separate) {
                Stream.of('W', 'X', 'Y', 'Z').forEach(suffix -> {
                    String str = settingsMap.get(name + suffix);
                    float f = Float.parseFloat(str);
                    switch (suffix) {
                        case 'W' -> quat.w = f;
                        case 'X' -> quat.x = f;
                        case 'Y' -> quat.y = f;
                        case 'Z' -> quat.z = f;
                    }
                });
            } else {
                String[] split = value.split(",");
                quat.w = Float.parseFloat(split[0]);
                quat.x = Float.parseFloat(split[1]);
                quat.y = Float.parseFloat(split[2]);
                quat.z = Float.parseFloat(split[3]);
            }
            return quat;
        } else if (Vector3.class.isAssignableFrom(type)) {
            Vector3 vec = new Vector3();
            if (separate) {
                Stream.of('X', 'Y', 'Z').forEach(suffix -> {
                    String str = settingsMap.get(name + suffix);
                    float f = Float.parseFloat(str);
                    switch (suffix) {
                        case 'X' -> vec.x = f;
                        case 'Y' -> vec.y = f;
                        case 'Z' -> vec.z = f;
                    }
                });
            } else {
                String[] split = value.split(",");
                vec.x = Float.parseFloat(split[0]);
                vec.y = Float.parseFloat(split[1]);
                vec.z = Float.parseFloat(split[2]);
            }
            return vec;
        }

        // If we get here, the value wasn't interpreted
        LOGGER.warn("Vivecraft: Don't know how to load default VR option {} with type {}", name, type.getSimpleName());
        return null;
    }

    /**
     * resets the given setting to its default value
     * @param option setting to reset
     */
    public void loadDefault(VrOptions option) {
        try {
            var mapping = this.fieldEnumMap.get(option);
            if (mapping == null) {
                return;
            }
            Field field = mapping.field;
            Class<?> type = field.getType();
            String name = mapping.configName;

            if (type.isArray()) {
                Object arr = field.get(this);
                int len = Array.getLength(arr);
                if (mapping.separate) {
                    for (int i = 0; i < len; i++) {
                        Object obj = Objects.requireNonNull(
                            loadDefault(name + "_" + i, null, option, type.getComponentType(), false, this.defaultsMap));
                        Array.set(arr, i, obj);
                    }
                } else {
                    String str = this.defaultsMap.get(name);
                    String[] split = str.split(";", -1); // Avoid conflicting with other comma-delimited types
                    for (int i = 0; i < len; i++) {
                        Object obj = Objects.requireNonNull(
                            loadDefault(name, split[i], option, type.getComponentType(), false, this.defaultsMap));
                        Array.set(arr, i, obj);
                    }
                }
            } else {
                Object obj = Objects.requireNonNull(
                    loadDefault(name, null, option, type, mapping.separate, this.defaultsMap));
                field.set(this, obj);
            }
        } catch (Exception exception) {
            LOGGER.warn("Vivecraft: Failed to load default VR option: {}", option, exception);
        }
    }

    /**
     * reads the config json and stores its contents in a map
     * @return map containing stored contents
     */
    private Map<String, String> loadSettingsFile() {
        Map<String, String> data = new HashMap<>();
        try {
            File legacyFile = null;
            boolean fileExists = true;
            if (!this.vrCfgFile.exists()) {
                this.vrCfgFile.createNewFile();
                // check if there is a legacy file
                legacyFile = new File(Minecraft.getInstance().gameDirectory, "optionsviveprofiles.txt");
                if (legacyFile.exists()) {
                    VRSettings.LOGGER.info("Vivecraft: Legacy Vivecraft settings File found, converting.");
                } else {
                    fileExists = false;
                    legacyFile = null;
                }
            }

            JsonObject currentConfig = new JsonObject();

            if (fileExists) {
                InputStreamReader inputstreamreader = new InputStreamReader(
                    new FileInputStream(legacyFile == null ? this.vrCfgFile : legacyFile), StandardCharsets.UTF_8);

                try {
                    currentConfig = JsonParser.parseReader(inputstreamreader).getAsJsonObject();
                } catch (Exception exception) {
                    VRSettings.LOGGER.error("Vivecraft: Error reading settings file:", exception);
                }

                inputstreamreader.close();
            }

            // check for legacy settings
            if (legacyFile != null && currentConfig.has("Profiles")) {
                currentConfig = currentConfig
                    .getAsJsonObject("Profiles")
                    .getAsJsonObject("Default")
                    .getAsJsonObject("Vr");
            }

            for (String key : currentConfig.keySet()) {
                data.put(key, currentConfig.get(key).getAsString());
            }
        } catch (Exception exception) {
            VRSettings.LOGGER.error("Vivecraft: FAILED to read Vivecraft settings:", exception);
        }
        return data;
    }

    /**
     * loads the settings from the config file
     */
    public void loadOptions() {
        Map<String, String> settings = loadSettingsFile();

        for (Map.Entry<String, String> entry : settings.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue() : "";
            try {
                var mapping = this.fieldConfigMap.get(name);

                if (mapping == null) continue;

                Field field = mapping.field;
                Class<?> type = field.getType();
                Object currentValue = field.get(this);

                if (type.isArray()) {
                    if (mapping.separate) {
                        int index = Integer.parseInt(name.substring(name.lastIndexOf('_') + 1));

                        Object obj = Objects.requireNonNull(
                            loadOption(name.substring(0, name.lastIndexOf('_')), value, Array.get(currentValue, index),
                                mapping.vrOption, type.getComponentType(), false));
                        Array.set(currentValue, index, obj);
                    } else {
                        int len = Array.getLength(currentValue);
                        String[] split = value.split(";", -1); // Avoid conflicting with other comma-delimited types
                        if (split.length != len && !mapping.fixedSize) {
                            Object newValue = Array.newInstance(type.getComponentType(), split.length);
                            System.arraycopy(currentValue, 0, newValue, 0, Math.min(len, split.length));
                            field.set(this, newValue);
                            currentValue = newValue;
                            len = split.length;
                        }
                        for (int i = 0; i < len; i++) {
                            Object obj = Objects.requireNonNull(
                                loadOption(name, split[i], Array.get(currentValue, i), mapping.vrOption,
                                    type.getComponentType(), false));
                            Array.set(currentValue, i, obj);
                        }
                    }
                } else {
                    Object obj = Objects.requireNonNull(
                        loadOption(name, value, currentValue, mapping.vrOption, type, mapping.separate));
                    field.set(this, obj);
                }
            } catch (Exception exception) {
                LOGGER.warn("Vivecraft: Skipping bad VR option: {}:{}", name, value, exception);
            }
        }

        this.preservedSettingMap = settings;
    }

    /**
     * saves the current settings to disk
     */
    public synchronized void saveOptions() {
        Map<String, String> data = new HashMap<>();
        saveOptions(data);

        JsonObject jsonStorage = new JsonObject();
        data.forEach((key, value) -> jsonStorage.add(key, new JsonPrimitive(value)));
        try {
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(this.vrCfgFile), StandardCharsets.UTF_8);
            writer.write(GSON.toJson(jsonStorage));
            writer.flush();
            writer.close();
        } catch (IOException ioException) {
            LOGGER.error("Vivecraft: Failed to save VR options to disk:", ioException);
        }
    }

    /**
     * stores the current settings in the {@code defaultsMap}
     */
    private synchronized void storeDefaults() {
        saveOptions(this.defaultsMap);
    }

    /**
     * writes the current settings to the given map
     */
    private void saveOptions(Map<String, String> data) {
        try {

            if (this.preservedSettingMap != null) {
                data.putAll(this.preservedSettingMap);
            }

            for (var entry : this.fieldConfigMap.entrySet()) {
                String name = entry.getKey();
                var mapping = entry.getValue();
                Field field = mapping.field;
                Class<?> type = field.getType();
                Object obj = field.get(this);

                try {
                    if (type.isArray()) {
                        if (mapping.separate) {
                            int index = Integer.parseInt(name.substring(name.lastIndexOf('_') + 1));
                            String value = Objects.requireNonNull(
                                saveOption(name.substring(0, name.lastIndexOf('_')), Array.get(obj, index),
                                    mapping.vrOption, type.getComponentType(), mapping.separate));
                            data.put(name, value);
                        } else {
                            StringJoiner joiner = new StringJoiner(";");
                            int len = Array.getLength(obj);
                            for (int i = 0; i < len; i++) {
                                String value = Objects.requireNonNull(
                                    saveOption(name, Array.get(obj, i), mapping.vrOption, type.getComponentType(),
                                        mapping.separate));
                                joiner.add(value);
                            }
                            data.put(name, joiner.toString());
                        }
                    } else {
                        String value = Objects.requireNonNull(
                            saveOption(name, obj, mapping.vrOption, type, mapping.separate));
                        data.put(name, value);
                    }
                } catch (Exception exception) {
                    LOGGER.error("Vivecraft: Failed to save VR option: {}", name, exception);
                }
            }
        } catch (Exception exception) {
            LOGGER.error("Vivecraft: Failed to save VR options:", exception);
        }
    }

    /**
     * convenience method to get the display string with option name
     */
    public String getButtonDisplayString(VrOptions vrOption) {
        return getButtonDisplayString(vrOption, false);
    }

    /**
     * constructs the display string, of label and value. optionally only the value
     *
     * @param vrOption  option to get the string for
     * @param valueOnly of only the value should be in the string
     * @return string containing name and value of the option
     */
    public String getButtonDisplayString(VrOptions vrOption, boolean valueOnly) {
        String name = I18n.get("vivecraft.options." + vrOption.name());

        String label = valueOnly ? "" : name + ": ";

        try {
            var mapping = this.fieldEnumMap.get(vrOption);
            if (mapping == null) {
                return name;
            }

            Field field = mapping.field;
            Class<?> type = field.getType();

            Object obj = field.get(this);
            if (this.overrides.hasSetting(vrOption)) {
                obj = this.overrides.getSetting(vrOption).getValue();
            }

            String str = vrOption.getDisplayString(label, obj);
            if (str != null) {
                return str;
            } else if (type == Boolean.TYPE) {
                var langKeys = vrOption.getBooleanLangKeys();
                return (boolean) obj ? label + I18n.get(langKeys.getLeft()) : label + I18n.get(langKeys.getRight());
            } else if (type == Float.TYPE || type == Double.TYPE) {
                if (vrOption.getDecimalPlaces() < 0) {
                    return label + Math.round(((Number) obj).floatValue() * 100) + "%";
                } else {
                    return label + String.format("%." + vrOption.getDecimalPlaces() + "f", ((Number) obj).floatValue());
                }
            } else if (OptionEnum.class.isAssignableFrom(type)) {
                return label + I18n.get(((OptionEnum<?>) obj).getLangKey());
            } else {
                return label + obj.toString();
            }
        } catch (Exception exception) {
            LOGGER.error("Vivecraft: Failed to get VR option display string: {}", vrOption, exception);
        }

        return name;
    }

    /**
     * gets the float value of the option, wiht server overrides accounted for
     *
     * @param vrOption option to get
     * @return value of the option, or 0 if unknown
     */
    public float getOptionFloatValue(VrOptions vrOption) {
        try {
            var mapping = this.fieldEnumMap.get(vrOption);
            if (mapping == null) {
                return 0.0F;
            }

            Field field = mapping.field;

            float value = ((Number) field.get(this)).floatValue();
            if (this.overrides.hasSetting(vrOption)) {
                value = this.overrides.getSetting(vrOption).getFloat();
            }

            return Objects.requireNonNullElse(vrOption.getOptionFloatValue(value), value);
        } catch (Exception exception) {
            LOGGER.error("Vivecraft: Failed to get VR option float value: {}", vrOption, exception);
        }

        return 0.0F;
    }

    /**
     * For non-float options. Toggles the option on/off, or cycles through the list i.e. render distances.
     *
     * @param vrOption option to toggle
     */
    public void setOptionValue(VrOptions vrOption) {
        try {
            var mapping = this.fieldEnumMap.get(vrOption);
            if (mapping == null) return;

            Field field = mapping.field;
            Class<?> type = field.getType();

            Object obj = vrOption.setOptionValue(field.get(this));
            if (obj != null) {
                field.set(this, obj);
            } else if (type == Boolean.TYPE) {
                field.set(this, !(boolean) field.get(this));
            } else if (OptionEnum.class.isAssignableFrom(type)) {
                field.set(this, ((OptionEnum<?>) field.get(this)).getNext());
            } else {
                LOGGER.warn("Vivecraft: Don't know how to set VR option {} with type {}", mapping.configName,
                    type.getSimpleName());
                return;
            }

            vrOption.onOptionChange();
            this.saveOptions();
        } catch (Exception exception) {
            LOGGER.error("Vivecraft: Failed to set VR option: {}", vrOption, exception);
        }
    }

    /**
     * For float options, accounts for server override limits when setting the value
     *
     * @param vrOption option to set
     * @param newValue new value that wants to be set
     */
    public void setOptionFloatValue(VrOptions vrOption, float newValue) {
        try {
            var mapping = this.fieldEnumMap.get(vrOption);
            if (mapping == null) return;

            Field field = mapping.field;
            Class<?> type = field.getType();

            float actualValue = Objects.requireNonNullElse(vrOption.setOptionFloatValue(newValue), newValue);
            if (this.overrides.hasSetting(vrOption)) {
                actualValue = Mth.clamp(actualValue, this.overrides.getSetting(vrOption).getValueMin(),
                    this.overrides.getSetting(vrOption).getValueMax());
            }

            if (type == Integer.TYPE) {
                field.set(this, (int) actualValue);
            } else if (type == Long.TYPE) {
                field.set(this, (long) actualValue);
            } else {
                field.set(this, actualValue);
            }

            vrOption.onOptionChange();
            this.saveOptions();
        } catch (Exception exception) {
            LOGGER.error("Vivecraft: Failed to set VR option float value: {}", vrOption, exception);
        }
    }

    record ConfigEntry(Field field, VrOptions vrOption, String configName, boolean separate, boolean fixedSize) {}

    public enum VrOptions {
        DUMMY(false, true), // Dummy
        VR_PLUGIN(false, true), // vr plugin to use
        VR_ENABLED(false, true) { // vr or nonvr

            @Override
            void onOptionChange() {
                VRState.VR_ENABLED = ClientDataHolderVR.getInstance().vrSettings.vrEnabled;
            }
        },
        VR_REMEMBER_ENABLED(false, true), // restore vr state on startup
        HUD_SCALE(true, false, 0.35f, 2.5f, 0.01f, -1), // Head HUD Size
        HUD_DISTANCE(true, false, 0.25f, 5.0f, 0.01f, 2) { // Head HUD Distance

            @Override
            String getDisplayString(String prefix, Object value) {
                return prefix + String.format("%.2f", (float) value) + "m";
            }
        },
        HUD_LOCK_TO(false, true) { // HUD Orientation Lock

            @Override
            Object convertOption(String value) {
                // TODO: remove conversion in the future
                try {
                    int ord = Integer.parseInt(value);
                    return HUDLock.values()[3 - ord];
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        },
        HUD_OPACITY(true, false, 0.15f, 1.0f, 0.05f, -1) { // HUD Opacity

            @Override
            String getDisplayString(String prefix, Object value) {
                if ((float) value > 0.99) {
                    return prefix + I18n.get("vivecraft.options.opaque");
                }
                return null;
            }
        },
        HUD_HIDE(false, true) { // Hide HUD (F1)

            @Override
            Object loadOption(String value) {
                Minecraft.getInstance().options.hideGui = value.equals("true");
                return false;
            }

            @Override
            String saveOption(Object value) {
                return Boolean.toString(Minecraft.getInstance().options.hideGui);
            }

            @Override
            String getDisplayString(String prefix, Object value) {
                return Minecraft.getInstance().options.hideGui ? prefix + LangHelper.getYes() :
                    prefix + LangHelper.getNo();
            }

            @Override
            Object setOptionValue(Object value) {
                Minecraft.getInstance().options.hideGui = !Minecraft.getInstance().options.hideGui;
                return false;
            }
        },
        RENDER_MENU_BACKGROUND(false, true), // HUD/GUI Background
        HUD_OCCLUSION(false, true), // HUD Occlusion
        MENU_ALWAYS_FOLLOW_FACE(false, true, "vivecraft.options.always",
            "vivecraft.options.seated"), // Main Menu Follow
        CROSSHAIR_OCCLUSION(false, true), // Crosshair Occlusion
        CROSSHAIR_SCALE(true, false, 0.25f, 1.0f, 0.01f, -1), // Crosshair Size
        MENU_CROSSHAIR_SCALE(true, false, 0.25f, 2.5f, 0.05f, -1), // Menu Crosshair Size
        RENDER_CROSSHAIR_MODE(false, true) { // Show Crosshair

            @Override
            Object convertOption(String value) {
                // TODO: remove conversion in the future
                try {
                    int ord = Integer.parseInt(value);
                    return RenderPointerElement.values()[ord];
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        },
        CHAT_NOTIFICATIONS(false, true) { // Chat Notifications

            @Override
            Object convertOption(String value) {
                // TODO: remove conversion in the future
                try {
                    int ord = Integer.parseInt(value);
                    return ChatNotifications.values()[ord];
                } catch (NumberFormatException ex) { // new method
                    return null;
                }
            }
        },
        CHAT_NOTIFICATION_SOUND(false, true) { // Notification Sound

            @Override
            String getDisplayString(String prefix, Object value) {
                try {
                    SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation((String) value));
                    return I18n.get(se.getLocation().getPath());
                } catch (Exception e) {
                    return "error";
                }
            }

            @Override
            Object setOptionValue(Object value) {
                SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation((String) value));
                int i = BuiltInRegistries.SOUND_EVENT.getId(se);
                if (++i >= BuiltInRegistries.SOUND_EVENT.keySet().size()) {
                    i = 0;
                }
                return BuiltInRegistries.SOUND_EVENT.byId(i).getLocation().getPath();
            }
        },
        CROSSHAIR_SCALES_WITH_DISTANCE(false, true), // Crosshair Scaling
        RENDER_BLOCK_OUTLINE_MODE(false, true) { // Show Block Outline

            @Override
            Object convertOption(String value) {
                // TODO: remove conversion in the future
                try {
                    int ord = Integer.parseInt(value);
                    return RenderPointerElement.values()[ord];
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        },
        SHOW_UPDATES(false, true, "vivecraft.options.always", "vivecraft.options.once"),
        UPDATE_TYPE(false, true),
        SHOW_PLUGIN(false, true),
        SHOW_PLUGIN_MISSING(false, true, "vivecraft.options.always", "vivecraft.options.once"),
        CHAT_MESSAGE_STENCIL(false, true), // warning for other mod using stencil
        AUTO_OPEN_KEYBOARD(false, true), // Always Open Keyboard
        AUTO_CLOSE_KEYBOARD(false, true), // Close Keyboard on Screenchange
        RADIAL_MODE_HOLD(false, true, "vivecraft.options.hold", "vivecraft.options.press"), // Radial Menu Mode
        PHYSICAL_KEYBOARD(false, true, "vivecraft.options.keyboard.physical",
            "vivecraft.options.keyboard.pointer"), // Keyboard Type
        PHYSICAL_KEYBOARD_SCALE(true, false, 0.75f, 1.5f, 0.01f, -1) { // Keyboard Size

            @Override
            void onOptionChange() {
                KeyboardHandler.PHYSICAL_KEYBOARD.setScale(
                    ClientDataHolderVR.getInstance().vrSettings.physicalKeyboardScale);
            }
        },
        PHYSICAL_KEYBOARD_THEME(false, false), // Keyboard Theme
        KEYBOARD_PRESS_BINDS(false, true), // Keyboard Presses Bindings
        GUI_APPEAR_OVER_BLOCK(false, true), // Appear Over Block
        SHADER_GUI_RENDER(false, false), // Shaders GUI
        DOUBLE_GUI_RESOLUTION(false, true), // 1440p GUI
        GUI_SCALE(true, true, 0, 6, 1, 0) { // GUI Scale

            @Override
            String getDisplayString(String prefix, Object value) {
                if ((int) value == 0) {
                    return prefix + I18n.get("options.guiScale.auto");
                } else {
                    if (ClientDataHolderVR.getInstance().vrSettings.doubleGUIResolution) {
                        return prefix + value;
                    } else {
                        return prefix + (int) Math.ceil((int) value * 0.5f);
                    }
                }
            }

            @Override
            void onOptionChange() {
                if (VRState.VR_INITIALIZED) {
                    ClientDataHolderVR.getInstance().vrRenderer.resizeFrameBuffers("");
                }
            }
        },
        HUD_MAX_GUI_SCALE(false, true), // force HUD to render with max GUI scale
        VR_TOGGLE_BUTTON_VISIBLE(false, true), // toggle in main menu
        VR_SETTINGS_BUTTON_VISIBLE(false, true), // setting button in options
        VR_SETTINGS_BUTTON_POSITION(false, true, "vivecraft.options.left",
            "vivecraft.options.right"), // setting button position
        MODIFY_PAUSE_MENU(false, true), // if the pause menu should be altered
        //HMD/render
        FSAA(false, true), // Lanczos Scaler
        LOW_HEALTH_INDICATOR(false, true), // red low health pulse
        HIT_INDICATOR(false, true), // red flash when hit
        WATER_EFFECT(false, true), // distortion when entering/exiting water
        PORTAL_EFFECT(false, true), // distortion when standing in portal
        FREEZE_EFFECT(false, true), // blue tint when freezing
        PUMPKIN_EFFECT(false, true), // orange fov reduction for carved pumpkin
        MIRROR_DISPLAY(false, true) { // Desktop Mirror

            @Override
            Object convertOption(String value) {
                // TODO: remove conversion in the future
                try {
                    int ord = Integer.parseInt(value);
                    return switch (ord) {
                        case 10 -> MirrorMode.OFF; // MIRROR_OFF
                        case 11 -> MirrorMode.DUAL; // MIRROR_ON_DUAL
                        case 12 -> MirrorMode.SINGLE; // MIRROR_ON_SINGLE
                        case 16 -> MirrorMode.CROPPED; // MIRROR_ON_CROPPED
                        default -> MirrorMode.values()[ord - 9];
                    };
                } catch (NumberFormatException ex) {
                    return null;
                }
            }

            @Override
            void onOptionChange() {
                if (VRState.VR_INITIALIZED) {
                    ClientDataHolderVR.getInstance().vrRenderer.reinitWithoutShaders("Mirror Setting Changed");
                }
            }
        },
        MIRROR_CROP(true, false, 0.0f, 0.25f, 0.01f, -1), // crop amount for mirror,
        MIRROR_EYE(false, true, "vivecraft.options.left", "vivecraft.options.right"), // Mirror Eye
        MIRROR_CENTER_SMOOTH(true, false, 0.0f, 1.0f, 0.1f, 1) {
            @Override
            String getDisplayString(String prefix, Object value) {
                if ((float) value == 0) {
                    return prefix + I18n.get("options.off");
                } else {
                    return prefix + String.format("%.1f", (float) value) + "s";
                }
            }
        },
        MIRROR_SCREENSHOT_CAMERA(false, true),
        MIXED_REALITY_KEY_COLOR(false, false) { // Key Color
            private static final List<Pair<Color, String>> COLORS;
            static {
                COLORS = new ArrayList<>();
                COLORS.add(Pair.of(new Color(0, 0, 0), "vivecraft.options.color.black"));
                COLORS.add(Pair.of(new Color(255, 0, 0), "vivecraft.options.color.red"));
                COLORS.add(Pair.of(new Color(255, 255, 0), "vivecraft.options.color.yellow"));
                COLORS.add(Pair.of(new Color(0, 255, 0), "vivecraft.options.color.green"));
                COLORS.add(Pair.of(new Color(0, 255, 255), "vivecraft.options.color.cyan"));
                COLORS.add(Pair.of(new Color(0, 0, 255), "vivecraft.options.color.blue"));
                COLORS.add(Pair.of(new Color(255, 0, 255), "vivecraft.options.color.magenta"));
            }

            @Override
            String getDisplayString(String prefix, Object value) {
                Color color = (Color) value;
                var p = COLORS.stream().filter(c -> c.getLeft().equals(color)).findFirst().orElse(null);
                return p != null ? prefix + I18n.get(p.getRight()) :
                    prefix + color.getRed() + " " + color.getGreen() + " " + color.getBlue();
            }

            @Override
            Object loadOption(String value) {
                String[] split = value.split(",");
                return new Color(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
            }

            @Override
            String saveOption(Object value) {
                Color color = (Color) value;
                return color.getRed() + "," + color.getGreen() + "," + color.getBlue();
            }

            @Override
            Object setOptionValue(Object value) {
                int index = IntStream.range(0, COLORS.size()).filter(i -> COLORS.get(i).getLeft().equals(value))
                    .findFirst().orElse(-1);
                return index == -1 || index == COLORS.size() - 1 ? COLORS.get(0).getLeft() :
                    COLORS.get(index + 1).getLeft();
            }
        },
        MIXED_REALITY_RENDER_HANDS(false, true), // Show Hands
        MIXED_REALITY_UNITY_LIKE(false, true, "vivecraft.options.unity", "vivecraft.options.sidebyside") { // Layout

            @Override
            void onOptionChange() {
                // reinit, because of maybe new first person pass
                if (VRState.VR_INITIALIZED) {
                    ClientDataHolderVR.getInstance().vrRenderer.reinitWithoutShaders("MR Setting Changed");
                }
            }
        },
        MIXED_REALITY_UNDISTORTED(false, true) { // Undistorted Pass

            @Override
            void onOptionChange() {
                // reinit, because of maybe new first person pass
                if (VRState.VR_INITIALIZED) {
                    ClientDataHolderVR.getInstance().vrRenderer.reinitWithoutShaders("MR Setting Changed");
                }
            }
        },
        MIXED_REALITY_ALPHA_MASK(false, true), // Alpha Mask,
        MIXED_REALITY_FOV(true, false, 0, 179, 1, 0) { // Third Person FOV

            @Override
            String getDisplayString(String prefix, Object value) {
                return prefix + String.format("%.0f" + DEGREE, (float) value);
            }
        },
        WALK_UP_BLOCKS(false, true), // Walk up blocks
        //Movement/aiming controls
        MOVEMENT_MULTIPLIER(true, false, 0.15f, 1.3f, 0.01f, 2), // Move. Speed Multiplier
        INERTIA_FACTOR(false, true) { // Player Inertia

            @Override
            Object convertOption(String value) {
                // TODO: remove conversion in the future
                try {
                    int ord = Integer.parseInt(value);
                    return InertiaFactor.values()[ord];
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        },
        // VIVE START - new options
        SIMULATE_FALLING(false, true), // Simulate falling
        WEAPON_COLLISION(false, true) { // Weapon collision

            @Override
            Object convertOption(String value) {
                // TODO: remove conversion in the future
                try {
                    int ord = Integer.parseInt(value);
                    return WeaponCollision.values()[ord];
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        },
        SWORD_BLOCK_COLLISION(false, true), // lets swords hit blocks that can be mined or instabroken
        // VIVE END - new options
        //JRBUDDA VIVE
        ALLOW_CRAWLING(false, true), // Roomscale Crawling
        LIMIT_TELEPORT(false, true), // Limit in Survival
        REVERSE_HANDS(false, true), // Reverse Hands
        REVERSE_BOW(false, true), // Reverses Roomscale Bow Aiming
        STENCIL_ON(false, true), // Use Eye Stencil
        BCB_ON(false, true), // Show Body Position
        WORLD_SCALE(true, false, 0, 29, 1, 2) { // World Scale

            @Override
            String getDisplayString(String prefix, Object value) {
                return prefix + String.format("%.2f", (float) value) + "x";
            }

            @Override
            Float getOptionFloatValue(float value) {
                if (value == 0.1f) {
                    return 0f;
                }
                if (value == 0.25f) {
                    return 1f;
                }
                if (value >= 0.5f && value <= 2.0f) {
                    return (value / 0.1f) - 3f;
                }
                if (value == 3) {
                    return 18f;
                }
                if (value == 4) {
                    return 19f;
                }
                if (value == 6) {
                    return 20f;
                }
                if (value == 8) {
                    return 21f;
                }
                if (value == 10) {
                    return 22f;
                }
                if (value == 12) {
                    return 23f;
                }
                if (value == 16) {
                    return 24f;
                }
                if (value == 20) {
                    return 25f;
                }
                if (value == 30) {
                    return 26f;
                }
                if (value == 50) {
                    return 27f;
                }
                if (value == 75) {
                    return 28f;
                }
                if (value == 100) {
                    return 29f;
                }
                return 7f;
            }

            @Override
            Float setOptionFloatValue(float value) {
                if (value == 0) {
                    return 0.1f;
                } else if (value == 1) {
                    return 0.25f;
                } else if (value >= 2 && value <= 17) {
                    return value * 0.1f + 0.3f;
                } else if (value == 18) {
                    return 3f;
                } else if (value == 19) {
                    return 4f;
                } else if (value == 20) {
                    return 6f;
                } else if (value == 21) {
                    return 8f;
                } else if (value == 22) {
                    return 10f;
                } else if (value == 23) {
                    return 12f;
                } else if (value == 24) {
                    return 16f;
                } else if (value == 25) {
                    return 20f;
                } else if (value == 26) {
                    return 30f;
                } else if (value == 27) {
                    return 50f;
                } else if (value == 28) {
                    return 75f;
                } else if (value == 29) {
                    return 100f;
                } else {
                    return 1f;
                }
            }

            @Override
            void onOptionChange() {
                if (VRState.VR_RUNNING) {
                    ClientDataHolderVR.getInstance().vrPlayer.roomScaleMovementDelay = 2;
                    ClientDataHolderVR.getInstance().vrPlayer.snapRoomOriginToPlayerEntity(
                        Minecraft.getInstance().player, false, true);
                    VRPlayer.get().preTick();
                }
            }
        },
        WORLD_ROTATION(true, false, 0, 360, 30, 0) { // World Rotation

            @Override
            String getDisplayString(String prefix, Object value) {
                return prefix + String.format("%.0f" + DEGREE, (float) value);
            }

            @Override
            Float setOptionFloatValue(float value) {
                return null;
            }
        },
        WORLD_ROTATION_INCREMENT(true, false, -1, 4, 1, 0) { // Rotation Increment

            @Override
            String getDisplayString(String prefix, Object value) {
                if ((float) value == 0) {
                    return prefix + I18n.get("vivecraft.options.smooth");
                }
                return prefix + String.format("%.0f" + DEGREE, (float) value);
            }

            @Override
            Float getOptionFloatValue(float value) {
                if (value == 0) {
                    return -1f;
                }
                if (value == 10f) {
                    return 0f;
                }
                if (value == 36f) {
                    return 1f;
                }
                if (value == 45f) {
                    return 2f;
                }
                if (value == 90f) {
                    return 3f;
                }
                if (value == 180f) {
                    return 4f;
                }
                return 2f;
            }

            @Override
            Float setOptionFloatValue(float value) {
                if (value == -1f) {
                    return 0f;
                }
                if (value == 0f) {
                    return 10f;
                }
                if (value == 1f) {
                    return 36f;
                }
                if (value == 2f) {
                    return 45f;
                }
                if (value == 3f) {
                    return 90f;
                }
                if (value == 4f) {
                    return 180f;
                }
                return 45f;
            }

            @Override
            void onOptionChange() {
                ClientDataHolderVR.getInstance().vrSettings.worldRotation = 0;
            }
        },
        TOUCH_HOTBAR(false, true), // Touch Hotbar Enabled
        PLAY_MODE_SEATED(false, true, "vivecraft.options.seated", "vivecraft.options.standing"), // Play Mode
        VR_HOTSWITCH(false, true),
        GUI_MIPMAPS(false, true), // gui rendering with mipmaps
        RENDER_SCALEFACTOR(true, false, 0.1f, 9f, 0.1f, 0) { // Resolution

            @Override
            String getDisplayString(String prefix, Object value) {
                if (VRState.VR_ENABLED) {
                    RenderTarget eye0 = ClientDataHolderVR.getInstance().vrRenderer.framebufferEye0;
                    return prefix + Math.round((float) value * 100) + "% (" +
                        (int) Math.ceil(eye0.viewWidth * Math.sqrt((float) value)) + "x" +
                        (int) Math.ceil(eye0.viewHeight * Math.sqrt((float) value)) + ")";
                } else {
                    return prefix + Math.round((float) value * 100) + "%";
                }
            }
        },
        MONO_FOV(true, false, 30, 110, 1, 0) { // Undistorted FOV

            @Override
            String getDisplayString(String prefix, Object value) {
                return prefix + String.format("%.0f" + DEGREE, (float) Minecraft.getInstance().options.fov().get());
            }

            @Override
            Float getOptionFloatValue(float value) {
                return (float) Minecraft.getInstance().options.fov().get();
            }

            @Override
            Float setOptionFloatValue(float value) {
                Minecraft.getInstance().options.fov().set((int) value);
                return 0f;
            }
        },
        HANDHELD_CAMERA_FOV(true, false, 1, 179, 1, 0) { // Camera FOV

            @Override
            String getDisplayString(String prefix, Object value) {
                return prefix + String.format("%.0f" + DEGREE, (float) value);
            }
        },
        HANDHELD_CAMERA_RENDER_SCALE(true, false, 0.5f, 3.0f, 0.25f, 0) { // Camera Resolution

            @Override
            String getDisplayString(String prefix, Object value) {
//                if (Config.isShaders()) { //optifine
//                    RenderTarget camfb = Minecraft.getInstance().vrRenderer.cameraFramebuffer;
//                    return prefix + camfb.viewWidth + "x" + camfb.viewHeight;
//                } else {
                return prefix + Math.round(1920 * (float) value) + "x" + Math.round(1080 * (float) value);
//                }
            }
        },
        MIXED_REALITY_RENDER_CAMERA_MODEL(false, true, LangHelper.YES_KEY, LangHelper.NO_KEY), // Show Camera Model
        //END JRBUDDA
        REALISTIC_JUMP(false, true), // Roomscale Jumping
        REALISTIC_SNEAK(false, true), // Roomscale Sneaking
        PHYSICAL_GUI(false, true) { // Physical GUIs

            @Override
            Object loadOption(String value) {
                // TODO: fix physical GUI... someday
                return false;
            }
        },
        REALISTIC_CLIMB(false, true), // Roomscale Climbing
        REALISTIC_SWIM(false, true), // Roomscale Swimming
        REALISTIC_ROW(false, true), // Roomscale Rowing
        REALISTIC_DISMOUNT(false, true), // Roomscale Dismounting
        REALISTIC_BLOCK_INTERACT(false, true), // Roomscale Block Interaction
        REALISTIC_ENTITY_INTERACT(false, true), // Roomscale Entity Interaction
        WALK_MULTIPLIER(true, false, 1f, 10f, 0.1f, 1), // Walking Multiplier
        FREEMOVE_MODE(false, true) { // Free Move Type

            @Override
            Object convertOption(String value) {
                // TODO: remove conversion in the future
                try {
                    int ord = Integer.parseInt(value);
                    return switch (ord) {
                        case 4 -> FreeMove.CONTROLLER; // legacy FREEMOVE_JOYPAD
                        case 5 -> FreeMove.ROOM; // FREEMOVE_ROOM
                        default -> FreeMove.values()[ord - 1];
                    };
                } catch (NumberFormatException ex) {
                    return null;
                }
            }

            @Override
            Object setOptionValue(Object value) {
                if (value == FreeMove.ROOM) {
                    // skip Auto
                    return FreeMove.CONTROLLER;
                }
                return null;
            }
        },
        FREEMOVE_FLY_MODE(false, true) {
            @Override
            Object setOptionValue(Object value) {
                if (value == FreeMove.CONTROLLER) {
                    return FreeMove.HMD;
                } else if (value == FreeMove.AUTO) {
                    return FreeMove.CONTROLLER;
                } else {
                    return FreeMove.AUTO;
                }
            }
        },
        VEHICLE_ROTATION(false, true), // Vehicle Rotation
        //SEATED
        RESET_ORIGIN(false, true), // Reset Origin
        X_SENSITIVITY(true, false, 0.1f, 5f, 0.01f, 2), // Rotation Speed
        Y_SENSITIVITY(true, false, 0.1f, 5f, 0.01f, 2), // Y Sensitivity
        KEYHOLE(true, false, 0f, 40f, 5f, 0) { // Keyhole

            @Override
            String getDisplayString(String prefix, Object value) {
                return prefix + String.format("%.0f" + DEGREE, (float) value);
            }
        },
        FOV_REDUCTION(false, true), // FOV Comfort Reduction
        FOV_REDUCTION_MIN(true, false, 0.1f, 0.7f, 0.05f, 2), // FOV Reduction Size
        FOV_REDUCTION_OFFSET(true, false, 0.0f, 0.3f, 0.01f, 2), // FOV Reduction Offset
        // OTher buttons
        SEATED_HMD(false, true, "vivecraft.options.hmd", "vivecraft.options.crosshair"), // Forward Direction
        SEATED_HUD_XHAIR(false, true, "vivecraft.options.crosshair", "vivecraft.options.hmd"), // HUD Follows
        BACKPACK_SWITCH(false, true), // Backpack Switching
        ANALOG_MOVEMENT(false, true), // Analog Movement
        AUTO_SPRINT(false, true), // Auto-sprint
        AUTO_SPRINT_THRESHOLD(true, false, 0.5f, 1f, 0.01f, 2), // Auto-sprint Threshold
        THIRDPERSON_ITEMTRANSFORMS(false, true), // 3rd person items
        THIRDPERSON_ITEMTRANSFORMS_CUSTOM(false, true), // 3rd person items, for items with custom model data
        BOW_MODE(false, true) { // Roomscale Bow Mode

            @Override
            Object convertOption(String value) {
                // TODO: remove conversion in the future
                try {
                    int ord = Integer.parseInt(value);
                    return BowMode.values()[ord];
                } catch (NumberFormatException ex) { // new method
                    return null;
                }
            }
        },
        TELEPORT_DOWN_LIMIT(true, false, 0, 16, 1, 0) { // Down Limit

            @Override
            String getDisplayString(String prefix, Object value) {
                return (int) value > 0 ? prefix + LangHelper.get("vivecraft.options.teleportlimit", value) :
                    prefix + "OFF";
            }
        },
        TELEPORT_UP_LIMIT(true, false, 0, 4, 1, 0) { // Up Limit

            @Override
            String getDisplayString(String prefix, Object value) {
                return (int) value > 0 ? prefix + LangHelper.get("vivecraft.options.teleportlimit", value) :
                    prefix + "OFF";
            }
        },
        TELEPORT_HORIZ_LIMIT(true, false, 0, 32, 1, 0) { // Distance Limit

            @Override
            String getDisplayString(String prefix, Object value) {
                return (int) value > 0 ? prefix + LangHelper.get("vivecraft.options.teleportlimit", value) :
                    prefix + "OFF";
            }
        },
        ALLOW_STANDING_ORIGIN_OFFSET(false, true, LangHelper.YES_KEY, LangHelper.NO_KEY), // Allow Origin Offset
        SEATED_FREE_MOVE(false, true, "vivecraft.options.freemove", "vivecraft.options.teleport"), // Movement Type
        FORCE_STANDING_FREE_MOVE(false, true, LangHelper.YES_KEY, LangHelper.NO_KEY), // Force Free Move
        ALLOW_ADVANCED_BINDINGS(false, true, LangHelper.YES_KEY, LangHelper.NO_KEY), // Show Advanced Bindings
        MENU_WORLD_SELECTION(false, false) { // Worlds

            @Override
            Object convertOption(String value) {
                // TODO: remove conversion in the future
                try {
                    int ord = Integer.parseInt(value);
                    return MenuWorld.values()[ord];
                } catch (NumberFormatException ex) { // new method
                    return null;
                }
            }
        },
        MENU_WORLD_FALLBACK(false, true, "vivecraft.options.menuworldfallback.panorama",
            "vivecraft.options.menuworldfallback.dirtbox"), // fallback for when menurwold is not shown
        HRTF_SELECTION(false, false) { // HRTF

            // this is now handled by vanilla
            @Override
            Object setOptionValue(Object value) {
                return value;
            }
        },
        RELOAD_EXTERNAL_CAMERA(false, false) { // Reload External Camera

            @Override
            String getDisplayString(String prefix, Object value) {
                return I18n.get("vivecraft.options." + name());
            }
        },
        INGAME_BINDINGS_IN_GUI(false, true),
        RADIAL_NUMBER(false, false, 4, 14, 2, 0),
        RIGHT_CLICK_DELAY(false, false); // Right Click Repeat

        private final boolean enumFloat;
        private final boolean enumBoolean;
        private final float valueStep;
        private final float valueMin;
        private final float valueMax;
        private final int decimalPlaces;
        private final Pair<String, String> booleanLangKeys;

        /**
         * @param isFloat if true, creates a float setting with range 0 or 1 state
         * @param isBoolean if true creates an ON/OFF setting
         */
        VrOptions(boolean isFloat, boolean isBoolean) {
            this(isFloat, isBoolean, 0.0F, 1.0F, 0.0F, 0);
        }

        /**
         * @param isFloat if true, creates a float setting with range 0 or 1 state
         * @param isBoolean if true creates a boolean setting with the given lang states
         * @param trueLangKey lang key for when the setting is ON
         * @param falseLangKey  lang key for when the setting is OFF
         */
        VrOptions(boolean isFloat, boolean isBoolean, String trueLangKey, String falseLangKey) {
            this(isFloat, isBoolean, 0.0F, 1.0F, 0.0F, 0, trueLangKey, falseLangKey);
        }

        /**
         * @param isFloat if true, creates a float setting with the specified parameters
         * @param isBoolean if true creates an ON/OFF setting
         * @param min minimum value of the float setting
         * @param max maximum value of the float setting
         * @param step step size between individual setting states
         * @param decimalPlaces number of decimal places for float value, negative to display as percentage
         */
        VrOptions(boolean isFloat, boolean isBoolean, float min, float max, float step, int decimalPlaces) {
            this(isFloat, isBoolean, min, max, step, decimalPlaces, LangHelper.ON_KEY, LangHelper.OFF_KEY);
        }

        VrOptions(
            boolean isFloat, boolean isBoolean, float min, float max, float step, int decimalPlaces, String trueLangKey,
            String falseLangKey)
        {
            this.enumFloat = isFloat;
            this.enumBoolean = isBoolean;
            this.valueMin = min;
            this.valueMax = max;
            this.valueStep = step;
            this.decimalPlaces = decimalPlaces;
            this.booleanLangKeys = Pair.of(trueLangKey, falseLangKey);
        }

        Object convertOption(String value) {
            return null;
        }

        Object loadOption(String value) {
            return null;
        }

        String saveOption(Object value) {
            return null;
        }

        String getDisplayString(String prefix, Object value) {
            return null;
        }

        Object setOptionValue(Object value) {
            return null;
        }

        Float getOptionFloatValue(float value) {
            return null;
        }

        Float setOptionFloatValue(float value) {
            return null;
        }

        void onOptionChange() {}

        public boolean getEnumFloat() {
            return this.enumFloat;
        }

        public boolean getEnumBoolean() {
            return this.enumBoolean;
        }

        public int returnEnumOrdinal() {
            return this.ordinal();
        }

        public float getValueMax() {
            return this.valueMax;
        }

        public float getValueMin() {
            return this.valueMin;
        }

        public int getDecimalPlaces() {
            return this.decimalPlaces;
        }

        public Pair<String, String> getBooleanLangKeys() {
            return this.booleanLangKeys;
        }

        protected float snapToStep(float value) {
            if (this.valueStep > 0.0F) {
                value = this.valueStep * (float) Math.round(value / this.valueStep);
            }

            return value;
        }

        public double normalizeValue(float value) {
            return Mth.clamp((this.snapToStep(value) - this.valueMin) / (this.valueMax - this.valueMin), 0.0F, 1.0F);
        }

        public double denormalizeValue(float value) {
            return this.snapToStep(this.valueMin + (this.valueMax - this.valueMin) * Mth.clamp(value, 0.0F, 1.0F));
        }
    }

    public static void initSettings() {
        var vrSettings = new VRSettings();

        vrSettings.saveOptions();

        ClientDataHolderVR.getInstance().vrSettings = vrSettings;
    }

    public String[] getQuickCommandsDefaults() {

        String[] out = new String[12];
        out[0] = "/gamemode survival";
        out[1] = "/gamemode creative";
        out[2] = "/help";
        out[3] = "/home";
        out[4] = "/sethome";
        out[5] = "/spawn";
        out[6] = "hi!";
        out[7] = "bye!";
        out[8] = "follow me!";
        out[9] = "take this!";
        out[10] = "thank you!";
        out[11] = "praise the sun!";

        return out;
    }

    public String[] getRadialItemsDefault() {
        String[] out = new String[14];
        out[0] = "key.drop";
        out[1] = "key.chat";
        out[2] = "vivecraft.key.rotateRight";
        out[3] = "key.pickItem";
        out[4] = "vivecraft.key.toggleHandheldCam";
        out[5] = "vivecraft.key.togglePlayerList";
        out[6] = "vivecraft.key.rotateLeft";
        out[7] = "vivecraft.key.quickTorch";
        for (int i = 8; i < 14; i++) {
            out[i] = "";
        }

        return out;
    }

    public String[] getRadialItemsAltDefault() {
        String[] out = new String[14];
        for (int i = 0; i < 14; i++) {
            out[i] = "";
        }

        return out;
    }

    public int[] getKeyboardCodesDefault() {
        // Some keys in the in-game keyboard don't have assignable key codes
        int[] out = new int[]{
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            GLFW.GLFW_KEY_1,
            GLFW.GLFW_KEY_2,
            GLFW.GLFW_KEY_3,
            GLFW.GLFW_KEY_4,
            GLFW.GLFW_KEY_5,
            GLFW.GLFW_KEY_6,
            GLFW.GLFW_KEY_7,
            GLFW.GLFW_KEY_8,
            GLFW.GLFW_KEY_9,
            GLFW.GLFW_KEY_0,
            GLFW.GLFW_KEY_MINUS,
            GLFW.GLFW_KEY_EQUAL,
            GLFW.GLFW_KEY_Q,
            GLFW.GLFW_KEY_W,
            GLFW.GLFW_KEY_E,
            GLFW.GLFW_KEY_R,
            GLFW.GLFW_KEY_T,
            GLFW.GLFW_KEY_Y,
            GLFW.GLFW_KEY_U,
            GLFW.GLFW_KEY_I,
            GLFW.GLFW_KEY_O,
            GLFW.GLFW_KEY_P,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            GLFW.GLFW_KEY_BACKSLASH,
            GLFW.GLFW_KEY_A,
            GLFW.GLFW_KEY_S,
            GLFW.GLFW_KEY_D,
            GLFW.GLFW_KEY_F,
            GLFW.GLFW_KEY_G,
            GLFW.GLFW_KEY_H,
            GLFW.GLFW_KEY_J,
            GLFW.GLFW_KEY_K,
            GLFW.GLFW_KEY_L,
            GLFW.GLFW_KEY_SEMICOLON,
            GLFW.GLFW_KEY_APOSTROPHE,
            GLFW.GLFW_KEY_UNKNOWN, // colon
            GLFW.GLFW_KEY_UNKNOWN, // quote
            GLFW.GLFW_KEY_Z,
            GLFW.GLFW_KEY_X,
            GLFW.GLFW_KEY_C,
            GLFW.GLFW_KEY_V,
            GLFW.GLFW_KEY_B,
            GLFW.GLFW_KEY_N,
            GLFW.GLFW_KEY_M,
            GLFW.GLFW_KEY_COMMA,
            GLFW.GLFW_KEY_PERIOD,
            GLFW.GLFW_KEY_SLASH,
            GLFW.GLFW_KEY_UNKNOWN, // question mark
            GLFW.GLFW_KEY_UNKNOWN, // less than
            GLFW.GLFW_KEY_UNKNOWN // greater than
        };

        return out;
    }

    public class ServerOverrides {
        private final Map<VrOptions, Setting> optionMap = new EnumMap<>(VrOptions.class);
        private final Map<String, Setting> networkNameMap = new HashMap<>();

        private ServerOverrides() {
            registerSetting(VrOptions.LIMIT_TELEPORT, "limitedTeleport",
                () -> VRSettings.this.vrLimitedSurvivalTeleport);
            registerSetting(VrOptions.TELEPORT_UP_LIMIT, "teleportLimitUp",
                () -> VRSettings.this.vrTeleportUpLimit);
            registerSetting(VrOptions.TELEPORT_DOWN_LIMIT, "teleportLimitDown",
                () -> VRSettings.this.vrTeleportDownLimit);
            registerSetting(VrOptions.TELEPORT_HORIZ_LIMIT, "teleportLimitHoriz",
                () -> VRSettings.this.vrTeleportHorizLimit);
            registerSetting(VrOptions.WORLD_SCALE, "worldScale",
                () -> VRSettings.this.worldScale);
            registerSetting(VrOptions.THIRDPERSON_ITEMTRANSFORMS, "thirdPersonItems",
                () -> VRSettings.this.thirdPersonItems);
            registerSetting(VrOptions.THIRDPERSON_ITEMTRANSFORMS_CUSTOM, "thirdPersonItemsCustom", () -> thirdPersonItemsCustom);
        }

        private void registerSetting(VrOptions option, String networkName, Supplier<Object> originalValue) {
            Setting setting = new Setting(option, networkName, originalValue);
            this.optionMap.put(option, setting);
            this.networkNameMap.put(networkName, setting);
        }

        public void resetAll() {
            for (Setting setting : this.optionMap.values()) {
                setting.valueSet = false;
                setting.valueMinSet = false;
                setting.valueMaxSet = false;
            }
        }

        public boolean hasSetting(VrOptions option) {
            return this.optionMap.containsKey(option);
        }

        public boolean hasSetting(String networkName) {
            return this.networkNameMap.containsKey(networkName);
        }

        public Setting getSetting(VrOptions option) {
            Setting setting = this.optionMap.get(option);
            if (setting == null) {
                throw new IllegalArgumentException("setting not registered: " + option);
            }

            return setting;
        }

        public Setting getSetting(String networkName) {
            Setting setting = this.networkNameMap.get(networkName);
            if (setting == null) {
                throw new IllegalArgumentException("setting not registered: " + networkName);
            }

            return setting;
        }

        public class Setting {
            private final VrOptions option;
            private final String networkName;
            private final Supplier<Object> originalValue;

            private boolean valueSet;
            private Object value;

            // For float options
            private boolean valueMinSet, valueMaxSet;
            private float valueMin, valueMax;

            public Setting(VrOptions option, String networkName, Supplier<Object> originalValue) {
                this.option = option;
                this.networkName = networkName;
                this.originalValue = originalValue;
            }

            private void checkFloat() {
                if (!this.option.enumFloat) {
                    throw new IllegalArgumentException("not a float option: " + this.option);
                }
            }

            public boolean isFloat() {
                return this.option.enumFloat;
            }

            public Object getOriginalValue() {
                return this.originalValue.get();
            }

            public boolean isValueOverridden() {
                return this.valueSet;
            }

            public Object getValue() {
                Object val;
                if (this.valueSet) {
                    val = this.value;
                } else {
                    val = this.originalValue.get();
                }

                if (val instanceof Integer) {
                    val = Mth.clamp(((Number) val).intValue(), (int) getValueMin(), (int) getValueMax());
                } else if (val instanceof Float) {
                    val = Mth.clamp(((Number) val).floatValue(), getValueMin(), getValueMax());
                }

                return val;
            }

            public boolean getBoolean() {
                Object val = getValue();
                return val instanceof Boolean && (boolean) val;
            }

            public int getInt() {
                Object val = getValue();
                return val instanceof Number ? ((Number) val).intValue() : 0;
            }

            public float getFloat() {
                Object val = getValue();
                return val instanceof Number ? ((Number) val).floatValue() : 0;
            }

            public String getString() {
                Object val = getValue();
                return val instanceof String ? val.toString() : "";
            }

            public void setValue(Object value) {
                this.value = value;
                this.valueSet = true;
            }

            public void resetValue() {
                this.valueSet = false;
            }

            public boolean isValueMinOverridden() {
                checkFloat();
                return this.valueMinSet;
            }

            public float getValueMin() {
                checkFloat();
                if (this.valueMinSet) {
                    return this.valueMin;
                } else {
                    return Float.MIN_VALUE;
                }
            }

            public void setValueMin(float valueMin) {
                checkFloat();
                this.valueMin = valueMin;
                this.valueMinSet = true;
            }

            public void resetValueMin() {
                checkFloat();
                this.valueMinSet = false;
            }

            public boolean isValueMaxOverridden() {
                checkFloat();
                return this.valueMaxSet;
            }

            public float getValueMax() {
                checkFloat();
                if (this.valueMaxSet) {
                    return this.valueMax;
                } else {
                    return Float.MAX_VALUE;
                }
            }

            public void setValueMax(float valueMax) {
                checkFloat();
                this.valueMax = valueMax;
                this.valueMaxSet = true;
            }

            public void resetValueMax() {
                checkFloat();
                this.valueMaxSet = false;
            }
        }
    }
}

