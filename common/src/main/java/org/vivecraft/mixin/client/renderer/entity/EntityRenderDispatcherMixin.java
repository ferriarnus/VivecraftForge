package org.vivecraft.mixin.client.renderer.entity;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client.extensions.EntityRenderDispatcherExtension;
import org.vivecraft.client.render.VRPlayerRenderer;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.common.utils.Utils;

import java.util.HashMap;
import java.util.Map;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin implements ResourceManagerReloadListener, EntityRenderDispatcherExtension {

    @Unique
    private final Map<String, VRPlayerRenderer> vivecraft$skinMapVR = new HashMap<>();

    @Unique
    private final Map<String, VRPlayerRenderer> vivecraft$skinMapVRSeated = new HashMap<>();

    @Unique
    private VRPlayerRenderer vivecraft$playerRendererVR;

    @Unique
    private VRPlayerRenderer vivecraft$playerRendererVRSeated;

    @Override
    public Map<String, VRPlayerRenderer> vivecraft$getSkinMapVR() {
        return this.vivecraft$skinMapVR;
    }

    @Override
    public Map<String, VRPlayerRenderer> vivecraft$getSkinMapVRSeated() {
        return this.vivecraft$skinMapVRSeated;
    }

    @Inject(method = "renderHitbox", at = @At("HEAD"))
    private static void vivecraft$renderHeadHitbox(
        PoseStack poseStack, VertexConsumer buffer, Entity entity, float partialTick, CallbackInfo ci)
    {
        AABB headBox;
        if (ClientDataHolderVR.getInstance().vrSettings.renderHeadHitbox &&
            (headBox = Utils.getEntityHeadHitbox(entity, 0.0)) != null)
        {
            // raw head box
            LevelRenderer.renderLineBox(poseStack, buffer,
                headBox.move(-entity.getX(), -entity.getY(), -entity.getZ()),
                1.0f, 1.0f, 0.0f, 1.0f);
            // inflated head box for arrows
            AABB headBoxArrow = Utils.getEntityHeadHitbox(entity, 0.3);
            LevelRenderer.renderLineBox(poseStack, buffer,
                headBoxArrow.move(-entity.getX(), -entity.getY(), -entity.getZ()),
                1.0f, 0.0f, 0.0f, 1.0f);
        }
    }

    @Inject(method = "getRenderer", at = @At("HEAD"), cancellable = true)
    private void vivecraft$getVRPlayerRenderer(
        Entity entity, CallbackInfoReturnable<EntityRenderer<AbstractClientPlayer>> cir)
    {
        if (entity instanceof AbstractClientPlayer player) {
            String skinType = player.getSkin().model().id();
            VRPlayersClient.RotInfo rotInfo = VRPlayersClient.getInstance().getRotationsForPlayer(player.getUUID());
            if (rotInfo != null) {
                VRPlayerRenderer vrPlayerRenderer;
                if (rotInfo.seated) {
                    vrPlayerRenderer = this.vivecraft$skinMapVRSeated
                        .getOrDefault(skinType, this.vivecraft$playerRendererVRSeated);
                } else {
                    vrPlayerRenderer = this.vivecraft$skinMapVR
                        .getOrDefault(skinType, this.vivecraft$playerRendererVR);
                }

                cir.setReturnValue(vrPlayerRenderer);
            }
        }
    }

    @Inject(method = "onResourceManagerReload", at = @At(value = "HEAD"))
    private void vivecraft$clearVRPlayerRenderer(CallbackInfo ci) {
        this.vivecraft$skinMapVRSeated.clear();
        this.vivecraft$skinMapVR.clear();
    }

    @Inject(method = "onResourceManagerReload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderers;createPlayerRenderers(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)Ljava/util/Map;"))
    private void vivecraft$reloadVRPlayerRenderer(CallbackInfo ci, @Local EntityRendererProvider.Context context) {
        this.vivecraft$playerRendererVRSeated = new VRPlayerRenderer(context, false, true);
        this.vivecraft$skinMapVRSeated.put("default", this.vivecraft$playerRendererVRSeated);
        this.vivecraft$skinMapVRSeated.put("slim", new VRPlayerRenderer(context, true, true));

        this.vivecraft$playerRendererVR = new VRPlayerRenderer(context, false, false);
        this.vivecraft$skinMapVR.put("default", this.vivecraft$playerRendererVR);
        this.vivecraft$skinMapVR.put("slim", new VRPlayerRenderer(context, true, false));
    }
}
