package org.vivecraft.mixin.client_vr.renderer.entity;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.extensions.EntityRenderDispatcherVRExtension;
import org.vivecraft.client_vr.extensions.LevelRendererExtension;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.VRArmRenderer;
import org.vivecraft.client_xr.render_pass.RenderPassType;

import java.util.HashMap;
import java.util.Map;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherVRMixin implements EntityRenderDispatcherVRExtension {

    @Unique
    private final Map<String, VRArmRenderer> vivecraft$armSkinMap = new HashMap<>();

    @Shadow
    public Camera camera;

    @Inject(method = "cameraOrientation", at = @At("HEAD"), cancellable = true)
    private void vivecraft$cameraOrientation(CallbackInfoReturnable<Quaternionf> cir) {
        if (RenderPassType.isWorldOnly()) {
            cir.setReturnValue(this.vivecraft$getVRCameraOrientation(0.5F, 0.0F));
        }
    }

    @Inject(method = "onResourceManagerReload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderers;createPlayerRenderers(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)Ljava/util/Map;"))
    private void vivecraft$reload(ResourceManager resourceManager, CallbackInfo ci, @Local EntityRendererProvider.Context context) {
        this.vivecraft$armSkinMap.put("default", new VRArmRenderer(context, false));
        this.vivecraft$armSkinMap.put("slim", new VRArmRenderer(context, true));
    }

    @Override
    @Unique
    public Quaternionf vivecraft$getVRCameraOrientation(float scale, float offset) {
        Entity entity = ((LevelRendererExtension) Minecraft.getInstance().levelRenderer).vivecraft$getRenderedEntity();
        if (entity == null) {
            return this.camera.rotation();
        } else {
            Vec3 source;
            if (RenderPass.isThirdPerson(ClientDataHolderVR.getInstance().currentPass)) {
                source = ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().getEye(ClientDataHolderVR.getInstance().currentPass).getPosition();
            } else {
                source = ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().getEye(RenderPass.CENTER).getPosition();
            }
            Vec3 direction = entity.position()
                .add(0.0D, entity.getBbHeight() * scale + offset, 0.0D)
                .subtract(source).normalize();

            return new Quaternionf()
                .rotateY((float) -Math.atan2(-direction.x, direction.z))
                .rotateX((float) -Math.asin(direction.y / direction.length()));
        }
    }

    @Override
    @Unique
    public Map<String, VRArmRenderer> vivecraft$getArmSkinMap() {
        return this.vivecraft$armSkinMap;
    }
}
