package org.vivecraft.mixin.client_vr.renderer.entity.layers;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.render.armor.VRArmorModel_WithArms;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.mod_compat_vr.ShadersHelper;
import org.vivecraft.mod_compat_vr.immersiveportals.ImmersivePortalsHelper;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {
    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void vivecraft$noHelmetInFirstPerson(
        CallbackInfo ci, @Local(argsOnly = true) LivingEntity entity, @Local(argsOnly = true) EquipmentSlot slot)
    {
        if (VRState.VR_RUNNING && entity == Minecraft.getInstance().player && slot == EquipmentSlot.HEAD &&
            ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
            RenderPass.isFirstPerson(ClientDataHolderVR.getInstance().currentPass) &&
            !ShadersHelper.isRenderingShadows() &&
            !(ImmersivePortalsHelper.isLoaded() && ImmersivePortalsHelper.isRenderingPortal()))
        {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmorPiece", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;setPartVisibility(Lnet/minecraft/client/model/HumanoidModel;Lnet/minecraft/world/entity/EquipmentSlot;)V", shift = At.Shift.AFTER))
    private void vivecraft$noArmsInFirstPerson(
        CallbackInfo ci, @Local(argsOnly = true) LivingEntity entity, @Local(argsOnly = true) EquipmentSlot slot,
        @Local(argsOnly = true) HumanoidModel model)
    {
        if (VRState.VR_RUNNING && entity == Minecraft.getInstance().player && slot == EquipmentSlot.CHEST &&
            ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
            RenderPass.isFirstPerson(ClientDataHolderVR.getInstance().currentPass) &&
            !ShadersHelper.isRenderingShadows() &&
            !(ImmersivePortalsHelper.isLoaded() && ImmersivePortalsHelper.isRenderingPortal()))
        {
            VRSettings.ModelArmsMode mode = ClientDataHolderVR.getInstance().vrSettings.modelArmsMode;

            // hide the arm armor, when not showing the arms in first person
            if (model instanceof VRArmorModel_WithArms<?> armsModel) {
                // shoulders when not off
                armsModel.leftArm.visible &= mode != VRSettings.ModelArmsMode.OFF;
                armsModel.rightArm.visible &= mode != VRSettings.ModelArmsMode.OFF;

                // front only when complete
                armsModel.leftHand.visible &= mode == VRSettings.ModelArmsMode.COMPLETE;
                armsModel.rightHand.visible &= mode == VRSettings.ModelArmsMode.COMPLETE;
            } else {
                // front only when complete
                model.leftArm.visible &= mode == VRSettings.ModelArmsMode.COMPLETE;
                model.rightArm.visible &= mode == VRSettings.ModelArmsMode.COMPLETE;
            }
        }

    }
}
