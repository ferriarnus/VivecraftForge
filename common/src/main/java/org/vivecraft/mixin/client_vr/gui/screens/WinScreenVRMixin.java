package org.vivecraft.mixin.client_vr.gui.screens;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screens.WinScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.client_vr.VRState;

@Mixin(WinScreen.class)
public class WinScreenVRMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;blendFunc(Lcom/mojang/blaze3d/platform/GlStateManager$SourceFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DestFactor;)V"))
    private void vivecraft$dontDestroyAlpha(
        GlStateManager.SourceFactor sourceFactor, GlStateManager.DestFactor destFactor, Operation<Void> original)
    {
        if (VRState.VR_RUNNING) {
            RenderSystem.blendFuncSeparate(sourceFactor, destFactor,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        } else {
            original.call(sourceFactor, destFactor);
        }

    }
}
