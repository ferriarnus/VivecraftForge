package org.vivecraft.client_vr.provider;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import org.vivecraft.client.extensions.RenderTargetExtension;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRTextureTarget;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.extensions.WindowExtension;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.gameplay.trackers.TelescopeTracker;
import org.vivecraft.client_vr.render.RenderConfigException;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.ShaderHelper;
import org.vivecraft.client_vr.render.VRShaders;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.WorldRenderPass;
import org.vivecraft.mod_compat_vr.ShadersHelper;
import org.vivecraft.mod_compat_vr.resolutioncontrol.ResolutionControlHelper;
import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public abstract class VRRenderer {
    public static final String RENDER_SETUP_FAILURE_MESSAGE = "Failed to initialise stereo rendering plugin: ";
    public RenderTarget cameraFramebuffer;
    public RenderTarget cameraRenderFramebuffer;
    protected int dispLastWidth;
    protected int dispLastHeight;
    public Matrix4f[] eyeproj = new Matrix4f[2];
    public RenderTarget framebufferMR;
    public RenderTarget framebufferUndistorted;
    public RenderTarget framebufferVrRender;
    public RenderTarget fsaaFirstPassResultFBO;
    public RenderTarget fsaaLastPassResultFBO;
    protected float[][] hiddenMesheVertecies = new float[2][];
    public ResourceKey<DimensionType> lastDimensionId = BuiltinDimensionTypes.OVERWORLD;
    public int lastDisplayFBHeight = 0;
    public int lastDisplayFBWidth = 0;
    public boolean lastEnableVsync = true;
    public boolean lastFogFancy = true;
    public boolean lastFogFast = false;
    private GraphicsStatus previousGraphics = null;
    public int lastGuiScale = 0;
    protected VRSettings.MirrorMode lastMirror;
    public int lastRenderDistanceChunks = -1;
    public long lastWindow = 0L;
    public float lastWorldScale = 0.0F;
    public int mirrorFBHeight;
    public int mirrorFBWidth;
    protected boolean reinitFramebuffers = true;
    protected boolean resizeFrameBuffers = false;
    protected boolean acceptReinits = true;
    public boolean reinitShadersFlag = false;
    public float renderScale;
    protected Tuple<Integer, Integer> resolution;
    public float ss = -1.0F;
    public RenderTarget telescopeFramebufferL;
    public RenderTarget telescopeFramebufferR;
    protected MCVR vr;

    public VRRenderer(MCVR vr) {
        this.vr = vr;
    }

    protected void checkGLError(String message) {
        //Config.checkGlError(message); TODO
        if (GlStateManager._getError() != 0) {
            System.err.println(message);
        }
    }

    public abstract void createRenderTexture(int var1, int var2) throws RenderConfigException;

    public abstract Matrix4f getProjectionMatrix(int var1, float var2, float var3);

    public abstract void endFrame() throws RenderConfigException;

    public abstract boolean providesStencilMask();

    public abstract RenderTarget getLeftEyeTarget();
    public abstract RenderTarget getRightEyeTarget();

    public void doStencil(boolean inverse) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientDataHolderVR dataholder = ClientDataHolderVR.getInstance();

        //setup stencil for writing
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        RenderSystem.stencilMask(0xFF); // Write to stencil buffer

        if (inverse) {
            //clear whole image for total mask in color, stencil, depth
            RenderSystem.clearStencil(0xFF);
            RenderSystem.clearDepth(0);

            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF); // Set any stencil to 0
            RenderSystem.colorMask(false, false, false, true);
        } else {
            //clear whole image for total transparency
            RenderSystem.clearStencil(0);
            RenderSystem.clearDepth(1);

            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0xFF, 0xFF); // Set any stencil to 1
            RenderSystem.colorMask(true, true, true, true);
        }

        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT, false);

        RenderSystem.clearStencil(0);
        RenderSystem.clearDepth(1);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.disableCull();

        RenderSystem.setShaderColor(0F, 0F, 0F, 1.0F);


        RenderTarget fb = minecraft.getMainRenderTarget();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, fb.viewWidth, 0.0F, fb.viewHeight, 0.0F, 20.0F), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().setIdentity();
        if (inverse) //draw on far clip
        {
            RenderSystem.getModelViewStack().translate(0, 0, -20);
        }
        RenderSystem.applyModelViewMatrix();
        int s = GlStateManager._getInteger(GL43.GL_CURRENT_PROGRAM);

        if (dataholder.currentPass == RenderPass.SCOPEL || dataholder.currentPass == RenderPass.SCOPER) {
            drawCircle(fb.viewWidth, fb.viewHeight);
        } else if (dataholder.currentPass == RenderPass.LEFT || dataholder.currentPass == RenderPass.RIGHT) {
            drawMask();
        }

        RenderSystem.restoreProjectionMatrix();
        RenderSystem.getModelViewStack().popPose();

        RenderSystem.depthMask(true); // Do write to depth buffer
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableDepthTest();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        ProgramManager.glUseProgram(s);
        RenderSystem.stencilFunc(GL11.GL_NOTEQUAL, 255, 1);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        RenderSystem.stencilMask(0); // Dont Write to stencil buffer
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    FloatBuffer buffer = MemoryUtil.memAllocFloat(16);
    FloatBuffer buffer2 = MemoryUtil.memAllocFloat(16);

    public void doFSAA(boolean hasShaders) {
        if (this.fsaaFirstPassResultFBO == null) {
            this.reinitFrameBuffers("FSAA Setting Changed");
        } else {
            RenderSystem.disableBlend();
            // set to always, so that we can skip the clear
            RenderSystem.depthFunc(GL43.GL_ALWAYS);

            // first pass
            this.fsaaFirstPassResultFBO.bindWrite(true);

            RenderSystem.setShaderTexture(0, framebufferVrRender.getColorTextureId());
            RenderSystem.setShaderTexture(1, framebufferVrRender.getDepthTextureId());

            RenderSystem.activeTexture(GL43.GL_TEXTURE1);
            this.framebufferVrRender.bindRead();
            RenderSystem.activeTexture(GL43.GL_TEXTURE2);
            RenderSystem.bindTexture(framebufferVrRender.getDepthTextureId());
            RenderSystem.activeTexture(GL43.GL_TEXTURE0);

            VRShaders.lanczosShader.setSampler("Sampler0", RenderSystem.getShaderTexture(0));
            VRShaders.lanczosShader.setSampler("Sampler1", RenderSystem.getShaderTexture(1));
            VRShaders._Lanczos_texelWidthOffsetUniform.set(1.0F / (3.0F * (float) this.fsaaFirstPassResultFBO.viewWidth));
            VRShaders._Lanczos_texelHeightOffsetUniform.set(0.0F);
            VRShaders.lanczosShader.apply();

            this.drawQuad();

            // second pass
            this.fsaaLastPassResultFBO.bindWrite(true);
            RenderSystem.setShaderTexture(0, this.fsaaFirstPassResultFBO.getColorTextureId());
            RenderSystem.setShaderTexture(1, this.fsaaFirstPassResultFBO.getDepthTextureId());

            RenderSystem.activeTexture(GL43.GL_TEXTURE1);
            this.fsaaFirstPassResultFBO.bindRead();
            RenderSystem.activeTexture(GL43.GL_TEXTURE2);
            RenderSystem.bindTexture(fsaaFirstPassResultFBO.getDepthTextureId());
            RenderSystem.activeTexture(GL43.GL_TEXTURE0);

            VRShaders.lanczosShader.setSampler("Sampler0", RenderSystem.getShaderTexture(0));
            VRShaders.lanczosShader.setSampler("Sampler1", RenderSystem.getShaderTexture(1));
            VRShaders._Lanczos_texelWidthOffsetUniform.set(0.0F);
            VRShaders._Lanczos_texelHeightOffsetUniform.set(1.0F / (3.0F * (float) this.fsaaLastPassResultFBO.viewHeight));
            VRShaders.lanczosShader.apply();

            this.drawQuad();

            // Clean up time
            VRShaders.lanczosShader.clear();
            this.fsaaLastPassResultFBO.unbindWrite();
            RenderSystem.depthFunc(GL43.GL_LEQUAL);
            RenderSystem.enableBlend();
        }
    }

    private void drawCircle(float width, float height) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        int i = 32;
        float f = width / 2;
        builder.vertex(width / 2, width / 2, 0.0F).endVertex();
        for (int j = 0; j < i + 1; ++j) {
            float f1 = (float) j / (float) i * (float) Math.PI * 2.0F;
            float f2 = (float) ((double) (width / 2) + Math.cos(f1) * (double) f);
            float f3 = (float) ((double) (width / 2) + Math.sin(f1) * (double) f);
            builder.vertex(f2, f3, 0.0F).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private void drawMask() {
        Minecraft mc = Minecraft.getInstance();
        ClientDataHolderVR dh = ClientDataHolderVR.getInstance();
        float[] verts = getStencilMask(dh.currentPass);
        if (verts == null) {
            return;
        }

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);

        mc.getTextureManager().bindForSetup(new ResourceLocation("vivecraft:textures/black.png"));

        for (int i = 0; i < verts.length; i += 2) {
            builder.vertex(verts[i] * dh.vrRenderer.renderScale, verts[i + 1] * dh.vrRenderer.renderScale, 0.0F).endVertex();
        }

        RenderSystem.setShader(GameRenderer::getPositionShader);
        BufferUploader.drawWithShader(builder.end());
    }

    private void drawQuad() {
        //RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1.0F, -1.0F, 0.0F).uv(0.0F, 0.0F).endVertex();
        builder.vertex(1.0F, -1.0F, 0.0F).uv(1.0F, 0.0F).endVertex();
        builder.vertex(1.0F, 1.0F, 0.0F).uv(1.0F, 1.0F).endVertex();
        builder.vertex(-1.0F, 1.0F, 0.0F).uv(0.0F, 1.0F).endVertex();
        BufferUploader.draw(builder.end());
    }

    public double getCurrentTimeSecs() {
        return (double) System.nanoTime() / 1.0E9D;
    }

    public double getFrameTiming() {
        return this.getCurrentTimeSecs();
    }

    public String getinitError() {
        return this.vr.initStatus;
    }

    public String getLastError() {
        return "";
    }

    public String getName() {
        return "OpenVR";
    }

    public List<RenderPass> getRenderPasses() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientDataHolderVR dataholder = ClientDataHolderVR.getInstance();
        List<RenderPass> list = new ArrayList<>();
        list.add(RenderPass.LEFT);
        list.add(RenderPass.RIGHT);

        // only do these, if the window is not minimized
        if (((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenWidth() > 0
            && ((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenHeight() > 0) {
            if (dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.FIRST_PERSON) {
                list.add(RenderPass.CENTER);
            } else if (dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY) {
                if (dataholder.vrSettings.mixedRealityUndistorted && dataholder.vrSettings.mixedRealityUnityLike) {
                    list.add(RenderPass.CENTER);
                }

                list.add(RenderPass.THIRD);
            } else if (dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON) {
                list.add(RenderPass.THIRD);
            }
        }

        if (minecraft.player != null) {
            if (TelescopeTracker.isTelescope(minecraft.player.getMainHandItem()) && TelescopeTracker.isViewing(0)) {
                list.add(RenderPass.SCOPER);
            }

            if (TelescopeTracker.isTelescope(minecraft.player.getOffhandItem()) && TelescopeTracker.isViewing(1)) {
                list.add(RenderPass.SCOPEL);
            }

            if (dataholder.cameraTracker.isVisible()) {
                list.add(RenderPass.CAMERA);
            }
        }

        return list;
    }

    public abstract Tuple<Integer, Integer> getRenderTextureSizes();

    public Tuple<Integer, Integer> getMirrorTextureSize(int eyeFBWidth, int eyeFBHeight, float resolutionScale) {
        mirrorFBWidth = (int) Math.ceil(((WindowExtension) (Object) Minecraft.getInstance().getWindow()).vivecraft$getActualScreenWidth() * resolutionScale);
        mirrorFBHeight = (int) Math.ceil(((WindowExtension) (Object) Minecraft.getInstance().getWindow()).vivecraft$getActualScreenHeight() * resolutionScale);

        if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY) {
            mirrorFBWidth = mirrorFBWidth / 2;

            if (ClientDataHolderVR.getInstance().vrSettings.mixedRealityUnityLike) {
                mirrorFBHeight = mirrorFBHeight / 2;
            }
        }

        if (ShadersHelper.needsSameSizeBuffers()) {
            mirrorFBWidth = eyeFBWidth;
            mirrorFBHeight = eyeFBHeight;
        }
        return new Tuple<>(mirrorFBWidth, mirrorFBHeight);
    }

    public Tuple<Integer, Integer> getTelescopeTextureSize(int eyeFBWidth, int eyeFBHeight) {
        int telescopeFBwidth = 720;
        int telescopeFBheight = 720;

        if (ShadersHelper.needsSameSizeBuffers()) {
            telescopeFBwidth = eyeFBWidth;
            telescopeFBheight = eyeFBHeight;
        }
        return new Tuple<>(telescopeFBwidth, telescopeFBheight);
    }

    public Tuple<Integer, Integer> getCameraTextureSize(int eyeFBWidth, int eyeFBHeight) {
        int cameraFBwidth = Math.round(1920.0F * ClientDataHolderVR.getInstance().vrSettings.handCameraResScale);
        int cameraFBheight = Math.round(1080.0F * ClientDataHolderVR.getInstance().vrSettings.handCameraResScale);

        if (ShadersHelper.needsSameSizeBuffers()) {
            // correct for camera aspect, since that is 16:9
            float aspect = (float) cameraFBwidth / (float) cameraFBheight;
            if (aspect > (float) (eyeFBWidth / eyeFBHeight)) {
                cameraFBwidth = eyeFBWidth;
                cameraFBheight = Math.round((float) eyeFBWidth / aspect);
            } else {
                cameraFBwidth = Math.round((float) eyeFBHeight * aspect);
                cameraFBheight = eyeFBHeight;
            }
        }
        return new Tuple<>(cameraFBwidth, cameraFBheight);
    }

    public float[] getStencilMask(RenderPass eye) {
        if (this.hiddenMesheVertecies != null && (eye == RenderPass.LEFT || eye == RenderPass.RIGHT)) {
            return eye == RenderPass.LEFT ? this.hiddenMesheVertecies[0] : this.hiddenMesheVertecies[1];
        } else {
            return null;
        }
    }

    public boolean isInitialized() {
        return this.vr.initSuccess;
    }

    public void reinitWithoutShaders(String cause) {
        if (ShadersHelper.isShaderActive()) {
            // shaders have all passes created, only need a resize
            this.resizeFrameBuffers(cause);
        } else {
            this.reinitFrameBuffers(cause);
        }
    }

    public void reinitFrameBuffers(String cause) {
        if (acceptReinits) {
            if (!reinitFramebuffers) {
                // only print the first cause
                System.out.println("Reinit Render: " + cause);
            }
            this.reinitFramebuffers = true;
        }
    }

    public void resizeFrameBuffers(String cause) {
        if (!cause.isEmpty() && !this.resizeFrameBuffers) {
            System.out.println("Resizing Buffers: " + cause);
        }
        this.resizeFrameBuffers = true;
    }

    public void setupRenderConfiguration(boolean render) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        ClientDataHolderVR dataholder = ClientDataHolderVR.getInstance();

        if (minecraft.getWindow().getWindow() != this.lastWindow) {
            this.lastWindow = minecraft.getWindow().getWindow();
            this.reinitFrameBuffers("Window Handle Changed");
        }

        if (this.lastEnableVsync != minecraft.options.enableVsync().get()) {
            this.reinitFrameBuffers("VSync Changed");
            this.lastEnableVsync = minecraft.options.enableVsync().get();
        }

        if (this.lastMirror != dataholder.vrSettings.displayMirrorMode) {
            this.reinitWithoutShaders("Mirror Changed");
            this.lastMirror = dataholder.vrSettings.displayMirrorMode;
        }

        if ((framebufferMR == null || framebufferUndistorted == null) && ShadersHelper.isShaderActive()) {
            this.reinitFrameBuffers("Shaders on, but some buffers not initialized");
        }
        if (Minecraft.getInstance().options.graphicsMode().get() != previousGraphics) {
            previousGraphics = Minecraft.getInstance().options.graphicsMode().get();
            this.reinitFrameBuffers("gfx setting change");
        }

        if (this.resizeFrameBuffers && !this.reinitFramebuffers) {
            resizeFrameBuffers = false;
            Tuple<Integer, Integer> tuple = this.getRenderTextureSizes();
            int eyew = tuple.getA();
            int eyeh = tuple.getB();

            float resolutionScale = ResolutionControlHelper.isLoaded() ? ResolutionControlHelper.getCurrentScaleFactor() : 1.0F;

            this.renderScale = (float) Math.sqrt(dataholder.vrSettings.renderScaleFactor) * resolutionScale;
            int eyeFBWidth = (int) Math.ceil(eyew * this.renderScale);
            int eyeFBHeight = (int) Math.ceil(eyeh * this.renderScale);

            Tuple<Integer, Integer> mirrorSize = getMirrorTextureSize(eyeFBWidth, eyeFBHeight, resolutionScale);
            Tuple<Integer, Integer> telescopeSize = getTelescopeTextureSize(eyeFBWidth, eyeFBHeight);
            Tuple<Integer, Integer> cameraSize = getCameraTextureSize(eyeFBWidth, eyeFBHeight);

            // main render target
            ((RenderTargetExtension) WorldRenderPass.stereoXR.target).vivecraft$setUseStencil(dataholder.vrSettings.vrUseStencil);
            WorldRenderPass.stereoXR.resize(eyeFBWidth, eyeFBHeight);
            if (dataholder.vrSettings.useFsaa) {
                this.fsaaFirstPassResultFBO.resize(eyew, eyeFBHeight, Minecraft.ON_OSX);
            }

            // mirror
            if (mirrorSize.getA() > 0 && mirrorSize.getB() > 0) {
                if (WorldRenderPass.center != null) {
                    WorldRenderPass.center.resize(mirrorSize.getA(), mirrorSize.getB());
                }
                if (WorldRenderPass.mixedReality != null) {
                    WorldRenderPass.mixedReality.resize(mirrorSize.getA(), mirrorSize.getB());
                }
            }

            // telescopes
            WorldRenderPass.leftTelescope.resize(telescopeSize.getA(), telescopeSize.getB());
            WorldRenderPass.rightTelescope.resize(telescopeSize.getA(), telescopeSize.getB());

            // camera
            cameraFramebuffer.resize(cameraSize.getA(), cameraSize.getB(), Minecraft.ON_OSX);
            if (ShadersHelper.needsSameSizeBuffers()) {
                WorldRenderPass.camera.resize(eyeFBWidth, eyeFBHeight);
            } else {
                WorldRenderPass.camera.resize(cameraSize.getA(), cameraSize.getB());
            }

            // resize gui, if changed
            if (GuiHandler.updateResolution()) {
                GuiHandler.guiFramebuffer.resize(GuiHandler.guiWidth, GuiHandler.guiHeight, Minecraft.ON_OSX);
                if (minecraft.screen != null) {
                    int l2 = minecraft.getWindow().getGuiScaledWidth();
                    int j3 = minecraft.getWindow().getGuiScaledHeight();
                    minecraft.screen.init(minecraft, l2, j3);
                }
            }
        }

        //for OPENXR, it needs to reinit
        this.eyeproj[0] = this.getProjectionMatrix(0, ((GameRendererExtension) minecraft.gameRenderer).vivecraft$getMinClipDistance(), ((GameRendererExtension) minecraft.gameRenderer).vivecraft$getClipDistance());
        this.eyeproj[1] = this.getProjectionMatrix(1, ((GameRendererExtension) minecraft.gameRenderer).vivecraft$getMinClipDistance(), ((GameRendererExtension) minecraft.gameRenderer).vivecraft$getClipDistance());

        if (this.reinitFramebuffers) {
            this.reinitShadersFlag = true;
            this.checkGLError("Start Init");

            if (Util.getPlatform() == Util.OS.WINDOWS && GlUtil.getRenderer().toLowerCase().contains("intel")) {
                StringBuilder gpus = new StringBuilder();
                boolean onlyIntel = true;
                for (GraphicsCard gpu : (new SystemInfo()).getHardware().getGraphicsCards()) {
                    gpus.append("\n");
                    if (gpu.getVendor().toLowerCase().contains("intel") || gpu.getName().toLowerCase().contains("intel")) {
                        gpus.append("§c❌§r ");
                    } else {
                        onlyIntel = false;
                        gpus.append("§a✔§r ");
                    }
                    gpus.append(gpu.getVendor()).append(": ").append(gpu.getName());
                }
                throw new RenderConfigException("Incompatible", Component.translatable(
                    "vivecraft.messages.intelgraphics1",
                    Component.literal(GlUtil.getRenderer()).withStyle(ChatFormatting.GOLD),
                    gpus.toString(),
                    onlyIntel ? Component.empty()
                              : Component.translatable("vivecraft.messages.intelgraphics2", Component.literal("https://www.vivecraft.org/faq/#gpu")
                                  .withStyle(style -> style.withUnderlined(true)
                                      .withColor(ChatFormatting.GREEN)
                                      .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, CommonComponents.GUI_OPEN_IN_BROWSER))
                                      .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.vivecraft.org/faq/#gpu"))))));
            }

            if (!this.isInitialized()) {
                throw new RenderConfigException("Failed to initialise stereo rendering plugin: " + this.getName(), Component.literal(this.getinitError()));
            }

            Tuple<Integer, Integer> tuple = this.getRenderTextureSizes();
            int eyew = tuple.getA();
            int eyeh = tuple.getB();

            destroy();

            this.createRenderTexture(eyew, eyeh);

            float resolutionScale = ResolutionControlHelper.isLoaded() ? ResolutionControlHelper.getCurrentScaleFactor() : 1.0F;

            this.renderScale = (float) Math.sqrt(dataholder.vrSettings.renderScaleFactor) * resolutionScale;
            int eyeFBWidth = (int) Math.ceil(eyew * this.renderScale);
            int eyeFBHeight = (int) Math.ceil(eyeh * this.renderScale);

            this.framebufferVrRender = new VRTextureTarget("3D Render", eyeFBWidth, eyeFBHeight, true, false, -1, true, true, dataholder.vrSettings.vrUseStencil);
            WorldRenderPass.stereoXR = new WorldRenderPass((VRTextureTarget) this.framebufferVrRender);
            dataholder.print(this.framebufferVrRender.toString());
            this.checkGLError("3D framebuffer setup");

            getMirrorTextureSize(eyeFBWidth, eyeFBHeight, resolutionScale);

            List<RenderPass> list = this.getRenderPasses();

            for (RenderPass renderpass : list) {
                System.out.println("Passes: " + renderpass.toString());
            }

            // only do these, if the window is not minimized
            if (mirrorFBWidth > 0 && mirrorFBHeight > 0) {
                if (list.contains(RenderPass.THIRD) || ShadersHelper.isShaderActive()) {
                    this.framebufferMR = new VRTextureTarget("Mixed Reality Render", this.mirrorFBWidth, this.mirrorFBHeight, true, false, -1, true, false, false);
                    WorldRenderPass.mixedReality = new WorldRenderPass((VRTextureTarget) this.framebufferMR);
                    dataholder.print(this.framebufferMR.toString());
                    this.checkGLError("Mixed reality framebuffer setup");
                }

                if (list.contains(RenderPass.CENTER) || ShadersHelper.isShaderActive()) {
                    this.framebufferUndistorted = new VRTextureTarget("Undistorted View Render", this.mirrorFBWidth, this.mirrorFBHeight, true, false, -1, false, false, false);
                    WorldRenderPass.center = new WorldRenderPass((VRTextureTarget) this.framebufferUndistorted);
                    dataholder.print(this.framebufferUndistorted.toString());
                    this.checkGLError("Undistorted view framebuffer setup");
                }
            }

            GuiHandler.updateResolution();
            GuiHandler.guiFramebuffer = new VRTextureTarget("GUI", GuiHandler.guiWidth, GuiHandler.guiHeight, true, false, -1, false, true, false);
            dataholder.print(GuiHandler.guiFramebuffer.toString());
            this.checkGLError("GUI framebuffer setup");
            KeyboardHandler.Framebuffer = new VRTextureTarget("Keyboard", GuiHandler.guiWidth, GuiHandler.guiHeight, true, false, -1, false, true, false);
            dataholder.print(KeyboardHandler.Framebuffer.toString());
            this.checkGLError("Keyboard framebuffer setup");
            RadialHandler.Framebuffer = new VRTextureTarget("Radial Menu", GuiHandler.guiWidth, GuiHandler.guiHeight, true, false, -1, false, true, false);
            dataholder.print(RadialHandler.Framebuffer.toString());
            this.checkGLError("Radial framebuffer setup");


            Tuple<Integer, Integer> telescopeSize = getTelescopeTextureSize(eyeFBWidth, eyeFBHeight);
            this.telescopeFramebufferR = new VRTextureTarget("TelescopeR", telescopeSize.getA(), telescopeSize.getB(), true, false, -1, true, false, false);
            WorldRenderPass.rightTelescope = new WorldRenderPass((VRTextureTarget) this.telescopeFramebufferR);
            dataholder.print(this.telescopeFramebufferR.toString());
            this.telescopeFramebufferR.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            this.telescopeFramebufferR.clear(Minecraft.ON_OSX);
            this.checkGLError("TelescopeR framebuffer setup");

            this.telescopeFramebufferL = new VRTextureTarget("TelescopeL", telescopeSize.getA(), telescopeSize.getB(), true, false, -1, true, false, false);
            WorldRenderPass.leftTelescope = new WorldRenderPass((VRTextureTarget) this.telescopeFramebufferL);
            dataholder.print(this.telescopeFramebufferL.toString());
            this.telescopeFramebufferL.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            this.telescopeFramebufferL.clear(Minecraft.ON_OSX);
            this.checkGLError("TelescopeL framebuffer setup");


            Tuple<Integer, Integer> cameraSize = getCameraTextureSize(eyeFBWidth, eyeFBHeight);
            int cameraRenderFBwidth = cameraSize.getA();
            int cameraRenderFBheight = cameraSize.getB();

            if (ShadersHelper.needsSameSizeBuffers()) {
                cameraRenderFBwidth = eyeFBWidth;
                cameraRenderFBheight = eyeFBHeight;
            }

            this.cameraFramebuffer = new VRTextureTarget("Handheld Camera", cameraSize.getA(), cameraSize.getB(), true, false, -1, true, false, false);
            dataholder.print(this.cameraFramebuffer.toString());

            this.checkGLError("Camera framebuffer setup");
            this.cameraRenderFramebuffer = new VRTextureTarget("Handheld Camera Render", cameraRenderFBwidth, cameraRenderFBheight, true, false, -1, true, true, false);
            WorldRenderPass.camera = new WorldRenderPass((VRTextureTarget) this.cameraRenderFramebuffer);
            dataholder.print(this.cameraRenderFramebuffer.toString());

            this.checkGLError("Camera render framebuffer setup");
            ((GameRendererExtension) minecraft.gameRenderer).vivecraft$setupClipPlanes();
            this.eyeproj[0] = this.getProjectionMatrix(0, ((GameRendererExtension) minecraft.gameRenderer).vivecraft$getMinClipDistance(), ((GameRendererExtension) minecraft.gameRenderer).vivecraft$getClipDistance());
            this.eyeproj[1] = this.getProjectionMatrix(1, ((GameRendererExtension) minecraft.gameRenderer).vivecraft$getMinClipDistance(), ((GameRendererExtension) minecraft.gameRenderer).vivecraft$getClipDistance());

            if (dataholder.vrSettings.useFsaa) {
                try {
                    this.checkGLError("pre FSAA FBO creation");
                    this.fsaaFirstPassResultFBO = new VRTextureTarget("FSAA Pass1 FBO", eyew, eyeFBHeight, true, false, -1, false, false, false);
                    this.fsaaLastPassResultFBO = new VRTextureTarget("FSAA Pass2 FBO", eyew, eyeh, true, false, -1, false, false, false);
                    dataholder.print(this.fsaaFirstPassResultFBO.toString());
                    dataholder.print(this.fsaaLastPassResultFBO.toString());
                    this.checkGLError("FSAA FBO creation");
                    VRShaders.setupFSAA();
                    ShaderHelper.checkGLError("FBO init fsaa shader");
                } catch (Exception exception) {
                    dataholder.vrSettings.useFsaa = false;
                    dataholder.vrSettings.saveOptions();
                    System.out.println(exception.getMessage());
                    this.reinitFramebuffers = true;
                    return;
                }
            }

            try {
                minecraft.mainRenderTarget = this.framebufferVrRender;
                VRShaders.setupDepthMask();
                ShaderHelper.checkGLError("init depth shader");
                VRShaders.setupFOVReduction();
                ShaderHelper.checkGLError("init FOV shader");
                VRShaders.setupPortalShaders();
                ShaderHelper.checkGLError("init portal shader");
                minecraft.gameRenderer.checkEntityPostEffect(minecraft.getCameraEntity());
            } catch (Exception exception1) {
                System.out.println(exception1.getMessage());
                System.exit(-1);
            }

            if (minecraft.screen != null) {
                int l2 = minecraft.getWindow().getGuiScaledWidth();
                int j3 = minecraft.getWindow().getGuiScaledHeight();
                minecraft.screen.init(minecraft, l2, j3);
            }

            long windowPixels = (long) ((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenWidth() * ((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenHeight();
            long vrPixels = eyeFBWidth * eyeFBHeight * 2L;

            if (list.contains(RenderPass.CENTER)) {
                vrPixels += windowPixels;
            }

            if (list.contains(RenderPass.THIRD)) {
                vrPixels += windowPixels;
            }

            System.out.println("[Minecrift] New render config:" +
                "\nOpenVR target width: " + eyew + ", height: " + eyeh + " [" + String.format("%.1f", (float) (eyew * eyeh) / 1000000.0F) + " MP]" +
                "\nRender target width: " + eyeFBWidth + ", height: " + eyeFBHeight + " [Render scale: " + Math.round(dataholder.vrSettings.renderScaleFactor * 100.0F) + "%, " + String.format("%.1f", (float) (eyeFBWidth * eyeFBHeight) / 1000000.0F) + " MP]" +
                "\nMain window width: " + ((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenWidth() + ", height: " + ((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenHeight() + " [" + String.format("%.1f", (float) windowPixels / 1000000.0F) + " MP]" +
                "\nTotal shaded pixels per frame: " + String.format("%.1f", (float) vrPixels / 1000000.0F) + " MP (eye stencil not accounted for)");
            this.lastDisplayFBWidth = eyeFBWidth;
            this.lastDisplayFBHeight = eyeFBHeight;
            this.reinitFramebuffers = false;

            acceptReinits = false;
            ShadersHelper.maybeReloadShaders();
            acceptReinits = true;
        }
    }

    public boolean wasDisplayResized() {
        Minecraft minecraft = Minecraft.getInstance();
        int i = ((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenHeight();
        int j = ((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenWidth();
        boolean flag = this.dispLastHeight != i || this.dispLastWidth != j;
        this.dispLastHeight = i;
        this.dispLastWidth = j;
        return flag;
    }

    public void destroy() {
        if (this.framebufferVrRender != null) {
            WorldRenderPass.stereoXR.close();
            WorldRenderPass.stereoXR = null;
            this.framebufferVrRender.destroyBuffers();
            this.framebufferVrRender = null;
        }

        if (this.framebufferMR != null) {
            WorldRenderPass.mixedReality.close();
            WorldRenderPass.mixedReality = null;
            this.framebufferMR.destroyBuffers();
            this.framebufferMR = null;
        }

        if (this.framebufferUndistorted != null) {
            WorldRenderPass.center.close();
            WorldRenderPass.center = null;
            this.framebufferUndistorted.destroyBuffers();
            this.framebufferUndistorted = null;
        }

        if (GuiHandler.guiFramebuffer != null) {
            GuiHandler.guiFramebuffer.destroyBuffers();
            GuiHandler.guiFramebuffer = null;
        }

        if (KeyboardHandler.Framebuffer != null) {
            KeyboardHandler.Framebuffer.destroyBuffers();
            KeyboardHandler.Framebuffer = null;
        }

        if (RadialHandler.Framebuffer != null) {
            RadialHandler.Framebuffer.destroyBuffers();
            RadialHandler.Framebuffer = null;
        }

        if (this.telescopeFramebufferL != null) {
            WorldRenderPass.leftTelescope.close();
            WorldRenderPass.leftTelescope = null;
            this.telescopeFramebufferL.destroyBuffers();
            this.telescopeFramebufferL = null;
        }

        if (this.telescopeFramebufferR != null) {
            WorldRenderPass.rightTelescope.close();
            WorldRenderPass.rightTelescope = null;
            this.telescopeFramebufferR.destroyBuffers();
            this.telescopeFramebufferR = null;
        }

        if (this.cameraFramebuffer != null) {
            this.cameraFramebuffer.destroyBuffers();
            this.cameraFramebuffer = null;
        }

        if (this.cameraRenderFramebuffer != null) {
            WorldRenderPass.camera.close();
            WorldRenderPass.camera = null;
            this.cameraRenderFramebuffer.destroyBuffers();
            this.cameraRenderFramebuffer = null;
        }

        if (this.fsaaFirstPassResultFBO != null) {
            this.fsaaFirstPassResultFBO.destroyBuffers();
            this.fsaaFirstPassResultFBO = null;
        }

        if (this.fsaaLastPassResultFBO != null) {
            this.fsaaLastPassResultFBO.destroyBuffers();
            this.fsaaLastPassResultFBO = null;
        }
    }
}
