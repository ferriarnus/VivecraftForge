package org.vivecraft.mod_compat_vr.iris.mixin;

import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_xr.render_pass.RenderPassType;
import org.vivecraft.mod_compat_vr.iris.IrisHelper;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.compat.dh.IrisLodRenderProgram")
public class IrisLodRenderProgramVRMixin {

    @Group(name = "projection adjust", min = 1, max = 1)
    @ModifyVariable(at = @At("HEAD"), method = "fillUniformData", ordinal = 0, argsOnly = true, remap = false, expect = 0, require = 0)
    private Matrix4fc vivecraft$correctProjectionMat4C(Matrix4fc dhProjection) {
        if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            return vivecraft$correctProjection(dhProjection);
        } else {
            return dhProjection;
        }
    }

    @Group(name = "projection adjust", min = 1, max = 1)
    @ModifyVariable(at = @At("HEAD"), method = "fillUniformData", ordinal = 0, argsOnly = true, remap = false, expect = 0, require = 0)
    private Matrix4f vivecraft$correctProjectionMat4(Matrix4f dhProjection) {
        if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            // safe to cast since this always returns a Matrix4f when a Matrix4f is put in
            return (Matrix4f) vivecraft$correctProjection(dhProjection);
        } else {
            return dhProjection;
        }
    }

    @Unique
    private Matrix4fc vivecraft$correctProjection(Matrix4fc dhProjection) {
        if (!RenderPassType.isVanilla()) {
            if (ClientDataHolderVR.getInstance().currentPass == RenderPass.LEFT ||
                ClientDataHolderVR.getInstance().currentPass == RenderPass.RIGHT) {
                // VR projections are not centered

                Matrix4fc vrProjection = IrisHelper.getGbufferProjection(CapturedRenderingState.INSTANCE);

                Matrix4f dhProjectionMutable = new Matrix4f(dhProjection);

                dhProjectionMutable.m00(vrProjection.m00());
                dhProjectionMutable.m11(vrProjection.m11());
                dhProjectionMutable.m20(vrProjection.m20());
                dhProjectionMutable.m21(vrProjection.m21());
                return dhProjectionMutable;
            }
        }
        return dhProjection;
    }
}
