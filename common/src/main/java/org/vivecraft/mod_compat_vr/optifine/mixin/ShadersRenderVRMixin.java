package org.vivecraft.mod_compat_vr.optifine.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_xr.render_pass.RenderPassType;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;

@Pseudo
@Mixin(targets = "net.optifine.shaders.ShadersRender")
public class ShadersRenderVRMixin {

    @Shadow(remap = false)
    public static void updateActiveRenderInfo(Camera activeRenderInfo, Minecraft mc, float partialTick) {}

    @Inject(method = "renderHand0", at = @At("HEAD"), remap = false, cancellable = true)
    private static void vivecraft$noTranslucentHandsInVR(CallbackInfo ci) {
        if (!RenderPassType.isVanilla()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHand1", at = @At("HEAD"), remap = false, cancellable = true)
    private static void vivecraft$noSolidHandsInVR(CallbackInfo ci) {
        if (!RenderPassType.isVanilla()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderShadowMap", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getInstance()Lnet/minecraft/client/Minecraft;", remap = true), remap = false, cancellable = true)
    private static void vivecraft$shadowsOnlyOnce(
        GameRenderer gameRenderer, Camera activeRenderInfo, int pass, float partialTick, long finishTimeNano,
        CallbackInfo ci)
    {
        if (!RenderPassType.isVanilla() && ClientDataHolderVR.getInstance().currentPass != RenderPass.LEFT &&
            !ClientDataHolderVR.getInstance().vrSettings.disableShaderOptimization)
        {
            updateActiveRenderInfo(activeRenderInfo, Minecraft.getInstance(), partialTick);
            OptifineHelper.setCameraShadow(new PoseStack(), activeRenderInfo, partialTick);
            ci.cancel();
        }
    }
}
