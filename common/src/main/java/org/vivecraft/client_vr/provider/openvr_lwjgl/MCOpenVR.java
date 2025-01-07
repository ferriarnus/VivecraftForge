package org.vivecraft.client_vr.provider.openvr_lwjgl;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.sun.jna.NativeLibrary;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import org.apache.commons.lang3.tuple.Triple;
import org.joml.*;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.openvr.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.gui.screens.FBTCalibrationScreen;
import org.vivecraft.client.utils.ClientUtils;
import org.vivecraft.client.utils.FileUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.provider.*;
import org.vivecraft.client_vr.provider.control.TrackpadSwipeSampler;
import org.vivecraft.client_vr.provider.control.VRInputAction;
import org.vivecraft.client_vr.provider.control.VRInputActionSet;
import org.vivecraft.client_vr.render.RenderConfigException;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_vr.utils.external.jinfinadeck;
import org.vivecraft.client_vr.utils.external.jkatvr;
import org.vivecraft.common.utils.MathUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.lang.Math;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;

import static org.lwjgl.openvr.VR.*;
import static org.lwjgl.openvr.VRApplications.*;
import static org.lwjgl.openvr.VRCompositor.*;
import static org.lwjgl.openvr.VRInput.*;
import static org.lwjgl.openvr.VRRenderModels.*;
import static org.lwjgl.openvr.VRSettings.VRSettings_GetFloat;
import static org.lwjgl.openvr.VRSystem.*;

/**
 * MCVR implementation to communicate with OpenVR/SteamVR
 */
public class MCOpenVR extends MCVR {
    protected static MCOpenVR OME;

    // action paths
    private static final String ACTION_EXTERNAL_CAMERA = "/actions/mixedreality/in/externalcamera";
    private static final String ACTION_LEFT_HAND = "/actions/global/in/lefthand";
    private static final String ACTION_LEFT_HAPTIC = "/actions/global/out/lefthaptic";
    private static final String ACTION_RIGHT_HAND = "/actions/global/in/righthand";
    private static final String ACTION_RIGHT_HAPTIC = "/actions/global/out/righthaptic";

    private final Map<VRInputActionSet, Long> actionSetHandles = new EnumMap<>(VRInputActionSet.class);
    private VRActiveActionSet.Buffer activeActionSetsBuffer;

    private Map<Long, String> controllerComponentNames;
    private Map<String, Matrix4f[]> controllerComponentTransforms;

    // if true prints out the device info
    private boolean debugInfo = true;
    // if true prints out all device properties
    private final boolean fullDebugInfo = false;
    // specifies if transforms should be refetched
    private boolean getXforms = true;

    private final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final int[] controllerDeviceIndex = new int[3];

    private final TrackedDevicePose.Buffer hmdTrackedDevicePoses;

    // haptic handles
    private long leftControllerHandle;
    private long leftHapticHandle;
    private long rightHapticHandle;

    // action pose handles
    private long leftPoseHandle;
    private long rightPoseHandle;
    private long cameraPoseHandle;

    // holds the handle to the devices other than the headset
    private final long[] deviceHandle = new long[MCVR.TRACKABLE_DEVICE_COUNT];

    private boolean inputInitialized;
    private final InputOriginInfo originInfo;
    private final InputPoseActionData poseData;
    private final InputDigitalActionData digital;
    private final InputAnalogActionData analog;

    private boolean paused = false;

    private final Map<String, TrackpadSwipeSampler> trackpadSwipeSamplers = new HashMap<>();
    private boolean triedToInit;

    private final Queue<VREvent> vrEvents = new LinkedList<>();

    private final VRTextureBounds texBounds;
    protected final Texture texType0;
    protected final Texture texType1;

    // general error buffer
    private final IntBuffer errorBuffer;

    // Last updated 10/29/2023
    // Hard-coded list of languages Steam supports
    private static final Map<String, String> STEAM_LANGUAGES = Map.ofEntries(
        Map.entry("english", "en_US"),
        Map.entry("bulgarian", "bg_BG"),
        Map.entry("schinese", "zh_CN"),
        Map.entry("tchinese", "zh_TW"),
        Map.entry("czech", "cs_CZ"),
        Map.entry("danish", "da_DK"),
        Map.entry("dutch", "nl_NL"),
        Map.entry("finnish", "fi_FI"),
        Map.entry("french", "fr_FR"),
        Map.entry("german", "de_DE"),
        Map.entry("greek", "el_GR"),
        Map.entry("hungarian", "hu_HU"),
        Map.entry("indonesian", "id_ID"),
        Map.entry("italian", "it_IT"),
        Map.entry("japanese", "ja_JP"),
        Map.entry("koreana", "ko_KR"),
        Map.entry("norwegian", "no_NO"),
        Map.entry("polish", "pl_PL"),
        Map.entry("portuguese", "pt_PT"),
        Map.entry("brazilian", "pt_BR"),
        Map.entry("romanian", "ro_RO"),
        Map.entry("russian", "ru_RU"),
        Map.entry("spanish", "es_ES"),
        Map.entry("latam", "es_MX"),
        Map.entry("swedish", "sv_SE"),
        Map.entry("thai", "th_TH"),
        Map.entry("turkish", "tr_TR"),
        Map.entry("ukrainian", "uk_UA"),
        Map.entry("vietnamese", "vi_VN")
    );

    // Steam uses some incorrect language codes, this remaps to those
    // SteamVR itself is also not translated into all languages Steam supports yet, so in those cases English may be used regardless
    private static final Map<String, String> STEAM_LANGUAGE_WRONG_MAPPINGS = Map.ofEntries(
        Map.entry("cs_CZ", "cs_CS"),
        Map.entry("da_DK", "da_DA"),
        Map.entry("el_GR", "el_EL"),
        Map.entry("sv_SE", "sv_SV")
    );

    /**
     * @return the current MCOpenVR instance
     */
    public static MCOpenVR get() {
        return OME;
    }

    /**
     * creates the MCOpenVR instance
     *
     * @param mc instance of Minecraft to use
     * @param dh instance of ClientDataHolderVR to use
     * @throws RenderConfigException if the wrong lwjgl version is loaded
     */
    public MCOpenVR(Minecraft mc, ClientDataHolderVR dh) throws RenderConfigException {
        super(mc, dh, VivecraftVRMod.INSTANCE);
        OME = this;
        // make sure the lwjgl version is the right one
        // check that the right lwjgl version is loaded that we ship the OpenVR part of, or stuff breaks
        final String[] lwjglVersions = new String[]{"3.3.3"};
        if (Arrays.stream(lwjglVersions).noneMatch(v -> Version.getVersion().startsWith(v))) {
            String suppliedJar = "";
            try {
                suppliedJar = new File(
                    Version.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getName();
            } catch (Exception e) {
                VRSettings.LOGGER.error("Vivecraft: couldn't check lwjgl source:", e);
            }

            throw new RenderConfigException(Component.translatable("vivecraft.messages.vriniterror"),
                Component.translatable("vivecraft.messages.rendersetupfailed",
                    I18n.get("vivecraft.messages.invalidlwjgl", Version.getVersion(),
                        lwjglVersions.length == 1 ? lwjglVersions[0] :
                            (lwjglVersions[0] + " - " + lwjglVersions[lwjglVersions.length - 1]), suppliedJar),
                    "OpenVR_LWJGL"));
        }

        this.hapticScheduler = new OpenVRHapticScheduler();

        this.poseMatrices = new Matrix4f[k_unMaxTrackedDeviceCount];
        this.deviceVelocity = new Vector3f[k_unMaxTrackedDeviceCount];

        for (int i = 0; i < k_unMaxTrackedDeviceCount; i++) {
            this.poseMatrices[i] = new Matrix4f();
            this.deviceVelocity[i] = new Vector3f();
        }

        // allocate memory
        this.poseData = InputPoseActionData.calloc();
        this.originInfo = InputOriginInfo.calloc();

        this.errorBuffer = MemoryUtil.memCallocInt(1);

        this.texBounds = VRTextureBounds.calloc();
        this.texType0 = Texture.calloc();
        this.texType1 = Texture.calloc();

        this.digital = InputDigitalActionData.calloc();
        this.analog = InputAnalogActionData.calloc();

        this.trackedDevicePoses = TrackedDevicePose.calloc(k_unMaxTrackedDeviceCount);
    }

    @Override
    public void destroy() {
        super.destroy();
        if (this.initialized) {
            try {
                VR_ShutdownInternal();
                this.initialized = false;

                if (ClientDataHolderVR.KAT_VR) {
                    jkatvr.Halt();
                }

                if (ClientDataHolderVR.INFINADECK) {
                    jinfinadeck.Destroy();
                }
            } catch (Throwable throwable) {
                VRSettings.LOGGER.error("Vivecraft: Error destroying OpenVR:", throwable);
            }
        }

        // free memory
        this.trackedDevicePoses.free();

        this.poseData.free();
        this.originInfo.free();

        this.texBounds.free();
        this.texType0.free();
        this.texType1.free();

        this.digital.free();
        this.analog.free();

        if (this.activeActionSetsBuffer != null) {
            this.activeActionSetsBuffer.free();
        }
    }

    /**
     * @param EVRInputError input error code to get the name for
     * @return Name of the error associated with that error code
     */
    protected static String getInputErrorName(int EVRInputError) {
        return switch (EVRInputError) {
            case 0 -> "None";
            case 1 -> "NameNotFound";
            case 2 -> "WrongType";
            case 3 -> "InvalidHandle";
            case 4 -> "InvalidParam";
            case 5 -> "NoSteam";
            case 6 -> "MaxCapacityReached";
            case 7 -> "IPCError";
            case 8 -> "NoActiveActionSet";
            case 9 -> "InvalidDevice";
            case 10 -> "InvalidSkeleton";
            case 11 -> "InvalidBoneCount";
            case 12 -> "InvalidCompressedData";
            case 13 -> "NoData";
            case 14 -> "BufferTooSmall";
            case 15 -> "MismatchedActionManifest";
            case 16 -> "MissingSkeletonData";
            case 17 -> "InvalidBoneIndex";
            case 18 -> "InvalidPriority";
            case 19 -> "PermissionDenied";
            case 20 -> "InvalidRenderModel";
            default -> "Unknown";
        };
    }

    @Override
    public String getName() {
        return "OpenVR_LWJGL";
    }

    @Override
    public Vector2fc getPlayAreaSize() {
        if (OpenVR.VRChaperone == null || OpenVR.VRChaperone.GetPlayAreaSize == 0L) {
            return null;
        } else {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer pSizeZ = stack.callocFloat(1);
                FloatBuffer pSizeX = stack.callocFloat(1);
                boolean valid = VRChaperone.VRChaperone_GetPlayAreaSize(pSizeX, pSizeZ);
                return valid ? new Vector2f(pSizeX.get(0) * this.dh.vrSettings.walkMultiplier,
                    pSizeZ.get(0) * this.dh.vrSettings.walkMultiplier) : null;
            }
        }
    }

