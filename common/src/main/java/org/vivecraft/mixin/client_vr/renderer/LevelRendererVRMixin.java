package org.vivecraft.mixin.client_vr.renderer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.extensions.LevelRendererExtension;
import org.vivecraft.client_vr.gameplay.trackers.InteractTracker;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.helpers.VREffectsHelper;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassManager;
import org.vivecraft.client_xr.render_pass.RenderPassType;
import org.vivecraft.mod_compat_vr.shaders.ShadersHelper;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;

import javax.annotation.Nullable;

// priority 999 to inject before iris, for the vrFast rendering
@Mixin(value = LevelRenderer.class, priority = 999)
public abstract class LevelRendererVRMixin implements ResourceManagerReloadListener, AutoCloseable, LevelRendererExtension {

    @Unique
    @Nullable
    private RenderTarget vivecraft$alphaSortVROccludedFramebuffer;
    @Unique
    @Nullable
    private RenderTarget vivecraft$alphaSortVRUnoccludedFramebuffer;
    @Unique
    @Nullable
    private RenderTarget vivecraft$alphaSortVRHandsFramebuffer;
    @Unique
    private boolean vivecraft$interactOutline;
    @Unique
    private Entity vivecraft$renderedEntity;

    @Final
    @Shadow
    private Minecraft minecraft;
    @Shadow
    private ClientLevel level;
    @Shadow
    private PostChain transparencyChain;
    @Final
    @Shadow
    private RenderBuffers renderBuffers;

    @Shadow
    protected abstract void renderHitOutline(
        PoseStack poseStack, VertexConsumer consumer, Entity entity, double camX, double camY, double camZ,
        BlockPos pos, BlockState state);

    @Shadow
    private static void renderShape(
        PoseStack poseStack, VertexConsumer consumer, VoxelShape shape, double x, double y, double z, float red,
        float green, float blue, float alpha)
    {}

