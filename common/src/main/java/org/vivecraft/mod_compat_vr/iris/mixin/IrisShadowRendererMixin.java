package org.vivecraft.mod_compat_vr.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_xr.render_pass.RenderPassType;
import org.vivecraft.mod_compat_vr.iris.IrisHelper;

@Pseudo
@Mixin(targets = {
    "net.coderbot.iris.pipeline.ShadowRenderer",
    "net.irisshaders.iris.shadows.ShadowRenderer"
})
public class IrisShadowRendererMixin {

    // only render shadows on the first RenderPass
    // cancel them here, or we would also cancel prepare shaders
    @Inject(method = "renderShadows", at = @At("HEAD"), cancellable = true, remap = false)
    private void vivecraft$onlyOneShadow(CallbackInfo ci) {
        if (!RenderPassType.isVanilla() && !ClientDataHolderVR.getInstance().isFirstPass && !IrisHelper.SLOW_MODE) {
            ci.cancel();
        }
    }
}
