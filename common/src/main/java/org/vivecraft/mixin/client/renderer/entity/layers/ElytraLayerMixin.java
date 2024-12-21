package org.vivecraft.mixin.client.renderer.entity.layers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.client.ClientVRPlayers;
import org.vivecraft.client.utils.ModelUtils;
import org.vivecraft.common.utils.MathUtils;

@Mixin(ElytraLayer.class)
public abstract class ElytraLayerMixin<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    @Unique
    private final Vector3f vivecraft$tempV = new Vector3f();

    @Unique
    private final Matrix3f vivecraft$bodyRot = new Matrix3f();

    public ElytraLayerMixin(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void vivecraft$elytraPosition(
        PoseStack instance, float x, float y, float z, Operation<Void> original,
        @Local(argsOnly = true) LivingEntity entity, @Local(argsOnly = true, ordinal = 2) float partialTick)
    {
        ClientVRPlayers.RotInfo rotInfo = ClientVRPlayers.getInstance().getRotationsForPlayer(entity.getUUID());
        // only do this if it's a player model and a vr player
        if (getParentModel() instanceof PlayerModel<?> model && rotInfo != null) {
            this.vivecraft$bodyRot.rotationZYX(model.body.zRot, -model.body.yRot, -model.body.xRot);

            this.vivecraft$bodyRot.transform(MathUtils.UP, this.vivecraft$tempV);
            float xRotation = (float) Math.atan2(this.vivecraft$tempV.y, this.vivecraft$tempV.z) - Mth.HALF_PI;

            this.vivecraft$bodyRot.transform(MathUtils.LEFT, this.vivecraft$tempV);
            float yRotation = (float) -Math.atan2(this.vivecraft$tempV.x, this.vivecraft$tempV.y) + Mth.HALF_PI;

            // position the cape behind the body
            float yOffset = 0F;
            if (entity.isFallFlying()) {
                // move it down, to not be in the players face
                yOffset = 2F;
            } else if (entity.isCrouching()) {
                // undo vanilla crouch offset
                yOffset = -3F;
            }
            // transform offset to be body relative
            this.vivecraft$tempV.set(0F, yOffset, 2F - 0.5F * (model.body.xRot / Mth.HALF_PI));
            this.vivecraft$tempV.rotateX(xRotation);
            this.vivecraft$tempV.rotateZ(yRotation);

            // +24 because it should be the offset to the default position, which is at 24
            this.vivecraft$tempV.add(model.body.x, model.body.y + 24F, model.body.z);

            // no yaw, since we  need the vector to be player rotated anyway
            ModelUtils.modelToWorld(entity, this.vivecraft$tempV, rotInfo, 0F, false, false, this.vivecraft$tempV);
            original.call(instance, this.vivecraft$tempV.x, -this.vivecraft$tempV.y, -this.vivecraft$tempV.z);

            // rotate elytra
            instance.mulPose(Axis.XP.rotation(xRotation));
            instance.mulPose(Axis.YP.rotation(yRotation));
        } else {
            original.call(instance, x, y, z);
        }
    }
}
