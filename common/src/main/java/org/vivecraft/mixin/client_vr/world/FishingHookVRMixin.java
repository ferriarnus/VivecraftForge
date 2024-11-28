package org.vivecraft.mixin.client_vr.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.MCVR;

@Mixin(FishingHook.class)
public abstract class FishingHookVRMixin extends Entity {

    @Shadow
    private boolean biting;

    @Shadow
    public abstract Player getPlayerOwner();

    public FishingHookVRMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    private boolean vivecraft$wasBiting = false;
    @Unique
    private boolean vivecraft$wasNibble = false;

    @Inject(method = "tick", at = @At(value = "HEAD"))
    private void vivecraft$fishhookFeedback(CallbackInfo ci) {
        if (!VRState.VR_RUNNING) {
            return;
        }
        Player player = this.getPlayerOwner();
        if (player != null && player.isLocalPlayer()) {
            if (this.biting && !this.vivecraft$wasBiting) {
                // bite, big feedback
                MCVR.get().triggerHapticPulse(
                    player.getMainHandItem().getItem() instanceof FishingRodItem ? ControllerType.RIGHT : ControllerType.LEFT,
                    0.005F, 160.0F, 0.5F);
            } else if (getDeltaMovement().y < -0.01 && !this.vivecraft$wasNibble) {
                // nibble, small feedback
                MCVR.get().triggerHapticPulse(
                    player.getMainHandItem().getItem() instanceof FishingRodItem ? ControllerType.RIGHT : ControllerType.LEFT,
                    0.0005F, 160.0F, 0.05F);
                this.vivecraft$wasNibble = true;
            }
        }
        this.vivecraft$wasBiting = this.biting;
        this.vivecraft$wasNibble = this.vivecraft$wasNibble && getDeltaMovement().y < 0.0;
    }
}
