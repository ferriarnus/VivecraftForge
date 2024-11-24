package org.vivecraft.mixin.client_vr.renderer;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Triple;
import javax.annotation.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client.Xevents;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.MethodHolder;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.XRCamera;
import org.vivecraft.client_vr.render.helpers.DebugRenderHelper;
import org.vivecraft.client_vr.render.helpers.RenderHelper;
import org.vivecraft.client_vr.render.helpers.VRArmHelper;
import org.vivecraft.client_vr.render.helpers.VREffectsHelper;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassManager;
import org.vivecraft.client_xr.render_pass.RenderPassType;
import org.vivecraft.client_xr.render_pass.WorldRenderPass;
import org.vivecraft.mod_compat_vr.immersiveportals.ImmersivePortalsHelper;

import java.io.IOException;

@Mixin(GameRenderer.class)
public abstract class GameRendererVRMixin
    implements ResourceManagerReloadListener, AutoCloseable, GameRendererExtension {

    @Unique
    private static final ClientDataHolderVR vivecraft$DATA_HOLDER = ClientDataHolderVR.getInstance();
    @Unique
    private static final float vivecraft$MIN_CLIP_DISTANCE = 0.02F;
    @Unique
    private Vec3 vivecraft$crossVec;
    @Unique
    private Matrix4f vivecraft$thirdPassProjectionMatrix = new Matrix4f();
    @Unique
    private boolean vivecraft$inwater;
    @Unique
    private float vivecraft$inBlock = 0.0F;
    @Unique
    private double vivecraft$rveX;
    @Unique
    private double vivecraft$rveY;
    @Unique
    private double vivecraft$rveZ;
    @Unique
    private double vivecraft$rvelastX;
    @Unique
    private double vivecraft$rvelastY;
    @Unique
    private double vivecraft$rvelastZ;
    @Unique
    private double vivecraft$rveprevX;
    @Unique
    private double vivecraft$rveprevY;
    @Unique
    private double vivecraft$rveprevZ;
    @Unique
    private float vivecraft$rveyaw;
    @Unique
    private float vivecraft$rvepitch;
    @Unique
    private float vivecraft$rvelastyaw;
    @Unique
    private float vivecraft$rvelastpitch;
    @Unique
    private float vivecraft$rveHeight;
    @Unique
    private boolean vivecraft$cached;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private float fov;

    @Shadow
    private float oldFov;

    @Shadow
    public abstract Matrix4f getProjectionMatrix(double fov);

    @Shadow
    protected abstract double getFov(Camera camera, float partialTick, boolean useFOVSetting);

    @Shadow
    public abstract void resetProjectionMatrix(Matrix4f projectionMatrix);

    @Shadow
    @Final
    private Camera mainCamera;

    @Shadow
    @Nullable
    private PostChain postEffect;

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/client/Camera"))
    private Camera vivecraft$replaceCamera() {
        return new XRCamera();
    }

    @Inject(method = {"shutdownEffect", "checkEntityPostEffect", "cycleEffect", "loadEffect"}, at = @At("HEAD"))
    private void vivecraft$shutdownVREffects(CallbackInfo ci) {
        if (VRState.VR_INITIALIZED) {
            RenderPassManager.setVanillaRenderPass();
            RenderPassManager.INSTANCE.vanillaPostEffect = null;
            if (WorldRenderPass.STEREO_XR != null && WorldRenderPass.STEREO_XR.postEffect != null) {
                WorldRenderPass.STEREO_XR.postEffect.close();
                WorldRenderPass.STEREO_XR.postEffect = null;
            }
            if (WorldRenderPass.CENTER != null && WorldRenderPass.CENTER.postEffect != null) {
                WorldRenderPass.CENTER.postEffect.close();
                WorldRenderPass.CENTER.postEffect = null;
            }
        }
    }

    @Inject(method = "loadEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PostChain;resize(II)V", shift = Shift.AFTER))
    private void vivecraft$loadVREffects(ResourceLocation resourceLocation, CallbackInfo ci) throws IOException {
        if (VRState.VR_INITIALIZED) {
            RenderPassManager.INSTANCE.vanillaPostEffect = this.postEffect;
            if (WorldRenderPass.STEREO_XR != null) {
                WorldRenderPass.STEREO_XR.postEffect = WorldRenderPass.createPostChain(resourceLocation, WorldRenderPass.STEREO_XR.target);
            }
            if (WorldRenderPass.CENTER != null) {
                WorldRenderPass.CENTER.postEffect = WorldRenderPass.createPostChain(resourceLocation, WorldRenderPass.CENTER.target);
            }
        }
    }

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void vivecraft$skipFirstPick(CallbackInfo ci) {
        if (VRState.VR_RUNNING && vivecraft$DATA_HOLDER.vrPlayer.vrdata_world_render == null) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "pick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult vivecraft$changeRaytrace(
        Entity instance, double hitDistance, float partialTick, boolean hitFluids, Operation<HitResult> original)
    {
        if (!VRState.VR_RUNNING) {
            return original.call(instance, hitDistance, partialTick, hitFluids);
        } else {
            this.vivecraft$crossVec = vivecraft$DATA_HOLDER.vrPlayer.AimedPointAtDistance(vivecraft$DATA_HOLDER.vrPlayer.vrdata_world_render, 0, hitDistance);
            return vivecraft$DATA_HOLDER.vrPlayer.rayTraceBlocksVR(vivecraft$DATA_HOLDER.vrPlayer.vrdata_world_render, 0, hitDistance, hitFluids);
        }
    }

    @WrapOperation(method = "pick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 vivecraft$changeRayStart(Entity instance, float partialTick, Operation<Vec3> original) {
        if (!VRState.VR_RUNNING) {
            return original.call(instance, partialTick);
        } else {
            return vivecraft$DATA_HOLDER.vrPlayer.vrdata_world_render.getController(0).getPosition();
        }
    }

    @WrapOperation(method = "pick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 vivecraft$changeRayDirection(Entity instance, float partialTick, Operation<Vec3> original) {
        if (!VRState.VR_RUNNING) {
            return original.call(instance, partialTick);
        } else {
            return new Vec3(vivecraft$DATA_HOLDER.vrPlayer.vrdata_world_render.getController(0).getDirection());
        }
    }

    @ModifyReturnValue(method = {"method_18144", "lambda$getMouseOver$61"}, at = @At("RETURN"))
    private static boolean vivecraft$dontHitRiddenEntity(boolean original, Entity entity) {
        // it is technically possible to hit the ridden entity when the distance is 0, we don't want that
        return original && (!VRState.VR_RUNNING || entity != Minecraft.getInstance().getCameraEntity().getVehicle());
    }

    @Inject(method = "tickFov", at = @At("HEAD"), cancellable = true)
    private void vivecraft$noFOVChangeInVR(CallbackInfo ci) {
        if (!RenderPassType.isVanilla()) {
            this.oldFov = this.fov = 1.0f;
            ci.cancel();
        }
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void vivecraft$fixedFOV(CallbackInfoReturnable<Double> cir) {
        // some mods don't expect this to be called outside levels
        if (this.minecraft.level == null || MethodHolder.isInMenuRoom()) {
            cir.setReturnValue(Double.valueOf(this.minecraft.options.fov().get()));
        }
    }

    @WrapOperation(method = "getProjectionMatrix", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;setPerspective(FFFF)Lorg/joml/Matrix4f;", remap = false), remap = true)
    private Matrix4f vivecraft$customProjectionMatrix(
        Matrix4f instance, float fovy, float aspect, float zNear, float zFar, Operation<Matrix4f> original) {
        if (VRState.VR_RUNNING) {
            zNear = vivecraft$MIN_CLIP_DISTANCE;
            if (MethodHolder.isInMenuRoom()) {
                // use 16 Chunks as minimum, to have no issues with clipping in the menuworld
                zFar = Math.max(zFar, 1024.0F);
            }

            if (vivecraft$DATA_HOLDER.currentPass == RenderPass.LEFT ||
                vivecraft$DATA_HOLDER.currentPass == RenderPass.RIGHT)
            {
                return vivecraft$DATA_HOLDER.vrRenderer.getCachedProjectionMatrix(
                    vivecraft$DATA_HOLDER.currentPass.ordinal(), zNear, zFar);
            }

            aspect = switch (vivecraft$DATA_HOLDER.currentPass) {
                case THIRD ->
                    vivecraft$DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY ?
                        vivecraft$DATA_HOLDER.vrSettings.mixedRealityAspectRatio : aspect;
                case CAMERA -> (float) vivecraft$DATA_HOLDER.vrRenderer.cameraFramebuffer.viewWidth /
                    (float) vivecraft$DATA_HOLDER.vrRenderer.cameraFramebuffer.viewHeight;
                case SCOPEL, SCOPER -> 1.0F;
                default -> aspect;
            };

            fovy = switch (vivecraft$DATA_HOLDER.currentPass) {
                case THIRD -> Mth.DEG_TO_RAD * vivecraft$DATA_HOLDER.vrSettings.mixedRealityFov;
                case CAMERA -> Mth.DEG_TO_RAD * vivecraft$DATA_HOLDER.vrSettings.handCameraFov;
                case SCOPEL, SCOPER -> Mth.DEG_TO_RAD * (70F / 8F);
                default -> fovy;
            };
        }

        Matrix4f proj = original.call(instance, fovy, aspect, zNear, zFar);

        if (VRState.VR_RUNNING && vivecraft$DATA_HOLDER.currentPass == RenderPass.THIRD) {
            this.vivecraft$thirdPassProjectionMatrix = proj;
        }
        return proj;
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void vivecraft$shouldDrawBlockOutline(CallbackInfoReturnable<Boolean> cir) {
        if (VRState.VR_RUNNING) {
            if (vivecraft$DATA_HOLDER.teleportTracker.isAiming() ||
                vivecraft$DATA_HOLDER.vrSettings.renderBlockOutlineMode == VRSettings.RenderPointerElement.NEVER)
            {
                // don't render outline when aiming with tp, or the user disabled it
                cir.setReturnValue(false);
            } else if (vivecraft$DATA_HOLDER.vrSettings.renderBlockOutlineMode == VRSettings.RenderPointerElement.ALWAYS) {
                // skip vanilla check and always render the outline
                cir.setReturnValue(true);
            }
            // VRSettings.RenderPointerElement.WITH_HUD uses the vanilla behaviour
        }
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isWindowActive()Z"))
    private boolean vivecraft$noPauseOnFocusLoss(boolean windowActive) {
        return windowActive || VRState.VR_RUNNING;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void vivecraft$renderFaceOverlay(
        GameRenderer instance, float partialTick, long finishTimeNano, PoseStack poseStack, Operation<Void> original) {
        original.call(instance, partialTick, finishTimeNano, poseStack);
        if (VRState.VR_RUNNING && vivecraft$DATA_HOLDER.currentPass != RenderPass.THIRD &&
            vivecraft$DATA_HOLDER.currentPass != RenderPass.CAMERA)
        {
            VREffectsHelper.renderFaceOverlay(partialTick, poseStack);
        }
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/GameRenderer;effectActive:Z"))
    private boolean vivecraft$noEffectInThird(boolean effectActive) {
        return effectActive && ClientDataHolderVR.getInstance().currentPass != RenderPass.THIRD;
    }

    @Unique
    private boolean vivecraft$shouldDrawScreen = false;
    @Unique
    private boolean vivecraft$shouldDrawGui = false;

    @Override
    @Unique
    public void vivecraft$setShouldDrawScreen(boolean shouldDrawScreen) {
        this.vivecraft$shouldDrawScreen = shouldDrawScreen;
    }

    @Override
    @Unique
    public void vivecraft$setShouldDrawGui(boolean shouldDrawGui) {
        this.vivecraft$shouldDrawGui = shouldDrawGui;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getWindow()Lcom/mojang/blaze3d/platform/Window;", ordinal = 6), cancellable = true)
    private void vivecraft$mainMenu(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        if (RenderPassType.isVanilla()) {
            return;
        }

        if (!renderLevel && this.vivecraft$shouldDrawScreen) {
            this.vivecraft$shouldDrawScreen = false;
            if (this.vivecraft$shouldDrawGui) {
                // when the gui is rendered it is expected that something got pushed to the profiler before
                // so do that now
                this.minecraft.getProfiler().push("vanillaGuiSetup");
            }
            return;
        }
        if (!renderLevel || this.minecraft.level == null || MethodHolder.isInMenuRoom()) {
            if (!renderLevel || this.minecraft.level == null) {
                // no "level" got pushed so do a manual push
                this.minecraft.getProfiler().push("MainMenu");
            } else {
                // do a popPush
                this.minecraft.getProfiler().popPush("MainMenu");
            }
            GL11.glDisable(GL11.GL_STENCIL_TEST);

            PoseStack poseStack = new PoseStack();
            RenderHelper.applyVRModelView(vivecraft$DATA_HOLDER.currentPass, poseStack);
            VREffectsHelper.renderGuiLayer(partialTick, true, poseStack);
            DebugRenderHelper.renderDebug(poseStack, partialTick);

            if (KeyboardHandler.SHOWING) {
                if (vivecraft$DATA_HOLDER.vrSettings.physicalKeyboard) {
                    VREffectsHelper.renderPhysicalKeyboard(partialTick, poseStack);
                } else {
                    VREffectsHelper.render2D(partialTick, KeyboardHandler.FRAMEBUFFER, KeyboardHandler.POS_ROOM,
                        KeyboardHandler.ROTATION_ROOM, vivecraft$DATA_HOLDER.vrSettings.menuAlwaysFollowFace && MethodHolder.isInMenuRoom(), poseStack);
                }
            }

            if (vivecraft$DATA_HOLDER.currentPass != RenderPass.CAMERA &&
                (vivecraft$DATA_HOLDER.currentPass != RenderPass.THIRD ||
                    vivecraft$DATA_HOLDER.vrSettings.mixedRealityRenderHands
                ))
            {
                VRArmHelper.renderVRHands(partialTick, true, true, true, true, poseStack);
            }
        }
        // pop the "level" push, since that would happen after this
        this.minecraft.getProfiler().pop();
        ci.cancel();
    }

    @ModifyVariable(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getWindow()Lcom/mojang/blaze3d/platform/Window;", shift = Shift.AFTER, ordinal = 6), ordinal = 0, argsOnly = true)
    private boolean vivecraft$renderGui(boolean renderLevel) {
        return RenderPassType.isVanilla() ? renderLevel : this.vivecraft$shouldDrawGui;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemActivationAnimation(IIF)V"))
    private void vivecraft$noItemActivationAnimationOnGUI(
        GameRenderer instance, int width, int height, float partialTick, Operation<Void> original) {
        if (RenderPassType.isVanilla()) {
            original.call(instance, width, height, partialTick);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
    private void vivecraft$noGUIWithViewOnly(
        Gui instance, GuiGraphics guiGraphics, float partialTick, Operation<Void> original)
    {
        if (RenderPassType.isVanilla() || !ClientDataHolderVR.VIEW_ONLY) {
            original.call(instance, guiGraphics, partialTick);
        }
    }

    @Inject(method = "takeAutoScreenshot", at = @At("HEAD"), cancellable = true)
    private void vivecraft$noScreenshotInMenu(CallbackInfo ci) {
        if (VRState.VR_RUNNING && MethodHolder.isInMenuRoom()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void vivecraft$cancelConfusionOverlayOnGUI(CallbackInfo ci) {
        if (vivecraft$DATA_HOLDER.currentPass == RenderPass.GUI) {
            ci.cancel();
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void vivecraft$cancelBobHurt(CallbackInfo ci) {
        if (!RenderPassType.isVanilla()) {
            ci.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void vivecraft$cancelBobView(CallbackInfo ci) {
        if (!RenderPassType.isVanilla()) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "renderItemActivationAnimation", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void vivecraft$noTranslateItemInVR(
        PoseStack instance, float x, float y, float z, Operation<Void> original) {
        if (RenderPassType.isVanilla()) {
            original.call(instance, x, y, z);
        }
    }

    @WrapOperation(method = "renderItemActivationAnimation", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void vivecraft$noScaleItem(
        PoseStack poseStack, float x, float y, float z, Operation<Void> original, @Local(ordinal = 5) float progress)
    {
        if (RenderPassType.isVanilla()) {
            original.call(poseStack, x, y, z);
        } else {
            float sinProgress = Mth.sin(progress) * 0.5F;
            poseStack.translate(0.0F, 0.0F, sinProgress - 1.0F);
            if (ClientDataHolderVR.getInstance().currentPass == RenderPass.THIRD) {
                sinProgress *= ClientDataHolderVR.getInstance().vrSettings.mixedRealityFov / 70.0F;
            }
            RenderHelper.applyVRModelView(ClientDataHolderVR.getInstance().currentPass, poseStack);
            RenderHelper.applyStereo(ClientDataHolderVR.getInstance().currentPass, poseStack);

            // call the scale with original to allow operation stacking
            original.call(poseStack, sinProgress, sinProgress, sinProgress);

            poseStack.mulPose(Axis.YP.rotation(-ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().getEye(ClientDataHolderVR.getInstance().currentPass).getYawRad()));
            poseStack.mulPose(Axis.XP.rotation(-ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().getEye(ClientDataHolderVR.getInstance().currentPass).getPitchRad()));
        }
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;pick(F)V"))
    private void vivecraft$onlyOnePick(GameRenderer instance, float partialTick, Operation<Void> original) {
        if (RenderPassType.isVanilla()) {
            original.call(instance, partialTick);
            return;
        } else if (vivecraft$DATA_HOLDER.isFirstPass &&
            !(ImmersivePortalsHelper.isLoaded() && ImmersivePortalsHelper.isRenderingPortal()))
        {
            original.call(instance, partialTick);

            if (this.minecraft.hitResult != null && this.minecraft.hitResult.getType() != HitResult.Type.MISS) {
                this.vivecraft$crossVec = this.minecraft.hitResult.getLocation();
            }

            if (this.minecraft.screen == null) {
                vivecraft$DATA_HOLDER.teleportTracker.updateTeleportDestinations(this.minecraft.player);
            }
        }

        this.vivecraft$cacheRVEPos((LivingEntity) this.minecraft.getCameraEntity());
        this.vivecraft$setupRVE();
        this.vivecraft$setupOverlayStatus();
    }

    @ModifyVariable(method = "renderLevel", at = @At(value = "STORE"))
    private int vivecraft$reduceNauseaSpeed(int oldVal) {
        if (!RenderPassType.isVanilla()) {
            return oldVal / 5;
        } else {
            return oldVal;
        }
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"))
    private float vivecraft$reduceNauseaAffect(float delta, float start, float end, Operation<Float> original) {
        if (!RenderPassType.isVanilla()) {
            // scales down the effect from (1,0.65) to (1,0.9)
            return original.call(delta, start, end) * 0.4F;
        } else {
            return original.call(delta, start, end);
        }
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/GameRenderer;renderHand:Z"))
    private boolean vivecraft$noHandsInVR(boolean renderHand) {
        return renderHand && RenderPassType.isVanilla();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void vivecraft$disableStencil(CallbackInfo ci) {
        if (!RenderPassType.isVanilla()) {
            VREffectsHelper.disableStencilTest();
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "TAIL"))
    private void vivecraft$restoreRVE(CallbackInfo ci) {
        if (!RenderPassType.isVanilla()) {
            this.vivecraft$restoreRVEPos((LivingEntity) this.minecraft.getCameraEntity());
        }
    }

    @Override
    @Unique
    public void vivecraft$setupRVE() {
        if (this.vivecraft$cached) {
            VRData.VRDevicePose eyePose = vivecraft$DATA_HOLDER.vrPlayer.vrdata_world_render
                .getEye(vivecraft$DATA_HOLDER.currentPass);
            Vec3 eye = eyePose.getPosition();
            LivingEntity entity = (LivingEntity) this.minecraft.getCameraEntity();
            entity.setPosRaw(eye.x, eye.y, eye.z);
            entity.xOld = eye.x;
            entity.yOld = eye.y;
            entity.zOld = eye.z;
            entity.xo = eye.x;
            entity.yo = eye.y;
            entity.zo = eye.z;
            entity.setXRot(-eyePose.getPitch());
            entity.xRotO = entity.getXRot();
            entity.setYRot(eyePose.getYaw());
            entity.yHeadRot = entity.getYRot();
            entity.yHeadRotO = entity.getYRot();
            // non 0 to fix some division by 0 issues
            entity.eyeHeight = 0.0001F;
        }
    }

    @Override
    @Unique
    public void vivecraft$cacheRVEPos(LivingEntity entity) {
        if (this.minecraft.getCameraEntity() != null && !this.vivecraft$cached) {
            this.vivecraft$rveX = entity.getX();
            this.vivecraft$rveY = entity.getY();
            this.vivecraft$rveZ = entity.getZ();
            this.vivecraft$rvelastX = entity.xOld;
            this.vivecraft$rvelastY = entity.yOld;
            this.vivecraft$rvelastZ = entity.zOld;
            this.vivecraft$rveprevX = entity.xo;
            this.vivecraft$rveprevY = entity.yo;
            this.vivecraft$rveprevZ = entity.zo;
            this.vivecraft$rveyaw = entity.yHeadRot;
            this.vivecraft$rvepitch = entity.getXRot();
            this.vivecraft$rvelastyaw = entity.yHeadRotO;
            this.vivecraft$rvelastpitch = entity.xRotO;
            this.vivecraft$rveHeight = entity.getEyeHeight();
            this.vivecraft$cached = true;
        }
    }

    @Override
    @Unique
    public void vivecraft$restoreRVEPos(LivingEntity entity) {
        if (entity != null) {
            entity.setPosRaw(this.vivecraft$rveX, this.vivecraft$rveY, this.vivecraft$rveZ);
            entity.xOld = this.vivecraft$rvelastX;
            entity.yOld = this.vivecraft$rvelastY;
            entity.zOld = this.vivecraft$rvelastZ;
            entity.xo = this.vivecraft$rveprevX;
            entity.yo = this.vivecraft$rveprevY;
            entity.zo = this.vivecraft$rveprevZ;
            entity.setYRot(this.vivecraft$rveyaw);
            entity.setXRot(this.vivecraft$rvepitch);
            entity.yRotO = this.vivecraft$rvelastyaw;
            entity.xRotO = this.vivecraft$rvelastpitch;
            entity.yHeadRot = this.vivecraft$rveyaw;
            entity.yHeadRotO = this.vivecraft$rvelastyaw;
            entity.eyeHeight = this.vivecraft$rveHeight;
            this.vivecraft$cached = false;
        }
    }

    @Override
    @Unique
    public double vivecraft$getRveY() {
        return this.vivecraft$rveY;
    }

    @Override
    @Unique
    public Vec3 vivecraft$getRvePos(float partialTick) {
        return new Vec3(
            Mth.lerp(partialTick, this.vivecraft$rvelastX, this.vivecraft$rveX),
            Mth.lerp(partialTick, this.vivecraft$rvelastY, this.vivecraft$rveY),
            Mth.lerp(partialTick, this.vivecraft$rvelastZ, this.vivecraft$rveZ)
        );
    }

    @Unique
    private void vivecraft$setupOverlayStatus() {
        this.vivecraft$inBlock = 0.0F;
        this.vivecraft$inwater = false;

        if (!this.minecraft.player.isSpectator() && !MethodHolder.isInMenuRoom() && this.minecraft.player.isAlive()) {
            Vec3 cameraPos = RenderHelper.getSmoothCameraPosition(vivecraft$DATA_HOLDER.currentPass,
                vivecraft$DATA_HOLDER.vrPlayer.vrdata_world_render);
            Triple<Float, BlockState, BlockPos> triple = VREffectsHelper.getNearOpaqueBlock(cameraPos,
                vivecraft$MIN_CLIP_DISTANCE);

            if (triple != null &&
                !Xevents.renderBlockOverlay(this.minecraft.player, new PoseStack(), triple.getMiddle(),
                    triple.getRight()))
            {
                this.vivecraft$inBlock = triple.getLeft();
            } else {
                this.vivecraft$inBlock = 0.0F;
            }

            this.vivecraft$inwater = this.minecraft.player.isEyeInFluid(FluidTags.WATER) &&
                !Xevents.renderWaterOverlay(this.minecraft.player, new PoseStack());
        }
    }

    @Override
    @Unique
    public boolean vivecraft$isInWater() {
        return this.vivecraft$inwater;
    }

    @Override
    @Unique
    public float vivecraft$isInBlock() {
        return this.vivecraft$inBlock;
    }

    @Override
    @Unique
    public Vec3 vivecraft$getCrossVec() {
        return this.vivecraft$crossVec;
    }

    @Override
    @Unique
    public float vivecraft$getMinClipDistance() {
        return vivecraft$MIN_CLIP_DISTANCE;
    }

    @Override
    @Unique
    public Matrix4f vivecraft$getThirdPassProjectionMatrix() {
        return this.vivecraft$thirdPassProjectionMatrix;
    }

    @Override
    @Unique
    public void vivecraft$resetProjectionMatrix(float partialTick) {
        this.resetProjectionMatrix(this.getProjectionMatrix(this.getFov(this.mainCamera, partialTick, true)));
    }
}