    @Override
    public boolean init() throws RenderConfigException {
        if (this.initialized) {
            return true;
        } else if (this.triedToInit) {
            return this.initialized;
        } else {
            this.triedToInit = true;
            try {
                // inits OpenVR, and loads the function tables
                this.initializeOpenVR();

                // sets up the tracking space and generates the render textures
                this.initOpenVRCompositor();
            } catch (Exception exception) {
                VRSettings.LOGGER.error("Vivecraft: Error initializing OpenVR:", exception);
                this.initSuccess = false;
                this.initStatus = exception.getLocalizedMessage();
                return false;
            }

            if (OpenVR.VRInput == null) {
                VRSettings.LOGGER.error("Vivecraft: Controller input not available. Forcing seated mode.");
                this.dh.vrSettings.seated = true;
            }

            VRSettings.LOGGER.info("Vivecraft: OpenVR initialized & VR connected.");

            this.initInputAndApplication();

            this.initialized = true;

            // initialize treadmill support, if they are enabled
            if (ClientDataHolderVR.KAT_VR) {
                try {
                    VRSettings.LOGGER.info("Vivecraft: Waiting for KATVR....");
                    FileUtils.unpackFolder("natives/katvr", "openvr/katvr");
                    NativeLibrary.addSearchPath(jkatvr.KATVR_LIBRARY_NAME,
                        new File("openvr/katvr").getAbsolutePath());
                    jkatvr.Init(1);
                    jkatvr.Launch();

                    if (jkatvr.CheckForLaunch()) {
                        VRSettings.LOGGER.info("Vivecraft: KATVR Loaded");
                    } else {
                        VRSettings.LOGGER.error("Vivecraft: KATVR Failed to load");
                    }
                } catch (Exception exception) {
                    VRSettings.LOGGER.error("Vivecraft: KATVR crashed:", exception);
                }
            }

            if (ClientDataHolderVR.INFINADECK) {
                try {
                    VRSettings.LOGGER.info("Vivecraft: Waiting for Infinadeck....");
                    FileUtils.unpackFolder("natives/infinadeck", "openvr/infinadeck");
                    NativeLibrary.addSearchPath(jinfinadeck.INFINADECK_LIBRARY_NAME,
                        new File("openvr/infinadeck").getAbsolutePath());

                    if (jinfinadeck.InitConnection()) {
                        jinfinadeck.CheckConnection();
                        VRSettings.LOGGER.info("Vivecraft: Infinadeck Loaded");
                    } else {
                        VRSettings.LOGGER.error("Vivecraft: Infinadeck Failed to load");
                    }
                } catch (Exception exception) {
                    VRSettings.LOGGER.error("Vivecraft: Infinadeck crashed:", exception);
                }
            }

            return true;
        }
    }

    /**
     * initializes LWJGL OpenVR
     *
     * @throws RuntimeException when an error happens during LWJGL init, or some critical OpenVR components are missing
     */
    private void initializeOpenVR() throws RuntimeException {
        int token = VR_InitInternal(this.errorBuffer, EVRApplicationType_VRApplication_Scene);

        if (!this.isError()) {
            OpenVR.create(token);
        }

        // check that the needed OpenVR stuff actually initialized, those can fail when using outdated steamvr
        if (OpenVR.VRApplications == null ||
            OpenVR.VRCompositor == null ||
            OpenVR.VRRenderModels == null ||
            OpenVR.VRSystem == null ||
            this.isError())
        {
            if (this.isError()) {
                throw new RuntimeException(VR_GetVRInitErrorAsEnglishDescription(this.getError()));
            } else {
                throw new RuntimeException(I18n.get("vivecraft.messages.outdatedsteamvr"));
            }
        }

        VRSettings.LOGGER.info("Vivecraft: OpenVR System Initialized OK.");
        this.initSuccess = true;
    }

    @Override
    public void poll(long frameIndex) {
        if (!this.initialized) return;

        this.paused = VRSystem_ShouldApplicationPause();
        Profiler.get().push("pollEvents");
        this.pollVREvents();
        Profiler.get().popPush("processEvents");
        this.processVREvents();
        Profiler.get().popPush("updatePose/Vsync");
        this.updatePose();

        if (!this.dh.vrSettings.seated) {
            if (this.mc.screen == null && this.dh.vrSettings.vrTouchHotbar) {
                Profiler.get().popPush("touchHotbar");
                if (this.dh.vrSettings.vrHudLockMode != VRSettings.HUDLock.HEAD && this.hudPopup) {
                    this.processHotbar();
                }
            }
        }

        Profiler.get().popPush("processInputs");
        this.processInputs();
        Profiler.get().popPush("hmdSampling");
        this.hmdSampling();
        Profiler.get().pop();
    }

    /**
     * @return if the error buffer contains any error
     */
    private boolean isError() {
        return this.errorBuffer.get(0) != 0L;
    }

    /**
     * @return the error code stored in the error buffer
     */
    private int getError() {
        return this.errorBuffer.get(0);
    }

    /**
     * prints information about the given device
     *
     * @param deviceIndex index of the device to ge t info from
     */
    private void debugOut(int deviceIndex) {
        if (this.fullDebugInfo) {
            VRSettings.LOGGER.info("Vivecraft: ******************* VR DEVICE: {} *************************",
                deviceIndex);
            // print all device properties
            for (Field field : VR.class.getDeclaredFields()) {
                try {
                    if (!field.getName().startsWith("ETrackedDeviceProperty_Prop_")) {
                        // we only care about device properties
                        continue;
                    }

                    String type = field.getName().substring(field.getName().lastIndexOf("_") + 1);
                    String property = field.getName().replace("ETrackedDeviceProperty_Prop_", "") + " ";

                    int prop = field.getInt(null);

                    property += switch (type) {
                        case "Float" -> VRSystem_GetFloatTrackedDeviceProperty(deviceIndex, prop, this.errorBuffer);
                        case "String" -> VRSystem_GetStringTrackedDeviceProperty(deviceIndex, prop, this.errorBuffer);
                        case "Bool" -> VRSystem_GetBoolTrackedDeviceProperty(deviceIndex, prop, this.errorBuffer);
                        case "Int32" -> VRSystem_GetInt32TrackedDeviceProperty(deviceIndex, prop, this.errorBuffer);
                        case "Uint64" -> VRSystem_GetUint64TrackedDeviceProperty(deviceIndex, prop, this.errorBuffer);
                        default -> "(skipped)";
                    };

                    VRSettings.LOGGER.info("Vivecraft: {}", property);
                } catch (IllegalAccessException illegalaccessexception) {
                    VRSettings.LOGGER.error("Vivecraft: Error reading device property:", illegalaccessexception);
                }
            }
            VRSettings.LOGGER.info("Vivecraft: ******************* END VR DEVICE: {} *************************",
                deviceIndex);
        } else {
            // print only manufacturer and model
            VRSettings.LOGGER.info("Vivecraft: VR DEVICE: {}, Manufacturer: {}, Model: {}", deviceIndex,
                VRSystem_GetStringTrackedDeviceProperty(deviceIndex,
                    ETrackedDeviceProperty_Prop_ManufacturerName_String, this.errorBuffer),
                VRSystem_GetStringTrackedDeviceProperty(deviceIndex,
                    VR.ETrackedDeviceProperty_Prop_ModelNumber_String, this.errorBuffer));
        }
    }

