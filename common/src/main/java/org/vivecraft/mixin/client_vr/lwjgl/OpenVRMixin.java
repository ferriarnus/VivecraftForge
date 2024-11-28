package org.vivecraft.mixin.client_vr.lwjgl;

import org.lwjgl.openvr.OpenVR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;
import java.util.function.LongFunction;

import static org.lwjgl.openvr.VR.*;

@Mixin(value = OpenVR.class, remap = false)
public abstract class OpenVRMixin {

    @Shadow
    @Nullable
    public static OpenVR.IVRSystem VRSystem;
    @Shadow
    @Nullable
    public static OpenVR.IVRChaperone VRChaperone;
    @Shadow
    @Nullable
    public static OpenVR.IVRChaperoneSetup VRChaperoneSetup;
    @Shadow
    @Nullable
    public static OpenVR.IVRCompositor VRCompositor;
    @Shadow
    @Nullable
    public static OpenVR.IVROverlay VROverlay;
    @Shadow
    @Nullable
    public static OpenVR.IVRRenderModels VRRenderModels;
    @Shadow
    @Nullable
    public static OpenVR.IVRExtendedDisplay VRExtendedDisplay;
    @Shadow
    @Nullable
    public static OpenVR.IVRSettings VRSettings;
    @Shadow
    @Nullable
    public static OpenVR.IVRApplications VRApplications;
    @Shadow
    @Nullable
    public static OpenVR.IVRScreenshots VRScreenshots;
    @Shadow
    @Nullable
    public static OpenVR.IVRInput VRInput;
    @Shadow
    private static int token;

    @Shadow
    @Nullable
    private static <T> T getGenericInterface(String interfaceNameVersion, LongFunction<T> supplier) {
        return null;
    }

    /**
     * @author thejudge156 / The Judge
     * @reason Open Composite doesn't implement all function tables, so trying to assign them will make it crash
     */
    @Overwrite
    public static void create(int tok) {
        token = tok;

        VRSystem = getGenericInterface(IVRSystem_Version, OpenVR.IVRSystem::new);
        VRChaperone = getGenericInterface(IVRChaperone_Version, OpenVR.IVRChaperone::new);
        VRChaperoneSetup = getGenericInterface(IVRChaperoneSetup_Version, OpenVR.IVRChaperoneSetup::new);
        VRCompositor = getGenericInterface(IVRCompositor_Version, OpenVR.IVRCompositor::new);
        if (VRCompositor == null) {
            // this happens when using an outdated Steamvr, IVRCompositor_026 seems to be binary compatible to IVRCompositor_027 so try to load that
            org.vivecraft.client_vr.settings.VRSettings.LOGGER.error("Vivecraft: openvr '{}' failed to load, trying IVRCompositor_026", IVRCompositor_Version);
            VRCompositor = getGenericInterface("IVRCompositor_026", OpenVR.IVRCompositor::new);
        }
        VROverlay = getGenericInterface(IVROverlay_Version, OpenVR.IVROverlay::new);
        // VRResources = getGenericInterface(IVRResources_Version, OpenVR.IVRResources::new);
        VRRenderModels = getGenericInterface(IVRRenderModels_Version, OpenVR.IVRRenderModels::new);
        VRExtendedDisplay = getGenericInterface(IVRExtendedDisplay_Version, OpenVR.IVRExtendedDisplay::new);
        VRSettings = getGenericInterface(IVRSettings_Version, OpenVR.IVRSettings::new);
        VRApplications = getGenericInterface(IVRApplications_Version, OpenVR.IVRApplications::new);
        // VRTrackedCamera = getGenericInterface(IVRTrackedCamera_Version, OpenVR.IVRTrackedCamera::new);
        VRScreenshots = getGenericInterface(IVRScreenshots_Version, OpenVR.IVRScreenshots::new);
        // VRDriverManager = getGenericInterface(IVRDriverManager_Version, OpenVR.IVRDriverManager::new);
        VRInput = getGenericInterface(IVRInput_Version, OpenVR.IVRInput::new);
        // VRIOBuffer = getGenericInterface(IVRIOBuffer_Version, OpenVR.IVRIOBuffer::new);
        // VRSpatialAnchors = getGenericInterface(IVRSpatialAnchors_Version, OpenVR.IVRSpatialAnchors::new);
        // VRDebug = getGenericInterface(IVRDebug_Version, OpenVR.IVRDebug::new);
        // VRNotifications = getGenericInterface(IVRNotifications_Version, OpenVR.IVRNotifications::new);
    }
}
