package org.vivecraft.mixin.world.entity.projectile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.server.ServerVRPlayers;
import org.vivecraft.server.ServerVivePlayer;

@Mixin(ThrowableProjectile.class)
public abstract class ThrowableProjectileMixin extends Entity {

    public ThrowableProjectileMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;)V", at = @At("TAIL"))
    private void vivecraft$satToHandPos(EntityType<?> entityType, LivingEntity shooter, Level level, CallbackInfo ci) {
        if (shooter instanceof ServerPlayer player) {
            ServerVivePlayer serverVivePlayer = ServerVRPlayers.getVivePlayer(player);
            if (serverVivePlayer != null && serverVivePlayer.isVR()) {
                Vec3 pos = serverVivePlayer.getLimbPos(serverVivePlayer.activeLimb);
                Vec3 dir = serverVivePlayer.getLimbDir(serverVivePlayer.activeLimb).scale(0.6F);
                this.setPos(pos.x + dir.x, pos.y + dir.y, pos.z + dir.z);
            }
        }
    }
}
