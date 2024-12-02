package org.vivecraft.mixin.client.renderer.entity.layers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client.utils.ModelUtils;
import org.vivecraft.common.utils.MathUtils;

@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    @Unique
    private final Vector3f vivecraft$tempV = new Vector3f();

    @Unique
    private final Matrix3f vivecraft$bodyRot = new Matrix3f();

    public CapeLayerMixin(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    // DEBUG CAPE
    /*
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/PlayerSkin;capeTexture()Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation vivecraft$whiteCape(PlayerSkin skin, Operation<ResourceLocation> original) {
        ResourceLocation capeTexture = original.call(skin);
        if (capeTexture == null) {
            capeTexture = new ResourceLocation("vivecraft:textures/white.png");
        }
        return capeTexture;
    }
    */

    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void vivecraft$modifyOffset(
        PoseStack poseStack, float x, float y, float z, Operation<Void> original,
        @Local(argsOnly = true) AbstractClientPlayer player, @Local(argsOnly = true, ordinal = 2) float partialTick,
        @Share("xRot") LocalFloatRef xRotation, @Share("yRot") LocalFloatRef yRotation)
    {
        VRPlayersClient.RotInfo rotInfo = VRPlayersClient.getInstance().getRotationsForPlayer(player.getUUID());
        // only do this if it's a vr player
        if (rotInfo != null) {
            this.vivecraft$bodyRot.rotationZYX(getParentModel().body.zRot, -getParentModel().body.yRot,
                -getParentModel().body.xRot);

            // attach the cape to the body
            this.vivecraft$bodyRot.transform(MathUtils.UP, this.vivecraft$tempV);
            xRotation.set((float) Math.atan2(this.vivecraft$tempV.y, this.vivecraft$tempV.z) - Mth.HALF_PI);

            // make sure it doesn't go below -PI
            xRotation.set(xRotation.get() < -Mth.PI ? xRotation.get() + Mth.TWO_PI : xRotation.get());

            this.vivecraft$bodyRot.transform(MathUtils.LEFT, this.vivecraft$tempV);
            yRotation.set((float) -Math.atan2(this.vivecraft$tempV.x, this.vivecraft$tempV.y) + Mth.HALF_PI);

            // transform offset to be body relative
            this.vivecraft$tempV.set(0F, 0F, 2F - 0.5F * (getParentModel().body.xRot / Mth.HALF_PI));
            if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
                // vanilla cape offset with armor
                this.vivecraft$tempV.add(0F, -0.85F, 1.1F);
            }
            this.vivecraft$tempV.rotateX(xRotation.get());
            this.vivecraft$tempV.rotateZ(yRotation.get());

            // +24 because it should be the offset to the default position, which is at 24
            this.vivecraft$tempV.add(getParentModel().body.x, getParentModel().body.y + 24F, getParentModel().body.z);

            // no yaw, since we  need the vector to be player rotated anyway
            ModelUtils.modelToWorld(player, this.vivecraft$tempV, rotInfo, 0F, false, false, this.vivecraft$tempV);
            original.call(poseStack, this.vivecraft$tempV.x, -this.vivecraft$tempV.y, -this.vivecraft$tempV.z);
        } else {
            original.call(poseStack, x, y, z);
        }
    }

    @ModifyVariable(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isCrouching()Z"), ordinal = 7)
    private float vivecraft$modifyXRot(
        float xRot, @Local(argsOnly = true) AbstractClientPlayer player,
        @Local(ordinal = 2, argsOnly = true) float partialTick, @Share("xRot") LocalFloatRef xRotation)
    {
        if (VRPlayersClient.getInstance().isVRPlayer(player)) {
            // rotate the cape with the body
            // cancel out crouch
            if (player.isCrouching()) {
                xRot -= 25F;
            }
            // rotate with body
            // max of 0 to keep it down when the body bends backwards
            float min = (player.isFallFlying() ? 1F : player.getSwimAmount(partialTick)) * -Mth.HALF_PI;
            xRot += Mth.RAD_TO_DEG * Math.max(min, xRotation.get());
        }
        return xRot;
    }

    @ModifyVariable(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isCrouching()Z"), method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", ordinal = 8)
    private float vivecraft$limitSpeedRot(
        float speedRot, @Local(argsOnly = true) AbstractClientPlayer player,
        @Share("xRot") LocalFloatRef xRotation)
    {
        if (VRPlayersClient.getInstance().isVRPlayer(player)) {
            // limit the up rotation when walking forward, depending on body rotation
            float rot = xRotation.get() / Mth.HALF_PI;
            if (rot >= 0) {
                return speedRot * (1F - Mth.clamp(rot, 0F, 1F));
            } else {
                return 0F;
            }
        }
        return speedRot;
    }

    @ModifyArg(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 2))
    private float vivecraft$modifyYRotation(
        float yRot, @Local(argsOnly = true) AbstractClientPlayer player,
        @Share("yRot") LocalFloatRef yRotation)
    {
        if (VRPlayersClient.getInstance().isVRPlayer(player)) {
            // rotate the cape with side body rotation
            yRot += Mth.RAD_TO_DEG * yRotation.get();
        }
        return yRot;
    }
}