    @ModifyArg(method = "renderSnowAndRain", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floor(D)I", ordinal = 0))
    private double vivecraft$rainX(double x, @Share("centerPos") LocalRef<Vec3> centerPos) {
        if (!RenderPassType.isVanilla() && (ClientDataHolderVR.getInstance().currentPass == RenderPass.LEFT ||
            ClientDataHolderVR.getInstance().currentPass == RenderPass.RIGHT
        ))
        {
            centerPos.set(ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.getEye(RenderPass.CENTER).getPosition());
            return centerPos.get().x;
        } else {
            return x;
        }
    }

    @ModifyArg(method = "renderSnowAndRain", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floor(D)I", ordinal = 1))
    private double vivecraft$rainY(double y, @Share("centerPos") LocalRef<Vec3> centerPos) {
        return centerPos.get() != null ? centerPos.get().y : y;
    }

    @ModifyArg(method = "renderSnowAndRain", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floor(D)I", ordinal = 2))
    private double vivecraft$rainZ(double z, @Share("centerPos") LocalRef<Vec3> centerPos) {
        return centerPos.get() != null ? centerPos.get().z : z;
    }

    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void vivecraft$reinitVR(ResourceManager resourceManager, CallbackInfo ci) {
        if (VRState.VR_INITIALIZED) {
            ClientDataHolderVR.getInstance().vrRenderer.reinitFrameBuffers("Resource Reload");
        }
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;pollLightUpdates()V"))
    private void vivecraft$onePollLightUpdates(ClientLevel instance, Operation<Void> original) {
        if (RenderPassType.isVanilla() || ClientDataHolderVR.getInstance().isFirstPass) {
            original.call(instance);
        }
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runLightUpdates()I"))
    private int vivecraft$oneLightingUpdates(LevelLightEngine instance, Operation<Integer> original) {
        if (RenderPassType.isVanilla() || ClientDataHolderVR.getInstance().isFirstPass) {
            return original.call(instance);
        } else {
            return 0;
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getRenderDistance()F"))
    private void vivecraft$stencil(CallbackInfo ci) {
        if (!RenderPassType.isVanilla()) {
            this.minecraft.getProfiler().popPush("stencil");
            VREffectsHelper.drawEyeStencil();
        }
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
    private boolean vivecraft$dontCullPlayer(boolean doRender, @Local Entity entity) {
        return doRender ||
            (ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf && entity == Minecraft.getInstance().player);
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z"))
    private boolean vivecraft$noPlayerWhenSleeping(boolean isSleeping) {
        // no self render, we don't want an out-of-body experience
       return isSleeping && !RenderPassType.isVanilla();
    }

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void vivecraft$storeEntityAndRestorePos(
        CallbackInfo ci, @Local(argsOnly = true) Entity entity,
        @Share("capturedEntity") LocalRef<Entity> capturedEntity)
    {
        if (!RenderPassType.isVanilla() && entity == this.minecraft.getCameraEntity()) {
            capturedEntity.set(entity);
            ((GameRendererExtension) this.minecraft.gameRenderer).vivecraft$restoreRVEPos(capturedEntity.get());
        }
        this.vivecraft$renderedEntity = entity;
    }

    @Inject(method = "renderEntity", at = @At("TAIL"))
    private void vivecraft$clearEntityAndSetupPos(
        CallbackInfo ci, @Local(argsOnly = true) Entity entity,
        @Share("capturedEntity") LocalRef<Entity> capturedEntity)
    {
        if (capturedEntity.get() != null) {
            ((GameRendererExtension) this.minecraft.gameRenderer).vivecraft$cacheRVEPos(capturedEntity.get());
            ((GameRendererExtension) this.minecraft.gameRenderer).vivecraft$setupRVE();
        }
        this.vivecraft$renderedEntity = null;
    }

    @ModifyVariable(method = "renderLevel", at = @At("HEAD"), argsOnly = true)
    private boolean vivecraft$noBlockOutlineOnInteract(boolean renderBlockOutline) {
        // don't draw the block outline when the interaction outline is active
        return renderBlockOutline && (RenderPassType.isVanilla() ||
            !(ClientDataHolderVR.getInstance().interactTracker.isInteractActive(0) &&
                (ClientDataHolderVR.getInstance().interactTracker.inBlockHit[0] != null ||
                    ClientDataHolderVR.getInstance().interactTracker.bukkit[0]
                )
            )
        );
    }

    @Inject(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;", ordinal = 1))
    private void vivecraft$interactOutline(
        CallbackInfo ci, @Local(argsOnly = true) Camera camera, @Local(argsOnly = true) PoseStack poseStack)
    {
        if (RenderPassType.isVanilla()) return;

        this.level.getProfiler().popPush("interact outline");
        this.vivecraft$interactOutline = true;
        if (OptifineHelper.isOptifineLoaded() && OptifineHelper.isShaderActive()) {
            OptifineHelper.beginOutlineShader();
        }

        InteractTracker interactTracker = ClientDataHolderVR.getInstance().interactTracker;

        for (int c = 0; c < 2; c++) {
            if (interactTracker.isInteractActive(c) &&
                (interactTracker.inBlockHit[c] != null || interactTracker.bukkit[c]))
            {
                BlockPos blockpos =interactTracker.inBlockHit[c] != null ?
                    interactTracker.inBlockHit[c].getBlockPos() : BlockPos.containing(
                    ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.getController(c).getPosition());
                BlockState blockstate = this.level.getBlockState(blockpos);
                this.renderHitOutline(poseStack,
                    this.renderBuffers.bufferSource().getBuffer(RenderType.lines()),
                    camera.getEntity(),
                    camera.getPosition().x,
                    camera.getPosition().y,
                    camera.getPosition().z,
                    blockpos, blockstate);
            }
        }
        if (OptifineHelper.isOptifineLoaded() && OptifineHelper.isShaderActive()) {
            this.renderBuffers.bufferSource().endBatch(RenderType.lines());
            OptifineHelper.endOutlineShader();
        }
        // reset outline color
        this.vivecraft$interactOutline = false;
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V", ordinal = 0, shift = Shift.AFTER))
    private void vivecraft$renderVrStuffPart1(
        CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack, @Local(argsOnly = true) float partialTick,
        @Share("guiRendered") LocalBooleanRef guiRendered)
    {
        if (RenderPassType.isVanilla()) return;

        if (this.transparencyChain != null) {
            VREffectsHelper.renderVRFabulous(partialTick, (LevelRenderer) (Object) this,
                ClientDataHolderVR.getInstance().menuHandMain, ClientDataHolderVR.getInstance().menuHandOff,
                poseStack);
        } else {
            VREffectsHelper.renderVrFast(partialTick, false, ClientDataHolderVR.getInstance().menuHandMain,
                ClientDataHolderVR.getInstance().menuHandOff, poseStack);
            if (ShadersHelper.isShaderActive() && ClientDataHolderVR.getInstance().vrSettings.shaderGUIRender ==
                VRSettings.ShaderGUIRender.BEFORE_TRANSLUCENT_SOLID)
            {
                // shaders active, and render gui before translucents
                VREffectsHelper.renderVrFast(partialTick, true, ClientDataHolderVR.getInstance().menuHandMain,
                    ClientDataHolderVR.getInstance().menuHandOff, poseStack);
                guiRendered.set(true);
            }
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = Shift.BEFORE, ordinal = 3))
    private void vivecraft$renderVrStuffPart2(
        CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack, @Local(argsOnly = true) float partialTick,
        @Share("guiRendered") LocalBooleanRef guiRendered)
    {
        if (RenderPassType.isVanilla()) return;

        if (this.transparencyChain == null && (!ShadersHelper.isShaderActive() ||
            ClientDataHolderVR.getInstance().vrSettings.shaderGUIRender == VRSettings.ShaderGUIRender.AFTER_TRANSLUCENT
        ))
        {
            // no shaders, or shaders, and gui after translucents
            VREffectsHelper.renderVrFast(partialTick, true, ClientDataHolderVR.getInstance().menuHandMain,
                ClientDataHolderVR.getInstance().menuHandOff, poseStack);
            guiRendered.set(true);
        }
    }

    // if the gui didn't render yet, render it now.
    // or if shaders are on, and option AFTER_SHADER is selected
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void vivecraft$renderVrStuffFinal(
        CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack, @Local(argsOnly = true) float partialTick,
        @Share("guiRendered") LocalBooleanRef guiRendered)
    {
        if (RenderPassType.isVanilla()) return;

        if (!guiRendered.get() && this.transparencyChain == null) {
            VREffectsHelper.renderVrFast(partialTick, true, ClientDataHolderVR.getInstance().menuHandMain,
                ClientDataHolderVR.getInstance().menuHandOff, poseStack);
        }
    }

    @WrapOperation(method = "renderHitOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"))
    private void vivecraft$interactHitBox(
        PoseStack poseStack, VertexConsumer consumer, VoxelShape shape, double x, double y, double z, float red,
        float green, float blue, float alpha, Operation<Void> original)
    {
        if (this.vivecraft$interactOutline) {
            original.call(poseStack, consumer, shape, x, y, z, 1F, 1F, 1F, alpha);
        } else {
            original.call(poseStack, consumer, shape, x, y, z, red, green, blue, alpha);
        }
    }

    @Inject(method = "levelEvent", at = @At("HEAD"))
    private void vivecraft$shakeOnSound(int type, BlockPos pos, int data, CallbackInfo ci) {
        boolean playerNearAndVR = VRState.VR_RUNNING && this.minecraft.player != null &&
            this.minecraft.player.isAlive() && this.minecraft.player.blockPosition().distSqr(pos) < 25.0D;
        if (playerNearAndVR) {
            switch (type) {
                /* pre 1.19.4, they are now separate
                case LevelEvent.LevelEvent.SOUND_CLOSE_IRON_DOOR,
                        LevelEvent.SOUND_CLOSE_WOODEN_DOOR,
                        LevelEvent.SOUND_CLOSE_WOODEN_TRAP_DOOR,
                        LevelEvent.SOUND_CLOSE_FENCE_GATE,
                        LevelEvent.SOUND_CLOSE_IRON_TRAP_DOOR
                        -> ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 250);
                 */
                case LevelEvent.SOUND_ZOMBIE_WOODEN_DOOR,
                     LevelEvent.SOUND_ZOMBIE_IRON_DOOR,
                     LevelEvent.SOUND_ZOMBIE_DOOR_CRASH
                    -> {
                    ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 750);
                    ClientDataHolderVR.getInstance().vr.triggerHapticPulse(1, 750);
                }
                case LevelEvent.SOUND_ANVIL_USED ->
                    ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 500);
                case LevelEvent.SOUND_ANVIL_LAND -> {
                    ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 1250);
                    ClientDataHolderVR.getInstance().vr.triggerHapticPulse(1, 1250);
                }
            }
        }
    }

    @Inject(method = {"initOutline", "initTransparency"}, at = @At("HEAD"))
    private void vivecraft$ensureVanillaPass(CallbackInfo ci) {
        if (VRState.VR_INITIALIZED) {
            RenderPassManager.setVanillaRenderPass();
        }
    }

    @WrapOperation(method = "initTransparency", at = @At(value = "NEW", target = "net/minecraft/resources/ResourceLocation"))
    private ResourceLocation vivecraft$vrTransparency(String location, Operation<ResourceLocation> original) {
        if (VRState.VR_INITIALIZED) {
            return original.call("shaders/post/vrtransparency.json");
        } else {
            return original.call(location);
        }
    }

    @Inject(method = "initTransparency", at = @At("TAIL"))
    private void vivecraft$getVRTargets(CallbackInfo ci) {
        if (VRState.VR_INITIALIZED && this.transparencyChain != null) {
            this.vivecraft$alphaSortVRHandsFramebuffer = this.transparencyChain.getTempTarget("vrhands");
            this.vivecraft$alphaSortVROccludedFramebuffer = this.transparencyChain.getTempTarget("vroccluded");
            this.vivecraft$alphaSortVRUnoccludedFramebuffer = this.transparencyChain.getTempTarget("vrunoccluded");
        }
    }

    @Inject(method = "deinitTransparency", at = @At("TAIL"))
    private void vivecraft$removeVRTargets(CallbackInfo ci) {
        this.vivecraft$alphaSortVRHandsFramebuffer = null;
        this.vivecraft$alphaSortVROccludedFramebuffer = null;
        this.vivecraft$alphaSortVRUnoccludedFramebuffer = null;
    }

    @Override
    @Unique
    public Entity vivecraft$getRenderedEntity() {
        return this.vivecraft$renderedEntity;
    }

    @Override
    @Unique
    public RenderTarget vivecraft$getAlphaSortVROccludedFramebuffer() {
        return this.vivecraft$alphaSortVROccludedFramebuffer;
    }

    @Override
    @Unique
    public RenderTarget vivecraft$getAlphaSortVRUnoccludedFramebuffer() {
        return this.vivecraft$alphaSortVRUnoccludedFramebuffer;
    }

    @Override
    @Unique
    public RenderTarget vivecraft$getAlphaSortVRHandsFramebuffer() {
        return this.vivecraft$alphaSortVRHandsFramebuffer;
    }
}
