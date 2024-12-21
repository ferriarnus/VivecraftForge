package org.vivecraft.mixin.world.entity.projectile;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.server.ServerVRPlayers;
import org.vivecraft.server.ServerVivePlayer;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin extends Entity {

    public FishingHookMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @WrapOperation(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getXRot()F"))
    private float vivecraft$modifyXRot(
        Player instance, Operation<Float> original, @Share("dir") LocalRef<Vec3> controllerDir,
        @Share("pos") LocalRef<Vec3> controllerPos)
    {
        // some mods like Aquaculture create a FishingHook on the client with a LocalPlayer
        // this is nonsense, so just ignore it
        if (instance instanceof ServerPlayer serverPlayer) {
            ServerVivePlayer serverVivePlayer = ServerVRPlayers.getVivePlayer(serverPlayer);
            if (serverVivePlayer != null && serverVivePlayer.isVR()) {
                controllerDir.set(serverVivePlayer.getLimbDir(serverVivePlayer.activeLimb));
                controllerPos.set(serverVivePlayer.getLimbPos(serverVivePlayer.activeLimb));

                return -(float) Math.toDegrees(Math.asin(controllerDir.get().y / controllerDir.get().length()));
            }
        }
        return original.call(instance);
    }

    @WrapOperation(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float vivecraft$modifyYRot(
        Player instance, Operation<Float> original, @Share("dir") LocalRef<Vec3> controllerDir)
    {
        if (controllerDir.get() != null) {
            return (float) Math.toDegrees(Math.atan2(-controllerDir.get().x, controllerDir.get().z));
        } else {
            return original.call(instance);
        }
    }

    @WrapOperation(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/FishingHook;moveTo(DDDFF)V"))
    private void vivecraft$modifyMoveTo(
        FishingHook instance, double x, double y, double z, float yRot, float xRot, Operation<Void> original,
        @Share("dir") LocalRef<Vec3> controllerDir, @Share("pos") LocalRef<Vec3> controllerPos)
    {
        if (controllerPos.get() != null) {
            original.call(
                instance,
                controllerPos.get().x + controllerDir.get().x * 0.6F,
                controllerPos.get().y + controllerDir.get().y * 0.6F,
                controllerPos.get().z + controllerDir.get().z * 0.6F,
                yRot, xRot);
        } else {
            original.call(instance, x, y, z, yRot, xRot);
        }
    }
}
