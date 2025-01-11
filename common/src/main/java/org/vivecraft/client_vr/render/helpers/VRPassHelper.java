package org.vivecraft.client_vr.render.helpers;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.profiling.Profiler;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;
import org.vivecraft.client.utils.ClientUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.render.RenderConfigException;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassManager;
import org.vivecraft.client_xr.render_pass.WorldRenderPass;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;

import java.util.List;

public class VRPassHelper {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final ClientDataHolderVR DATA_HOLDER = ClientDataHolderVR.getInstance();

    /**
     * renders a single RenderPass view
     *
     * @param eye          RenderPass to render
     * @param deltaTracker tracker to get the partial tick from
     * @param renderLevel  if the level should be rendered, or just the screen
     */
    public static void renderSingleView(RenderPass eye, DeltaTracker.Timer deltaTracker, boolean renderLevel) {
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
        RenderSystem.clear(GL13C.GL_COLOR_BUFFER_BIT | GL13C.GL_DEPTH_BUFFER_BIT);
        RenderSystem.enableDepthTest();

        // THIS IS WHERE EVERYTHING IS RENDERED
        MC.gameRenderer.render(deltaTracker, renderLevel);

        RenderHelper.checkGLError("post game render " + eye);

        if (DATA_HOLDER.currentPass == RenderPass.LEFT || DATA_HOLDER.currentPass == RenderPass.RIGHT) {
            // copies the rendered scene to eye tex with fsaa and other postprocessing effects.
            Profiler.get().push("postProcessEye");
            RenderTarget rendertarget = MC.getMainRenderTarget();

            if (DATA_HOLDER.vrSettings.useFsaa) {
                Profiler.get().push("fsaa");
                ShaderHelper.doFSAA(DATA_HOLDER.vrRenderer.framebufferVrRender,
                    DATA_HOLDER.vrRenderer.fsaaFirstPassResultFBO,
                    DATA_HOLDER.vrRenderer.fsaaLastPassResultFBO);
                rendertarget = DATA_HOLDER.vrRenderer.fsaaLastPassResultFBO;
                RenderHelper.checkGLError("fsaa " + eye);
                Profiler.get().pop();
            }

            if (eye == RenderPass.LEFT) {
                DATA_HOLDER.vrRenderer.getLeftEyeTarget().bindWrite(true);
            } else {
                DATA_HOLDER.vrRenderer.getRightEyeTarget().bindWrite(true);
            }

            // do post-processing
            ShaderHelper.doVrPostProcess(eye, rendertarget, deltaTracker.getGameTimeDeltaPartialTick(false));

            RenderHelper.checkGLError("post overlay" + eye);
            Profiler.get().pop();
        }

        if (DATA_HOLDER.currentPass == RenderPass.CAMERA) {
            Profiler.get().push("cameraCopy");
            DATA_HOLDER.vrRenderer.cameraFramebuffer.bindWrite(true);
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
            RenderSystem.clear(GL13C.GL_COLOR_BUFFER_BIT | GL13C.GL_DEPTH_BUFFER_BIT);
            DATA_HOLDER.vrRenderer.cameraRenderFramebuffer.blitToScreen(
                DATA_HOLDER.vrRenderer.cameraFramebuffer.viewWidth,
                DATA_HOLDER.vrRenderer.cameraFramebuffer.viewHeight);
            Profiler.get().pop();
        }

        if (DATA_HOLDER.currentPass == RenderPass.THIRD &&
            DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY &&
            renderLevel && MC.level != null &&
            OptifineHelper.isOptifineLoaded() && OptifineHelper.isShaderActive() &&
            OptifineHelper.bindShaderFramebuffer())
        {
            // copy optifine depth buffer, since we need it for the mixed reality split
            RenderSystem.activeTexture(GL13C.GL_TEXTURE0);
            RenderSystem.bindTexture(DATA_HOLDER.vrRenderer.framebufferMR.getDepthTextureId());
            RenderHelper.checkGLError("pre copy depth");
            GlStateManager._glCopyTexSubImage2D(GL13C.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                DATA_HOLDER.vrRenderer.framebufferMR.width, DATA_HOLDER.vrRenderer.framebufferMR.height);
            RenderHelper.checkGLError("post copy depth");
            // rebind the original buffer
            DATA_HOLDER.vrRenderer.framebufferMR.bindWrite(false);
        }
    }

