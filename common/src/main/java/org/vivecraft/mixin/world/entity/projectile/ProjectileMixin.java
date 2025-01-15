package org.vivecraft.mixin.world.entity.projectile;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.vivecraft.server.ServerVRPlayers;
import org.vivecraft.server.ServerVivePlayer;

@Mixin(Projectile.class)
public class ProjectileMixin {

    // this one first, because this one also needs access to the ServerVivePlayer
    @ModifyVariable(method = "shootFromRotation", at = @At("HEAD"), ordinal = 3, argsOnly = true)
    private float vivecraft$modifyVelocity(float velocity, Entity shooter, @Share("dir") LocalRef<Vec3> direction) {
        if (shooter instanceof ServerPlayer player) {
            ServerVivePlayer serverVivePlayer = ServerVRPlayers.getVivePlayer(player);
            if (serverVivePlayer != null && serverVivePlayer.isVR()) {
                Projectile projectile = (Projectile) (Object) this;

                // aim direction
                direction.set(serverVivePlayer.getAimDir());
            }
        }
        return velocity;
    }

    @ModifyVariable(method = "shootFromRotation", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float vivecraft$modifyXRot(float xRot, @Share("dir") LocalRef<Vec3> direction) {
        if (direction.get() != null) {
            return -(float) Math.toDegrees(Math.asin(direction.get().y / direction.get().length()));
        } else {
            return xRot;
        }
    }

    @ModifyVariable(method = "shootFromRotation", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float vivecraft$modifyYRot(float yRot, @Share("dir") LocalRef<Vec3> direction) {
        if (direction.get() != null) {
            return (float) Math.toDegrees(Math.atan2(-direction.get().x, direction.get().z));
        } else {
            return yRot;
        }
    }
}
