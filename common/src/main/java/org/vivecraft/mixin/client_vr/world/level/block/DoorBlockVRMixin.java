package org.vivecraft.mixin.client_vr.world.level.block;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;

@Mixin(DoorBlock.class)
public class DoorBlockVRMixin {

    @Inject(method = "playSound", at = @At("HEAD"))
    private void vivecraft$hapticFeedbackOnClose(
        Entity source, Level level, BlockPos pos, boolean isOpening, CallbackInfo ci)
    {
        if (VRState.VR_RUNNING && !isOpening && Minecraft.getInstance().player != null &&
            Minecraft.getInstance().player.isAlive() &&
            Minecraft.getInstance().player.blockPosition().distSqr(pos) < 25.0D)
        {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 250);
        }
    }
}
