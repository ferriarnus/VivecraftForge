package org.vivecraft.mixin.advancements;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.advancements.critereon.PlayerPredicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.server.ServerVRPlayers;
import org.vivecraft.server.ServerVivePlayer;

@Mixin(PlayerPredicate.class)
public class PlayerPredicateMixin {
    @WrapOperation(method = "matches", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 vivecraft$spyglassLookPosition(
        ServerPlayer player, Operation<Vec3> original, @Share("vivePlayer") LocalRef<ServerVivePlayer> vivePlayer)
    {
        if (player.getUseItem().is(Items.SPYGLASS) && ServerVRPlayers.isVRPlayer(player)) {
            vivePlayer.set(ServerVRPlayers.getVivePlayer(player));
            if (!vivePlayer.get().isSeated()) {
                return vivePlayer.get().getControllerPos(player.getUsedItemHand().ordinal());
            }
        }
        return original.call(player);
    }

    @WrapOperation(method = "matches", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 vivecraft$spyglassLookDirection(
        ServerPlayer player, float partialTick, Operation<Vec3> original,
        @Share("vivePlayer") LocalRef<ServerVivePlayer> vivePlayer)
    {
        if (vivePlayer.get() != null && !vivePlayer.get().isSeated()) {
            return vivePlayer.get().getControllerVectorCustom(player.getUsedItemHand().ordinal(), MathUtils.DOWN);
        } else {
            return original.call(player, partialTick);
        }
    }
}
