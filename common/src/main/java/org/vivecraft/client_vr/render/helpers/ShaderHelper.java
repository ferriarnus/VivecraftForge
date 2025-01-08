package org.vivecraft.client_vr.render.helpers;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.ShaderProgram;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL43;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.render.MirrorNotification;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.VRShaders;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.mod_compat_vr.iris.IrisHelper;

import java.util.Objects;

public class ShaderHelper {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final ClientDataHolderVR DATA_HOLDER = ClientDataHolderVR.getInstance();

    private static float FOV_REDUCTION = 1.0F;
    private static float WATER_EFFECT;
    private static boolean WAS_IN_WATER;

    /**
     * renders a fullscreen quad with the given shader, and the given RenderTarget bound as "Sampler0"
     *
     * @param instance shader to use to render
     * @param source   RenderTarget to sample from
     */
    public static void renderFullscreenQuad(@NotNull ShaderProgram instance, @NotNull RenderTarget source) {
        RenderSystem.colorMask(true, true, true, false);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();

        CompiledShaderProgram program = Objects.requireNonNull(
            RenderSystem.setShader(instance), "required shader not loaded");

        program.bindSampler("Sampler0", source.getColorTextureId());
        program.apply();

        drawFullscreenQuad(instance.vertexFormat());

        program.clear();
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
    }

