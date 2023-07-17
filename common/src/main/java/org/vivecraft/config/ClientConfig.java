package org.vivecraft.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.ConfigSpec;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.vivecraft.client.Xplat;
import org.vivecraft.client_vr.gui.PhysicalKeyboard;
import org.vivecraft.client_vr.settings.VRSettings;

public class ClientConfig {

    public static ConfigBuilder.BooleanValue physicalKeyboard;
    public static ConfigBuilder.BooleanValue autoOpenKeyboard;
    public static ConfigBuilder.BooleanValue alwaysSimulateKeyboard;
    public static ConfigBuilder.StringValue keyboardKeys;
    public static ConfigBuilder.StringValue keyboardKeysShift;
    public static ConfigBuilder.DoubleValue physicalKeyboardScale;
    public static ConfigBuilder.EnumValue<PhysicalKeyboard.KeyboardTheme> physicalKeyboardTheme;


    public static ConfigBuilder.DoubleValue mrMovingCamOffsetZ;
    public static ConfigBuilder.DoubleValue mrMovingCamOffsetY;
    public static ConfigBuilder.DoubleValue mrMovingCamOffsetX;
    public static ConfigBuilder.BooleanValue smoothTick;
    public static ConfigBuilder.DoubleValue sneakThreshold;
    public static ConfigBuilder.DoubleValue crawlThreshold;
    public static ConfigBuilder.EnumValue<VRSettings.WeaponCollision> weaponCollision;
    public static ConfigBuilder.BooleanValue vrTouchHotbar;
    public static ConfigBuilder.StringValue mixedRealityKeyColor;
    public static ConfigBuilder.BooleanValue useCrosshairOcclusion;
    public static ConfigBuilder.EnumValue<VRSettings.MirrorMode> displayMirrorMode;
    public static ConfigBuilder.DoubleValue vrFixedCamrotW;
    public static ConfigBuilder.BooleanValue realisticJumpEnabled;
    public static ConfigBuilder.BooleanValue realisticRowEnabled;
    public static ConfigBuilder.DoubleValue vrFixedCamrotZ;
    public static ConfigBuilder.DoubleValue vrFixedCamrotY;
    public static ConfigBuilder.DoubleValue vrFixedCamrotX;
    public static ConfigBuilder.BooleanValue lowHealthIndicator;
    public static ConfigBuilder.IntValue version;
    //"originOffset": "0.0,0.0,0.0",
    public static ConfigBuilder.BooleanValue mixedRealityAlphaMask;
    public static ConfigBuilder.DoubleValue handCameraResScale;
    public static ConfigBuilder.BooleanValue forceHardwareDetection;
    public static ConfigBuilder.IntValue handCameraFov;
    public static ConfigBuilder.StringValue lastUpdate;
    public static ConfigBuilder.BooleanValue realisticClimbEnabled;
    public static ConfigBuilder.BooleanValue forceStandingFreeMove;
    public static ConfigBuilder.EnumValue<VRSettings.InertiaFactor> inertiaFactor;
    public static ConfigBuilder.BooleanValue reverseHands;
    public static ConfigBuilder.BooleanValue thirdPersonItems;
    public static ConfigBuilder.IntValue worldScale;
    public static ConfigBuilder.DoubleValue mixedRealityFov;
    public static ConfigBuilder.BooleanValue alwaysShowUpdates;
    public static ConfigBuilder.DoubleValue hudDistance;
    public static ConfigBuilder.BooleanValue stencilOn;
    public static ConfigBuilder.DoubleValue hudPitchOffset;
    public static ConfigBuilder.BooleanValue vehicleRotation;
    public static ConfigBuilder.BooleanValue autoSprint;
    public static ConfigBuilder.DoubleValue xSensitivity;
    public static ConfigBuilder.IntValue teleportLimitHoriz;
    public static ConfigBuilder.DoubleValue monoFOV;
    public static ConfigBuilder.IntValue teleportLimitDown;
    public static ConfigBuilder.BooleanValue realisticSwimEnabled;
    public static ConfigBuilder.DoubleValue ySensitivity;
    public static ConfigBuilder.IntValue teleportLimitUp;
    public static ConfigBuilder.StringValue stereoProviderPluginID;
    public static ConfigBuilder.BooleanValue allowAdvancedBindings;
    public static ConfigBuilder.BooleanValue seatedHudAltMode;
    public static ConfigBuilder.BooleanValue insideBlockSolidColor;
    public static ConfigBuilder.BooleanValue radialModeHold;
    public static ConfigBuilder.DoubleValue mrMovingCamOffsetRotW;
    public static ConfigBuilder.DoubleValue mrMovingCamOffsetRotZ;
    public static ConfigBuilder.DoubleValue mrMovingCamOffsetRotY;
    public static ConfigBuilder.DoubleValue mrMovingCamOffsetRotX;
    public static ConfigBuilder.DoubleValue headHudScale;
    public static ConfigBuilder.DoubleValue autoSprintThreshold;
    public static ConfigBuilder.EnumValue<VRSettings.ChatNotifications> chatNotifications;
    public static ConfigBuilder.EnumValue<VRSettings.RenderPointerElement> renderBlockOutlineMode;
    public static ConfigBuilder.BooleanValue firstRun;
    public static ConfigBuilder.BooleanValue showServerPluginMessage;
    public static ConfigBuilder.BooleanValue menuBackground;
    public static ConfigBuilder.StringValue QUICKCOMMAND_0;
    public static ConfigBuilder.StringValue QUICKCOMMAND_1;
    public static ConfigBuilder.StringValue QUICKCOMMAND_2;
    public static ConfigBuilder.StringValue QUICKCOMMAND_3;
    public static ConfigBuilder.StringValue QUICKCOMMAND_4;
    public static ConfigBuilder.StringValue QUICKCOMMAND_5;
    public static ConfigBuilder.StringValue QUICKCOMMAND_6;
    public static ConfigBuilder.StringValue QUICKCOMMAND_7;
    public static ConfigBuilder.StringValue QUICKCOMMAND_8;
    public static ConfigBuilder.StringValue QUICKCOMMAND_9;
    public static ConfigBuilder.StringValue badStereoProviderPluginID;
    public static ConfigBuilder.BooleanValue realisticSneakEnabled;
    public static ConfigBuilder.BooleanValue guiAppearOverBlock;
    public static ConfigBuilder.BooleanValue analogMovement;
    public static ConfigBuilder.BooleanValue bcbOn;
    public static ConfigBuilder.IntValue hrtfSelection;
    public static ConfigBuilder.DoubleValue menuCrosshairScale;
    public static ConfigBuilder.DoubleValue headToHmdLength;
    public static ConfigBuilder.BooleanValue seatedhmd;
    public static ConfigBuilder.StringValue chatNotificationSound;
    public static ConfigBuilder.DoubleValue jumpThreshold;
    public static ConfigBuilder.DoubleValue movementSpeedMultiplier;
    public static ConfigBuilder.BooleanValue seated;
    public static ConfigBuilder.EnumValue<VRSettings.RightClickDelay> rightclickDelay;
    public static ConfigBuilder.BooleanValue limitedTeleport;
    public static ConfigBuilder.DoubleValue manualCalibration;
    public static ConfigBuilder.BooleanValue allowCrawling;
    public static ConfigBuilder.DoubleValue smoothRunTickCount;
    //bowMode
    public static ConfigBuilder.BooleanValue physicalGuiEnabled;
    public static ConfigBuilder.BooleanValue allowStandingOriginOffset;
    public static ConfigBuilder.BooleanValue vrHotswitchingEnabled;
    public static ConfigBuilder.DoubleValue hudYawOffset;
    public static ConfigBuilder.BooleanValue crosshairScalesWithDistance;
    public static ConfigBuilder.DoubleValue fovReductionMin;
    public static ConfigBuilder.DoubleValue autoCalibration;
    public static ConfigBuilder.EnumValue<VRSettings.RenderPointerElement> renderInGameCrosshairMode;
    public static ConfigBuilder.IntValue worldRotation;
    public static ConfigBuilder.StringValue externalCameraAngleOrder;
    public static ConfigBuilder.DoubleValue keyholeX;
    public static ConfigBuilder.BooleanValue mixedRealityRenderCameraModel;
    public static ConfigBuilder.BooleanValue vrFixedCamposZ;
    public static ConfigBuilder.DoubleValue vrFixedCamposX;
    public static ConfigBuilder.BooleanValue vrFixedCamposY;
    public static ConfigBuilder.DoubleValue fovRedutioncOffset;
    public static ConfigBuilder.StringValue RADIAL_1;
    public static ConfigBuilder.StringValue RADIAL_2;
    public static ConfigBuilder.StringValue RADIAL_3;
    public static ConfigBuilder.StringValue RADIAL_4;
    public static ConfigBuilder.StringValue RADIAL_5;
    public static ConfigBuilder.StringValue RADIAL_6;
    public static ConfigBuilder.StringValue RADIAL_7;
    public static ConfigBuilder.StringValue RADIAL_8;
    public static ConfigBuilder.StringValue RADIAL_9;
    public static ConfigBuilder.BooleanValue mixedRealityRenderHands;
    public static ConfigBuilder.DoubleValue hudOpacity;
    public static ConfigBuilder.DoubleValue vrWorldRotationIncrement;
    public static ConfigBuilder.BooleanValue hudOcclusion;
    public static ConfigBuilder.EnumValue<VRSettings.FreeMove> vrFreeMoveFlyMode; //TODO check
    public static ConfigBuilder.BooleanValue mixedRealityUndistorted;
    public static ConfigBuilder.BooleanValue mixedRealityUnityLike;
    public static ConfigBuilder.EnumValue<VRSettings.FreeMove> vrFreeMoveMode;
    public static ConfigBuilder.DoubleValue walkMultiplier;
    public static ConfigBuilder.BooleanValue fovReduction;
    public static ConfigBuilder.BooleanValue menuAlwaysFollowFace;
    public static ConfigBuilder.EnumValue<VRSettings.HUDLock> vrHudLockMode;
    public static ConfigBuilder.EnumValue<VRSettings.MenuWorld> menuWorldSelection;
    public static ConfigBuilder.BooleanValue backpackSwitching;
    public static ConfigBuilder.BooleanValue useFsaa;
    public static ConfigBuilder.DoubleValue renderScaleFactor;
    public static ConfigBuilder.EnumValue<VRSettings.ShaderGUIRender> shaderGUIRender;
    public static ConfigBuilder.BooleanValue disableFun;
    public static ConfigBuilder.BooleanValue displayMirrorLeftEye;
    public static ConfigBuilder.DoubleValue crosshairScale;
    public static ConfigBuilder.BooleanValue hideGUI;
    public static ConfigBuilder.BooleanValue vrEnabled;
    public static ConfigBuilder.BooleanValue walkUpBlocks;
    public static ConfigBuilder.BooleanValue seatedFreeMove;
    public static ConfigBuilder.EnumValue<VRSettings.BowMode> bowMode;
    private static CommentedFileConfig config;
    private static ConfigBuilder builder;

