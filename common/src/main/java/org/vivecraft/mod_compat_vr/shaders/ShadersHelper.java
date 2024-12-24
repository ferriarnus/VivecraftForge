package org.vivecraft.mod_compat_vr.shaders;

import org.vivecraft.mod_compat_vr.iris.IrisHelper;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;

/**
 * helper to wrap general shader related task in one class, independent if running Optifine or iris
 */
public class ShadersHelper {

    /**
     * gets the minimum light to apply to hand/gui, depending on if shaders are active or not
     * @return minimum light to apply
     */
    public static int ShaderLight() {
        return isShaderActive() ? 8 : 4;
    }

    /**
     * @return if a shaderpack is active
     */
    public static boolean isShaderActive() {
        return (IrisHelper.isLoaded() && IrisHelper.isShaderActive()) ||
            (OptifineHelper.isOptifineLoaded() && OptifineHelper.isShaderActive());
    }

    /**
     * @return if the current shader implementation needs the same buffer sizes for all passes
     */
    public static boolean needsSameSizeBuffers() {
        return OptifineHelper.isOptifineLoaded() && OptifineHelper.isShaderActive();
    }

    /**
     * reloads shaders, if the shader implementation needs it
     */
    public static void maybeReloadShaders() {
        if (IrisHelper.isLoaded()) {
            IrisHelper.reload();
        }
    }
}
