package org.vivecraft.mixin.world.entity.projectile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.vivecraft.server.ServerVRPlayers;
import org.vivecraft.server.ServerVivePlayer;

@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {
    @Redirect(method = "getHitResultOnViewVector", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 vivecraft$roomscleBowVector(Entity instance, float partialTick) {
        if (instance instanceof ServerPlayer serverPlayer) {
            ServerVivePlayer vivePlayer = ServerVRPlayers.getVivePlayer(serverPlayer);
            if (vivePlayer != null && vivePlayer.isVR() && !vivePlayer.isSeated() && vivePlayer.draw > 0.0) {
                return vivePlayer.getControllerPos(1, serverPlayer, true)
                    .subtract(vivePlayer.getControllerPos(0, serverPlayer, true))
                    .normalize();
            }
        }
        return instance.getViewVector(partialTick);
    }
}
