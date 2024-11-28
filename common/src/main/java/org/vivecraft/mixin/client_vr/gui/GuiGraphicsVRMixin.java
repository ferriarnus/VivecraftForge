package org.vivecraft.mixin.client_vr.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.VRState;

@Mixin(GuiGraphics.class)
public class GuiGraphicsVRMixin {

    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;enableBlend()V", remap = false, shift = At.Shift.AFTER))
    private void vivecraft$changeAlphaBlend(CallbackInfo ci) {
        if (VRState.VR_RUNNING) {
            // this one already has blending so just change the alpha blend function
            RenderSystem.blendFuncSeparate(
                GlStateManager.BLEND.srcRgb,
                GlStateManager.BLEND.dstRgb,
                GlStateManager.SourceFactor.ONE.value,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value);
        }
    }

    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShader(Ljava/util/function/Supplier;)V", remap = false, shift = At.Shift.AFTER))
    private void vivecraft$addBlend(CallbackInfo ci) {
        if (VRState.VR_RUNNING) {
            // enable blending and only change the alpha blending
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                GlStateManager.BLEND.srcRgb,
                GlStateManager.BLEND.dstRgb,
                GlStateManager.SourceFactor.ONE.value,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value);
        }
    }

    @Inject(method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V", at = @At("TAIL"))
    private void vivecraft$stopBlend(CallbackInfo ci) {
        if (VRState.VR_RUNNING) {
            RenderSystem.disableBlend();
        }
    }
}
