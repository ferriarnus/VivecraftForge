package org.vivecraft.mixin.client_vr.model;

import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.vivecraft.client_vr.extensions.BoatExtension;

@Mixin(BoatModel.class)
public abstract class BoatModelMixin {
    @Inject(at = @At(value = "HEAD"), method = "animatePaddle(Lnet/minecraft/world/entity/vehicle/Boat;ILnet/minecraft/client/model/geom/ModelPart;F)V", locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private static void vivecraft$animatePaddle(Boat boat, int paddle, ModelPart mp, float f, CallbackInfo ci) {
        Vec3 paddleAngle = ((BoatExtension) boat).vivecraft$getPaddleAngles()[paddle];
        if (paddleAngle != null) {
            mp.xRot = (float) Math.atan2(paddleAngle.y, Math.sqrt(paddleAngle.x * paddleAngle.x + paddleAngle.z * paddleAngle.z));
            mp.yRot = (float) Math.atan2(paddleAngle.z, paddleAngle.x);
            ci.cancel();
        }
    }
}