    /**
     * renders all passes, and submits the final frames to the VR runtime
     *
     * @param renderLevel  if the level is being rendered
     * @param deltaTracker tracker to get the partial tick from
     */
    public static void renderAndSubmit(boolean renderLevel, DeltaTracker.Timer deltaTracker) {
        // still rendering
        Profiler.get().push("gameRenderer");

        Profiler.get().push("VR guis");

        // some mods mess with the depth mask?
        RenderSystem.depthMask(true);
        // some mods mess with the backface culling?
        RenderSystem.enableCull();

        // to render gui stuff
        GuiGraphics guiGraphics = new GuiGraphics(MC, MC.renderBuffers().bufferSource());

        Profiler.get().push("gui cursor");
        // draw cursor on Gui Layer
        if (MC.screen != null || !MC.mouseHandler.isMouseGrabbed()) {
            Matrix4fStack poseStack = RenderSystem.getModelViewStack();
            poseStack.pushMatrix();
            poseStack.identity();
            poseStack.translate(0.0f, 0.0f, -11000.0f);

            Matrix4f guiProjection = (new Matrix4f()).setOrtho(
                0.0F, MC.getWindow().getGuiScaledWidth(),
                MC.getWindow().getGuiScaledHeight(), 0.0F,
                1000.0F, 21000.0F);
            RenderSystem.setProjectionMatrix(guiProjection, ProjectionType.ORTHOGRAPHIC);

            int x = (int) (
                MC.mouseHandler.xpos() * (double) MC.getWindow().getGuiScaledWidth() /
                    (double) MC.getWindow().getScreenWidth()
            );
            int y = (int) (
                MC.mouseHandler.ypos() * (double) MC.getWindow().getGuiScaledHeight() /
                    (double) MC.getWindow().getScreenHeight()
            );
            RenderHelper.drawMouseMenuQuad(guiGraphics, x, y);

            guiGraphics.flush();
            poseStack.popMatrix();
        }

        // pop pose that we pushed before the gui
        RenderSystem.getModelViewStack().popMatrix();

        if (DATA_HOLDER.vrSettings.guiMipmaps) {
            // update mipmaps
            MC.mainRenderTarget.bindRead();
            GL30C.glGenerateMipmap(GL30C.GL_TEXTURE_2D);
            MC.mainRenderTarget.unbindRead();
        }

        Profiler.get().popPush("2D Keyboard");
        if (KeyboardHandler.SHOWING && !DATA_HOLDER.vrSettings.physicalKeyboard) {
            MC.mainRenderTarget = KeyboardHandler.FRAMEBUFFER;
            MC.mainRenderTarget.clear();
            MC.mainRenderTarget.bindWrite(true);
            RenderHelper.drawScreen(guiGraphics, deltaTracker, KeyboardHandler.UI, true);
        }

        Profiler.get().popPush("Radial Menu");
        if (RadialHandler.isShowing()) {
            MC.mainRenderTarget = RadialHandler.FRAMEBUFFER;
            MC.mainRenderTarget.clear();
            MC.mainRenderTarget.bindWrite(true);
            RenderHelper.drawScreen(guiGraphics, deltaTracker, RadialHandler.UI, true);
        }
        Profiler.get().pop();
        RenderHelper.checkGLError("post 2d ");

        // done with guis
        Profiler.get().pop();

        // render the different vr passes
        List<RenderPass> list = DATA_HOLDER.vrRenderer.getRenderPasses();
        DATA_HOLDER.isFirstPass = true;
        for (RenderPass renderpass : list) {
            DATA_HOLDER.currentPass = renderpass;

            if (DATA_HOLDER.vrSettings.displayMirrorUseScreenshotCamera && DATA_HOLDER.cameraTracker.isVisible()) {
                if (renderpass == RenderPass.CENTER) {
                    continue;
                } else if (renderpass == RenderPass.THIRD &&
                    DATA_HOLDER.vrSettings.displayMirrorMode != VRSettings.MirrorMode.MIXED_REALITY)
                {
                    continue;
                }
            }

            switch (renderpass) {
                case LEFT, RIGHT -> RenderPassManager.setWorldRenderPass(WorldRenderPass.STEREO_XR);
                case CENTER -> RenderPassManager.setWorldRenderPass(WorldRenderPass.CENTER);
                case THIRD -> RenderPassManager.setWorldRenderPass(WorldRenderPass.MIXED_REALITY);
                case SCOPEL -> RenderPassManager.setWorldRenderPass(WorldRenderPass.LEFT_TELESCOPE);
                case SCOPER -> RenderPassManager.setWorldRenderPass(WorldRenderPass.RIGHT_TELESCOPE);
                case CAMERA -> RenderPassManager.setWorldRenderPass(WorldRenderPass.CAMERA);
            }

            Profiler.get().push("Eye:" + DATA_HOLDER.currentPass);
            Profiler.get().push("setup");
            MC.mainRenderTarget.bindWrite(true);
            Profiler.get().pop();
            VRPassHelper.renderSingleView(renderpass, deltaTracker, renderLevel);
            Profiler.get().pop();

            if (DATA_HOLDER.grabScreenShot) {
                boolean flag;

                if (list.contains(RenderPass.CAMERA)) {
                    flag = renderpass == RenderPass.CAMERA;
                } else if (list.contains(RenderPass.CENTER)) {
                    flag = renderpass == RenderPass.CENTER;
                } else {
                    flag = DATA_HOLDER.vrSettings.displayMirrorLeftEye ?
                        renderpass == RenderPass.LEFT :
                        renderpass == RenderPass.RIGHT;
                }

                if (flag) {
                    RenderTarget rendertarget = MC.mainRenderTarget;

                    if (renderpass == RenderPass.CAMERA) {
                        rendertarget = DATA_HOLDER.vrRenderer.cameraFramebuffer;
                    }

                    MC.mainRenderTarget.unbindWrite();
                    ClientUtils.takeScreenshot(rendertarget);
                    MC.getWindow().updateDisplay(null);
                    DATA_HOLDER.grabScreenShot = false;
                }
            }

            DATA_HOLDER.isFirstPass = false;
        }
        // now we are done with rendering
        Profiler.get().pop();

        DATA_HOLDER.vrPlayer.postRender(deltaTracker.getGameTimeDeltaPartialTick(true));

        Profiler.get().push("vrMirror");
        // use the vanilla target for the mirror
        RenderPassManager.setMirrorRenderPass();
        MC.mainRenderTarget.bindWrite(true);
        ShaderHelper.drawMirror();
        RenderHelper.checkGLError("post-mirror");

        Profiler.get().popPush("Display/Reproject");

        try {
            DATA_HOLDER.vrRenderer.endFrame();
        } catch (RenderConfigException exception) {
            VRSettings.LOGGER.error("Vivecraft: error ending frame: {}", exception.error.getString());
        }
        Profiler.get().pop();
        RenderHelper.checkGLError("post submit");
    }
}
