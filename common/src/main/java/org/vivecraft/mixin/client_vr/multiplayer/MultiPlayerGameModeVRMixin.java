package org.vivecraft.mixin.client_vr.multiplayer;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;

/**
 * we override the players look direction so the server handles any interactions as if the player looked at the interacted block
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeVRMixin {

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
}
