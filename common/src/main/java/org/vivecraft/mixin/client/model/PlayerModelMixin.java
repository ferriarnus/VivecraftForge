package org.vivecraft.mixin.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client.render.VRPlayerModel;

@Mixin(PlayerModel.class)
public class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {
    @Shadow
    @Final
    private ModelPart cloak;

    @Unique
    private final Vector3f vivecraft$tempV = new Vector3f();
    @Unique
    private final Vector3f vivecraft$tempV2 = new Vector3f();
    @Unique
    private final Matrix3f vivecraft$tempM = new Matrix3f();

    public PlayerModelMixin(ModelPart root) {
        super(root);
    }


    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void vivecraft$VRAnim(
        LivingEntity player, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, CallbackInfo ci)
    {
        if (VRPlayersClient.getInstance().isVRPlayer(player)) {
            VRPlayerModel.animateVRModel((PlayerModel<LivingEntity>) (Object) this, player, limbSwing, limbSwingAmount,
                this.vivecraft$tempV, this.vivecraft$tempV2, this.vivecraft$tempM);

            // we do the positioning in CapeLayerMixin
            this.cloak.setPos(0,0,0);
        }
    }
}
