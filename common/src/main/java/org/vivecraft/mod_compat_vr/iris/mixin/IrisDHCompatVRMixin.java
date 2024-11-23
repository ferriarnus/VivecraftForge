package org.vivecraft.mod_compat_vr.iris.mixin;

import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_xr.render_pass.RenderPassType;
import org.vivecraft.mod_compat_vr.iris.IrisHelper;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.compat.dh.DHCompat")
public class IrisDHCompatVRMixin {
    @Inject(at = @At(value = "RETURN", ordinal = 1), method = "getProjection", remap = false, cancellable = true)
    private static void vivecraft$correctProjection(CallbackInfoReturnable<Matrix4f> cir) {
        if (!RenderPassType.isVanilla()) {
            if (ClientDataHolderVR.getInstance().currentPass == RenderPass.LEFT ||
                ClientDataHolderVR.getInstance().currentPass == RenderPass.RIGHT) {
                // VR projections are not centered

                Matrix4fc vrProjection = IrisHelper.getGbufferProjection(CapturedRenderingState.INSTANCE);
                Matrix4f dhProjection = cir.getReturnValue();

                dhProjection.m00(vrProjection.m00());
                dhProjection.m11(vrProjection.m11());
                dhProjection.m20(vrProjection.m20());
                dhProjection.m21(vrProjection.m21());
                cir.setReturnValue(dhProjection);
            }
        }
    }
}
