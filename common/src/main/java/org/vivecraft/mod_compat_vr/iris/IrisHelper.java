package org.vivecraft.mod_compat_vr.iris;

import net.irisshaders.iris.api.v0.IrisApi;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.vivecraft.client.Xplat;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

public class IrisHelper {

    private static boolean INITIALIZED = false;
    private static boolean INIT_FAILED = false;

    private static Method Iris_reload;
    private static Method Iris_getPipelineManager;
    private static Method PipelineManager_getPipeline;
    private static Method WorldRenderingPipeline_shouldRenderUnderwaterOverlay;

    // for iris/dh compat
    private static boolean DH_PRESENT = false;
    private static Object dhOverrideInjector;
    private static Method OverrideInjector_unbind;

    private static Class<?> IDhApiFramebuffer;
    private static Method Pipeline_getDHCompat;
    private static Method DHCompatInternal_getInstance;
    private static Method DHCompatInternal_getShadowFBWrapper;
    private static Method DHCompatInternal_getSolidFBWrapper;

    // DH 2.2+
    private static Class<?> IDhApiGenericObjectShaderProgram;
    private static Method DHCompatInternal_getGenericShader;

    private static Method CapturedRenderingState_getGbufferProjection;

    public static boolean isLoaded() {
        return Xplat.isModLoaded("iris") || Xplat.isModLoaded("oculus");
    }

    /**
     * @return if a shaderpack is in use
     */
    public static boolean isShaderActive() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    /**
     * @return if a shaderpack is in use
     */
    public static boolean isRenderingShadows() {
        return IrisApi.getInstance().isRenderingShadowPass();
    }

