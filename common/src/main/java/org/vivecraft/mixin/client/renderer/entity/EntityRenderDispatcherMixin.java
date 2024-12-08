package org.vivecraft.mixin.client.renderer.entity;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
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
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.utils.Utils;

import java.util.HashMap;
import java.util.Map;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin implements ResourceManagerReloadListener, EntityRenderDispatcherExtension {
    @Unique
    private final Map<String, VRPlayerRenderer> vivecraft$skinMapVRVanilla = new HashMap<>();

    @Unique
    private final Map<String, VRPlayerRenderer> vivecraft$skinMapVRArms = new HashMap<>();

    @Unique
    private final Map<String, VRPlayerRenderer> vivecraft$skinMapVRLegs = new HashMap<>();

    @Unique
    private VRPlayerRenderer vivecraft$playerRendererVRVanilla;

    @Unique
    private VRPlayerRenderer vivecraft$playerRendererVRArms;

    @Unique
    private VRPlayerRenderer vivecraft$playerRendererVRLegs;

    @Override
    public Map<String, VRPlayerRenderer> vivecraft$getSkinMapVRVanilla() {
        return this.vivecraft$skinMapVRVanilla;
    }

    @Override
    public Map<String, VRPlayerRenderer> vivecraft$getSkinMapVRArms() {
        return this.vivecraft$skinMapVRArms;
    }

    @Override
    public Map<String, VRPlayerRenderer> vivecraft$getSkinMapVRLegs() {
        return this.vivecraft$skinMapVRLegs;
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
        // don't do any animations for dummy players
        if (entity instanceof AbstractClientPlayer player &&
            (player.getClass() == LocalPlayer.class || player.getClass() == RemotePlayer.class))
        {
            String skinType = player.getSkin().model().id();
            VRPlayersClient.RotInfo rotInfo = VRPlayersClient.getInstance().getRotationsForPlayer(player.getUUID());
            if (rotInfo != null) {
                VRPlayerRenderer vrPlayerRenderer;
                if (rotInfo.seated ||
                    ClientDataHolderVR.getInstance().vrSettings.playerModelType == VRSettings.PlayerModelType.VANILLA)
                {
                    vrPlayerRenderer = this.vivecraft$skinMapVRVanilla.getOrDefault(skinType,
                        this.vivecraft$playerRendererVRVanilla);
                } else if (ClientDataHolderVR.getInstance().vrSettings.playerModelType ==
                    VRSettings.PlayerModelType.SPLIT_ARMS)
                {
                    vrPlayerRenderer = this.vivecraft$skinMapVRArms.getOrDefault(skinType, this.vivecraft$playerRendererVRArms);
                } else {
                    vrPlayerRenderer = this.vivecraft$skinMapVRLegs.getOrDefault(skinType, this.vivecraft$playerRendererVRLegs);
                }

                cir.setReturnValue(vrPlayerRenderer);
            }
        }
    }

    @Inject(method = "onResourceManagerReload", at = @At(value = "HEAD"))
    private void vivecraft$clearVRPlayerRenderer(CallbackInfo ci) {
        this.vivecraft$skinMapVRVanilla.clear();
        this.vivecraft$skinMapVRArms.clear();
        this.vivecraft$skinMapVRLegs.clear();
    }

    @Inject(method = "onResourceManagerReload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderers;createPlayerRenderers(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)Ljava/util/Map;"))
    private void vivecraft$reloadVRPlayerRenderer(CallbackInfo ci, @Local EntityRendererProvider.Context context) {
        this.vivecraft$playerRendererVRVanilla = new VRPlayerRenderer(context, false,
            VRPlayerRenderer.ModelType.VANILLA);
        this.vivecraft$skinMapVRVanilla.put("default", this.vivecraft$playerRendererVRVanilla);
        this.vivecraft$skinMapVRVanilla.put("slim",
            new VRPlayerRenderer(context, true, VRPlayerRenderer.ModelType.VANILLA));

        this.vivecraft$playerRendererVRArms = new VRPlayerRenderer(context, false,
            VRPlayerRenderer.ModelType.SPLIT_ARMS);
        this.vivecraft$skinMapVRArms.put("default", this.vivecraft$playerRendererVRArms);
        this.vivecraft$skinMapVRArms.put("slim", new VRPlayerRenderer(context, true,
            VRPlayerRenderer.ModelType.SPLIT_ARMS));

        this.vivecraft$playerRendererVRLegs = new VRPlayerRenderer(context, false,
            VRPlayerRenderer.ModelType.SPLIT_ARMS_LEGS);
        this.vivecraft$skinMapVRLegs.put("default", this.vivecraft$playerRendererVRLegs);
        this.vivecraft$skinMapVRLegs.put("slim",
            new VRPlayerRenderer(context, true, VRPlayerRenderer.ModelType.SPLIT_ARMS_LEGS));
    }
}
