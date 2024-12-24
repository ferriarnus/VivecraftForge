package org.vivecraft.mixin.client_vr.renderer.entity.layers;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.mod_compat_vr.shaders.ShadersHelper;
import org.vivecraft.mod_compat_vr.immersiveportals.ImmersivePortalsHelper;

@Mixin(CustomHeadLayer.class)
public class CustomHeadLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void vivecraft$noHelmetInFirstPerson(
        CallbackInfo ci, @Local(argsOnly = true) LivingEntity entity)
    {
        if (VRState.VR_RUNNING && entity == Minecraft.getInstance().player &&
            ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
            RenderPass.isFirstPerson(ClientDataHolderVR.getInstance().currentPass) &&
            !ShadersHelper.isRenderingShadows() &&
            !(ImmersivePortalsHelper.isLoaded() && ImmersivePortalsHelper.isRenderingPortal()))
        {
            ci.cancel();
        }
    }
}