    public static void init(ConfigSpec.CorrectionListener listener){
        Config.setInsertionOrderPreserved(true);
        config = CommentedFileConfig
                .builder(Xplat.getConfigPath("vivecraft-client-config.toml"))
                .autosave()
                .concurrent()
                .build();

        config.load();

        if (listener == null) {
            listener = (action, path, incorrectValue, correctedValue) -> {
                if (incorrectValue != null) {
                    System.out.println("Corrected " + String.join(".", path) + ": was " + incorrectValue + ", is now " + correctedValue);
                }
            };
        }

        fixConfig(config, listener);

        config.save();
    }

    private static void fixConfig(CommentedConfig config, ConfigSpec.CorrectionListener listener) {

        builder = new ConfigBuilder(config, new ConfigSpec());

        builder
                .push("Keyboard");
        physicalKeyboard = builder
                .push("Physical")
                .comment("Enables the Physical Keyboard")
                .define(true);
        autoOpenKeyboard = builder
                .push("Open")
                .comment("Automatically open the keyboard")
                .define(false);
        alwaysSimulateKeyboard = builder
                .push("Simulate")
                .comment("Always simulate the Keyboard")
                .define(false);
        keyboardKeys = builder
                .push("Keys")
                .comment("Keyboard key Layout")
                .define("`1234567890-=qwertyuiop[]\\asdfghjkl;':\"zxcvbnm,./?<>");
        keyboardKeysShift = builder
                .push("ShiftKeys")
                .comment("Keyboard key Layout for shift")
                .define("~!@#$%^&*()_+QWERTYUIOP{}|ASDFGHJKL;':\\\"ZXCVBNM,./?<>");
        physicalKeyboardScale = builder
                .push("scale")
                .comment("The scale for the Physical Keyboard")
                .defineInRange(1.0d, 0.75d, 1.5d);
        physicalKeyboardTheme = builder
                .push("Theme")
                .comment("Keyboard theme used for the Physical Keyboard")
                .define(PhysicalKeyboard.KeyboardTheme.DEFAULT);
        builder.pop();

        builder
                .push("roomscale");
        realisticSneakEnabled = builder
                .push("sneak")
                .comment("Enable room scale sneaking")
                .define(true);
        sneakThreshold = builder
                .push("sneakthreshold")
                .comment("Threshold for when the player starts sneaking")
                .defineInRange(0.4, 0, 2); //2 should be enough range
        allowCrawling = builder
                .push("crawl")
                .comment("Enable room scale crawling")
                .define(true);
        crawlThreshold = builder
                .push("crawlthreshold")
                .comment("Threshold for when the player starts crawling")
                .defineInRange(0.82, 0, 2); //2 should be enough range
        weaponCollision = builder
                .push("weaponcollision")
                .comment("Weapon Collision mode used")
                .define(VRSettings.WeaponCollision.AUTO);
        bowMode = builder
                .push("bow")
                .comment("Bow mode")
                .define(VRSettings.BowMode.ON);
        vrTouchHotbar = builder
                .push("hotbar")
                .comment("Allow selecting items from by touching the hotbar")
                .define(true);
        physicalGuiEnabled = builder
                .push("gui")
                .comment("Allow interactions by touching the gui")
                .define(true);
        realisticJumpEnabled = builder
                .push("jump")
                .comment("Enable room scale jumping")
                .define(true);
        jumpThreshold = builder
                .push("jumpthreshhold")
                .comment("Threshhold for automatically jumping")
                .defineInRange(0.05d, 0d, 2d); //2 should be enough
        realisticRowEnabled = builder
                .push("row")
                .comment("Enable room scale rowing")
                .define(true);
        realisticClimbEnabled = builder
                .push("climbing")
                .comment("Enable room scale climbing")
                .define(true);
        realisticSwimEnabled = builder
                .push("swimming")
                .comment("Enable room scale swimming")
                .define(true);
        autoSprint = builder
                .push("sprint")
                .comment("Automatically sprint")
                .define(true);
        autoSprintThreshold = builder
                .push("sprintthreshhold")
                .comment("Threshhold for automatically sprinting")
                .defineInRange(0.9d, 0.5d, 1d);
        insideBlockSolidColor = builder
                .push("insideblock")
                .comment("If the the inside of a block should be solid black")
                .define(false);
        guiAppearOverBlock = builder
                .push("guiblock")
                .comment("Places the gui over the accessed block")
                .define(true);
        movementSpeedMultiplier = builder
                .push("movementspeed")
                .comment("Movement Speed modifier")
                .defineInRange(1.0d, 0.15d, 1.3d);
        walkMultiplier = builder
                .push("walkspeed")
                .comment("Walk Speed modifier")
                .defineInRange(1.0d, 1d, 10d);
        menuAlwaysFollowFace = builder
                .push("followface")
                .comment("Makes the menu follow the players head")
                .define(false);
        vrHudLockMode = builder
                .push("hudlock")
                .comment("Determines where the hotbar is locked")
                .define(VRSettings.HUDLock.HEAD);
        backpackSwitching = builder
                .push("backpack")
                .comment("Enables backpack switching")
                .define(true);
        walkUpBlocks = builder
                .push("walkup")
                .comment("Enable auto step up")
                .define(true);
        builder.pop();

        builder
                .push("seated");
        seated = builder
                .push("enable")
                .comment("Enables seated mode")
                .define(false);
        seatedhmd = builder
                .push("hmd")
                .comment("")
                .define(false);
        seatedFreeMove = builder
                .push("freemove")
                .comment("Allow free move while playing as seated")
                .define(true);
        seatedHudAltMode = builder
                .push("altmode")
                .comment("Enables the alternative HUD position when playing as seated")
                .define(false);
        builder.pop();

        builder
                .push("display");
        useCrosshairOcclusion = builder
                .push("crosshairocclusion")
                .comment("Occludes the crosshair")
                .define(true);
        displayMirrorMode = builder
                .push("mirror")
                .comment("Mirror mode")
                .define(VRSettings.MirrorMode.SINGLE);
        lowHealthIndicator = builder
                .push("health")
                .comment("Enables the flashing of red on screen when on low health")
                .define(true);
        hudDistance = builder
                .push("hud")
                .comment("The distance between the player and the HUD")
                .defineInRange(1.25d, 0.25d, 5.0d);
        hudPitchOffset = builder
                .push("hudpitch")
                .comment("The pitch offset of the hud")
                .defineInRange(-2.0d, -5d,5d); //TODO what limit is fine here? int.max seems to much?
        hudYawOffset = builder
                .push("hudyaw")
                .comment("The yaw offset of the hud")
                .defineInRange(0.0d, -5d,5d); //TODO what limit is fine here? int.max seems to much?
        headHudScale = builder
                .push("hudscale")
                .comment("The scale of the hud")
                .defineInRange(1.0d, 0.35d, 2.5d);
        hudOpacity = builder
                .push("hudopacity")
                .comment("The opacity of the hud")
                .defineInRange(1.0d, 0.15d, 1.0d);
        stencilOn = builder
                .push("stencil")
                .comment("Enables the eye stencil")
                .define(true);
        headToHmdLength = builder
                .push("hmdhead")
                .comment("The distance between the head and the hmd")
                .defineInRange(0.10d, 0d, 1.0d);
        useFsaa = builder
                .push("fsaa")
                .comment("Enable FSAA")
                .define(false);
        renderScaleFactor = builder
                .push("scale")
                .comment("The scale used to render")
                .defineInRange(1.0d, 0.1d, 9d);
        shaderGUIRender = builder
                .push("shaders")
                .comment("Changes how shaders are applied on GUI's")
                .define(VRSettings.ShaderGUIRender.AFTER_SHADER);
        displayMirrorLeftEye = builder
                .push("lefteye")
                .comment("Use the left eye instead of the right eye for the mirror")
                .define(false);
        hideGUI = builder //TODO remove as it is a dummy storage field
                .push("hide")
                .comment("Hides the gui")
                .define(false);
        builder.pop();

        vrHotswitchingEnabled = builder
                .push("hotswitch")
                .comment("")
                .define(true);

        worldScale = builder
                .push("worldscale")
                .comment("")
                .defineInRange(1,0, 29);

        worldRotation = builder
                .push("worldrotation")
                .comment("")
                .defineInRange(0,0, 360);

        builder
                .push("camera");

        handCameraResScale = builder
                .push("resolution")
                .comment("")
                .defineInRange(1.0d, 0.5d, 3.0d);

        handCameraFov = builder
                .push("fov")
                .comment("")
                .defineInRange(70, 1, 179);
        builder.pop();

        builder.correct(listener);


    }
}
