package org.vivecraft.mixin.client.renderer.entity.layers;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
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
import org.vivecraft.client.render.VRPlayerModel;
import org.vivecraft.client.render.VRPlayerModel_WithArms;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.trackers.ClimbTracker;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.mod_compat_vr.ShadersHelper;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin extends RenderLayer {

    public ItemInHandLayerMixin(RenderLayerParent renderer) {
        super(renderer);
    }

    @ModifyVariable(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("STORE"), ordinal = 0)
    private boolean vivecraft$isRightMainHand(boolean isRightMainHand, @Local(argsOnly = true) LivingEntity entity) {
        if (this.getParentModel() instanceof VRPlayerModel) {
            // we ignore the vanilla main arm setting, and use our own
            return !VRPlayersClient.getInstance().isVRAndLeftHanded(entity.getUUID());
        } else {
            return isRightMainHand;
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void vivecraft$noItemsInFirstPerson(CallbackInfo ci, @Local(argsOnly = true) LivingEntity entity, @Local(argsOnly = true) HumanoidArm arm) {
        if (entity == Minecraft.getInstance().player && VRState.VR_RUNNING && !ShadersHelper.isRenderingShadows() &&
            RenderPass.isFirstPerson(ClientDataHolderVR.getInstance().currentPass) &&
            // don't cancel climbing claws, unless menu hand
            (ClientDataHolderVR.getInstance().vrSettings.modelArmsMode != VRSettings.ModelArmsMode.COMPLETE ||
                ClientDataHolderVR.getInstance().isMenuHand(arm) ||
                !(ClientDataHolderVR.getInstance().climbTracker.isActive(Minecraft.getInstance().player) &&
                    ClimbTracker.hasClimbeyClimbEquipped(Minecraft.getInstance().player)
                )
            ))
        {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack vivecraft$climbClawsOverride(
        ItemStack itemStack, @Local(argsOnly = true) LivingEntity entity, @Local(argsOnly = true) HumanoidArm arm)
    {
        if (ClientNetworking.SERVER_ALLOWS_CLIMBEY && entity instanceof Player && !ClimbTracker.isClaws(itemStack) &&
            getParentModel() instanceof VRPlayerModel)
        {
            VRPlayersClient.RotInfo rotInfo = VRPlayersClient.getInstance().getRotationsForPlayer(entity.getUUID());
            if (rotInfo != null) {
                boolean mainHand = arm == (rotInfo.leftHanded ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
                ItemStack otherStack = mainHand ? entity.getOffhandItem() : entity.getMainHandItem();
                if (ClimbTracker.isClaws(otherStack)) {
                    if (entity instanceof LocalPlayer player && VRState.VR_RUNNING &&
                        ClientDataHolderVR.getInstance().climbTracker.isActive(player) &&
                        ClimbTracker.hasClimbeyClimbEquipped(player))
                    {
                        return otherStack;
                    } else if (entity instanceof RemotePlayer && !rotInfo.seated) {
                        return otherStack;
                    }
                }
            }
        }

        return itemStack;
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ArmedModel;translateToHand(Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V", shift = At.Shift.AFTER))
    private void vivecraft$firstPersonItemScale(CallbackInfo ci, @Local(argsOnly = true) LivingEntity entity, @Local(argsOnly = true) PoseStack poseStack) {
        if (entity == Minecraft.getInstance().player && VRState.VR_RUNNING && !ShadersHelper.isRenderingShadows() &&
            RenderPass.isFirstPerson(ClientDataHolderVR.getInstance().currentPass)) {
            // make the item scale equal in all directions
            if (getParentModel() instanceof VRPlayerModel_WithArms<?>) {
                poseStack.translate(0.0F, 0.65F, 0.0F);
            }
            poseStack.scale(1F, ClientDataHolderVR.getInstance().vrSettings.playerModelArmsScale,  1f);
            if (getParentModel() instanceof VRPlayerModel_WithArms<?>) {
                poseStack.translate(0.0F, -0.65F, 0.0F);
            }
        }


    }
}
