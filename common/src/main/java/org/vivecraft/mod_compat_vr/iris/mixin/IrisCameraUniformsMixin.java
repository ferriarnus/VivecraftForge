package org.vivecraft.mod_compat_vr.iris.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.client_xr.render_pass.RenderPassType;

@Pseudo
@Mixin(targets = {
    "net.coderbot.iris.uniforms.CameraUniforms",
    "net.irisshaders.iris.uniforms.CameraUniforms"
})
public class IrisCameraUniformsMixin {
    // we change the near plane to be a bit shorter
    @ModifyExpressionValue(method = "lambda$addCameraUniforms$0", at = @At(value = "CONSTANT", args = "doubleValue=0.05"), remap = false)
    private static double vivecraft$nearPlane(double original) {
        // see GameRendererVRMixin#vivecraft$MIN_CLIP_DISTANCE
        return RenderPassType.isVanilla() ? original : 0.02;
    }
}
