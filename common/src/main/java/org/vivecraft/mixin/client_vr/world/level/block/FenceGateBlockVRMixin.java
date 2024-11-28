package org.vivecraft.mixin.client_vr.world.level.block;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.FenceGateBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;

@Mixin(FenceGateBlock.class)
public class FenceGateBlockVRMixin {

    @Inject(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private void vivecraft$hapticFeedbackOnClose1(
        CallbackInfoReturnable<InteractionResult> cir, @Local(argsOnly = true) BlockPos pos,
        @Local boolean opening)
    {
        if (VRState.VR_RUNNING && !opening && Minecraft.getInstance().player != null &&
            Minecraft.getInstance().player.isAlive() &&
            Minecraft.getInstance().player.blockPosition().distSqr(pos) < 25.0D)
        {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 250);
        }
    }

    @Inject(method = "neighborChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private void vivecraft$hapticFeedbackOnClose2(
        CallbackInfo ci, @Local(argsOnly = true, ordinal = 0) BlockPos pos, @Local(ordinal = 1) boolean opening)
    {
        if (VRState.VR_RUNNING && !opening && Minecraft.getInstance().player != null &&
            Minecraft.getInstance().player.isAlive() &&
            Minecraft.getInstance().player.blockPosition().distSqr(pos) < 25.0D)
        {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 250);
        }
    }
}
