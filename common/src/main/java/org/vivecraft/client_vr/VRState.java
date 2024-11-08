package org.vivecraft.client_vr;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import org.vivecraft.client.Xplat;
import org.vivecraft.client.gui.screens.ErrorScreen;
import org.vivecraft.client.gui.screens.GarbageCollectorScreen;
import org.vivecraft.client.utils.TextUtils;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.menuworlds.MenuWorldRenderer;
import org.vivecraft.client_vr.provider.nullvr.NullVR;
import org.vivecraft.client_vr.provider.openvr_lwjgl.MCOpenVR;
import org.vivecraft.client_vr.render.RenderConfigException;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassManager;
import org.vivecraft.mod_compat_vr.ShadersHelper;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;

/**
 * this class holds the current VR states and handles starting and stopping VR
 */
public class VRState {

    /**
     * true when VR is enabled
     */
    public static boolean VR_ENABLED = false;
    /**
     * true when VR is enabled, and successfully initialized
     */
    public static boolean VR_INITIALIZED = false;
    /**
     * true when VR is enabled, successfully initialized and currently active
     */
    public static boolean VR_RUNNING = false;

    public static void initializeVR() {
        if (VR_INITIALIZED) {
            return;
        }
        try {
            if (OptifineHelper.isOptifineLoaded() && OptifineHelper.isAntialiasing()) {
                throw new RenderConfigException(
                    Component.translatable("vivecraft.messages.incompatiblesettings"),
                    Component.translatable("vivecraft.messages.optifineaa"));
            }

            ClientDataHolderVR dh = ClientDataHolderVR.getInstance();
            if (dh.vrSettings.stereoProviderPluginID == VRSettings.VRProvider.OPENVR) {
                dh.vr = new MCOpenVR(Minecraft.getInstance(), dh);
            } else {
                dh.vr = new NullVR(Minecraft.getInstance(), dh);
            }
            if (!dh.vr.init()) {
                throw new RenderConfigException(Component.translatable("vivecraft.messages.vriniterror"),
                    Component.translatable("vivecraft.messages.rendersetupfailed", dh.vr.initStatus, dh.vr.getName()));
            }

            dh.vrRenderer = dh.vr.createVRRenderer();

            // everything related to VR is created now
            VR_INITIALIZED = true;

            dh.vrRenderer.setupRenderConfiguration();
            RenderPassManager.setVanillaRenderPass();

            dh.vrPlayer = new VRPlayer();
            dh.vrPlayer.registerTracker(dh.backpackTracker);
            dh.vrPlayer.registerTracker(dh.bowTracker);
            dh.vrPlayer.registerTracker(dh.climbTracker);
            dh.vrPlayer.registerTracker(dh.autoFood);
            dh.vrPlayer.registerTracker(dh.jumpTracker);
            dh.vrPlayer.registerTracker(dh.rowTracker);
            dh.vrPlayer.registerTracker(dh.runTracker);
            dh.vrPlayer.registerTracker(dh.sneakTracker);
            dh.vrPlayer.registerTracker(dh.swimTracker);
            dh.vrPlayer.registerTracker(dh.swingTracker);
            dh.vrPlayer.registerTracker(dh.interactTracker);
            dh.vrPlayer.registerTracker(dh.teleportTracker);
            dh.vrPlayer.registerTracker(dh.horseTracker);
            dh.vrPlayer.registerTracker(dh.vehicleTracker);
            dh.vrPlayer.registerTracker(dh.crawlTracker);
            dh.vrPlayer.registerTracker(dh.cameraTracker);
            dh.vrPlayer.registerTracker(dh.fbtCalibrationTracker);

            dh.menuWorldRenderer = new MenuWorldRenderer();

            dh.menuWorldRenderer.init();

            try {
                String garbageCollector = StringUtils.getCommonPrefix(ManagementFactory.getGarbageCollectorMXBeans().stream().map(MemoryManagerMXBean::getName).toArray(String[]::new)).trim();
                if (garbageCollector.isEmpty()) {
                    garbageCollector = ManagementFactory.getGarbageCollectorMXBeans().get(0).getName();
                }
                VRSettings.LOGGER.info("Vivecraft: Garbage collector: {}", garbageCollector);

                // Fully qualified name here to avoid any ambiguity
                com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                // Might as well log this stuff since we have it, could be useful for technical support
                VRSettings.LOGGER.info("Vivecraft: Available CPU threads: {}", Runtime.getRuntime().availableProcessors());
                VRSettings.LOGGER.info("Vivecraft: Total physical memory: {} GiB", String.format("%.01f", os.getTotalMemorySize() / 1073741824.0F));
                VRSettings.LOGGER.info("Vivecraft: Free physical memory: {} GiB", String.format("%.01f", os.getFreeMemorySize() / 1073741824.0F));

                if (!garbageCollector.startsWith("ZGC") && !ClientDataHolderVR.getInstance().vrSettings.disableGarbageCollectorMessage) {
                    // At least 12 GiB RAM (minus 256 MiB for possible reserved) and 8 CPU threads
                    if (os.getTotalMemorySize() >= 1073741824L * 12L - 1048576L * 256L && Runtime.getRuntime().availableProcessors() >= 6) {
                        // store the garbage collector, as indicator, that the GarbageCollectorScreen should be shown, if it would be discarded
                        dh.incorrectGarbageCollector = garbageCollector;
                        Minecraft.getInstance().setScreen(new GarbageCollectorScreen(garbageCollector));
                    }
                }
            } catch (Throwable e) {
                VRSettings.LOGGER.error("Vivecraft: Failed checking GC: ", e);
            }
        } catch (Throwable exception) {
            VRSettings.LOGGER.error("Vivecraft: Failed to initialize VR: ", exception);
            destroyVR(true);
            if (exception instanceof RenderConfigException renderConfigException) {
                Minecraft.getInstance()
                    .setScreen(new ErrorScreen(renderConfigException.title, renderConfigException.error));
            } else {
                Minecraft.getInstance()
                    .setScreen(new ErrorScreen(Component.translatable("vivecraft.messages.vriniterror"),
                        TextUtils.throwableToComponent(exception)));
            }
        }
    }

    public static void destroyVR(boolean disableVRSetting) {
        ClientDataHolderVR dh = ClientDataHolderVR.getInstance();
        if (dh.vr != null) {
            dh.vr.destroy();
        }
        dh.vr = null;
        dh.vrPlayer = null;
        if (dh.vrRenderer != null) {
            dh.vrRenderer.destroy();
            dh.vrRenderer = null;
        }
        if (dh.menuWorldRenderer != null) {
            dh.menuWorldRenderer.completeDestroy();
            dh.menuWorldRenderer = null;
        }
        VR_ENABLED = false;
        VR_INITIALIZED = false;
        VR_RUNNING = false;
        if (disableVRSetting) {
            dh.vrSettings.vrEnabled = false;
            dh.vrSettings.saveOptions();
        }
        // fixes an issue with DH shaders where the depth texture gets stuck
        if (Xplat.isModLoaded("distanthorizons") && disableVRSetting) {
            ShadersHelper.maybeReloadShaders();
        }
    }
}