    /**
     * tessellates a fullscreen quad and draws it with the bound shader
     *
     * @param format VertexFormat to use for rendering
     */
    private static void drawFullscreenQuad(VertexFormat format) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, format);

        if (format == DefaultVertexFormat.POSITION_TEX) {
            builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
            builder.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
            builder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
            builder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);
        } else if (format == DefaultVertexFormat.POSITION_TEX_COLOR) {
            builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F)
                .setColor(255, 255, 255, 255);
            builder.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F)
                .setColor(255, 255, 255, 255);
            builder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F)
                .setColor(255, 255, 255, 255);
            builder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F)
                .setColor(255, 255, 255, 255);
        } else {
            throw new IllegalStateException("Unexpected vertex format " + format);
        }

        BufferUploader.draw(builder.buildOrThrow());
    }

    /**
     * does post-processing for the vr pass
     * this includes red damage indicator
     * blue freeze indicator
     * screen dimming when sleeping
     * fov reduction when walking
     * water and portal wobbles
     *
     * @param eye         RenderPass that is being post processed, LEFT or RIGHT
     * @param source      RenderTarget that holds the rendered image
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

            if (MC.player != null && MC.level != null) {

                boolean isInWater = ((GameRendererExtension) MC.gameRenderer).vivecraft$isInWater();
                if (DATA_HOLDER.vrSettings.waterEffect && WAS_IN_WATER != isInWater) {
                    // water state changed, start effect
                    WATER_EFFECT = 2.3F;
                } else {
                    if (isInWater) {
                        // slow falloff in water
                        WATER_EFFECT -= 1F / 120F;
                    } else {
                        // fast falloff outside water
                        WATER_EFFECT -= 1F / 60F;
                    }

                    if (WATER_EFFECT < 0.0F) {
                        WATER_EFFECT = 0.0F;
                    }
                }

                WAS_IN_WATER = isInWater;

                if (IrisHelper.isLoaded() && !IrisHelper.hasWaterEffect()) {
                    WATER_EFFECT = 0.0F;
                }

                float portalTime = Mth.lerp(partialTick, MC.player.oSpinningEffectIntensity,
                    MC.player.spinningEffectIntensity);
                if (DATA_HOLDER.vrSettings.portalEffect &&
                    // vanilla check for portal overlay
                    portalTime > 0.0F && !MC.player.hasEffect(MobEffects.CONFUSION))
                {
                    portalEffect = portalTime;
                }

                ItemStack itemstack = MC.player.getInventory().getArmor(3);

                if (DATA_HOLDER.vrSettings.pumpkinEffect && itemstack.getItem() == Blocks.CARVED_PUMPKIN.asItem() &&
                    (!itemstack.has(DataComponents.CUSTOM_MODEL_DATA)))
                {
                    pumpkinEffect = 1.0F;
                }

                float hurtTimer = (float) MC.player.hurtTime - partialTick;
                float healthPercent = 1.0F - MC.player.getHealth() / MC.player.getMaxHealth();
                healthPercent = (healthPercent - 0.5F) * 0.75F;

                if (DATA_HOLDER.vrSettings.hitIndicator && hurtTimer > 0.0F) { // hurt flash
                    hurtTimer = hurtTimer / (float) MC.player.hurtDuration;
                    hurtTimer = healthPercent +
                        Mth.sin(hurtTimer * hurtTimer * hurtTimer * hurtTimer * Mth.PI) * 0.5F;
                    red = hurtTimer;
                } else if (DATA_HOLDER.vrSettings.lowHealthIndicator) { // red due to low health
                    red = healthPercent * Mth.abs(Mth.sin((2.5F * time) / (1.0F - healthPercent + 0.1F)));

                    if (MC.player.isCreative()) {
                        red = 0.0F;
                    }
                }

                float freeze = MC.player.getPercentFrozen();
                if (DATA_HOLDER.vrSettings.freezeEffect && freeze > 0) {
                    blue = red;
                    blue = Math.max(freeze / 2, blue);
                    red = 0;
                }

                if (MC.player.isSleeping()) {
                    black = 0.5F + 0.3F * MC.player.getSleepTimer() * 0.01F;
                }

                if (DATA_HOLDER.vr.isWalkingAbout && black < 0.8F) {
                    black = 0.5F;
                }

                // fov reduction when moving
                if (DATA_HOLDER.vrSettings.useFOVReduction && DATA_HOLDER.vrPlayer.getFreeMove()) {
                    if (Math.abs(MC.player.zza) > 0.0F || Math.abs(MC.player.xxa) > 0.0F) {
                        FOV_REDUCTION = FOV_REDUCTION - 0.05F;
                    } else {
                        FOV_REDUCTION = FOV_REDUCTION + 0.01F;
                    }
                    FOV_REDUCTION = Mth.clamp(FOV_REDUCTION, DATA_HOLDER.vrSettings.fovReductionMin, 0.8F);
                } else {
                    FOV_REDUCTION = 1.0F;
                }
            } else {
                WATER_EFFECT = 0.0F;
                FOV_REDUCTION = 1.0F;
            }

            if (pumpkinEffect > 0.0F) {
                VRShaders.POST_PROCESSING_FOV_REDUCTION_RADIUS_UNIFORM.set(0.3F);
                VRShaders.POST_PROCESSING_FOV_REDUCTION_BORDER_UNIFORM.set(0.0F);
            } else {
                VRShaders.POST_PROCESSING_FOV_REDUCTION_RADIUS_UNIFORM.set(FOV_REDUCTION);
                VRShaders.POST_PROCESSING_FOV_REDUCTION_BORDER_UNIFORM.set(0.06F);
            }

            VRShaders.POST_PROCESSING_FOV_REDUCTION_OFFSET_UNIFORM.set(DATA_HOLDER.vrSettings.fovRedutioncOffset);

            VRShaders.POST_PROCESSING_OVERLAY_HEALTH_ALPHA_UNiFORM.set(red);
            VRShaders.POST_PROCESSING_OVERLAY_FREEZE_ALPHA_UNiFORM.set(blue);
            VRShaders.POST_PROCESSING_OVERLAY_BLACK_ALPHA_UNIFORM.set(black);
            VRShaders.POST_PROCESSING_OVERLAY_TIME_UNIFORM.set(time);
            VRShaders.POST_PROCESSING_OVERLAY_WATER_AMPLITUDE_UNIFORM.set(WATER_EFFECT);
            VRShaders.POST_PROCESSING_OVERLAY_PORTAL_AMPLITUDE_UNIFORM.set(portalEffect);
            VRShaders.POST_PROCESSING_OVERLAY_PUMPKIN_AMPLITUDE_UNIFORM.set(pumpkinEffect);
        }

        // this needs to be set for each eye
        VRShaders.POST_PROCESSING_OVERLAY_EYE_UNIFORM.set(eye == RenderPass.LEFT ? 1 : -1);

        ShaderHelper.renderFullscreenQuad(VRShaders.POST_PROCESSING_SHADER, source);
    }

    /**
     * draws the desktop mirror to the bound buffer
     */
    public static void drawMirror() {
        if (DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.OFF &&
            DATA_HOLDER.vr.isHMDTracking())
        {
            // no mirror, only show when headset is not tracking, to be able to see the menu with the headset off
            MirrorNotification.notify("Mirror is OFF", true, 1000);
        } else if (DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY) {
            if (VRShaders.MIXED_REALITY_SHADER != null) {
                ShaderHelper.doMixedRealityMirror();
            } else {
                MirrorNotification.notify("Mixed Reality Shader compile failed, see log for info", true,
                    10000);
            }
        } else if (DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.DUAL &&
            (!DATA_HOLDER.vrSettings.displayMirrorUseScreenshotCamera ||
                !DATA_HOLDER.cameraTracker.isVisible()
            ))
        {
            // show both eyes side by side
            RenderTarget leftEye = DATA_HOLDER.vrRenderer.getLeftEyeTarget();
            RenderTarget rightEye = DATA_HOLDER.vrRenderer.getRightEyeTarget();

            int screenWidth = MC.mainRenderTarget.width / 2;
            int screenHeight = MC.mainRenderTarget.height;

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
            RenderTarget source = DATA_HOLDER.vrRenderer.getLeftEyeTarget();

            if (DATA_HOLDER.vrSettings.displayMirrorUseScreenshotCamera &&
                DATA_HOLDER.cameraTracker.isVisible())
            {
                source = DATA_HOLDER.vrRenderer.cameraFramebuffer;
                keepAspect = true;
            } else if (DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.FIRST_PERSON) {
                source = DATA_HOLDER.vrRenderer.framebufferUndistorted;
            } else if (DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON) {
                source = DATA_HOLDER.vrRenderer.framebufferMR;
            } else if (DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.GUI) {
                source = GuiHandler.GUI_FRAMEBUFFER;
            } else if (DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.SINGLE ||
                DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.OFF)
            {
                if (!DATA_HOLDER.vrSettings.displayMirrorLeftEye) {
                    source = DATA_HOLDER.vrRenderer.getRightEyeTarget();
                }
            } else if (DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.CROPPED) {
                if (!DATA_HOLDER.vrSettings.displayMirrorLeftEye) {
                    source = DATA_HOLDER.vrRenderer.getRightEyeTarget();
                }

                xCrop = DATA_HOLDER.vrSettings.mirrorCrop;
                yCrop = DATA_HOLDER.vrSettings.mirrorCrop;
                keepAspect = true;
            }
            // Debug
            // source = DataHolder.getInstance().vrRenderer.telescopeFramebufferR;
            //
            if (source != null) {
                ShaderHelper.blitToScreen(source,
                    0, MC.mainRenderTarget.width,
                    MC.mainRenderTarget.height, 0,
                    xCrop, yCrop, keepAspect);
            }
        }

        // draw mirror text
        MirrorNotification.render();
    }

    public static void doMixedRealityMirror() {
        // set viewport to fullscreen, since it would be still on the one from the last pass
        RenderSystem.viewport(0, 0,
            MC.mainRenderTarget.width,
            MC.mainRenderTarget.height);

        Vec3 camPlayer = DATA_HOLDER.vrPlayer.vrdata_room_pre.getHeadPivot()
            .subtract(DATA_HOLDER.vrPlayer.vrdata_room_pre.getEye(RenderPass.THIRD).getPosition());

        // transpose, because camera rotations are transposed
        Matrix4f viewMatrix = DATA_HOLDER.vrPlayer.vrdata_room_pre.getEye(RenderPass.THIRD).getMatrix().transpose();
        Vector3f cameraLook = DATA_HOLDER.vrPlayer.vrdata_room_pre.getEye(RenderPass.THIRD).getDirection();

        // set uniforms
        VRShaders.MIXED_REALITY_PROJECTION_MATRIX_UNIFORM.set(
            ((GameRendererExtension) MC.gameRenderer).vivecraft$getThirdPassProjectionMatrix());
        VRShaders.MIXED_REALITY_VIEW_MATRIX_UNIFORM.set(viewMatrix);

        VRShaders.MIXED_REALITY_HMD_VIEW_POSITION_UNIFORM.set((float) camPlayer.x, (float) camPlayer.y,
            (float) camPlayer.z);
        VRShaders.MIXED_REALITY_HMD_PLANE_NORMAL_UNIFORM.set(-cameraLook.x, 0.0F, -cameraLook.z);

        boolean alphaMask =
            DATA_HOLDER.vrSettings.mixedRealityUnityLike && DATA_HOLDER.vrSettings.mixedRealityAlphaMask;

        if (!alphaMask) {
            VRShaders.MIXED_REALITY_KEY_COLOR_UNIFORM.set(
                (float) DATA_HOLDER.vrSettings.mixedRealityKeyColor.getRed() / 255.0F,
                (float) DATA_HOLDER.vrSettings.mixedRealityKeyColor.getGreen() / 255.0F,
                (float) DATA_HOLDER.vrSettings.mixedRealityKeyColor.getBlue() / 255.0F);
        } else {
            VRShaders.MIXED_REALITY_KEY_COLOR_UNIFORM.set(0F, 0F, 0F);
        }
        VRShaders.MIXED_REALITY_ALPHA_MODE_UNIFORM.set(alphaMask ? 1 : 0);

        VRShaders.MIXED_REALITY_FIRST_PERSON_PASS_UNIFORM.set(DATA_HOLDER.vrSettings.mixedRealityUnityLike ? 1 : 0);

        CompiledShaderProgram mixedRealityShader = Objects.requireNonNull(
            RenderSystem.setShader(VRShaders.MIXED_REALITY_SHADER), "mixed reality shader not loaded");

        // bind textures
        mixedRealityShader.bindSampler("thirdPersonColor",
            DATA_HOLDER.vrRenderer.framebufferMR.getColorTextureId());
        mixedRealityShader.bindSampler("thirdPersonDepth",
            DATA_HOLDER.vrRenderer.framebufferMR.getDepthTextureId());

        if (DATA_HOLDER.vrSettings.mixedRealityUnityLike) {
            RenderTarget source;
            if (DATA_HOLDER.vrSettings.displayMirrorUseScreenshotCamera && DATA_HOLDER.cameraTracker.isVisible()) {
                source = DATA_HOLDER.vrRenderer.cameraFramebuffer;
            } else if (DATA_HOLDER.vrSettings.mixedRealityUndistorted) {
                source = DATA_HOLDER.vrRenderer.framebufferUndistorted;
            } else {
                if (DATA_HOLDER.vrSettings.displayMirrorLeftEye) {
                    source = DATA_HOLDER.vrRenderer.getLeftEyeTarget();
                } else {
                    source = DATA_HOLDER.vrRenderer.getRightEyeTarget();
                }
            }
            mixedRealityShader.bindSampler("firstPersonColor", source.getColorTextureId());
        }

        mixedRealityShader.apply();

        drawFullscreenQuad(VRShaders.MIXED_REALITY_SHADER.vertexFormat());

        mixedRealityShader.clear();
    }

    /**
     * uses a lanczos filter to scale the source RenderTarget to the secondPass RenderTarget size
     *
     * @param source     RenderTarget with the low/high resolution frame
     * @param firstPass  RenderTarget with source height and target width, for the intermediary step
     * @param secondPass RenderTarget with the target size
     */
    public static void doFSAA(RenderTarget source, RenderTarget firstPass, RenderTarget secondPass) {
        if (firstPass == null) {
            DATA_HOLDER.vrRenderer.reinitFrameBuffers("FSAA Setting Changed");
        } else {
            RenderSystem.disableBlend();
            // set to always, since we want to override the depth
            // disabling depth test would disable depth writes
            RenderSystem.depthFunc(GL43.GL_ALWAYS);

            CompiledShaderProgram lanczosShader = Objects.requireNonNull(
                RenderSystem.setShader(VRShaders.LANCZOS_SHADER), "lanczos shader not loaded");

            // first pass, horizontal
            firstPass.bindWrite(true);

            lanczosShader.bindSampler("Sampler0", source.getColorTextureId());
            lanczosShader.bindSampler("Sampler1", source.getDepthTextureId());
            VRShaders.LANCZOS_TEXEL_WIDTH_OFFSET_UNIFORM.set(1.0F / (3.0F * (float) firstPass.viewWidth));
            VRShaders.LANCZOS_TEXEL_HEIGHT_OFFSET_UNIFORM.set(0.0F);
            lanczosShader.apply();

            drawFullscreenQuad(VRShaders.LANCZOS_SHADER.vertexFormat());

            // second pass, vertical
            secondPass.bindWrite(true);

            lanczosShader.bindSampler("Sampler0", firstPass.getColorTextureId());
            lanczosShader.bindSampler("Sampler1", firstPass.getDepthTextureId());
            VRShaders.LANCZOS_TEXEL_WIDTH_OFFSET_UNIFORM.set(0.0F);
            VRShaders.LANCZOS_TEXEL_HEIGHT_OFFSET_UNIFORM.set(1.0F / (3.0F * (float) secondPass.viewHeight));
            lanczosShader.apply();

            drawFullscreenQuad(VRShaders.LANCZOS_SHADER.vertexFormat());

            // Clean up time
            lanczosShader.clear();
            secondPass.unbindWrite();

            RenderSystem.depthFunc(GL43.GL_LEQUAL);
            RenderSystem.enableBlend();
        }
    }

    /**
     * blits the given {@code source} RenderTarget to the screen/bound buffer<br>
     * the {@code source} is drawn to the rectangle at {@code left},{@code top} with a size of {@code width},{@code height}<br>
     * if {@code xCropFactor} or {@code yCropFactor} are non 0 the {@code source} gets zoomed in
     *
     * @param source      RenderTarget to draw to the screen
     * @param left        left edge of the target area
     * @param width       width of the target area
     * @param height      height of the target area
     * @param top         top edge of the target area
     * @param xCropFactor vertical crop factor for the {@code source}
     * @param yCropFactor horizontal crop factor for the {@code source}
     * @param keepAspect  keeps the aspect ratio in takt when cropping the buffer
     */
    public static void blitToScreen(
        RenderTarget source, int left, int width, int height, int top, float xCropFactor, float yCropFactor,
        boolean keepAspect)
    {
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

        CompiledShaderProgram blitShader = Objects.requireNonNull(
            RenderSystem.setShader(VRShaders.BLIT_VR_SHADER), "Vivecraft blit shader not loaded");
        blitShader.bindSampler("DiffuseSampler", source.getColorTextureId());

        blitShader.apply();

        BufferBuilder bufferbuilder = RenderSystem.renderThreadTesselator()
            .begin(VertexFormat.Mode.QUADS, VRShaders.BLIT_VR_SHADER.vertexFormat());

        bufferbuilder.addVertex(-1.0F, -1.0F, 0.0F).setUv(xMin, yMin);
        bufferbuilder.addVertex(1.0F, -1.0F, 0.0F).setUv(xMax, yMin);
        bufferbuilder.addVertex(1.0F, 1.0F, 0.0F).setUv(xMax, yMax);
        bufferbuilder.addVertex(-1.0F, 1.0F, 0.0F).setUv(xMin, yMax);
        BufferUploader.draw(bufferbuilder.buildOrThrow());
        blitShader.clear();

        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
    }
}
