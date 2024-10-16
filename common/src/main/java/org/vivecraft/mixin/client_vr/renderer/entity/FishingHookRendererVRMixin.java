package org.vivecraft.mixin.client_vr.renderer.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.render.helpers.RenderHelper;
import org.vivecraft.client_xr.render_pass.RenderPassType;

@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererVRMixin {

    @WrapOperation(method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F", ordinal = 0))
    private float vivecraft$fishingLineStartRotation(
        float delta, float start, float end, Operation<Float> original, @Local Player player)
    {
        VRPlayersClient.RotInfo info;
        if (player != Minecraft.getInstance().player && VRPlayersClient.getInstance().isVRPlayer(player) && (info = VRPlayersClient.getInstance().getRotationsForPlayer(player.getUUID())).seated) {
            // other players in seated mode
            return (float) Math.toDegrees(info.getBodyYawRadians());
        } else {
            return original.call(delta, start, end);
        }
    }

    @ModifyVariable(method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("LOAD"), index = 25)
    private double vivecraft$fishingLineStartX(
        double value, FishingHook fishingHook, @Local(ordinal = 1, argsOnly = true) float partialTick,
        @Local Player player, @Share("linePos") LocalRef<Vec3> linePos)
    {
        VRPlayersClient.RotInfo info;
        if (!RenderPassType.isVanilla() && player == Minecraft.getInstance().player) {
            // own player
            int c = player.getMainHandItem().getItem() instanceof FishingRodItem ? 0 : 1;
            Vec3 aimSource = RenderHelper.getControllerRenderPos(c);
            Vector3f aimDirection = ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.getHand(c).getDirection();
            aimDirection.mul(0.47F * ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.worldScale);

            linePos.set(aimSource.add(aimDirection.x, aimDirection.y, aimDirection.z));
            return linePos.get().x;
        } else if (VRPlayersClient.getInstance().isVRPlayer(player) && !(info = VRPlayersClient.getInstance().getRotationsForPlayer(player.getUUID())).seated) {
            // other players in standing mode
            Vector3fc aimSource = player.getMainHandItem().getItem() instanceof FishingRodItem ? info.rightArmPos : info.leftArmPos;
            // just set it to the hand, everything else looks silly
            linePos.set(player.getPosition(partialTick).add(aimSource.x(), aimSource.y(), aimSource.z()));
            return linePos.get().x;
        } else {
            return value;
        }
    }

    @ModifyVariable(method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("LOAD"), index = 27)
    private double vivecraft$fishingLineStartY(
        double value, FishingHook fishingHook, @Share("linePos") LocalRef<Vec3> linePos)
    {
        if (linePos.get() != null) {
            return linePos.get().y;
        } else {
            return value;
        }
    }

    @ModifyVariable(method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("LOAD"), index = 29)
    private double vivecraft$fishingLineStartZ(
        double value, FishingHook fishingHook, @Share("linePos") LocalRef<Vec3> linePos)
    {
        if (linePos.get() != null) {
            return linePos.get().z;
        } else {
            return value;
        }
    }

    @ModifyVariable(method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("LOAD"), index = 31)
    private float vivecraft$fishingLineStartHeightOffset(
        float value, FishingHook fishingHook, @Share("linePos") LocalRef<Vec3> linePos)
    {
        if (linePos.get() != null) {
            return 0.0F;
        } else {
            return value;
        }
    }
}
