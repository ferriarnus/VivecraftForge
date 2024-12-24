package org.vivecraft.mod_compat_vr.optifine.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.helpers.RenderHelper;
import org.vivecraft.client_xr.render_pass.RenderPassType;

@Pseudo
@Mixin(targets = "net.optifine.shaders.Shaders")
public class ShadersVRMixin {
    @Inject(method = "beginRender", at = @At("TAIL"), remap = false)
    private static void vivecraft$resetBlend(CallbackInfo ci) {
        // somehow the blend state is wrong here after shadows, when a spider gets rendered?
        RenderSystem.defaultBlendFunc();
    }

    @ModifyExpressionValue(method = "setProgramUniforms", at = @At(value = "CONSTANT", args = "floatValue=0.05F"), remap = false)
    private static float vivecraft$nearPlane(float original) {
        return RenderPassType.isVanilla() ? original : 0.02F;
    }

    @WrapOperation(method = "setCameraShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;", remap = true), remap = false)
    private static Vec3 vivecraft$positionCameraForShadows(Camera camera, Operation<Vec3> original) {
        if (RenderPassType.isVanilla() || ClientDataHolderVR.getInstance().vrSettings.disableShaderOptimization) {
            return original.call(camera);
        } else {
            return ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().getEye(RenderPass.CENTER).getPosition();
        }
    }

    @ModifyVariable(method = "setCameraShadow", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;pose()Lorg/joml/Matrix4f;", shift = At.Shift.AFTER, remap = true), remap = false)
    private static PoseStack vivecraft$offsetShadow(PoseStack shadowModelViewMat) {
        if (!RenderPassType.isVanilla() || ClientDataHolderVR.getInstance().vrSettings.disableShaderOptimization) {
            Vec3 offset = RenderHelper.getSmoothCameraPosition(ClientDataHolderVR.getInstance().currentPass,
                ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld()).subtract(
                ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().getEye(RenderPass.CENTER).getPosition());
            shadowModelViewMat.translate((float) offset.x, (float) offset.y, (float) offset.z);
        }
        return shadowModelViewMat;
    }

    @WrapOperation(method = "setCameraOffset", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D", remap = true), remap = false)
    private static double vivecraft$sameX(Entity entity, Operation<Double> original) {
        if (RenderPassType.isVanilla() || ClientDataHolderVR.getInstance().vrSettings.disableShaderOptimization) {
            return original.call(entity);
        } else {
            return ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().getEye(RenderPass.CENTER).getPosition().x;
        }
    }

    @WrapOperation(method = "setCameraOffset", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D", remap = true), remap = false)
    private static double vivecraft$sameZ(Entity entity, Operation<Double> original) {
        if (RenderPassType.isVanilla() || ClientDataHolderVR.getInstance().vrSettings.disableShaderOptimization) {
            return original.call(entity);
        } else {
            return ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().getEye(RenderPass.CENTER).getPosition().z;
        }
    }
}