    @Override
    protected ControllerType findActiveBindingControllerType(KeyMapping keyMapping) {
        if (!this.inputInitialized) {
            return null;
        } else {
            long origin = this.getInputAction(keyMapping).getLastOrigin();
            return origin != k_ulInvalidInputValueHandle ? this.getOriginControllerType(origin) : null;
        }
    }

    /**
     * generates the actionmanifest json for OpenVR, which holds the info about all KeyMappings
     * also exports the default configs from the jar
     */
    private void generateActionManifest() {
        Map<String, Object> map = new HashMap<>();
        List<Map<String, Object>> actionSets = new ArrayList<>();

        // action sets
        for (VRInputActionSet actionSet : VRInputActionSet.values()) {
            String usage = actionSet.usage;

            if (actionSet.advanced && !this.dh.vrSettings.allowAdvancedBindings) {
                usage = "hidden";
            }

            actionSets.add(
                ImmutableMap.<String, Object>builder().put("name", actionSet.name).put("usage", usage).build());
        }
        map.put("action_sets", actionSets);

        // binding
        // Sort the bindings, so they're easy to look through in SteamVR
        List<VRInputAction> sortedActions = new ArrayList<>(this.inputActions.values());
        sortedActions.sort(Comparator.comparing((action) -> action.keyBinding));

        List<Map<String, Object>> actions = new ArrayList<>();

        for (VRInputAction action : sortedActions) {
            actions.add(
                ImmutableMap.<String, Object>builder().put("name", action.name).put("requirement", action.requirement)
                    .put("type", action.type).build());
        }

        // controller poses
        actions.add(ImmutableMap.<String, Object>builder()
            .put("name", ACTION_LEFT_HAND)
            .put("requirement", "suggested")
            .put("type", "pose").build());
        actions.add(ImmutableMap.<String, Object>builder()
            .put("name", ACTION_RIGHT_HAND)
            .put("requirement", "suggested")
            .put("type", "pose").build());

        // controller haptic targets
        actions.add(ImmutableMap.<String, Object>builder()
            .put("name", ACTION_LEFT_HAPTIC)
            .put("requirement", "suggested")
            .put("type", "vibration").build());
        actions.add(ImmutableMap.<String, Object>builder()
            .put("name", ACTION_RIGHT_HAPTIC)
            .put("requirement", "suggested")
            .put("type", "vibration").build());

        // camera tracker
        actions.add(ImmutableMap.<String, Object>builder()
            .put("name", ACTION_EXTERNAL_CAMERA)
            .put("requirement", "optional")
            .put("type", "pose").build());

        map.put("actions", actions);

        // localizations
        // TODO: revert to exporting all Steam languages when Valve fixes the crash with large action manifests
        List<String> languages = new ArrayList<>();
        languages.add("en_US");

        boolean gotRegistryValue = false;
        if (Util.getPlatform() == Util.OS.WINDOWS) {
            // Try to read the user's Steam language setting from the registry
            String language = ClientUtils.readWinRegistry("HKCU\\SOFTWARE\\Valve\\Steam\\Language");
            if (language != null) {
                gotRegistryValue = true;
                VRSettings.LOGGER.info("Vivecraft: Steam language setting: {}", language);
                if (!language.equals("english") && STEAM_LANGUAGES.containsKey(language)) {
                    languages.add(STEAM_LANGUAGES.get(language));
                }
            } else {
                VRSettings.LOGGER.warn("Vivecraft: Unable to read Steam language setting");
            }
        }

        if (!gotRegistryValue && !this.mc.options.languageCode.startsWith("en_")) {
            // Try to find a Steam language matching the user's in-game language selection
            String ucLanguageCode =
                this.mc.options.languageCode.substring(0, this.mc.options.languageCode.indexOf('_')) +
                    this.mc.options.languageCode.substring(this.mc.options.languageCode.indexOf('_')).toUpperCase();
            if (STEAM_LANGUAGES.containsValue(ucLanguageCode)) {
                languages.add(ucLanguageCode);
            } else {
                Optional<String> langCode = STEAM_LANGUAGES.values().stream().filter(
                    s -> ucLanguageCode.substring(0, ucLanguageCode.indexOf('_'))
                        .equals(s.substring(0, s.indexOf('_')))).findFirst();
                langCode.ifPresent(languages::add);
            }
        }

        List<Map<String, Object>> localeList = new ArrayList<>();
        for (String langCode : languages) {
            Map<String, Object> localeMap = new HashMap<>();

            // Load the language
            List<String> langs = new ArrayList<>();
            langs.add("en_us");
            if (!langCode.equals("en_US")) {
                langs.add(langCode.toLowerCase());
            }
            Language lang = ClientLanguage.loadFrom(this.mc.getResourceManager(), langs, false);

            for (VRInputAction action : sortedActions) {
                localeMap.put(action.name, lang.getOrDefault(action.keyBinding.getCategory()) + " - " +
                    lang.getOrDefault(action.keyBinding.getName()));
            }

            for (VRInputActionSet actionSet : VRInputActionSet.values()) {
                localeMap.put(actionSet.name, lang.getOrDefault(actionSet.localizedName));
            }

            // We don't really care about localizing these
            localeMap.put(ACTION_LEFT_HAND, "Left Hand Pose");
            localeMap.put(ACTION_RIGHT_HAND, "Right Hand Pose");
            localeMap.put(ACTION_LEFT_HAPTIC, "Left Hand Haptic");
            localeMap.put(ACTION_RIGHT_HAPTIC, "Right Hand Haptic");
            localeMap.put(ACTION_EXTERNAL_CAMERA, "External Camera");

            localeMap.put("language_tag", STEAM_LANGUAGE_WRONG_MAPPINGS.getOrDefault(langCode, langCode));
            localeList.add(localeMap);
        }
        map.put("localization", localeList);

        // link default bindings
        List<Map<String, Object>> defaults = new ArrayList<>();
        BiConsumer<String, String> defaultsAdder = (type, url) ->
            defaults.add(ImmutableMap.<String, Object>builder()
                .put("controller_type", type)
                .put("binding_url", url).build());

        defaultsAdder.accept("vive_controller", "vive_defaults.json");
        defaultsAdder.accept("oculus_touch", "oculus_defaults.json");
        defaultsAdder.accept("holographic_controller", "wmr_defaults.json");
        defaultsAdder.accept("knuckles", "knuckles_defaults.json");
        defaultsAdder.accept("vive_cosmos_controller", "cosmos_defaults.json");
        defaultsAdder.accept("vive_tracker_camera", "tracker_defaults.json");
        defaultsAdder.accept("vive_tracker_waist", "tracker_waist_defaults.json");
        defaultsAdder.accept("vive_tracker_left_foot", "tracker_left_foot_defaults.json");
        defaultsAdder.accept("vive_tracker_right_foot", "tracker_right_foot_defaults.json");
        defaultsAdder.accept("vive_tracker_left_elbow", "tracker_left_elbow_defaults.json");
        defaultsAdder.accept("vive_tracker_right_elbow", "tracker_right_elbow_defaults.json");
        defaultsAdder.accept("vive_tracker_left_knee", "tracker_left_knee_defaults.json");
        defaultsAdder.accept("vive_tracker_right_knee", "tracker_right_knee_defaults.json");

        map.put("default_bindings", defaults);

        // write action manifest to disk
        try {
            (new File("openvr/input")).mkdirs();

            try (OutputStreamWriter outputstreamwriter = new OutputStreamWriter(
                new FileOutputStream("openvr/input/action_manifest.json"), StandardCharsets.UTF_8))
            {
                this.GSON.toJson(map, outputstreamwriter);
            }
        } catch (Exception exception) {
            throw new RuntimeException("Failed to write action manifest", exception);
        }

        // write defaults to disk
        String rev = this.dh.vrSettings.reverseHands ? "_reversed" : "";
        // controllers
        FileUtils.unpackAsset("input/vive_defaults" + rev + ".json", "openvr/input/vive_defaults.json", false);
        FileUtils.unpackAsset("input/oculus_defaults" + rev + ".json", "openvr/input/oculus_defaults.json", false);
        FileUtils.unpackAsset("input/wmr_defaults" + rev + ".json", "openvr/input/wmr_defaults.json", false);
        FileUtils.unpackAsset("input/knuckles_defaults" + rev + ".json", "openvr/input/knuckles_defaults.json", false);
        FileUtils.unpackAsset("input/cosmos_defaults" + rev + ".json", "openvr/input/cosmos_defaults.json", false);

        // camera tracker
        FileUtils.unpackAssetToFolder("input/tracker_defaults.json", "openvr", false);
    }

