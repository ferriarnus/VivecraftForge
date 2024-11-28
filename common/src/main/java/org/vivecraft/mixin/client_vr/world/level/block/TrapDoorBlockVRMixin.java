package org.vivecraft.mixin.client_vr.world.level.block;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;

@Mixin(TrapDoorBlock.class)
public class TrapDoorBlockVRMixin {

    @Inject(method = "playSound", at = @At("HEAD"))
    private void vivecraft$hapticFeedbackOnClose(
        Player player, Level level, BlockPos pos, boolean isOpened, CallbackInfo ci)
    {
        if (VRState.VR_RUNNING && !isOpened && Minecraft.getInstance().player != null &&
            Minecraft.getInstance().player.isAlive() &&
            Minecraft.getInstance().player.blockPosition().distSqr(pos) < 25.0D)
        {
            ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 250);
        }
    }
}
