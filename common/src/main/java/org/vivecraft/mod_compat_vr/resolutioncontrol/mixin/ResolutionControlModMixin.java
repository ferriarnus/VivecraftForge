package org.vivecraft.mod_compat_vr.resolutioncontrol.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_xr.render_pass.RenderPassType;
import org.vivecraft.client_xr.render_pass.WorldRenderPass;

@Pseudo
@Mixin(targets = {
    "cc.flawcra.resolutioncontrol.ResolutionControlMod ",
    "io.github.ultimateboomer.resolutioncontrol.ResolutionControlMod"})
public class ResolutionControlModMixin {

    @Inject(method = "setShouldScale", at = @At("HEAD"), remap = false, cancellable = true)
    private void vivecraft$dontResizeGUI(CallbackInfo ci) {
        // we handle the resize ourselves
        if (!RenderPassType.isVanilla()) {
            ci.cancel();
        }
    }

    @Inject(method = "updateFramebufferSize", at = @At("HEAD"), remap = false)
    private void vivecraft$resizeVRBuffers(CallbackInfo ci) {
        if (VRState.VR_INITIALIZED) {
            ClientDataHolderVR.getInstance().vrRenderer.resizeFrameBuffers("");
        }
    }

    @Inject(method = "getCurrentWidth", at = @At("HEAD"), remap = false, cancellable = true)
    private void vivecraft$getVRWidth(CallbackInfoReturnable<Integer> cir) {
        if (VRState.VR_RUNNING) {
            cir.setReturnValue(WorldRenderPass.STEREO_XR.target.width);
        }
    }

    @Inject(method = "getCurrentHeight", at = @At("HEAD"), remap = false, cancellable = true)
    private void vivecraft$getVRHeight(CallbackInfoReturnable<Integer> cir) {
        if (VRState.VR_RUNNING) {
            cir.setReturnValue(WorldRenderPass.STEREO_XR.target.height);
        }
    }
}
