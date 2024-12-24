package org.vivecraft.mod_compat_vr.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.mod_compat_vr.shaders.ShaderPatcher;

@Pseudo
@Mixin(targets = {
    "net.coderbot.iris.shaderpack.preprocessor.JcppProcessor",
    "net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor"
})
public class JcppProcessorVRMixin {
    @ModifyVariable(method = "glslPreprocessSource", at = @At("HEAD"), ordinal = 0, argsOnly = true, remap = false)
    private static String vivecraft$patchShader0(String oldShader) {
        if (VRState.VR_INITIALIZED) {
            oldShader = ShaderPatcher.patchShader(oldShader);
        }
        return oldShader;
    }

}
