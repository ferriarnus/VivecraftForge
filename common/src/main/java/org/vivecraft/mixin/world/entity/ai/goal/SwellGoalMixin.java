package org.vivecraft.mixin.world.entity.ai.goal;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.server.ServerVRPlayers;
import org.vivecraft.server.ServerVivePlayer;
import org.vivecraft.server.config.ServerConfig;

@Mixin(SwellGoal.class)
public class SwellGoalMixin {

    @ModifyExpressionValue(method = "canUse", at = @At(value = "CONSTANT", args = "doubleValue=9"))
    private double vivecraft$vrSwellDistance(double swellDistance, @Local LivingEntity target) {
        if (target instanceof ServerPlayer player && ServerVRPlayers.isVRPlayer(player)) {
            ServerVivePlayer serverVivePlayer = ServerVRPlayers.getVivePlayer(player);
            if (serverVivePlayer != null && !serverVivePlayer.isSeated()) {
                return ServerConfig.CREEPER_SWELL_DISTANCE.get() * ServerConfig.CREEPER_SWELL_DISTANCE.get();
            }
        }
        return swellDistance;
    }
}
