package org.vivecraft.client_vr.render.helpers;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.opengl.GL13C;
import org.vivecraft.client.Xplat;
import org.vivecraft.client.extensions.RenderTargetExtension;
import org.vivecraft.client.utils.Utils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.extensions.GuiExtension;
import org.vivecraft.client_vr.extensions.MinecraftExtension;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.render.RenderConfigException;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.VRShaders;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassManager;
import org.vivecraft.client_xr.render_pass.WorldRenderPass;
import org.vivecraft.mod_compat_vr.iris.IrisHelper;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;

import java.util.List;

public class VRPassHelper {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

    private static float fovReduction = 1.0F;

    public static void renderSingleView(RenderPass eye, float partialTicks, long nanoTime, boolean renderWorld) {
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
        RenderSystem.clear(16384, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        mc.gameRenderer.render(partialTicks, nanoTime, renderWorld);
        checkGLError("post game render " + eye.name());

        if (dataHolder.currentPass == RenderPass.LEFT || dataHolder.currentPass == RenderPass.RIGHT) {
            mc.getProfiler().push("postProcessEye");
            RenderTarget rendertarget = mc.getMainRenderTarget();

            if (dataHolder.vrSettings.useFsaa) {
                RenderSystem.clearColor(RenderSystem.getShaderFogColor()[0], RenderSystem.getShaderFogColor()[1], RenderSystem.getShaderFogColor()[2], RenderSystem.getShaderFogColor()[3]);
                if (eye == RenderPass.LEFT) {
                    dataHolder.vrRenderer.getLeftEyeTarget().bindWrite(true);
                } else {
                    dataHolder.vrRenderer.getRightEyeTarget().bindWrite(true);
                }
                RenderSystem.clear(16384, Minecraft.ON_OSX);
                mc.getProfiler().push("fsaa");
                dataHolder.vrRenderer.doFSAA(false);
                rendertarget = dataHolder.vrRenderer.fsaaLastPassResultFBO;
                checkGLError("fsaa " + eye.name());
                mc.getProfiler().pop();
            }

            if (eye == RenderPass.LEFT) {
                dataHolder.vrRenderer.getLeftEyeTarget().bindWrite(true);
            } else {
                dataHolder.vrRenderer.getRightEyeTarget().bindWrite(true);
            }

            if (dataHolder.vrSettings.useFOVReduction
                && dataHolder.vrPlayer.getFreeMove()) {
                if (mc.player != null && (Math.abs(mc.player.zza) > 0.0F || Math.abs(mc.player.xxa) > 0.0F)) {
                    fovReduction = fovReduction - 0.05F;

                    if (fovReduction < dataHolder.vrSettings.fovReductionMin) {
                        fovReduction = dataHolder.vrSettings.fovReductionMin;
                    }
                } else {
                    fovReduction = fovReduction + 0.01F;

                    if (fovReduction > 0.8F) {
                        fovReduction = 0.8F;
                    }
                }
            } else {
                fovReduction = 1.0F;
            }

            VRShaders._FOVReduction_OffsetUniform.set(
                dataHolder.vrSettings.fovRedutioncOffset);
            float red = 0.0F;
            float black = 0.0F;
            float blue = 0.0F;
            float time = (float) Util.getMillis() / 1000.0F;

            if (mc.player != null && mc.level != null) {
                if (((GameRendererExtension) mc.gameRenderer)
                    .vivecraft$wasInWater() != ((GameRendererExtension) mc.gameRenderer).vivecraft$isInWater()) {
                    dataHolder.watereffect = 2.3F;
                } else {
                    if (((GameRendererExtension) mc.gameRenderer).vivecraft$isInWater()) {
                        dataHolder.watereffect -= 0.008333334F;
                    } else {
                        dataHolder.watereffect -= 0.016666668F;
                    }

                    if (dataHolder.watereffect < 0.0F) {
                        dataHolder.watereffect = 0.0F;
                    }
                }

                ((GameRendererExtension) mc.gameRenderer)
                    .vivecraft$setWasInWater(((GameRendererExtension) mc.gameRenderer).vivecraft$isInWater());

                if (Xplat
                    .isModLoaded("iris") || Xplat.isModLoaded("oculus")) {
                    if (!IrisHelper.hasWaterEffect()) {
                        dataHolder.watereffect = 0.0F;
                    }
                }

                if (((GameRendererExtension) mc.gameRenderer).vivecraft$isInPortal()) {
                    dataHolder.portaleffect = 1.0F;
                } else {
                    dataHolder.portaleffect -= 0.016666668F;

                    if (dataHolder.portaleffect < 0.0F) {
                        dataHolder.portaleffect = 0.0F;
                    }
                }

                ItemStack itemstack = mc.player.getInventory().getArmor(3);

                if (itemstack.getItem() == Blocks.CARVED_PUMPKIN.asItem()
                    && (!itemstack.hasTag() || itemstack.getTag().getInt("CustomModelData") == 0)) {
                    dataHolder.pumpkineffect = 1.0F;
                } else {
                    dataHolder.pumpkineffect = 0.0F;
                }

                float hurtTimer = (float) mc.player.hurtTime - partialTicks;
                float healthPercent = 1.0F - mc.player.getHealth() / mc.player.getMaxHealth();
                healthPercent = (healthPercent - 0.5F) * 0.75F;

                if (hurtTimer > 0.0F) { // hurt flash
                    hurtTimer = hurtTimer / (float) mc.player.hurtDuration;
                    hurtTimer = healthPercent + Mth.sin(hurtTimer * hurtTimer * hurtTimer * hurtTimer * (float) Math.PI) * 0.5F;
                    red = hurtTimer;
                } else if (dataHolder.vrSettings.low_health_indicator) { // red due to low health
                    red = healthPercent * Mth.abs(Mth.sin((2.5F * time) / (1.0F - healthPercent + 0.1F)));

                    if (mc.player.isCreative()) {
                        red = 0.0F;
                    }
                }

                float freeze = mc.player.getPercentFrozen();
                if (freeze > 0) {
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
            } else {
                dataHolder.watereffect = 0.0F;
                dataHolder.portaleffect = 0.0F;
                dataHolder.pumpkineffect = 0.0F;
            }

            if (dataHolder.pumpkineffect > 0.0F) {
                VRShaders._FOVReduction_RadiusUniform.set(0.3F);
                VRShaders._FOVReduction_BorderUniform.set(0.0F);
            } else {
                VRShaders._FOVReduction_RadiusUniform.set(fovReduction);
                VRShaders._FOVReduction_BorderUniform.set(0.06F);
            }

            VRShaders._Overlay_HealthAlpha.set(red);
            VRShaders._Overlay_FreezeAlpha.set(blue);
            VRShaders._Overlay_BlackAlpha.set(black);
            VRShaders._Overlay_time.set(time);
            VRShaders._Overlay_waterAmplitude.set(dataHolder.watereffect);
            VRShaders._Overlay_portalAmplitutde.set(dataHolder.portaleffect);
            VRShaders._Overlay_pumpkinAmplitutde.set(dataHolder.pumpkineffect);

            VRShaders._Overlay_eye.set(dataHolder.currentPass == RenderPass.LEFT ? 1 : -1);

            ((RenderTargetExtension) rendertarget).vivecraft$blitFovReduction(VRShaders.fovReductionShader, dataHolder.vrRenderer.getLeftEyeTarget().viewWidth, dataHolder.vrRenderer.getLeftEyeTarget().viewHeight);
            ProgramManager.glUseProgram(0);
            checkGLError("post overlay" + eye);
            mc.getProfiler().pop();
        }

        if (dataHolder.currentPass == RenderPass.CAMERA) {
            mc.getProfiler().push("cameraCopy");
            dataHolder.vrRenderer.cameraFramebuffer.bindWrite(true);
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
            RenderSystem.clear(16640, Minecraft.ON_OSX);
            ((RenderTargetExtension) dataHolder.vrRenderer.cameraRenderFramebuffer).vivecraft$blitToScreen(0,
                dataHolder.vrRenderer.cameraFramebuffer.viewWidth,
                dataHolder.vrRenderer.cameraFramebuffer.viewHeight, 0, true, 0.0F, 0.0F, false);
            mc.getProfiler().pop();
        }

        if (dataHolder.currentPass == RenderPass.THIRD
            && dataHolder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY
            && OptifineHelper.isOptifineLoaded()
            && renderWorld && mc.level != null
            && OptifineHelper.isShaderActive()
            && OptifineHelper.bindShaderFramebuffer()) {
            // copy optifine depth buffer, since we need it for the mixed reality split
            RenderSystem.activeTexture(GL13C.GL_TEXTURE0);
            RenderSystem.bindTexture(dataHolder.vrRenderer.framebufferMR.getDepthTextureId());
            checkGLError("pre copy depth");
            GlStateManager._glCopyTexSubImage2D(GL13C.GL_TEXTURE_2D, 0, 0, 0, 0, 0, dataHolder.vrRenderer.framebufferMR.width, dataHolder.vrRenderer.framebufferMR.height);
            checkGLError("post copy depth");
            // rebind the original buffer
            dataHolder.vrRenderer.framebufferMR.bindWrite(false);
        }
    }

    public static void renderAndSubmit(boolean renderLevel, long nanoTime, float actualPartialTicks) {
        // still rendering
        mc.getProfiler().push("gameRenderer");

        mc.getProfiler().push("VR guis");

        // some mods mess with the depth mask?
        RenderSystem.depthMask(true);
        // some mods mess with the backface culling?
        RenderSystem.enableCull();

        mc.getProfiler().push("gui cursor");
        // draw cursor on Gui Layer
        if (mc.screen != null || !mc.mouseHandler.isMouseGrabbed()) {
            PoseStack poseStack = RenderSystem.getModelViewStack();
            poseStack.pushPose();
            poseStack.setIdentity();
            poseStack.translate(0.0f, 0.0f, -11000.0f);
            RenderSystem.applyModelViewMatrix();

            int x = (int) (Minecraft.getInstance().mouseHandler.xpos() * (double) Minecraft.getInstance().getWindow().getGuiScaledWidth() / (double) Minecraft.getInstance().getWindow().getScreenWidth());
            int y = (int) (Minecraft.getInstance().mouseHandler.ypos() * (double) Minecraft.getInstance().getWindow().getGuiScaledHeight() / (double) Minecraft.getInstance().getWindow().getScreenHeight());
            ((GuiExtension) mc.gui).vivecraft$drawMouseMenuQuad(x, y);

            poseStack.popPose();
            RenderSystem.applyModelViewMatrix();
        }

        mc.getProfiler().popPush("fps pie");
        // draw debug pie
        ((MinecraftExtension) mc).vivecraft$drawProfiler();

        // pop pose that we pushed before the gui
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();

        // generate mipmaps
        // TODO: does this do anything?
        mc.mainRenderTarget.bindRead();
        ((RenderTargetExtension) mc.mainRenderTarget).vivecraft$genMipMaps();
        mc.mainRenderTarget.unbindRead();

        mc.getProfiler().popPush("2D Keyboard");
        GuiGraphics guiGraphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        if (KeyboardHandler.Showing && !dataHolder.vrSettings.physicalKeyboard) {
            mc.mainRenderTarget = KeyboardHandler.Framebuffer;
            mc.mainRenderTarget.clear(Minecraft.ON_OSX);
            mc.mainRenderTarget.bindWrite(true);
            RenderHelper.drawScreen(actualPartialTicks, KeyboardHandler.UI, guiGraphics);
            guiGraphics.flush();
        }

        mc.getProfiler().popPush("Radial Menu");
        if (RadialHandler.isShowing()) {
            mc.mainRenderTarget = RadialHandler.Framebuffer;
            mc.mainRenderTarget.clear(Minecraft.ON_OSX);
            mc.mainRenderTarget.bindWrite(true);
            RenderHelper.drawScreen(actualPartialTicks, RadialHandler.UI, guiGraphics);
            guiGraphics.flush();
        }
        mc.getProfiler().pop();
        checkGLError("post 2d ");

        // done with guis
        mc.getProfiler().pop();

        // render the different vr passes
        List<RenderPass> list = dataHolder.vrRenderer.getRenderPasses();
        dataHolder.isFirstPass = true;
        for (RenderPass renderpass : list) {
            dataHolder.currentPass = renderpass;

            if (dataHolder.vrSettings.displayMirrorUseScreenshotCamera && dataHolder.cameraTracker.isVisible()) {
                if (renderpass == RenderPass.CENTER) {
                    continue;
                } else if (renderpass == RenderPass.THIRD && dataHolder.vrSettings.displayMirrorMode != VRSettings.MirrorMode.MIXED_REALITY) {
                    continue;
                }
            }

            switch (renderpass) {
                case LEFT, RIGHT -> RenderPassManager.setWorldRenderPass(WorldRenderPass.stereoXR);
                case CENTER -> RenderPassManager.setWorldRenderPass(WorldRenderPass.center);
                case THIRD -> RenderPassManager.setWorldRenderPass(WorldRenderPass.mixedReality);
                case SCOPEL -> RenderPassManager.setWorldRenderPass(WorldRenderPass.leftTelescope);
                case SCOPER -> RenderPassManager.setWorldRenderPass(WorldRenderPass.rightTelescope);
                case CAMERA -> RenderPassManager.setWorldRenderPass(WorldRenderPass.camera);
            }

            mc.getProfiler().push("Eye:" + dataHolder.currentPass);
            mc.getProfiler().push("setup");
            mc.mainRenderTarget.bindWrite(true);
            mc.getProfiler().pop();
            VRPassHelper.renderSingleView(renderpass, actualPartialTicks, nanoTime, renderLevel);
            mc.getProfiler().pop();

            if (dataHolder.grabScreenShot) {
                boolean flag;

                if (list.contains(RenderPass.CAMERA)) {
                    flag = renderpass == RenderPass.CAMERA;
                } else if (list.contains(RenderPass.CENTER)) {
                    flag = renderpass == RenderPass.CENTER;
                } else {
                    flag = dataHolder.vrSettings.displayMirrorLeftEye ?
                           renderpass == RenderPass.LEFT :
                           renderpass == RenderPass.RIGHT;
                }

                if (flag) {
                    RenderTarget rendertarget = mc.mainRenderTarget;

                    if (renderpass == RenderPass.CAMERA) {
                        rendertarget = dataHolder.vrRenderer.cameraFramebuffer;
                    }

                    mc.mainRenderTarget.unbindWrite();
                    Utils.takeScreenshot(rendertarget);
                    mc.getWindow().updateDisplay();
                    dataHolder.grabScreenShot = false;
                }
            }

            dataHolder.isFirstPass = false;
        }
        // now we are done with rendering
        mc.getProfiler().pop();

        dataHolder.vrPlayer.postRender(actualPartialTicks);
        mc.getProfiler().push("Display/Reproject");

        try {
            dataHolder.vrRenderer.endFrame();
        } catch (RenderConfigException exception) {
            VRSettings.logger.error(exception.toString());
        }
        mc.getProfiler().pop();
        checkGLError("post submit ");
    }

    private static void checkGLError(String string) {
        int i = GlStateManager._getError();
        if (i != 0) {
            System.err.println(string + ": " + i);
        }
    }
}
