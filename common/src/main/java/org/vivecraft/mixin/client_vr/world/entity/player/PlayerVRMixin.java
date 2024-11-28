package org.vivecraft.mixin.client_vr.world.entity.player;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.client_vr.VRState;

@Mixin(Player.class)
public class PlayerVRMixin {

    @WrapOperation(method = "maybeBackOffFromEdge", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;move(DDD)Lnet/minecraft/world/phys/AABB;"))
    private AABB vivecraft$moveSidewaysExtendDown(AABB instance, double x, double y, double z, Operation<AABB> original) {
        if (!VRState.VR_RUNNING) {
            return original.call(instance, x, y, z);
        } else {
            // this is to fix an issue with a maxStepUp size of 1 and trapdoors
            return new AABB(instance.minX + x, instance.minY + y, instance.minZ + z, instance.maxX + x, instance.maxY,
                instance.maxZ + z);
        }
    }
}
