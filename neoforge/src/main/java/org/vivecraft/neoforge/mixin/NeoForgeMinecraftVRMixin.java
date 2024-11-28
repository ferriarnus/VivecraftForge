package org.vivecraft.neoforge.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Timer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.render.helpers.VRPassHelper;

@Mixin(Minecraft.class)
public class NeoForgeMinecraftVRMixin {

    @Shadow
    @Final
    private Timer timer;

    @Shadow
    private volatile boolean pause;

    @Shadow
    private float pausePartialTick;

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;onRenderTickEnd(F)V", shift = At.Shift.AFTER, remap = false), remap = true)
    private void vivecraft$renderVRPassesNeoForge(boolean renderLevel, CallbackInfo ci, @Local(ordinal = 0) long nanoTime) {
        if (VRState.VR_RUNNING) {
            VRPassHelper.renderAndSubmit(renderLevel, nanoTime,
                this.pause ? this.pausePartialTick : this.timer.partialTick);
        }
    }
}
