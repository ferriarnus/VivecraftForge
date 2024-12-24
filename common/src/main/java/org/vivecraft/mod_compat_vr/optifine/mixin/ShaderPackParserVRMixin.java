package org.vivecraft.mod_compat_vr.optifine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.optifine.util.LineBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.mod_compat_vr.shaders.ShaderPatcher;

@Pseudo
@Mixin(targets = "net.optifine.shaders.config.ShaderPackParser")
public class ShaderPackParserVRMixin {
    @WrapOperation(method = "loadShader", at = @At(value = "INVOKE", target = "Lnet/optifine/shaders/config/ShaderPackParser;addMacros(Lnet/optifine/util/LineBuffer;I)Lnet/optifine/util/LineBuffer;"), remap = false)
    private static LineBuffer vivecraft$patchShader(LineBuffer reader, int index, Operation<LineBuffer> original) {
        if (VRState.VR_INITIALIZED) {
            String patched = ShaderPatcher.patchShader(reader.toString());
            reader = new LineBuffer(patched.split("\n"));
        }
        return original.call(reader, index);
    }
}
