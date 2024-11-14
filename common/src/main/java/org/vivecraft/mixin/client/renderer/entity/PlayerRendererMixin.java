package org.vivecraft.mixin.client.renderer.entity;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @ModifyArg(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V", at = @At(value = "INVOKE", target = "Ljava/lang/Math;acos(D)D"))
    private double vivecraft$fixFlicker(double acos) {
        // because of imprecision issues this can cause nans
        // is fixed in 1.21.4
        return Math.min(1.0, acos);
    }
}
