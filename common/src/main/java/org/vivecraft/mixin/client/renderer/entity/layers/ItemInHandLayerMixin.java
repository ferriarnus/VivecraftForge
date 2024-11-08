package org.vivecraft.mixin.client.renderer.entity.layers;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.trackers.ClimbTracker;
import org.vivecraft.client_vr.render.RenderPass;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void vivecraft$noItemsInFirstPerson(CallbackInfo ci, @Local(argsOnly = true) LivingEntity livingEntity) {
        if (livingEntity == Minecraft.getInstance().player &&
            (ClientDataHolderVR.getInstance().currentPass == RenderPass.LEFT ||
                ClientDataHolderVR.getInstance().currentPass == RenderPass.RIGHT ||
                ClientDataHolderVR.getInstance().currentPass == RenderPass.CENTER
            ))
        {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack vivecraft$climbClawsOverride(
        ItemStack itemStack, @Local(argsOnly = true) LivingEntity entity, @Local(argsOnly = true) HumanoidArm arm)
    {
        if (ClientNetworking.SERVER_ALLOWS_CLIMBEY && entity instanceof Player && !ClimbTracker.isClaws(itemStack)) {
            ItemStack otherStack = arm == entity.getMainArm() ? entity.getOffhandItem() : entity.getMainHandItem();
            if (ClimbTracker.isClaws(otherStack)) {
                ClimbTracker tracker = ClientDataHolderVR.getInstance().climbTracker;
                if (entity instanceof LocalPlayer player && VRState.VR_RUNNING && tracker.isActive(player) && ClimbTracker.hasClimbeyClimbEquipped(player)) {
                    return otherStack;
                } else if (entity instanceof RemotePlayer player && VRPlayersClient.getInstance().isVRPlayer(player) && !VRPlayersClient.getInstance().getRotationsForPlayer(player.getUUID()).seated) {
                    return otherStack;
                }
            }
        }

        return itemStack;
    }
}