    /**
     * @param name name/path of the action
     * @return handle of the action
     * @throws RuntimeException if OpenVR gives an error
     */
    private long getActionHandle(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longRef = stack.callocLong(1);
            int error = VRInput_GetActionHandle(name, longRef);

            if (error != EVRInputError_VRInputError_None) {
                throw new RuntimeException(
                    "Error getting action handle for '" + name + "': " + getInputErrorName(error));
            } else {
                return longRef.get(0);
            }
        }
    }

    /**
     * updates which ActionSets should be active at this moment
     *
     * @return if any ActionSets are active
     */
    private boolean updateActiveActionSets() {
        ArrayList<VRInputActionSet> activeSets = new ArrayList<>();
        activeSets.add(VRInputActionSet.GLOBAL);

        // we are always modded
        activeSets.add(VRInputActionSet.MOD);

        activeSets.add(VRInputActionSet.MIXED_REALITY);
        activeSets.add(VRInputActionSet.TECHNICAL);

        if (this.mc.screen == null) {
            activeSets.add(VRInputActionSet.INGAME);
            activeSets.add(VRInputActionSet.CONTEXTUAL);
        } else {
            activeSets.add(VRInputActionSet.GUI);
            if (ClientDataHolderVR.getInstance().vrSettings.ingameBindingsInGui) {
                activeSets.add(VRInputActionSet.INGAME);
            }
            if (this.mc.screen instanceof FBTCalibrationScreen) {
                activeSets.add(VRInputActionSet.CONTEXTUAL);
            }
        }

        if (KeyboardHandler.SHOWING || RadialHandler.isShowing()) {
            activeSets.add(VRInputActionSet.KEYBOARD);
        }

        // make sure the ActionSetsBuffer has the right size
        if (this.activeActionSetsBuffer == null) {
            this.activeActionSetsBuffer = VRActiveActionSet.calloc(activeSets.size());
        } else if (this.activeActionSetsBuffer.capacity() != activeSets.size()) {
            this.activeActionSetsBuffer.free();
            this.activeActionSetsBuffer = VRActiveActionSet.calloc(activeSets.size());
        }

        for (int i = 0; i < activeSets.size(); i++) {
            this.activeActionSetsBuffer.get(i)
                .set(this.getActionSetHandle(activeSets.get(i)), k_ulInvalidInputValueHandle, 0, 0);
        }

        return !activeSets.isEmpty();
    }

    @Override
    public Matrix4fc getControllerComponentTransform(int controllerIndex, String componentName) {
        return this.controllerComponentTransforms != null &&
            this.controllerComponentTransforms.containsKey(componentName) &&
            this.controllerComponentTransforms.get(componentName)[controllerIndex] != null ?
            this.controllerComponentTransforms.get(componentName)[controllerIndex] :
            new Matrix4f();
    }

    private Matrix4fc getControllerComponentTransformFromButton(int controllerIndex, long button) {
        return this.controllerComponentNames != null && this.controllerComponentNames.containsKey(button) ?
            this.getControllerComponentTransform(controllerIndex, this.controllerComponentNames.get(button)) :
            new Matrix4f();
    }

    /**
     * @param hand {@link ControllerType#LEFT} or {@link ControllerType#RIGHT}
     * @return the haptic handle for the specified controller
     */
    protected long getHapticHandle(ControllerType hand) {
        return hand == ControllerType.RIGHT ? this.rightHapticHandle : this.leftHapticHandle;
    }

    /**
     * @param buf ByteBuffer pointing to a String
     * @return String contained in the given buffer
     */
    private String memUTF8NullTerminated(ByteBuffer buf) {
        return MemoryUtil.memUTF8(MemoryUtil.memAddress(buf));
    }

    /**
     * @return current OpenVR resolution scaling
     */
    protected float getSuperSampling() {
        return VRSettings_GetFloat("steamvr", "supersampleScale", this.errorBuffer);
    }


    /**
     * fetches the controller poses from openvr
     */
    private void getTransforms() {
        if (this.getXforms) {
            this.controllerComponentTransforms = new HashMap<>();
        }

        if (this.controllerComponentNames == null) {
            this.controllerComponentNames = new HashMap<>();
        }

        int count = VRRenderModels_GetRenderModelCount();

        // TODO get the controller-specific list
        List<String> componentNames = new ArrayList<>();

        // see here for available poses https://github.com/ValveSoftware/openvr/blob/ae46a8dd0172580648c8922658a100439115d3eb/headers/openvr.h#L4479
        // and here for more documentation https://github.com/ValveSoftware/openvr/wiki/Render-Model-Reference
        componentNames.add(k_pch_Controller_Component_Tip);
        // wmr doesn't define these...
        // componentNames.add(k_pch_Controller_Component_Base);
        // componentNames.add(k_pch_Controller_Component_Status);
        componentNames.add(k_pch_Controller_Component_HandGrip);

        boolean failed = false;

        for (String component : componentNames) {
            this.controllerComponentTransforms.put(component, new Matrix4f[2]);

            for (int c = 0; c < 2; c++) {
                if (this.deviceSource[c].source != DeviceSource.Source.OPENVR ||
                    this.deviceSource[c].deviceIndex == k_unTrackedDeviceIndexInvalid)
                {
                    failed = true;
                    continue;
                }
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    var stringBuffer = stack.calloc(k_unMaxPropertyStringSize);

                    VRSystem_GetStringTrackedDeviceProperty(this.deviceSource[c].deviceIndex,
                        VR.ETrackedDeviceProperty_Prop_RenderModelName_String, stringBuffer, this.errorBuffer);

                    String renderModelName = memUTF8NullTerminated(stringBuffer);

                    VRSystem_GetStringTrackedDeviceProperty(this.deviceSource[c].deviceIndex,
                        VR.ETrackedDeviceProperty_Prop_InputProfilePath_String, stringBuffer, this.errorBuffer);

                    String inputProfilePath = memUTF8NullTerminated(stringBuffer);
                    boolean isWMR = inputProfilePath.contains("holographic");
                    boolean isRifts = inputProfilePath.contains("rifts");

                    String componentName = component;
                    if (isWMR && component.equals(k_pch_Controller_Component_HandGrip)) {
                        // I have no idea, Microsoft, none.
                        componentName = "body";
                    }

                    long button = VRRenderModels_GetComponentButtonMask(renderModelName, componentName);

                    if (button > 0L) {
                        // see now... wtf OpenVR, '0' is the system button, it cant also be the error value!
                        // (hint: it's a mask, not an index)
                        // u get 1 button per component, nothing more
                        this.controllerComponentNames.put(button, component);
                    }

                    long sourceHandle = this.deviceHandle[c];

                    if (sourceHandle == k_ulInvalidInputValueHandle) {
                        failed = true;
                        continue;
                    }

                    var renderModelComponentState = RenderModelComponentState.calloc(stack);
                    boolean valid = VRRenderModels_GetComponentStateForDevicePath(renderModelName, componentName,
                        sourceHandle, RenderModelControllerModeState.calloc(stack), renderModelComponentState);

                    if (!valid) {
                        failed = true;
                        continue;
                    }

                    Matrix4f localTransform = OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(
                        renderModelComponentState.mTrackingToComponentLocal(), new Matrix4f());
                    this.controllerComponentTransforms.get(component)[c] = localTransform;

                    if (c == LEFT_CONTROLLER && isRifts && component.equals(k_pch_Controller_Component_HandGrip)) {
                        // I have no idea, Valve, none.
                        this.controllerComponentTransforms.get(
                            component)[LEFT_CONTROLLER] = this.controllerComponentTransforms.get(
                            component)[RIGHT_CONTROLLER];
                    }

                    if (!failed && c == RIGHT_CONTROLLER) {
                        // calculate gun angle
                        try {
                            Matrix4fc tip = this.getControllerComponentTransform(RIGHT_CONTROLLER,
                                k_pch_Controller_Component_Tip);
                            Matrix4fc hand = this.getControllerComponentTransform(RIGHT_CONTROLLER,
                                k_pch_Controller_Component_HandGrip);

                            Vector3f tipVec = tip.transformDirection(MathUtils.BACK, new Vector3f());
                            Vector3f handVec = hand.transformDirection(MathUtils.BACK, new Vector3f());

                            float dot = Math.abs(tipVec.dot(handVec));

                            float angleRad = (float) Math.acos(dot);
                            float angleDeg = Mth.RAD_TO_DEG * angleRad;

                            this.gunStyle = angleDeg > 10.0F;
                            this.gunAngle = angleDeg;
                        } catch (Exception exception) {
                            failed = true;
                        }
                    }
                }
            }

            // fetch transforms as long as we don't have both controllers
            this.getXforms = failed;
        }
    }

    /**
     * populates input actions, and tells OpenVR what app we are
     *
     * @throws RenderConfigException in case minecraft runs in a path unsupported by SteamVR
     */
    private void initInputAndApplication() throws RenderConfigException {
        this.populateInputActions();

        if (OpenVR.VRInput == null) return;

        // generate and submit bindings
        this.generateActionManifest();
        this.loadActionManifest();
        this.loadActionHandles();
        this.installApplicationManifest(false);
        this.inputInitialized = true;
    }

    /**
     * sets tracking space, checks what headset, and set's up the render textures
     */
    private void initOpenVRCompositor() {
        VRCompositor_SetTrackingSpace(ETrackingUniverseOrigin_TrackingUniverseStanding);
        String actualTrackingSpace = switch (VRCompositor_GetTrackingSpace()) {
            case ETrackingUniverseOrigin_TrackingUniverseSeated -> "seated";
            case ETrackingUniverseOrigin_TrackingUniverseStanding -> "standing";
            case ETrackingUniverseOrigin_TrackingUniverseRawAndUncalibrated -> "raw uncalibrated";
            default -> "unknown";
        };
        VRSettings.LOGGER.info("Vivecraft: TrackingSpace: {}", actualTrackingSpace);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer stringBuffer = stack.calloc(20);

            VRSystem_GetStringTrackedDeviceProperty(k_unTrackedDeviceIndex_Hmd,
                ETrackedDeviceProperty_Prop_ManufacturerName_String, stringBuffer, this.errorBuffer);

            String deviceName = memUTF8NullTerminated(stringBuffer);
            VRSettings.LOGGER.info("Vivecraft: Device manufacturer is: {}", deviceName);
            this.detectedHardware = HardwareType.fromManufacturer(deviceName);
        }

        // texture bounds, currently unused, since we use the full texture anyway
        this.texBounds.uMax(1.0F);
        this.texBounds.uMin(0.0F);
        this.texBounds.vMax(1.0F);
        this.texBounds.vMin(0.0F);

        // set up texture types
        this.texType0.eColorSpace(VR.EColorSpace_ColorSpace_Gamma);
        this.texType0.eType(VR.ETextureType_TextureType_OpenGL);
        this.texType0.handle(-1);

        this.texType1.eColorSpace(VR.EColorSpace_ColorSpace_Gamma);
        this.texType1.eType(VR.ETextureType_TextureType_OpenGL);
        this.texType1.handle(-1);

        VRSettings.LOGGER.info("Vivecraft: OpenVR Compositor initialized OK.");
    }

    /**
     * checks if hte given path contains any non ASCII character, because steamvr refuses to support those
     *
     * @param path        path to check
     * @param knownError  String of an error that should be shown to the user when this fails
     * @param alwaysThrow if this should always throw the exception with the give n error, regardless of the result.
     * @throws RenderConfigException if there are non ASCII characters in the path or alwaysThrow is set
     */
    private void checkPathValid(String path, String knownError, boolean alwaysThrow) throws RenderConfigException {
        String pathFormatted = "";
        boolean hasInvalidChars = false;
        for (char c : path.toCharArray()) {
            if (c > 127) {
                hasInvalidChars = true;
                pathFormatted += "§c" + c + "§r";
            } else {
                pathFormatted += c;
            }
        }

        if (hasInvalidChars || alwaysThrow) {
            String error = knownError + (hasInvalidChars ? "\nInvalid characters in path: \n" : "\n");
            if (hasInvalidChars) {
                throw new RenderConfigException(Component.translatable("vivecraft.messages.vriniterror"),
                    Component.translatable("vivecraft.messages.steamvrInvalidCharacters", pathFormatted));
            } else {
                throw new RenderConfigException(Component.translatable("vivecraft.messages.vriniterror"),
                    Component.empty().append(error).append(pathFormatted));
            }
        }
    }

    /**
     * reads the app_key from the bundled application vrmanifest, registers the application and tells it that we are that application
     *
     * @param force forces to resubmit the application manifest to OpenVR
     * @throws RenderConfigException if a path is invalid for OpenVR
     */
    private void installApplicationManifest(boolean force) throws RenderConfigException {
        File manifestFile = new File("openvr/vivecraft.vrmanifest");
        FileUtils.unpackAssetToFolder("vivecraft.vrmanifest", "openvr", true);

        File customFile = new File("openvr/custom.vrmanifest");
        if (customFile.exists()) {
            manifestFile = customFile;
        }

        String appKey;

        try (FileReader fileReader = new FileReader(manifestFile)) {
            Map<?, ?> map = this.GSON.fromJson(fileReader, Map.class);
            appKey = ((Map<?, ?>) ((List<?>) map.get("applications")).get(0)).get("app_key").toString();
        } catch (Exception e) {
            // TODO: should we abort here?
            VRSettings.LOGGER.error("Vivecraft: Error reading appkey from manifest:", e);
            return;
        }

        VRSettings.LOGGER.info("Vivecraft: Appkey: {}", appKey);

        // check if path is valid always, since if the application was already installed, it will not check it again
        checkPathValid(manifestFile.getAbsolutePath(), "Failed to install application manifest", false);

        if (!force && VRApplications_IsApplicationInstalled(appKey)) {
            VRSettings.LOGGER.info("Vivecraft: Application manifest already installed");
        } else {
            int error = VRApplications_AddApplicationManifest(manifestFile.getAbsolutePath(), true);
            if (error != EVRApplicationError_VRApplicationError_None) {
                // application needs to be installed, so abort
                checkPathValid(manifestFile.getAbsolutePath(),
                    "Failed to install application manifest: " + VRApplications_GetApplicationsErrorNameFromEnum(error),
                    true);
            }

            VRSettings.LOGGER.info("Vivecraft: Application manifest installed successfully");
        }

        // OpenVR doc says pid = 0 will use the calling process, but it actually doesn't, so we
        // have to use this dumb hack that *probably* works on all relevant platforms.
        // TODO: could be replaced with ProcessHandle.current().pid()
        int pid;
        try {
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            pid = Integer.parseInt(runtimeName.split("@")[0]);
        } catch (Exception e) {
            // TODO: should we abort here?
            VRSettings.LOGGER.error("Vivecraft: Error getting process id:", e);
            return;
        }

        int error = VRApplications_IdentifyApplication(pid, appKey);
        if (error != EVRApplicationError_VRApplicationError_None) {
            VRSettings.LOGGER.error("Vivecraft: Failed to identify application: {}",
                VRApplications_GetApplicationsErrorNameFromEnum(error));
        } else {
            VRSettings.LOGGER.info("Vivecraft: Application identified successfully");
        }
    }

    /**
     * queries the handles for controllers, haptics and trackers
     *
     * @throws RuntimeException if OpenVR gives an error
     */
    private void loadActionHandles() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longRef = stack.callocLong(1);

            for (VRInputAction action : this.inputActions.values()) {
                int error = VRInput_GetActionHandle(action.name, longRef);

                if (error != EVRInputError_VRInputError_None) {
                    throw new RuntimeException(
                        "Error getting action handle for '" + action.name + "': " + getInputErrorName(error));
                }

                action.setHandle(longRef.get(0));
            }

            // controllers
            this.rightPoseHandle = this.getActionHandle(ACTION_RIGHT_HAND);
            this.leftPoseHandle = this.getActionHandle(ACTION_LEFT_HAND);

            this.rightHapticHandle = this.getActionHandle(ACTION_RIGHT_HAPTIC);
            this.leftHapticHandle = this.getActionHandle(ACTION_LEFT_HAPTIC);

            // camera tracker
            this.cameraPoseHandle = this.getActionHandle(ACTION_EXTERNAL_CAMERA);

            for (VRInputActionSet actionSet : VRInputActionSet.values()) {
                int error = VRInput_GetActionSetHandle(actionSet.name, longRef);

                if (error != EVRInputError_VRInputError_None) {
                    throw new RuntimeException(
                        "Error getting action set handle for '" + actionSet.name + "': " + getInputErrorName(error));
                }

                this.actionSetHandles.put(actionSet, longRef.get(0));
            }

            this.deviceHandle[RIGHT_CONTROLLER] = this.getInputSourceHandle(k_pchPathUserHandRight);
            this.deviceHandle[LEFT_CONTROLLER] = this.getInputSourceHandle(k_pchPathUserHandLeft);
            this.deviceHandle[CAMERA_TRACKER] = this.getInputSourceHandle(k_pchPathUserCamera);
            this.deviceHandle[WAIST_TRACKER] = this.getInputSourceHandle(k_pchPathUserWaist);
            this.deviceHandle[RIGHT_FOOT_TRACKER] = this.getInputSourceHandle(k_pchPathUserFootRight);
            this.deviceHandle[LEFT_FOOT_TRACKER] = this.getInputSourceHandle(k_pchPathUserFootLeft);
            this.deviceHandle[RIGHT_ELBOW_TRACKER] = this.getInputSourceHandle(k_pchPathUserElbowRight);
            this.deviceHandle[LEFT_ELBOW_TRACKER] = this.getInputSourceHandle(k_pchPathUserElbowLeft);
            this.deviceHandle[RIGHT_KNEE_TRACKER] = this.getInputSourceHandle(k_pchPathUserKneeRight);
            this.deviceHandle[LEFT_KNEE_TRACKER] = this.getInputSourceHandle(k_pchPathUserKneeLeft);
        }
    }

    /**
     * submits the path for or the previously generated action_manifest to OpenVR
     *
     * @throws RenderConfigException if OpenVR throws any error, or the path is invalid
     */
    private void loadActionManifest() throws RenderConfigException {
        String actionsPath = new File("openvr/input/action_manifest.json").getAbsolutePath();
        // check if path is valid for steamvr, since it would just silently fail
        checkPathValid(actionsPath, "Failed to install action manifest", false);
        int error = VRInput_SetActionManifestPath(actionsPath);

        if (error != EVRInputError_VRInputError_None) {
            throw new RenderConfigException(Component.translatable("vivecraft.messages.vriniterror"),
                Component.literal("Failed to load action manifest: " + getInputErrorName(error)));
        }
    }

    /**
     * updates the KeyMapping state that is linked to the given VRInputAction
     *
     * @param action VRInputAction to process
     */
    private void processInputAction(VRInputAction action) {
        if (action.isActive() && action.isEnabledRaw() &&
            // try to prevent double left clicks
            (!ClientDataHolderVR.getInstance().vrSettings.ingameBindingsInGui ||
                !(action.actionSet == VRInputActionSet.INGAME &&
                    action.keyBinding.key.getType() == InputConstants.Type.MOUSE &&
                    action.keyBinding.key.getValue() == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.mc.screen != null
                )
            ))
        {
            if (action.isButtonChanged()) {
                if (action.isButtonPressed() && action.isEnabled()) {
                    // We do this, so shit like closing a GUI by clicking a button won't
                    // also click in the world immediately after.
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

    /**
     * checks the axis input of the VRInputAction linked to {@code keyMapping} and runs the callbacks when it's non 0
     *
     * @param keyMapping   KeyMapping to check
     * @param upCallback   action to do when the axis input is positive
     * @param downCallback action to do when the axis input is negative
     */
    private void processScrollInput(KeyMapping keyMapping, Runnable upCallback, Runnable downCallback) {
        VRInputAction action = this.getInputAction(keyMapping);

        if (action.isEnabled() && action.getLastOrigin() != k_ulInvalidInputValueHandle) {
            float value = action.getAxis2D(false).y();
            if (value != 0.0F) {
                if (value > 0.0F) {
                    upCallback.run();
                } else if (value < 0.0F) {
                    downCallback.run();
                }
            }
        }
    }

    /**
     * checks the trackpad input of the controller the {@code keyMapping} is on
     *
     * @param keyMapping    KeyMapping to check
     * @param leftCallback  action to do when swiped to the left
     * @param rightCallback action to do when swiped to the right
     * @param upCallback    action to do when swiped to the up
     * @param downCallback  action to do when swiped to the down
     */
    private void processSwipeInput(
        KeyMapping keyMapping, Runnable leftCallback, Runnable rightCallback, Runnable upCallback,
        Runnable downCallback)
    {
        VRInputAction action = this.getInputAction(keyMapping);

        if (action.isEnabled() && action.getLastOrigin() != k_ulInvalidInputValueHandle) {
            ControllerType controller = this.findActiveBindingControllerType(keyMapping);

            if (controller != null) {
                // if that keyMapping is not tracked yet, create a new sampler
                if (!this.trackpadSwipeSamplers.containsKey(keyMapping.getName())) {
                    this.trackpadSwipeSamplers.put(keyMapping.getName(), new TrackpadSwipeSampler());
                }

                TrackpadSwipeSampler trackpadswipesampler = this.trackpadSwipeSamplers.get(keyMapping.getName());
                trackpadswipesampler.update(controller, action.getAxis2D(false));

                if (trackpadswipesampler.isSwipedUp() && upCallback != null) {
                    this.triggerHapticPulse(controller, 0.001F, 400.0F, 0.5F);
                    upCallback.run();
                }

                if (trackpadswipesampler.isSwipedDown() && downCallback != null) {
                    this.triggerHapticPulse(controller, 0.001F, 400.0F, 0.5F);
                    downCallback.run();
                }

                if (trackpadswipesampler.isSwipedLeft() && leftCallback != null) {
                    this.triggerHapticPulse(controller, 0.001F, 400.0F, 0.5F);
                    leftCallback.run();
                }

                if (trackpadswipesampler.isSwipedRight() && rightCallback != null) {
                    this.triggerHapticPulse(controller, 0.001F, 400.0F, 0.5F);
                    rightCallback.run();
                }
            }
        }
    }

    /**
     * fetches all available VREvents from OpenVR
     */
    private void pollVREvents() {
        VREvent vrEvent = VREvent.calloc();
        while (VRSystem_PollNextEvent(vrEvent, VREvent.SIZEOF)) {
            this.vrEvents.add(vrEvent);
            vrEvent = VREvent.calloc();
        }
        // free the one that didn't get used
        vrEvent.free();
    }

    /**
     * processes all previously fetched VREvents
     */
    private void processVREvents() {
        while (!this.vrEvents.isEmpty()) {
            // try with, to free the event buffer after use
            try (VREvent event = this.vrEvents.poll()) {
                switch (event.eventType()) {
                    // new device connected, so re fetch controller transforms
                    case EVREventType_VREvent_TrackedDeviceActivated,
                         EVREventType_VREvent_TrackedDeviceDeactivated,
                         EVREventType_VREvent_TrackedDeviceUpdated,
                         EVREventType_VREvent_TrackedDeviceRoleChanged,
                         EVREventType_VREvent_ModelSkinSettingsHaveChanged -> this.getXforms = true;
                    // OpenVR closed / told the app to exit
                    case EVREventType_VREvent_Quit -> {
                        if (ClientDataHolderVR.getInstance().vrSettings.closeWithRuntime) {
                            VRSettings.LOGGER.info("Vivecraft: SteamVR closed, closing the game with it");
                            this.mc.stop();
                        } else {
                            VRSettings.LOGGER.info("Vivecraft: SteamVR closed, disabling VR");
                            VRState.VR_ENABLED = !VRState.VR_ENABLED;
                            ClientDataHolderVR.getInstance().vrSettings.vrEnabled = VRState.VR_ENABLED;
                            ClientDataHolderVR.getInstance().vrSettings.saveOptions();
                        }
                    }
                }
            }
        }
    }

    /**
     * reads info about the origin of the inputHandle into {@code this.originInfo}
     *
     * @param inputValueHandle action to get the originInfo for
     * @throws RuntimeException if OpenVR gives an error
     */
    private void readOriginInfo(long inputValueHandle) {
        int error = VRInput_GetOriginTrackedDeviceInfo(inputValueHandle, this.originInfo, InputOriginInfo.SIZEOF);

        if (error != EVRInputError_VRInputError_None) {
            throw new RuntimeException("Error reading origin info: " + getInputErrorName(error));
        }
    }

    /**
     * reads info about the origin of the inputHandle into {@code this.originInfo}
     *
     * @param inputValueHandle action to get the originInfo for
     * @return if the origin was successfully read
     */
    private boolean readOriginInfoNoError(long inputValueHandle) {
        int error = VRInput_GetOriginTrackedDeviceInfo(inputValueHandle, this.originInfo, InputOriginInfo.SIZEOF);

        return error == EVRInputError_VRInputError_None;
    }

    /**
     * reads the pose info of the given pose handle into {@code this.poseData}
     *
     * @param poseHandle handle to the pose to get the poseActionData for
     * @throws RuntimeException if OpenVR gives an error
     */
    private void readPoseData(long poseHandle) {
        int error = VRInput_GetPoseActionDataForNextFrame(poseHandle, ETrackingUniverseOrigin_TrackingUniverseStanding,
            this.poseData, InputPoseActionData.SIZEOF, k_ulInvalidInputValueHandle);

        if (error != EVRInputError_VRInputError_None) {
            throw new RuntimeException("Error reading pose data: " + getInputErrorName(error));
        }
    }

    /**
     * updates the pose and tracking state for the given controller/tracker by its given actionHandle
     *
     * @param controller   controller/tracker to check for new pose
     * @param actionHandle pose handle of the specified controller/tracker
     */
    private void updateControllerPose(int controller, long actionHandle) {
        this.readPoseData(actionHandle);

        if (this.poseData.activeOrigin() != k_ulInvalidInputValueHandle) {
            this.readOriginInfo(this.poseData.activeOrigin());
            int deviceIndex = this.originInfo.trackedDeviceIndex();

            if (this.deviceSource[controller].source != DeviceSource.Source.OPENVR ||
                deviceIndex != this.deviceSource[controller].deviceIndex)
            {
                this.deviceSource[controller].set(DeviceSource.Source.OPENVR, deviceIndex);
                // index changed, re fetch controller transforms
                this.getXforms = true;
            }

            this.deviceSource[controller].deviceIndex = deviceIndex;

            if (deviceIndex != k_unTrackedDeviceIndexInvalid) {
                TrackedDevicePose pose = this.poseData.pose();

                if (pose.bPoseIsValid()) {
                    OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(pose.mDeviceToAbsoluteTracking(),
                        this.poseMatrices[deviceIndex]);
                    HmdVector3 velocity = pose.vVelocity();
                    this.deviceVelocity[deviceIndex].set(
                        velocity.v(0),
                        velocity.v(1),
                        velocity.v(2));
                    this.controllerPose[controller].set(this.poseMatrices[deviceIndex]);
                    this.controllerTracking[controller] = true;
                    // controller is tracking, don't execute the code below
                    return;
                }
            }
        } else {
            this.deviceSource[controller].reset();
        }

        // not tracking
        this.controllerTracking[controller] = false;
    }

    /**
     * updates the pose and tracking state for the given tracker, by its deviceHandle
     *
     * @param controller controller/tracker to check for new pose
     */
    private void updateTrackerPose(int controller, boolean fetchDeviceIndex) {
        if (fetchDeviceIndex) {
            if (readOriginInfoNoError(this.deviceHandle[controller])) {

                int deviceIndex = this.originInfo.trackedDeviceIndex();

                this.deviceSource[controller].set(DeviceSource.Source.OPENVR, deviceIndex);
            } else {
                this.deviceSource[controller].reset();
            }
        }
        if (this.deviceSource[controller].source == DeviceSource.Source.OPENVR &&
            this.deviceSource[controller].deviceIndex != k_unTrackedDeviceIndexInvalid)
        {
            if (VRSystem_IsTrackedDeviceConnected(this.deviceSource[controller].deviceIndex)) {
                this.controllerPose[controller].set(this.poseMatrices[this.deviceSource[controller].deviceIndex]);
                this.controllerTracking[controller] = true;
                // controller is tracking, don't execute the code below
                return;
            }
        }

        // not tracking
        this.deviceSource[controller].reset();
        this.controllerTracking[controller] = false;
    }

    /**
     * tells OpenVR that a new frame started, and gets the device poses for this frame <br>
     * OpenVR caps the fps here
     */
    private void updatePose() {
        // gets poses for all tracked devices from OpenVR
        int error = VRCompositor_WaitGetPoses(this.trackedDevicePoses, null);

        if (error > EVRCompositorError_VRCompositorError_None) {
            VRSettings.LOGGER.error("Vivecraft: Compositor Error: GetPoseError {}",
                OpenVRStereoRenderer.getCompositorError(error));
        }

        if (error == EVRCompositorError_VRCompositorError_DoNotHaveFocus) {
            // this is so dumb, but it works
            // apparently there is/was a bug, that needed that to make the haptics work
            this.triggerHapticPulse(0, 500);
            this.triggerHapticPulse(1, 500);
        }

        if (this.getXforms) {
            // set to null by events.
            // do we want the dynamic info? I don't think so...
            this.getTransforms();
        } else if (this.debugInfo) {
            // print device info once
            this.debugInfo = false;
            this.debugOut(k_unTrackedDeviceIndex_Hmd);
            this.debugOut(this.deviceSource[RIGHT_CONTROLLER].deviceIndex);
            this.debugOut(this.deviceSource[LEFT_CONTROLLER].deviceIndex);
        }

        // eye transforms
        try (MemoryStack stack = MemoryStack.stackPush()) {
            HmdMatrix34 temp = HmdMatrix34.calloc(stack);
            OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(VRSystem_GetEyeToHeadTransform(EVREye_Eye_Left, temp),
                this.hmdPoseLeftEye);
            OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(VRSystem_GetEyeToHeadTransform(EVREye_Eye_Right, temp),
                this.hmdPoseRightEye);
        }

        // copy device poses
        for (int device = 0; device < k_unMaxTrackedDeviceCount; device++) {
            TrackedDevicePose pose = this.trackedDevicePoses.get(device);
            if (pose.bPoseIsValid()) {
                OpenVRUtil.convertSteamVRMatrix3ToMatrix4f(pose.mDeviceToAbsoluteTracking(), this.poseMatrices[device]);
                HmdVector3 velocity = pose.vVelocity();
                this.deviceVelocity[device].set(
                    velocity.v(0),
                    velocity.v(1),
                    velocity.v(2));
            }
        }

        // check headset tracking state
        if (this.trackedDevicePoses.get(k_unTrackedDeviceIndex_Hmd).bPoseIsValid()) {
            this.hmdPose.set(this.poseMatrices[k_unTrackedDeviceIndex_Hmd]);
            this.headIsTracking = true;
        } else {
            this.headIsTracking = false;
            this.hmdPose.identity();
            this.hmdPose.m31(1.62F);
        }

        // ret the complete room eye transforms
        this.hmdPose.mul(this.hmdPoseLeftEye, this.hmdPoseLeftEye);
        this.hmdPose.mul(this.hmdPoseRightEye, this.hmdPoseRightEye);

        // Gotta do this here so we can get the poses
        if (this.inputInitialized) {
            Profiler.get().push("updateActionState");

            // update ActionSets if changed
            if (this.updateActiveActionSets()) {
                int updateError = VRInput_UpdateActionState(this.activeActionSetsBuffer, VRActiveActionSet.SIZEOF);

                if (updateError != EVRInputError_VRInputError_None) {
                    throw new RuntimeException("Error updating action state: code " + getInputErrorName(updateError));
                }
            }

            // read data for all inputActions
            this.inputActions.values().forEach(this::readNewData);

            Profiler.get().pop();

            // controller poses
            if (this.dh.vrSettings.reverseHands) {
                this.updateControllerPose(RIGHT_CONTROLLER, this.leftPoseHandle);
                this.updateControllerPose(LEFT_CONTROLLER, this.rightPoseHandle);
            } else {
                this.updateControllerPose(RIGHT_CONTROLLER, this.rightPoseHandle);
                this.updateControllerPose(LEFT_CONTROLLER, this.leftPoseHandle);
            }

            // camera tracker pose
            this.updateControllerPose(CAMERA_TRACKER, this.cameraPoseHandle);

            // fbt trackers poses
            for (int i = 3; i < MCVR.TRACKABLE_DEVICE_COUNT; i++) {
                // don't clear osc trackers, only if they are not tracking
                if (this.deviceSource[i].source != DeviceSource.Source.OSC ||
                    !this.oscTrackers.trackers[this.deviceSource[i].deviceIndex].isTracking())
                {
                    this.updateTrackerPose(i, !this.usingUnlabeledTrackers);
                }
            }
        }

        this.updateAim();
    }

    /**
     * @param actionSet ActionSet to get the handle for
     * @return handle of the ActionSet
     */
    private long getActionSetHandle(VRInputActionSet actionSet) {
        return this.actionSetHandles.get(actionSet);
    }

    /**
     * @param hand controller to get the handle for
     * @return handle for the controller
     */
    private long getControllerHandle(ControllerType hand) {
        if (this.dh.vrSettings.reverseHands) {
            return this.deviceHandle[hand == ControllerType.RIGHT ? LEFT_CONTROLLER : RIGHT_CONTROLLER];
        } else {
            return this.deviceHandle[hand == ControllerType.RIGHT ? RIGHT_CONTROLLER : LEFT_CONTROLLER];
        }
    }

    /**
     * @param path input path to get the handle for
     * @return handle for the given input path
     * @throws RuntimeException if OpenVR gives an error
     */
    private long getInputSourceHandle(String path) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longRef = stack.callocLong(1);
            int error = VRInput_GetInputSourceHandle(path, longRef);

            if (error != EVRInputError_VRInputError_None) {
                throw new RuntimeException(
                    "Error getting input source handle for '" + path + "': " + getInputErrorName(error));
            } else {
                return longRef.get(0);
            }
        }
    }

    @Override
    public ControllerType getOriginControllerType(long inputValueHandle) {
        if (inputValueHandle != k_ulInvalidInputValueHandle) {
            this.readOriginInfo(inputValueHandle);

            if (this.originInfo.trackedDeviceIndex() != k_unTrackedDeviceIndexInvalid) {
                if (this.deviceSource[RIGHT_CONTROLLER].source == DeviceSource.Source.OPENVR &&
                    this.originInfo.trackedDeviceIndex() == this.deviceSource[RIGHT_CONTROLLER].deviceIndex)
                {
                    return ControllerType.RIGHT;
                } else if (this.deviceSource[LEFT_CONTROLLER].source == DeviceSource.Source.OPENVR &&
                    this.originInfo.trackedDeviceIndex() == this.deviceSource[LEFT_CONTROLLER].deviceIndex)
                {
                    return ControllerType.LEFT;
                }
            }
        }
        return null;
    }

    /**
     * reads input data for the given VRInputAction
     *
     * @param action to read the data for
     */
    private void readNewData(VRInputAction action) {
        switch (action.type) {
            case "boolean" -> {
                if (action.isHanded()) {
                    for (ControllerType type : ControllerType.values()) {
                        this.readDigitalData(action, type);
                    }
                } else {
                    this.readDigitalData(action, null);
                }
            }

            case "vector1", "vector2", "vector3" -> {
                if (action.isHanded()) {
                    for (ControllerType type : ControllerType.values()) {
                        this.readAnalogData(action, type);
                    }
                } else {
                    this.readAnalogData(action, null);
                }
            }
        }
    }

    /**
     * reads digital (on/off) data for the given InputAction
     *
     * @param action InputAction to check
     * @param hand   if specified checks the input on that hand, else it's a global check
     * @throws RuntimeException if OpenVR gives an error
     */
    private void readDigitalData(VRInputAction action, ControllerType hand) {
        int index = hand != null ? hand.ordinal() : RIGHT_CONTROLLER;

        int error = VRInput_GetDigitalActionData(action.handle, this.digital, InputDigitalActionData.SIZEOF,
            hand != null ? this.getControllerHandle(hand) : k_ulInvalidInputValueHandle);

        if (error != EVRInputError_VRInputError_None) {
            throw new RuntimeException(
                "Error reading digital data for '" + action.name + "': " + getInputErrorName(error));
        } else {
            action.digitalData[index].activeOrigin = this.digital.activeOrigin();
            action.digitalData[index].isActive = this.digital.bActive();
            action.digitalData[index].state = this.digital.bState();
            action.digitalData[index].isChanged = this.digital.bChanged();
        }
    }

    /**
     * reads analog (x/y/z axis) data for the given InputAction
     *
     * @param action InputAction to check
     * @param hand   if specified checks the input on that hand, else it's a global check
     * @throws RuntimeException if OpenVR gives an error
     */
    private void readAnalogData(VRInputAction action, ControllerType hand) {
        int index = hand != null ? hand.ordinal() : RIGHT_CONTROLLER;

        int error = VRInput_GetAnalogActionData(action.handle, this.analog, InputAnalogActionData.SIZEOF,
            hand != null ? this.getControllerHandle(hand) : k_ulInvalidInputValueHandle);

        if (error != EVRInputError_VRInputError_None) {
            throw new RuntimeException(
                "Error reading analog data for '" + action.name + "': " + getInputErrorName(error));
        } else {
            action.analogData[index].x = this.analog.x();
            action.analogData[index].y = this.analog.y();
            action.analogData[index].z = this.analog.z();

            action.analogData[index].deltaX = this.analog.deltaX();
            action.analogData[index].deltaY = this.analog.deltaY();
            action.analogData[index].deltaZ = this.analog.deltaZ();

            action.analogData[index].activeOrigin = this.analog.activeOrigin();
            action.analogData[index].isActive = this.analog.bActive();
        }
    }

    @Override
    public void calibrateFBT(float headsetYaw) {
        // reset device indices
        for (int i = 3; i < MCVR.TRACKABLE_DEVICE_COUNT; i++) {
            this.updateTrackerPose(i, true);
        }
        super.calibrateFBT(headsetYaw);
    }

    /**
     * @return List containing all device indices that correspond to a tracker
     */
    private List<Integer> getTrackerIds() {
        List<Integer> trackers = new ArrayList<>();
        for (int device = 0; device < k_unMaxTrackedDeviceCount; device++) {
            if (VRSystem_IsTrackedDeviceConnected(device) &&
                VRSystem_GetTrackedDeviceClass(device) ==
                    ETrackedDeviceClass_TrackedDeviceClass_GenericTracker)
            {
                trackers.add(device);
            }
        }
        return trackers;
    }

    @Override
    public List<Triple<DeviceSource, Integer, Matrix4fc>> getTrackers() {
        List<Triple<DeviceSource, Integer, Matrix4fc>> trackers = super.getTrackers();
        List<Integer> ovrTrackers = getTrackerIds();
        for (int tracker : ovrTrackers) {
            int type = -1;
            // check if we already know the role of the tracker
            for (int i = 0; i < TRACKABLE_DEVICE_COUNT; i++) {
                if (this.deviceSource[i].is(DeviceSource.Source.OPENVR, tracker)) {
                    type = i;
                }
            }
            // super already adds the assigned trackers
            if (trackers.stream().noneMatch(t -> t.getLeft().is(DeviceSource.Source.OPENVR, tracker))) {
                trackers.add(
                    Triple.of(new DeviceSource(DeviceSource.Source.OPENVR, tracker), type, this.poseMatrices[tracker]));
            }
        }
        return trackers;
    }

    @Override
    public List<Long> getOrigins(VRInputAction action) {
        List<Long> list = new ArrayList<>();
        if (OpenVR.VRInput != null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer longRef = stack.callocLong(16);
                int error = VRInput_GetActionOrigins(this.getActionSetHandle(action.actionSet), action.handle, longRef);

                if (error != 0) {
                    throw new RuntimeException(
                        "Error getting action origins for '" + action.name + "': " + getInputErrorName(error));
                } else {

                    while (longRef.remaining() > 0) {
                        long handle = longRef.get();
                        if (handle != k_ulInvalidActionHandle) {
                            list.add(handle);
                        }
                    }
                }
            }
        }
        return list;
    }

    @Override
    public String getOriginName(long origin) {
        if (OpenVR.VRInput == null) {
            return "";
        } else {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer str = stack.calloc(32768);
                // omit controller type
                int error = VRInput_GetOriginLocalizedName(origin, str,
                    EVRInputStringBits_VRInputString_Hand | EVRInputStringBits_VRInputString_InputSource);

                if (error != 0) {
                    throw new RuntimeException("Error getting origin name: " + getInputErrorName(error));
                } else {
                    return memUTF8NullTerminated(str);
                }
            }
        }
    }

    @Override
    public VRRenderer createVRRenderer() {
        return new OpenVRStereoRenderer(this);
    }

    @Override
    public boolean isActive() {
        int activityLevel = VRSystem_GetTrackedDeviceActivityLevel(k_unTrackedDeviceIndex_Hmd);
        return activityLevel == EDeviceActivityLevel_k_EDeviceActivityLevel_UserInteraction ||
            activityLevel == EDeviceActivityLevel_k_EDeviceActivityLevel_UserInteraction_Timeout;
    }

    @Override
    public float getIPD() {
        return VRSystem_GetFloatTrackedDeviceProperty(k_unTrackedDeviceIndex_Hmd,
            ETrackedDeviceProperty_Prop_UserIpdMeters_Float, this.errorBuffer);
    }

    /**
     * this should query the actual name from the runtime, but OpenVR doesn't seem to have an api for that
     */
    @Override
    public String getRuntimeName() {
        return "SteamVR";
    }
}