    /**
     * enabled or disables shaders
     * @param enabled if shaders should be on or off
     */
    public static void setShadersActive(boolean enabled) {
        IrisApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);
    }

    /**
     * triggers a shader reload
     */
    public static void reload() {
        RenderPassManager.setVanillaRenderPass();
        if (init()) {
            try {
                // Iris.reload();
                Iris_reload.invoke(null);
            } catch (Exception e) {
                // catch Exception, because that call can throw an IOException
                VRSettings.LOGGER.error("Vivecraft: Error reloading Iris shaders on Frame Buffer reinit:", e);
            }
        }
    }

    /**
     * @return if the active shader has the vanilla water overlay enabled or disabled
     */
    public static boolean hasWaterEffect() {
        if (init()) {
            try {
                // Iris.getPipelineManager().getPipeline().map(WorldRenderingPipeline::shouldRenderUnderwaterOverlay).orElse(true);
                return (boolean) ((Optional<?>) PipelineManager_getPipeline.invoke(Iris_getPipelineManager.invoke(null))).map(o -> {
                    try {
                        return WorldRenderingPipeline_shouldRenderUnderwaterOverlay.invoke(o);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        VRSettings.LOGGER.error("Vivecraft: Iris water effect check failed:", e);
                        return true;
                    }
                }).orElse(true);
            } catch (InvocationTargetException | IllegalAccessException e) {
                VRSettings.LOGGER.error("Vivecraft: Iris water effect check failed:", e);
            }
        }
        return true;
    }

    /**
     * removes the DH overrides from the given {@code pipeline}
     * this is here, because iris doesn't do that on pipeline changes
     * @param pipeline Rendering pileple to uinregister the overrides for
     */
    public static void unregisterDHIfThere(Object pipeline) {
        if (init() && DH_PRESENT) {
            try {
                Object dhCompat = Pipeline_getDHCompat.invoke(pipeline);
                // check if the shader even has a dh part
                if (dhCompat != null) {
                    Object dhCompatInstance = DHCompatInternal_getInstance.invoke(dhCompat);
                    if (dhCompatInstance != null) {
                        // now disable the overrides
                        OverrideInjector_unbind.invoke(dhOverrideInjector, IDhApiFramebuffer, DHCompatInternal_getShadowFBWrapper.invoke(dhCompatInstance));
                        OverrideInjector_unbind.invoke(dhOverrideInjector, IDhApiFramebuffer, DHCompatInternal_getSolidFBWrapper.invoke(dhCompatInstance));
                        // generic override for DH 2.2+
                        if (DHCompatInternal_getGenericShader != null) {
                            OverrideInjector_unbind.invoke(dhOverrideInjector, IDhApiGenericObjectShaderProgram, DHCompatInternal_getGenericShader.invoke(dhCompatInstance));
                        }
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                VRSettings.LOGGER.error("Vivecraft: Iris DH reset failed", e);
            }
        }
    }

    /**
     * needed, because some Iris versions return a Matrix4f and others a Matrix4fc, which causes a runtime exception
     * @param source CapturedRenderingState INSTANCE to call this on
     * @return Matrix4fc current projection matrix
     */
    public static Matrix4fc getGbufferProjection(Object source) {
        if (init() && DH_PRESENT) {
            try {
                return (Matrix4fc) CapturedRenderingState_getGbufferProjection.invoke(source);
            } catch (IllegalAccessException | InvocationTargetException e) {
                VRSettings.LOGGER.error("Vivecraft: couldn't get iris gbuffer projection matrix:", e);
            }
        }
        return new Matrix4f();
    }

    /**
     * initializes all Reflections
     * @return if init was successful
     */
    private static boolean init() {
        if (INITIALIZED) {
            return !INIT_FAILED;
        }
        try {
            Class<?> iris = getClassWithAlternative(
                "net.coderbot.iris.Iris",
                "net.irisshaders.iris.Iris");
            Iris_reload = iris.getMethod("reload");
            Iris_getPipelineManager = iris.getMethod("getPipelineManager");

            Class<?> pipelineManager = getClassWithAlternative(
                "net.coderbot.iris.pipeline.PipelineManager",
                "net.irisshaders.iris.pipeline.PipelineManager");

            PipelineManager_getPipeline = pipelineManager.getMethod("getPipeline");

            Class<?> worldRenderingPipeline = getClassWithAlternative(
                "net.coderbot.iris.pipeline.WorldRenderingPipeline",
                "net.irisshaders.iris.pipeline.WorldRenderingPipeline");

            WorldRenderingPipeline_shouldRenderUnderwaterOverlay = worldRenderingPipeline.getMethod("shouldRenderUnderwaterOverlay");

            // distant horizon compat
            if (Xplat.isModLoaded("distanthorizons")) {
                try {
                    Class<?> OverrideInjector = Class.forName("com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector");
                    dhOverrideInjector = OverrideInjector.getDeclaredField("INSTANCE").get(null);

                    OverrideInjector_unbind = OverrideInjector.getMethod("unbind", Class.class, Class.forName("com.seibel.distanthorizons.api.interfaces.override.IDhApiOverrideable"));

                    IDhApiFramebuffer = Class.forName("com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer");

                    Pipeline_getDHCompat = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline").getMethod("getDHCompat");

                    DHCompatInternal_getInstance = Class.forName("net.irisshaders.iris.compat.dh.DHCompat").getMethod("getInstance");
                    Class<?> DHCompatInternal = Class.forName("net.irisshaders.iris.compat.dh.DHCompatInternal");
                    DHCompatInternal_getShadowFBWrapper = DHCompatInternal.getMethod("getShadowFBWrapper");
                    DHCompatInternal_getSolidFBWrapper = DHCompatInternal.getMethod("getSolidFBWrapper");

                    // DH 2.2+
                    try {
                        IDhApiGenericObjectShaderProgram = Class.forName("com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram");
                        DHCompatInternal_getGenericShader = DHCompatInternal.getMethod("getGenericShader");
                    } catch (ClassNotFoundException | NoSuchMethodException ignored) {}

                    Class<?> CapturedRenderingState = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
                    CapturedRenderingState_getGbufferProjection = CapturedRenderingState.getMethod("getGbufferProjection");
                    DH_PRESENT = true;
                } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | IllegalAccessException e) {
                    VRSettings.LOGGER.error("Vivecraft: DH present but compat init failed:", e);
                    DH_PRESENT = false;
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            INIT_FAILED = true;
        }

        INITIALIZED = true;
        return !INIT_FAILED;
    }

    /**
     * does a class Lookup with an alternative, for convenience, since iris changed packages
     * @param class1 first option
     * @param class2 alternative option
     * @return found class
     * @throws ClassNotFoundException if neither class exists
     */
    private static Class<?> getClassWithAlternative(String class1, String class2) throws ClassNotFoundException {
        try {
            return Class.forName(class1);
        } catch (ClassNotFoundException e) {
            return Class.forName(class2);
        }
    }
}
