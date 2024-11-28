package org.vivecraft.mixin.client_vr.renderer;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.VRState;

@Mixin(ItemProperties.class)
public class ItemPropertiesVRMixin {

    @Inject(method = "method_43611", at = @At("HEAD"),cancellable = true)
    private static void vivecraft$noHornUseAnim(
        CallbackInfoReturnable<Float> cir, @Local(argsOnly = true) LivingEntity entity)
    {
        if (VRState.VR_RUNNING && entity == Minecraft.getInstance().player) {
            cir.setReturnValue(0.0F);
        }
    }
}
