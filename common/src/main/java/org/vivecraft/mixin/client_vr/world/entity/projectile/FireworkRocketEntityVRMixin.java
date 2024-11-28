package org.vivecraft.mixin.client_vr.world.entity.projectile;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.VRState;

@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityVRMixin {

    @Shadow
    private @Nullable LivingEntity attachedToEntity;

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"), index = 1)
    private double vivecraft$modifyX(double x, @Share("handPos") LocalRef<Vec3> handPos) {
        if (VRState.VR_RUNNING && this.attachedToEntity == Minecraft.getInstance().player &&
            this.attachedToEntity instanceof LocalPlayer localPlayer)
        {
            boolean fireworkInMainHand = localPlayer.getMainHandItem().is(Items.FIREWORK_ROCKET) &&
                !localPlayer.getOffhandItem().is(Items.FIREWORK_ROCKET);
            VRData.VRDevicePose controller = ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld()
                .getHand(fireworkInMainHand ? 0 : 1);
            handPos.set(controller.getPosition().add(controller.getDirection().scale(0.25)));
            return handPos.get().x;
        }
        return x;
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"), index = 2)
    private double vivecraft$modifyY(double y, @Share("handPos") LocalRef<Vec3> handPos) {
        return handPos.get() != null ? handPos.get().y : y;
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"), index = 3)
    private double vivecraft$modifyZ(double z, @Share("handPos") LocalRef<Vec3> handPos) {
        return handPos.get() != null ? handPos.get().z : z;
    }

    /*
    // server offset, this is wrong somehow
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getHandHoldingItemAngle(Lnet/minecraft/world/item/Item;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 vivecraft$redirectHandOffset(LivingEntity instance, Item item){
        if (instance instanceof ServerPlayer serverPlayer) {
            ServerVivePlayer vivePLayer = ServerVRPlayers.getVivePlayer(serverPlayer);
            if(vivePLayer != null && vivePLayer.isVR()) {
                   return vivePLayer.getControllerPos(serverPlayer.getOffhandItem().is(item) && !serverPlayer.getMainHandItem().is(item) ? 1 : 0, serverPlayer, true).subtract(instance.position());
            }
        }
        return instance.getHandHoldingItemAngle(item);
    }
    */
}
