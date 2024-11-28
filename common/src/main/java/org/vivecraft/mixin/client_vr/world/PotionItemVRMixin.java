package org.vivecraft.mixin.client_vr.world;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;

@Mixin(PotionItem.class)
public class PotionItemVRMixin {
    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void vivecraft$drinkEasterEgg(ItemStack itemStack, Level level, LivingEntity livingEntity, CallbackInfoReturnable<ItemStack> cir) {
        if (VRState.VR_INITIALIZED && livingEntity instanceof LocalPlayer && itemStack.getHoverName().getString().equals("DRINK ME")) {
            ClientDataHolderVR.getInstance().vrPlayer.wfMode = -0.05;
            ClientDataHolderVR.getInstance().vrPlayer.wfCount = 400;
        }
    }
}
