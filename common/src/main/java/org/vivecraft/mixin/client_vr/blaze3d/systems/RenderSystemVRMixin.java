package org.vivecraft.mixin.client_vr.blaze3d.systems;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.provider.MCVR;

@Mixin(RenderSystem.class)
public class RenderSystemVRMixin {

    @ModifyArg(method = "defaultBlendFunc", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;blendFuncSeparate(Lcom/mojang/blaze3d/platform/GlStateManager$SourceFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DestFactor;Lcom/mojang/blaze3d/platform/GlStateManager$SourceFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DestFactor;)V", remap = true), remap = false, index = 3)
    private static GlStateManager.DestFactor vivecraft$defaultBlendFuncAlphaBlending(GlStateManager.DestFactor destFactor) {
        return GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA;
    }

    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true, remap = false)
    private static void vivecraft$noFPSlimit(CallbackInfo ci) {
        if (VRState.VR_RUNNING && !MCVR.get().capFPS()) {
            ci.cancel();
        }
    }
}
