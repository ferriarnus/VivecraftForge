package org.vivecraft.client_vr.render.helpers;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL43;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.extensions.WindowExtension;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.render.MirrorNotification;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.VRShaders;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.utils.math.Vector3;
import org.vivecraft.mod_compat_vr.iris.IrisHelper;

public class ShaderHelper {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

    private static float fovReduction = 1.0F;
    private static float waterEffect;
    private static boolean wasInWater;

    /**
     * renders a fullscreen quad with the given shader, and the given RenderTarget bound as "Sampler0"
     * @param instance shader to use to render
     * @param source RenderTarget to sample from
     */
    public static void renderFullscreenQuad(@NotNull ShaderInstance instance, @NotNull RenderTarget source) {
        RenderSystem.colorMask(true, true, true, false);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();

        instance.setSampler("Sampler0", source.getColorTextureId());
        instance.apply();

        drawFullscreenQuad(instance.getVertexFormat());

        instance.clear();
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
    }

    /**
     * tessellates a fullscreen quad and draws it with the bound shader
     * @param format VertexFormat to use for rendering
     */
    private static void drawFullscreenQuad(VertexFormat format) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, format);

        if (format == DefaultVertexFormat.POSITION_TEX) {
            builder.vertex(-1.0, -1.0, 0.0).uv(0.0F, 0.0F).endVertex();
            builder.vertex(1.0, -1.0, 0.0).uv(1.0F, 0.0F).endVertex();
            builder.vertex(1.0, 1.0, 0.0).uv(1.0F, 1.0F).endVertex();
            builder.vertex(-1.0, 1.0, 0.0).uv(0.0F, 1.0F).endVertex();
        } else if (format == DefaultVertexFormat.POSITION_TEX_COLOR) {
            builder.vertex(-1.0, -1.0, 0.0).uv(0.0F, 0.0F)
                .color(255, 255, 255, 255).endVertex();
            builder.vertex(1.0, -1.0, 0.0).uv(1.0F, 0.0F)
                .color(255, 255, 255, 255).endVertex();
            builder.vertex(1.0, 1.0, 0.0).uv(1.0F, 1.0F)
                .color(255, 255, 255, 255).endVertex();
            builder.vertex(-1.0, 1.0, 0.0).uv(0.0F, 1.0F)
                .color(255, 255, 255, 255).endVertex();
        } else {
            throw new IllegalStateException("Unexpected vertex format " + format);
        }

        BufferUploader.draw(builder.end());
    }

    /**
     * does post-processing for the vr pass
     * this includes red damage indicator
     * blue freeze indicator
     * screen dimming when sleeping
     * fov reduction when walking
     * water and portal wobbles
     * @param eye RenderPass that is being post processed, LEFT or RIGHT
     * @param source RenderTarget that holds the rendered image
     * @param partialTick current partial tick
     */
    public static void doVrPostProcess(RenderPass eye, RenderTarget source, float partialTick) {
        if (eye == RenderPass.LEFT) {
            // only update these once per frame, or the effects are twice as fast
            // and could be out of sync between the eyes

            // status effects
            float red = 0.0F;
            float black = 0.0F;
            float blue = 0.0F;
            float time = (float) Util.getMillis() / 1000.0F;

            float pumpkinEffect = 0.0F;
            float portalEffect = 0.0F;

            if (mc.player != null && mc.level != null) {

                boolean isInWater = ((GameRendererExtension) mc.gameRenderer).vivecraft$isInWater();
                if (dataHolder.vrSettings.waterEffect && wasInWater != isInWater) {
                    // water state changed, start effect
                    waterEffect = 2.3F;
                } else {
                    if (isInWater) {
                        // slow falloff in water
                        waterEffect -= 1F / 120F;
                    } else {
                        // fast falloff outside water
                        waterEffect -= 1F / 60F;
                    }

                    if (waterEffect < 0.0F) {
                        waterEffect = 0.0F;
                    }
                }

                wasInWater = isInWater;

                if (IrisHelper.isLoaded() && !IrisHelper.hasWaterEffect()) {
                    waterEffect = 0.0F;
                }

                if (Mth.lerp(partialTick, mc.player.oSpinningEffectIntensity, mc.player.spinningEffectIntensity) >
                    0.0F && !mc.player.hasEffect(MobEffects.CONFUSION))
                {

                }

                float portalTime = Mth.lerp(partialTick, mc.player.oSpinningEffectIntensity,
                    mc.player.spinningEffectIntensity);
                if (dataHolder.vrSettings.portalEffect &&
                    // vanilla check for portal overlay
                    portalTime > 0.0F && !mc.player.hasEffect(MobEffects.CONFUSION))
                {
                    portalEffect = portalTime;
                }

                ItemStack itemstack = mc.player.getInventory().getArmor(3);

                if (dataHolder.vrSettings.pumpkinEffect && itemstack.getItem() == Blocks.CARVED_PUMPKIN.asItem() &&
                    (!itemstack.hasTag() || itemstack.getTag().getInt("CustomModelData") == 0))
                {
                    pumpkinEffect = 1.0F;
                }

                float hurtTimer = (float) mc.player.hurtTime - partialTick;
                float healthPercent = 1.0F - mc.player.getHealth() / mc.player.getMaxHealth();
                healthPercent = (healthPercent - 0.5F) * 0.75F;

                if (dataHolder.vrSettings.hitIndicator && hurtTimer > 0.0F) { // hurt flash
                    hurtTimer = hurtTimer / (float) mc.player.hurtDuration;
                    hurtTimer = healthPercent +
                        Mth.sin(hurtTimer * hurtTimer * hurtTimer * hurtTimer * (float) Math.PI) * 0.5F;
                    red = hurtTimer;
                } else if (dataHolder.vrSettings.lowHealthIndicator) { // red due to low health
                    red = healthPercent * Mth.abs(Mth.sin((2.5F * time) / (1.0F - healthPercent + 0.1F)));

                    if (mc.player.isCreative()) {
                        red = 0.0F;
                    }
                }

                float freeze = mc.player.getPercentFrozen();
                if (dataHolder.vrSettings.freezeEffect && freeze > 0) {
                    blue = red;
                    blue = Math.max(freeze / 2, blue);
                    red = 0;
                }

                if (mc.player.isSleeping()) {
                    black = 0.5F + 0.3F * mc.player.getSleepTimer() * 0.01F;
                }

                if (dataHolder.vr.isWalkingAbout && black < 0.8F) {
                    black = 0.5F;
                }

                // fov reduction when moving
                if (dataHolder.vrSettings.useFOVReduction && dataHolder.vrPlayer.getFreeMove()) {
                    if (Math.abs(mc.player.zza) > 0.0F || Math.abs(mc.player.xxa) > 0.0F) {
                        fovReduction = fovReduction - 0.05F;
                    } else {
                        fovReduction = fovReduction + 0.01F;
                    }
                    fovReduction = Mth.clamp(fovReduction, dataHolder.vrSettings.fovReductionMin, 0.8F);
                } else {
                    fovReduction = 1.0F;
                }
            } else {
                waterEffect = 0.0F;
                fovReduction = 1.0F;
            }

            if (pumpkinEffect > 0.0F) {
                VRShaders._FOVReduction_RadiusUniform.set(0.3F);
                VRShaders._FOVReduction_BorderUniform.set(0.0F);
            } else {
                VRShaders._FOVReduction_RadiusUniform.set(fovReduction);
                VRShaders._FOVReduction_BorderUniform.set(0.06F);
            }

            VRShaders._FOVReduction_OffsetUniform.set(dataHolder.vrSettings.fovRedutioncOffset);

            VRShaders._Overlay_HealthAlpha.set(red);
            VRShaders._Overlay_FreezeAlpha.set(blue);
            VRShaders._Overlay_BlackAlpha.set(black);
            VRShaders._Overlay_time.set(time);
            VRShaders._Overlay_waterAmplitude.set(waterEffect);
            VRShaders._Overlay_portalAmplitutde.set(portalEffect);
            VRShaders._Overlay_pumpkinAmplitutde.set(pumpkinEffect);
        }

        // this needs to be set for each eye
        VRShaders._Overlay_eye.set(eye == RenderPass.LEFT ? 1 : -1);

        ShaderHelper.renderFullscreenQuad(VRShaders.postProcessingShader, source);
    }

    /**
     * draws the desktop mirror to the bound buffer
     */
    public static void drawMirror() {
        if (dataHolder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.OFF &&
            dataHolder.vr.isHMDTracking())
        {
            // no mirror, only show when headset is not tracking, to be able to see the menu with the headset off
            MirrorNotification.notify("Mirror is OFF", true, 1000);
        } else if (dataHolder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY) {
            if (VRShaders.mixedRealityShader != null) {
                ShaderHelper.doMixedRealityMirror();
            } else {
                MirrorNotification.notify("Mixed Reality Shader compile failed, see log for info", true,
                    10000);
            }
        } else if (dataHolder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.DUAL &&
            (!dataHolder.vrSettings.displayMirrorUseScreenshotCamera ||
                !dataHolder.cameraTracker.isVisible()
            ))
        {
            // show both eyes side by side
            RenderTarget leftEye = dataHolder.vrRenderer.framebufferEye0;
            RenderTarget rightEye = dataHolder.vrRenderer.framebufferEye1;

            int screenWidth = ((WindowExtension) (Object) mc.getWindow()).vivecraft$getActualScreenWidth() / 2;
            int screenHeight = ((WindowExtension) (Object) mc.getWindow()).vivecraft$getActualScreenHeight();

            if (leftEye != null) {
                ShaderHelper.blitToScreen(leftEye, 0, screenWidth, screenHeight, 0, 0.0F, 0.0F, false);
            }

            if (rightEye != null) {
                ShaderHelper.blitToScreen(rightEye, screenWidth, screenWidth, screenHeight, 0, 0.0F, 0.0F, false);
            }
        } else {
            // general single buffer case
            float xCrop = 0.0F;
            float yCrop = 0.0F;
            boolean keepAspect = false;
            RenderTarget source = dataHolder.vrRenderer.framebufferEye0;

            if (dataHolder.vrSettings.displayMirrorUseScreenshotCamera &&
                dataHolder.cameraTracker.isVisible())
            {
                source = dataHolder.vrRenderer.cameraFramebuffer;
                keepAspect = true;
            } else if (dataHolder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.FIRST_PERSON) {
                source = dataHolder.vrRenderer.framebufferUndistorted;
            } else if (dataHolder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON) {
                source = dataHolder.vrRenderer.framebufferMR;
            } else if (dataHolder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.GUI) {
                source = GuiHandler.guiFramebuffer;
            } else if (dataHolder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.SINGLE ||
                dataHolder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.OFF)
            {
                if (!dataHolder.vrSettings.displayMirrorLeftEye) {
                    source = dataHolder.vrRenderer.framebufferEye1;
                }
            } else if (dataHolder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.CROPPED) {
                if (!dataHolder.vrSettings.displayMirrorLeftEye) {
                    source = dataHolder.vrRenderer.framebufferEye1;
                }

                xCrop = dataHolder.vrSettings.mirrorCrop;
                yCrop = dataHolder.vrSettings.mirrorCrop;
                keepAspect = true;
            }
            // Debug
            // source = DataHolder.getInstance().vrRenderer.telescopeFramebufferR;
            //
            if (source != null) {
                ShaderHelper.blitToScreen(source,
                    0, ((WindowExtension) (Object) mc.getWindow()).vivecraft$getActualScreenWidth(),
                    ((WindowExtension) (Object) mc.getWindow()).vivecraft$getActualScreenHeight(), 0,
                    xCrop, yCrop, keepAspect);
            }
        }

        // draw mirror text
        MirrorNotification.render();
    }

    public static void doMixedRealityMirror() {
        // set viewport to fullscreen, since it would be still on the one from the last pass
        RenderSystem.viewport(0, 0,
            ((WindowExtension) (Object) mc.getWindow()).vivecraft$getActualScreenWidth(),
            ((WindowExtension) (Object) mc.getWindow()).vivecraft$getActualScreenHeight());

        Vec3 camPlayer = dataHolder.vrPlayer.vrdata_room_pre.getHeadPivot()
            .subtract(dataHolder.vrPlayer.vrdata_room_pre.getEye(RenderPass.THIRD).getPosition());
        Matrix4f viewMatrix = dataHolder.vrPlayer.vrdata_room_pre.getEye(RenderPass.THIRD)
            .getMatrix().transposed().toMCMatrix();
        Vector3 cameraLook = dataHolder.vrPlayer.vrdata_room_pre.getEye(RenderPass.THIRD).getMatrix()
            .transform(Vector3.forward());

        // set uniforms
        VRShaders._MixedReality_projectionMatrix.set(
            ((GameRendererExtension) mc.gameRenderer).vivecraft$getThirdPassProjectionMatrix());
        VRShaders._MixedReality_viewMatrix.set(viewMatrix);

        VRShaders._MixedReality_hmdViewPosition.set((float) camPlayer.x, (float) camPlayer.y, (float) camPlayer.z);
        VRShaders._MixedReality_hmdPlaneNormal.set(-cameraLook.getX(), 0.0F, -cameraLook.getZ());

        boolean alphaMask = dataHolder.vrSettings.mixedRealityUnityLike && dataHolder.vrSettings.mixedRealityAlphaMask;

        if (!alphaMask) {
            VRShaders._MixedReality_keyColorUniform.set(
                (float) dataHolder.vrSettings.mixedRealityKeyColor.getRed() / 255.0F,
                (float) dataHolder.vrSettings.mixedRealityKeyColor.getGreen() / 255.0F,
                (float) dataHolder.vrSettings.mixedRealityKeyColor.getBlue() / 255.0F);
        } else {
            VRShaders._MixedReality_keyColorUniform.set(0F, 0F, 0F);
        }
        VRShaders._MixedReality_alphaModeUniform.set(alphaMask ? 1 : 0);

        VRShaders._MixedReality_firstPersonPassUniform.set(dataHolder.vrSettings.mixedRealityUnityLike ? 1 : 0);

        // bind textures
        VRShaders.mixedRealityShader.setSampler("thirdPersonColor",
            dataHolder.vrRenderer.framebufferMR.getColorTextureId());
        VRShaders.mixedRealityShader.setSampler("thirdPersonDepth",
            dataHolder.vrRenderer.framebufferMR.getDepthTextureId());

        if (dataHolder.vrSettings.mixedRealityUnityLike) {
            RenderTarget source;
            if (dataHolder.vrSettings.displayMirrorUseScreenshotCamera && dataHolder.cameraTracker.isVisible()) {
                source = dataHolder.vrRenderer.cameraFramebuffer;
            } else if (dataHolder.vrSettings.mixedRealityUndistorted) {
                source = dataHolder.vrRenderer.framebufferUndistorted;
            } else {
                if (dataHolder.vrSettings.displayMirrorLeftEye) {
                    source = dataHolder.vrRenderer.framebufferEye0;
                } else {
                    source = dataHolder.vrRenderer.framebufferEye1;
                }
            }
            VRShaders.mixedRealityShader.setSampler("firstPersonColor", source.getColorTextureId());
        }

        VRShaders.mixedRealityShader.apply();

        drawFullscreenQuad(VRShaders.mixedRealityShader.getVertexFormat());

        VRShaders.mixedRealityShader.clear();
    }

    /**
     * uses a lanczos filter to scale the source RenderTarget to the secondPass RenderTarget size
     * @param source RenderTarget with the low/high resolution frame
     * @param firstPass RenderTarget with source height and target width, for the intermediary step
     * @param secondPass RenderTarget with the target size
     */
    public static void doFSAA(RenderTarget source, RenderTarget firstPass, RenderTarget secondPass) {
        if (firstPass == null) {
            dataHolder.vrRenderer.reinitFrameBuffers("FSAA Setting Changed");
        } else {
            RenderSystem.disableBlend();
            // set to always, since we want to override the depth
            // disabling depth test would disable depth writes
            RenderSystem.depthFunc(GL43.GL_ALWAYS);

            // first pass, horizontal
            firstPass.bindWrite(true);

            VRShaders.lanczosShader.setSampler("Sampler0", source.getColorTextureId());
            VRShaders.lanczosShader.setSampler("Sampler1", source.getDepthTextureId());
            VRShaders._Lanczos_texelWidthOffsetUniform.set(1.0F / (3.0F * (float) firstPass.viewWidth));
            VRShaders._Lanczos_texelHeightOffsetUniform.set(0.0F);
            VRShaders.lanczosShader.apply();

            drawFullscreenQuad(VRShaders.lanczosShader.getVertexFormat());

            // second pass, vertical
            secondPass.bindWrite(true);

            VRShaders.lanczosShader.setSampler("Sampler0", firstPass.getColorTextureId());
            VRShaders.lanczosShader.setSampler("Sampler1", firstPass.getDepthTextureId());
            VRShaders._Lanczos_texelWidthOffsetUniform.set(0.0F);
            VRShaders._Lanczos_texelHeightOffsetUniform.set(1.0F / (3.0F * (float) secondPass.viewHeight));
            VRShaders.lanczosShader.apply();

            drawFullscreenQuad(VRShaders.lanczosShader.getVertexFormat());

            // Clean up time
            VRShaders.lanczosShader.clear();
            secondPass.unbindWrite();

            RenderSystem.depthFunc(GL43.GL_LEQUAL);
            RenderSystem.enableBlend();
        }
    }

    /**
     * blits the given {@code source} RenderTarget to the screen/bound buffer<br>
     * the {@code source} is drawn to the rectangle at {@code left},{@code top} with a size of {@code width},{@code height}<br>
     * if {@code xCropFactor} or {@code yCropFactor} are non 0 the {@code source} gets zoomed in
     * @param source RenderTarget to draw to the screen
     * @param left left edge of the target area
     * @param width width of the target area
     * @param height height of the target area
     * @param top top edge of the target area
     * @param xCropFactor vertical crop factor for the {@code source}
     * @param yCropFactor horizontal crop factor for the {@code source}
     * @param keepAspect keeps the aspect ratio in takt when cropping the buffer
     */
    public static void blitToScreen(RenderTarget source, int left, int width, int height, int top, float xCropFactor, float yCropFactor, boolean keepAspect) {
        RenderSystem.assertOnRenderThread();
        RenderSystem.colorMask(true, true, true, false);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.viewport(left, top, width, height);
        RenderSystem.disableBlend();

        float drawAspect = (float) width / (float) height;
        float bufferAspect = (float) source.viewWidth / (float) source.viewHeight;

        float xMin = xCropFactor;
        float yMin = yCropFactor;
        float xMax = 1.0F - xCropFactor;
        float yMax = 1.0F - yCropFactor;

        if (keepAspect) {
            if (drawAspect > bufferAspect) {
                // destination is wider than the buffer
                float heightAspect = (bufferAspect / drawAspect) * (0.5F - yCropFactor);

                yMin = 0.5F - heightAspect;
                yMax = 0.5F + heightAspect;
            } else {
                // destination is taller than the buffer
                float widthAspect = (drawAspect / bufferAspect) * (0.5F - xCropFactor);

                xMin = 0.5F - widthAspect;
                xMax = 0.5F + widthAspect;
            }
        }

        ShaderInstance instance = VRShaders.blitVRShader;
        instance.setSampler("DiffuseSampler", source.getColorTextureId());

        instance.apply();

        BufferBuilder bufferbuilder = RenderSystem.renderThreadTesselator().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, instance.getVertexFormat());

        bufferbuilder.vertex(-1.0F, -1.0F, 0.0F).uv(xMin, yMin).endVertex();
        bufferbuilder.vertex(1.0F, -1.0F, 0.0F).uv(xMax, yMin).endVertex();
        bufferbuilder.vertex(1.0F, 1.0F, 0.0F).uv(xMax, yMax).endVertex();
        bufferbuilder.vertex(-1.0F, 1.0F, 0.0F).uv(xMin, yMax).endVertex();
        BufferUploader.draw(bufferbuilder.end());
        instance.clear();

        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
    }
}
