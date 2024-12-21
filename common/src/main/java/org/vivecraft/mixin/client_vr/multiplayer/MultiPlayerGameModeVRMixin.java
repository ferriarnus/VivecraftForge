package org.vivecraft.mixin.client_vr.multiplayer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.common.network.Limb;

/**
 * we override the players look direction so the server handles any interactions as if the player looked at the interacted block
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeVRMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "useItem", at = @At("HEAD"))
    private void vivecraft$overrideUse(
        Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir)
    {
        if (VRState.VR_RUNNING) {
            ClientNetworking.overrideLook(player, ClientDataHolderVR.getInstance().vrPlayer.getRightClickLookOverride(player, hand.ordinal()));
        }
    }

    @Inject(method = "releaseUsingItem", at = @At("HEAD"))
    private void vivecraft$overrideReleaseUse(Player player, CallbackInfo ci) {
        if (VRState.VR_RUNNING) {
            ClientNetworking.overrideLook(player, ClientDataHolderVR.getInstance().vrPlayer.getRightClickLookOverride(player, player.getUsedItemHand().ordinal()));
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void vivecraft$overrideUseOn(
        LocalPlayer player, InteractionHand hand, BlockHitResult result, CallbackInfoReturnable<InteractionResult> cir)
    {
        if (VRState.VR_RUNNING) {
            ClientNetworking.overrideLook(player, result.getLocation().subtract(player.getEyePosition(1.0F)).normalize());
        }
    }

    @WrapOperation(method = "sameDestroyTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameTags(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean vivecraft$dualWieldingSkipItemCheck(ItemStack stack, ItemStack other, Operation<Boolean> original) {
        if (VRState.VR_RUNNING && ClientNetworking.SERVER_ALLOWS_DUAL_WIELDING) {
            // check if main or offhand items match the started item, we want to limit abuse of this,
            // but still make both items work
            Limb lastLimb = ClientNetworking.LAST_SENT_LIMB;
            ClientNetworking.LAST_SENT_LIMB = Limb.MAIN_HAND;
            boolean sameItem = original.call(stack, other) || original.call(this.minecraft.player.getOffhandItem(), other);
            ClientNetworking.LAST_SENT_LIMB = lastLimb;
            return sameItem;
        } else {
            return original.call(stack, other);
        }
    }
}
