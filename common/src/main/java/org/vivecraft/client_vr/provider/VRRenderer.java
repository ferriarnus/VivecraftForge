package org.vivecraft.client_vr.provider;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.vivecraft.client.Xplat;
import org.vivecraft.client.extensions.RenderTargetExtension;
import org.vivecraft.client.utils.StencilHelper;
import org.vivecraft.client.utils.TextUtils;
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
import org.vivecraft.client_vr.render.VRShaders;
import org.vivecraft.client_vr.render.helpers.RenderHelper;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.WorldRenderPass;
import org.vivecraft.mod_compat_vr.resolutioncontrol.ResolutionControlHelper;
import org.vivecraft.mod_compat_vr.shaders.ShadersHelper;
import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class VRRenderer {

    // projection matrices
    public Matrix4f[] eyeProj = new Matrix4f[2];
    private float lastFarClip = 0F;

    // render buffers
    protected int LeftEyeTextureId = -1;
    protected int RightEyeTextureId = -1;
    public RenderTarget framebufferMR;
    public RenderTarget framebufferUndistorted;
    public RenderTarget framebufferVrRender;
    public RenderTarget fsaaFirstPassResultFBO;
    public RenderTarget fsaaLastPassResultFBO;
    public RenderTarget cameraFramebuffer;
    public RenderTarget cameraRenderFramebuffer;
    public RenderTarget telescopeFramebufferL;
    public RenderTarget telescopeFramebufferR;
    public RenderTarget mirrorFramebuffer;

    // Stencil mesh buffer for each eye
    protected float[][] hiddenMeshVertices = new float[2][];

    // variables to check setting changes that need framebuffers reinits/resizes
    private GraphicsStatus previousGraphics = null;
    protected VRSettings.MirrorMode lastMirror;
    public long lastWindow = 0L;
    public int mirrorFBHeight;
    public int mirrorFBWidth;
    protected boolean reinitFrameBuffers = true;
    protected boolean resizeFrameBuffers = false;
    public float renderScale;

    // render resolution set by the VR runtime, includes the supersampling factor
    protected Tuple<Integer, Integer> resolution;

    // supersampling set by the vr runtime
    public float ss = -1.0F;
    protected MCVR vr;

    // last error caused by this renderer
    protected String lastError = "";

    public VRRenderer(MCVR vr) {
        this.vr = vr;
    }

    /**
     * creates the textures needed for the VR runtime to submit the frames
     *
     * @param width  width of the texture
     * @param height height of the texture
     */
    public abstract void createRenderTexture(int width, int height) throws RenderConfigException;

    /**
     * gets the cached projection matrix if the farClip distance matches with the last, else gets a new one from the VR runtime
     *
     * @param eyeType  which eye to get the projection matrix for, 0 = Left, 1 = Right
     * @param nearClip near clip plane of the projection matrix
     * @param farClip  far clip plane of the projection matrix
     * @return the projection matrix
     */
    public Matrix4f getCachedProjectionMatrix(int eyeType, float nearClip, float farClip) {
        if (farClip != this.lastFarClip) {
            this.lastFarClip = farClip;
            // fetch both at the same time to make sure they use the same clip planes
            this.eyeProj[0] = this.getProjectionMatrix(0, nearClip, farClip);
            this.eyeProj[1] = this.getProjectionMatrix(1, nearClip, farClip);
        }

        return this.eyeProj[eyeType];
    }

    /**
     * gets the projection matrix from the vr runtime with the given parameters
     *
     * @param eyeType  which eye to get the projection matrix for, 0 = Left, 1 = Right
     * @param nearClip near clip plane of the projection matrix
     * @param farClip  far clip plane of the projection matrix
     * @return the projection matrix
     */
    protected abstract Matrix4f getProjectionMatrix(int eyeType, float nearClip, float farClip);

    /**
     * this is the last thing to call after all passes are rendered.
     * Submits the rendered VR views to the VR runtime
     *
     * @throws RenderConfigException when the VR runtime throws any errors
     */
    public abstract void endFrame() throws RenderConfigException;

    /**
     * @return if this VRRenderer provides stencil for the left/right RenderPass
     */
    public abstract boolean providesStencilMask();

    /**
     * @return the left eye rendertarget
     */
    public abstract RenderTarget getLeftEyeTarget();

    /**
     * @return the right eye rendertarget
     */
    public abstract RenderTarget getRightEyeTarget();
    /**
     * gets an array with the vertex info of the stencil mesh, if there is one provided by this renderer
     *
     * @param eye which eye the stencil should be for
     * @return the stencil for that eye, if available
     */
    public float[] getStencilMask(RenderPass eye) {
        if (eye == RenderPass.LEFT || eye == RenderPass.RIGHT) {
            return eye == RenderPass.LEFT ? this.hiddenMeshVertices[0] : this.hiddenMeshVertices[1];
        } else {
            return null;
        }
    }

    /**
     * sets up the stencil rendering, and draws the stencil
     *
     * @param inverse if the stencil covered part, or the inverse of it should be drawn
     */
    public void doStencil(boolean inverse) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientDataHolderVR dataholder = ClientDataHolderVR.getInstance();

        // setup stencil for writing
        if (StencilHelper.stencilBufferSupported()) {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            RenderSystem.stencilMask(0xFF); // Write to stencil buffer
        }

        if (inverse) {
            // clear whole image for total mask in color, stencil, depth
            RenderSystem.clearStencil(0xFF);
            RenderSystem.clearDepth(0);

            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF); // Set any stencil to 0
            RenderSystem.colorMask(false, false, false, true);
        } else {
            // clear whole image for total transparency
            RenderSystem.clearStencil(0);
            RenderSystem.clearDepth(1);

            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0xFF, 0xFF); // Set any stencil to 1
            RenderSystem.colorMask(true, true, true, true);
        }

        if (StencilHelper.stencilBufferSupported()) {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        } else {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT);
        }

        RenderSystem.clearStencil(0);
        RenderSystem.clearDepth(1);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.disableCull();

        RenderSystem.setShaderColor(0F, 0F, 0F, 1.0F);

        RenderTarget fb = minecraft.getMainRenderTarget();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, fb.viewWidth, 0.0F, fb.viewHeight, 0.0F, 20.0F),
            ProjectionType.ORTHOGRAPHIC);
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        if (inverse) {
            // draw on far clip
            RenderSystem.getModelViewStack().translate(0, 0, -20);
        }

        CompiledShaderProgram lastShader = RenderSystem.getShader();

        if (dataholder.currentPass == RenderPass.SCOPEL || dataholder.currentPass == RenderPass.SCOPER) {
            drawCircle(fb.viewWidth, fb.viewHeight);
        } else if (providesStencilMask() &&
            (dataholder.currentPass == RenderPass.LEFT || dataholder.currentPass == RenderPass.RIGHT))
        {
            drawMask();
        }

        RenderSystem.restoreProjectionMatrix();
        RenderSystem.getModelViewStack().popMatrix();

        RenderSystem.depthMask(true); // Do write to depth buffer
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableDepthTest();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        RenderSystem.setShader(lastShader);
        if (StencilHelper.stencilBufferSupported()) {
            RenderSystem.stencilFunc(GL11.GL_NOTEQUAL, 255, 1);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.stencilMask(0); // Dont Write to stencil buffer
        }
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    /**
     * triangulates a circle and draws it
     *
     * @param width  width of the circle in screen pixels
     * @param height height of the circle in screen pixels
     */
    private void drawCircle(float width, float height) {
        BufferBuilder builder = Tesselator.getInstance()
            .begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        final float edges = 32.0F;
        float radius = width / 2.0F;

        // put middle vertex
        builder.addVertex(radius, radius, 0.0F);

        // put outer vertices
        for (int i = 0; i < edges + 1; i++) {
            float startAngle = (float) i / edges * Mth.TWO_PI;
            builder.addVertex(
                radius + Mth.cos(startAngle) * radius,
                radius + Mth.sin(startAngle) * radius,
                0.0F);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /**
     * draws the stencil provided by the VR runtime
     */
    private void drawMask() {
        Minecraft mc = Minecraft.getInstance();
        ClientDataHolderVR dh = ClientDataHolderVR.getInstance();
        float[] verts = getStencilMask(dh.currentPass);
        if (verts == null) {
            return;
        }

        BufferBuilder builder = Tesselator.getInstance()
            .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);

        RenderSystem.setShaderTexture(0, RenderHelper.BLACK_TEXTURE);
        RenderSystem.bindTexture(RenderSystem.getShaderTexture(0));
        RenderSystem.setShaderTexture(0, RenderHelper.BLACK_TEXTURE);

        for (int i = 0; i < verts.length; i += 2) {
            builder.addVertex(
                verts[i] * this.renderScale + 0.5F,
                verts[i + 1] * this.renderScale + 0.5F,
                0.0F);
        }

        RenderSystem.setShader(CoreShaders.POSITION);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /**
     * @return String of any error that happened during init of the VR provider
     */
    public String getInitError() {
        return this.vr.initStatus;
    }

    /**
     * @return String with the last error, if there was one
     */
    public String getLastError() {
        return this.lastError;
    }

    /**
     * @return name of the VRRenderer
     */
    public abstract String getName();

    /**
     * @return a list of passes that need to be rendered
     */
    public List<RenderPass> getRenderPasses() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientDataHolderVR dataholder = ClientDataHolderVR.getInstance();
        List<RenderPass> passes = new ArrayList<>();

        // Always do these for obvious reasons
        passes.add(RenderPass.LEFT);
        passes.add(RenderPass.RIGHT);

        // only do these, if the window is not minimized
        if (((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenWidth() > 0 &&
            ((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenHeight() > 0)
        {
            if (dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.FIRST_PERSON) {
                passes.add(RenderPass.CENTER);
            } else if (dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY) {
                if (dataholder.vrSettings.mixedRealityUndistorted && dataholder.vrSettings.mixedRealityUnityLike) {
                    passes.add(RenderPass.CENTER);
                }

                passes.add(RenderPass.THIRD);
            } else if (dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON) {
                passes.add(RenderPass.THIRD);
            }
        }

        if (minecraft.player != null) {
            if (TelescopeTracker.isTelescope(minecraft.player.getMainHandItem()) && TelescopeTracker.isViewing(0)) {
                passes.add(RenderPass.SCOPER);
            }

            if (TelescopeTracker.isTelescope(minecraft.player.getOffhandItem()) && TelescopeTracker.isViewing(1)) {
                passes.add(RenderPass.SCOPEL);
            }

            if (dataholder.cameraTracker.isVisible()) {
                passes.add(RenderPass.CAMERA);
            }
        }

        return passes;
    }

    /**
     * @return resolution of the headset view
     */
    public abstract Tuple<Integer, Integer> getRenderTextureSizes();

    /**
     * calculates the resolution of first/third person mirror view
     *
     * @param eyeFBWidth      headset view width
     * @param eyeFBHeight     headset view height
     * @param resolutionScale render scale from 3rd party mods
     * @return resolution of the desktop view mirror
     */
    public Tuple<Integer, Integer> getMirrorTextureSize(int eyeFBWidth, int eyeFBHeight, float resolutionScale) {
        this.mirrorFBWidth = (int) Math.ceil(
            ((WindowExtension) (Object) Minecraft.getInstance().getWindow()).vivecraft$getActualScreenWidth() *
                resolutionScale);
        this.mirrorFBHeight = (int) Math.ceil(
            ((WindowExtension) (Object) Minecraft.getInstance().getWindow()).vivecraft$getActualScreenHeight() *
                resolutionScale);

        if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY) {
            this.mirrorFBWidth = this.mirrorFBWidth / 2;

            if (ClientDataHolderVR.getInstance().vrSettings.mixedRealityUnityLike) {
                this.mirrorFBHeight = this.mirrorFBHeight / 2;
            }
        }

        if (ShadersHelper.needsSameSizeBuffers()) {
            this.mirrorFBWidth = eyeFBWidth;
            this.mirrorFBHeight = eyeFBHeight;
        }
        return new Tuple<>(this.mirrorFBWidth, this.mirrorFBHeight);
    }

    /**
     * calculates the resolution of the telescope view
     *
     * @param eyeFBWidth  headset view width
     * @param eyeFBHeight headset view height
     * @return resolution of the telescope view
     */
    public Tuple<Integer, Integer> getTelescopeTextureSize(int eyeFBWidth, int eyeFBHeight) {
        int telescopeFBwidth = 720;
        int telescopeFBheight = 720;

        if (ShadersHelper.needsSameSizeBuffers()) {
            telescopeFBwidth = eyeFBWidth;
            telescopeFBheight = eyeFBHeight;
        }
        return new Tuple<>(telescopeFBwidth, telescopeFBheight);
    }

    /**
     * calculates the resolution of the screenshot camera view
     *
     * @param eyeFBWidth  headset view width
     * @param eyeFBHeight headset view height
     * @return resolution of the screenshot camera view
     */
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

    /**
     * @return if this is successfully initialized
     */
    public boolean isInitialized() {
        return this.vr.initSuccess;
    }

    /**
     * method to tell the vrRenderer, that render buffers changed
     * when shaders are active a simple resize is called, without shaders they are completely reinitialized
     *
     * @param cause cause that gets logged
     */
    public void reinitWithoutShaders(String cause) {
        if (ShadersHelper.isShaderActive()) {
            // shaders have all passes created, only need a resize
            this.resizeFrameBuffers(cause);
        } else {
            this.reinitFrameBuffers(cause);
        }
    }

    /**
     * method to tell the vrRenderer, that render buffers changed and need to be regenerated next frame
     *
     * @param cause cause that gets logged
     */
    public void reinitFrameBuffers(String cause) {
        if (!this.reinitFrameBuffers) {
            // only print the first cause
            VRSettings.LOGGER.info("Vivecraft: Reinit Render: {}", cause);
        }
        this.reinitFrameBuffers = true;
    }

    /**
     * method to tell the vrRenderer, that render buffers size changed and just need to be resized next frame
     *
     * @param cause cause that gets logged
     */
    public void resizeFrameBuffers(String cause) {
        if (!cause.isEmpty() && !this.resizeFrameBuffers) {
            VRSettings.LOGGER.info("Vivecraft: Resizing Buffers: {}", cause);
        }
        this.resizeFrameBuffers = true;
    }

    /**
     * sets up rendering, and makes sure all buffers are generated and sized correctly
     *
     * @throws RenderConfigException in case something failed to initialize or the gpu vendor is unsupported
     * @throws IOException           can be thrown by the WorldRenderPass init when trying to load the shaders
     */
    public void setupRenderConfiguration(boolean render) throws RenderConfigException, IOException {
        Minecraft minecraft = Minecraft.getInstance();
        ClientDataHolderVR dataholder = ClientDataHolderVR.getInstance();

        // check if window is still the same
        if (minecraft.getWindow().getWindow() != this.lastWindow) {
            this.lastWindow = minecraft.getWindow().getWindow();
            this.reinitFrameBuffers("Window Handle Changed");
        }

        if (this.lastMirror != dataholder.vrSettings.displayMirrorMode) {
            this.reinitWithoutShaders("Mirror Changed");
            this.lastMirror = dataholder.vrSettings.displayMirrorMode;
        }

        if ((this.framebufferMR == null || this.framebufferUndistorted == null) && ShadersHelper.isShaderActive()) {
            this.reinitFrameBuffers("Shaders on, but some buffers not initialized");
        }
        if (Minecraft.getInstance().options.graphicsMode().get() != this.previousGraphics) {
            this.previousGraphics = Minecraft.getInstance().options.graphicsMode().get();
            this.reinitFrameBuffers("gfx setting changed to: " + this.previousGraphics);
        }

        if (this.resizeFrameBuffers && !this.reinitFrameBuffers) {
            Tuple<Integer, Integer> tuple = this.getRenderTextureSizes();
            int eyew = tuple.getA();
            int eyeh = tuple.getB();

            float resolutionScale =
                ResolutionControlHelper.isLoaded() ? ResolutionControlHelper.getCurrentScaleFactor() : 1.0F;

            this.renderScale = (float) Math.sqrt(dataholder.vrSettings.renderScaleFactor) * resolutionScale;
            int eyeFBWidth = (int) Math.ceil(eyew * this.renderScale);
            int eyeFBHeight = (int) Math.ceil(eyeh * this.renderScale);

            Tuple<Integer, Integer> mirrorSize = getMirrorTextureSize(eyeFBWidth, eyeFBHeight, resolutionScale);
            Tuple<Integer, Integer> telescopeSize = getTelescopeTextureSize(eyeFBWidth, eyeFBHeight);
            Tuple<Integer, Integer> cameraSize = getCameraTextureSize(eyeFBWidth, eyeFBHeight);

            // main render target
            if (dataholder.vrSettings.vrUseStencil && StencilHelper.stencilBufferSupported()) {
                ((RenderTargetExtension) WorldRenderPass.STEREO_XR.target)
                    .vivecraft$setStencil(!Xplat.enableRenderTargetStencil(WorldRenderPass.STEREO_XR.target));
            } else {
                ((RenderTargetExtension) WorldRenderPass.STEREO_XR.target).vivecraft$setStencil(false);
            }
            WorldRenderPass.STEREO_XR.resize(eyeFBWidth, eyeFBHeight);
            if (dataholder.vrSettings.useFsaa) {
                this.fsaaFirstPassResultFBO.resize(eyew, eyeFBHeight);
            }

            // mirror
            if (mirrorSize.getA() > 0 && mirrorSize.getB() > 0) {
                if (WorldRenderPass.CENTER != null) {
                    WorldRenderPass.CENTER.resize(mirrorSize.getA(), mirrorSize.getB());
                }
                if (WorldRenderPass.MIXED_REALITY != null) {
                    WorldRenderPass.MIXED_REALITY.resize(mirrorSize.getA(), mirrorSize.getB());
                }
                this.mirrorFramebuffer.resize(
                    ((WindowExtension) (Object) Minecraft.getInstance().getWindow()).vivecraft$getActualScreenWidth(),
                    ((WindowExtension) (Object) Minecraft.getInstance().getWindow()).vivecraft$getActualScreenHeight());
            }

            // telescopes
            WorldRenderPass.LEFT_TELESCOPE.resize(telescopeSize.getA(), telescopeSize.getB());
            WorldRenderPass.RIGHT_TELESCOPE.resize(telescopeSize.getA(), telescopeSize.getB());

            // camera
            this.cameraFramebuffer.resize(cameraSize.getA(), cameraSize.getB());
            if (ShadersHelper.needsSameSizeBuffers()) {
                WorldRenderPass.CAMERA.resize(eyeFBWidth, eyeFBHeight);
            } else {
                WorldRenderPass.CAMERA.resize(cameraSize.getA(), cameraSize.getB());
            }

            // resize gui, if changed
            boolean mipmapChanged = dataholder.vrSettings.guiMipmaps !=
                ((RenderTargetExtension) GuiHandler.GUI_FRAMEBUFFER).vivecraft$hasMipmaps();
            if (GuiHandler.updateResolution() || mipmapChanged) {
                boolean mipmaps = dataholder.vrSettings.guiMipmaps;
                ((RenderTargetExtension) GuiHandler.GUI_FRAMEBUFFER).vivecraft$setMipmaps(mipmaps);
                GuiHandler.GUI_FRAMEBUFFER.resize(GuiHandler.GUI_WIDTH, GuiHandler.GUI_HEIGHT);

                ((RenderTargetExtension) RadialHandler.FRAMEBUFFER).vivecraft$setMipmaps(mipmaps);
                RadialHandler.FRAMEBUFFER.resize(GuiHandler.GUI_WIDTH, GuiHandler.GUI_HEIGHT);

                ((RenderTargetExtension) KeyboardHandler.FRAMEBUFFER).vivecraft$setMipmaps(mipmaps);
                KeyboardHandler.FRAMEBUFFER.resize(GuiHandler.GUI_WIDTH, GuiHandler.GUI_HEIGHT);
                if (minecraft.screen != null) {
                    int guiWidth = minecraft.getWindow().getGuiScaledWidth();
                    int guiHeight = minecraft.getWindow().getGuiScaledHeight();
                    minecraft.screen.init(minecraft, guiWidth, guiHeight);
                }
            }
            // need to recall this, for PostChains to get the right resize
            Minecraft.getInstance().resizeDisplay();

            this.resizeFrameBuffers = false;
        }

        //for OPENXR, it needs to reinit
        this.eyeProj[0] = this.getProjectionMatrix(0, ((GameRendererExtension) minecraft.gameRenderer).vivecraft$getMinClipDistance(), lastFarClip);
        this.eyeProj[1] = this.getProjectionMatrix(1, ((GameRendererExtension) minecraft.gameRenderer).vivecraft$getMinClipDistance(), lastFarClip);

        if (this.reinitFrameBuffers) {
            RenderHelper.checkGLError("Start Init");

            // intel drivers have issues with opengl interop on windows so throw an error
            if (Util.getPlatform() == Util.OS.WINDOWS && GlUtil.getRenderer().toLowerCase().contains("intel")) {
                StringBuilder gpus = new StringBuilder();
                boolean onlyIntel = true;
                for (GraphicsCard gpu : (new SystemInfo()).getHardware().getGraphicsCards()) {
                    gpus.append("\n");
                    if (gpu.getVendor().toLowerCase().contains("intel") ||
                        gpu.getName().toLowerCase().contains("intel"))
                    {
                        gpus.append("§c❌§r ");
                    } else {
                        onlyIntel = false;
                        gpus.append("§a✔§r ");
                    }
                    gpus.append(gpu.getVendor()).append(": ").append(gpu.getName());
                }
                throw new RenderConfigException(Component.translatable("vivecraft.messages.incompatiblegpu"),
                    Component.translatable("vivecraft.messages.intelgraphics1",
                        Component.literal(GlUtil.getRenderer()).withStyle(ChatFormatting.GOLD),
                        gpus.toString(),
                        onlyIntel ? Component.empty()
                            : Component.translatable("vivecraft.messages.intelgraphics2",
                            Component.literal("https://www.vivecraft.org/faq/#gpu")
                                .withStyle(style -> style.withUnderlined(true)
                                    .withColor(ChatFormatting.GREEN)
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        CommonComponents.GUI_OPEN_IN_BROWSER))
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                                        "https://www.vivecraft.org/faq/#gpu"))))));
            }

            if (!this.isInitialized()) {
                throw new RenderConfigException(
                    Component.translatable("vivecraft.messages.renderiniterror", this.getName()),
                    Component.literal(this.getInitError()));
            }

            Tuple<Integer, Integer> tuple = this.getRenderTextureSizes();
            int eyew = tuple.getA();
            int eyeh = tuple.getB();

            destroyBuffers();

            this.createRenderTexture(eyew, eyeh);

                VRSettings.LOGGER.info("Vivecraft: VR Provider supplied render texture IDs: {}, {}",
                    this.LeftEyeTextureId, this.RightEyeTextureId);
                VRSettings.LOGGER.info("Vivecraft: VR Provider supplied texture resolution: {} x {}", eyew, eyeh);

            RenderHelper.checkGLError("Render Texture setup");

            float resolutionScale =
                ResolutionControlHelper.isLoaded() ? ResolutionControlHelper.getCurrentScaleFactor() : 1.0F;

            this.renderScale = (float) Math.sqrt(dataholder.vrSettings.renderScaleFactor) * resolutionScale;
            int eyeFBWidth = (int) Math.ceil(eyew * this.renderScale);
            int eyeFBHeight = (int) Math.ceil(eyeh * this.renderScale);

            this.framebufferVrRender = new VRTextureTarget("3D Render", eyeFBWidth, eyeFBHeight, true, -1, true, false,
                dataholder.vrSettings.vrUseStencil && StencilHelper.stencilBufferSupported());
            WorldRenderPass.STEREO_XR = new WorldRenderPass(this.framebufferVrRender);
            VRSettings.LOGGER.info("Vivecraft: {}", this.framebufferVrRender);
            RenderHelper.checkGLError("3D framebuffer setup");

            getMirrorTextureSize(eyeFBWidth, eyeFBHeight, resolutionScale);

            List<RenderPass> list = this.getRenderPasses();

            VRSettings.LOGGER.info("Vivecraft: Active RenderPasses: {}",
                list.stream().map(Enum::toString).collect(Collectors.joining(", ")));

            // only do these, if the window is not minimized
            if (this.mirrorFBWidth > 0 && this.mirrorFBHeight > 0) {
                if (list.contains(RenderPass.THIRD) || ShadersHelper.isShaderActive()) {
                    this.framebufferMR = new VRTextureTarget("Mixed Reality Render", this.mirrorFBWidth,
                        this.mirrorFBHeight, true, -1, true, false, false);
                    WorldRenderPass.MIXED_REALITY = new WorldRenderPass(this.framebufferMR);
                    VRSettings.LOGGER.info("Vivecraft: {}", this.framebufferMR);
                    RenderHelper.checkGLError("Mixed reality framebuffer setup");
                }

                if (list.contains(RenderPass.CENTER) || ShadersHelper.isShaderActive()) {
                    this.framebufferUndistorted = new VRTextureTarget("Undistorted View Render", this.mirrorFBWidth,
                        this.mirrorFBHeight, true, -1, true, false, false);
                    WorldRenderPass.CENTER = new WorldRenderPass(this.framebufferUndistorted);
                    VRSettings.LOGGER.info("Vivecraft: {}", this.framebufferUndistorted);
                    RenderHelper.checkGLError("Undistorted view framebuffer setup");
                }
            }
            this.mirrorFramebuffer = new VRTextureTarget("Mirror", Math.max(1,
                ((WindowExtension) (Object) Minecraft.getInstance().getWindow()).vivecraft$getActualScreenWidth()),
                Math.max(1,
                    ((WindowExtension) (Object) Minecraft.getInstance().getWindow()).vivecraft$getActualScreenHeight()),
                false, -1, false, false, false);

            GuiHandler.updateResolution();
            GuiHandler.GUI_FRAMEBUFFER = new VRTextureTarget("GUI", GuiHandler.GUI_WIDTH, GuiHandler.GUI_HEIGHT, true,
                -1, true, dataholder.vrSettings.guiMipmaps, false);
            VRSettings.LOGGER.info("Vivecraft: {}", GuiHandler.GUI_FRAMEBUFFER);
            RenderHelper.checkGLError("GUI framebuffer setup");

            KeyboardHandler.FRAMEBUFFER = new VRTextureTarget("Keyboard", GuiHandler.GUI_WIDTH, GuiHandler.GUI_HEIGHT,
                true, -1, true, dataholder.vrSettings.guiMipmaps, false);
            VRSettings.LOGGER.info("Vivecraft: {}", KeyboardHandler.FRAMEBUFFER);
            RenderHelper.checkGLError("Keyboard framebuffer setup");

            RadialHandler.FRAMEBUFFER = new VRTextureTarget("Radial Menu", GuiHandler.GUI_WIDTH, GuiHandler.GUI_HEIGHT,
                true, -1, true, dataholder.vrSettings.guiMipmaps, false);
            VRSettings.LOGGER.info("Vivecraft: {}", RadialHandler.FRAMEBUFFER);
            RenderHelper.checkGLError("Radial framebuffer setup");


            Tuple<Integer, Integer> telescopeSize = getTelescopeTextureSize(eyeFBWidth, eyeFBHeight);

            this.telescopeFramebufferR = new VRTextureTarget("TelescopeR", telescopeSize.getA(), telescopeSize.getB(),
                true, -1, false, false, false);
            WorldRenderPass.RIGHT_TELESCOPE = new WorldRenderPass(this.telescopeFramebufferR);
            VRSettings.LOGGER.info("Vivecraft: {}", this.telescopeFramebufferR);
            this.telescopeFramebufferR.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            this.telescopeFramebufferR.clear();
            RenderHelper.checkGLError("TelescopeR framebuffer setup");

            this.telescopeFramebufferL = new VRTextureTarget("TelescopeL", telescopeSize.getA(), telescopeSize.getB(),
                true, -1, false, false, false);
            WorldRenderPass.LEFT_TELESCOPE = new WorldRenderPass(this.telescopeFramebufferL);
            VRSettings.LOGGER.info("Vivecraft: {}", this.telescopeFramebufferL);
            this.telescopeFramebufferL.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            this.telescopeFramebufferL.clear();
            RenderHelper.checkGLError("TelescopeL framebuffer setup");


            Tuple<Integer, Integer> cameraSize = getCameraTextureSize(eyeFBWidth, eyeFBHeight);
            int cameraRenderFBwidth = cameraSize.getA();
            int cameraRenderFBheight = cameraSize.getB();

            if (ShadersHelper.needsSameSizeBuffers()) {
                cameraRenderFBwidth = eyeFBWidth;
                cameraRenderFBheight = eyeFBHeight;
            }

            this.cameraFramebuffer = new VRTextureTarget("Handheld Camera", cameraSize.getA(), cameraSize.getB(), true,
                -1, false, false, false);
            VRSettings.LOGGER.info("Vivecraft: {}", this.cameraFramebuffer);

            RenderHelper.checkGLError("Camera framebuffer setup");
            this.cameraRenderFramebuffer = new VRTextureTarget("Handheld Camera Render", cameraRenderFBwidth,
                cameraRenderFBheight, true, -1, true, false, false);
            WorldRenderPass.CAMERA = new WorldRenderPass(this.cameraRenderFramebuffer);
            VRSettings.LOGGER.info("Vivecraft: {}", this.cameraRenderFramebuffer);
            RenderHelper.checkGLError("Camera render framebuffer setup");

            if (dataholder.vrSettings.useFsaa) {
                try {
                    RenderHelper.checkGLError("pre FSAA FBO creation");
                    this.fsaaFirstPassResultFBO = new VRTextureTarget("FSAA Pass1 FBO", eyew, eyeFBHeight, true, -1,
                        false, false, false);
                    this.fsaaLastPassResultFBO = new VRTextureTarget("FSAA Pass2 FBO", eyew, eyeh, true, -1, false,
                        false, false);
                    VRSettings.LOGGER.info("Vivecraft: {}", this.fsaaFirstPassResultFBO);
                    VRSettings.LOGGER.info("Vivecraft: {}", this.fsaaLastPassResultFBO);
                    RenderHelper.checkGLError("FSAA FBO creation");

                    VRShaders.setupFSAA();
                    RenderHelper.checkGLError("FBO init fsaa shader");
                } catch (Exception exception) {
                    // FSAA failed to initialize so don't use it
                    dataholder.vrSettings.useFsaa = false;
                    dataholder.vrSettings.saveOptions();
                    VRSettings.LOGGER.error("Vivecraft: FSAA init failed: ", exception);
                    // redo the setup next frame
                    this.reinitFrameBuffers = true;
                    return;
                }
            }

            try {
                minecraft.mainRenderTarget = this.framebufferVrRender;
                VRShaders.setupDepthMask();
                RenderHelper.checkGLError("init depth shader");
                VRShaders.setupFOVReduction();
                RenderHelper.checkGLError("init FOV shader");
                minecraft.gameRenderer.checkEntityPostEffect(minecraft.getCameraEntity());
            } catch (Exception exception) {
                VRSettings.LOGGER.error("Vivecraft: Shader creation failed:", exception);
                throw new RenderConfigException(
                    Component.translatable("vivecraft.messages.renderiniterror", this.getName()),
                    TextUtils.throwableToComponent(exception));
            }

            if (minecraft.screen != null) {
                int w = minecraft.getWindow().getGuiScaledWidth();
                int h = minecraft.getWindow().getGuiScaledHeight();
                minecraft.screen.init(minecraft, w, h);
            }

            long windowPixels =
                (long) ((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenWidth() *
                    ((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenHeight();
            long mirrorPixels = (long) this.mirrorFBWidth * (long) this.mirrorFBHeight;

            long vrPixels = (long) eyeFBWidth * (long) eyeFBHeight;
            long pixelsPerFrame = vrPixels * 2L;

            if (list.contains(RenderPass.CENTER)) {
                pixelsPerFrame += mirrorPixels;
            }

            if (list.contains(RenderPass.THIRD)) {
                pixelsPerFrame += mirrorPixels;
            }

            VRSettings.LOGGER.info("""
                    Vivecraft:
                    New VR render config:
                    VR target: {}x{} [{}MP]
                    Render target: {}x{} [Render scale: {}%, {}MP]
                    Main window: {}x{} [{}MP]
                    Total shaded pixels per frame: {}MP (eye stencil not accounted for)""",
                eyew, eyeh, String.format("%.1f", (eyew * eyeh) / 1000000.0F),
                eyeFBWidth, eyeFBHeight, dataholder.vrSettings.renderScaleFactor * 100.0F,
                String.format("%.1f", vrPixels / 1000000.0F),
                ((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenWidth(),
                ((WindowExtension) (Object) minecraft.getWindow()).vivecraft$getActualScreenHeight(),
                String.format("%.1f", windowPixels / 1000000.0F),
                String.format("%.1f", pixelsPerFrame / 1000000.0F));

            // regenerates the outline target to have every pass in it
            minecraft.levelRenderer.onResourceManagerReload(minecraft.getResourceManager());

            ShadersHelper.maybeReloadShaders();

            this.reinitFrameBuffers = false;
            this.resizeFrameBuffers = false;
        }
    }

    /**
     * only destroys the render buffers, everything else stays in takt
     */
    protected void destroyBuffers() {
        if (this.framebufferVrRender != null) {
            WorldRenderPass.STEREO_XR.close();
            WorldRenderPass.STEREO_XR = null;
            this.framebufferVrRender.destroyBuffers();
            this.framebufferVrRender = null;
        }

        if (this.framebufferMR != null) {
            WorldRenderPass.MIXED_REALITY.close();
            WorldRenderPass.MIXED_REALITY = null;
            this.framebufferMR.destroyBuffers();
            this.framebufferMR = null;
        }

        if (this.framebufferUndistorted != null) {
            WorldRenderPass.CENTER.close();
            WorldRenderPass.CENTER = null;
            this.framebufferUndistorted.destroyBuffers();
            this.framebufferUndistorted = null;
        }

        if (GuiHandler.GUI_FRAMEBUFFER != null) {
            GuiHandler.GUI_FRAMEBUFFER.destroyBuffers();
            GuiHandler.GUI_FRAMEBUFFER = null;
        }

        if (KeyboardHandler.FRAMEBUFFER != null) {
            KeyboardHandler.FRAMEBUFFER.destroyBuffers();
            KeyboardHandler.FRAMEBUFFER = null;
        }

        if (RadialHandler.FRAMEBUFFER != null) {
            RadialHandler.FRAMEBUFFER.destroyBuffers();
            RadialHandler.FRAMEBUFFER = null;
        }

        if (this.telescopeFramebufferL != null) {
            WorldRenderPass.LEFT_TELESCOPE.close();
            WorldRenderPass.LEFT_TELESCOPE = null;
            this.telescopeFramebufferL.destroyBuffers();
            this.telescopeFramebufferL = null;
        }

        if (this.telescopeFramebufferR != null) {
            WorldRenderPass.RIGHT_TELESCOPE.close();
            WorldRenderPass.RIGHT_TELESCOPE = null;
            this.telescopeFramebufferR.destroyBuffers();
            this.telescopeFramebufferR = null;
        }

        if (this.cameraFramebuffer != null) {
            this.cameraFramebuffer.destroyBuffers();
            this.cameraFramebuffer = null;
        }

        if (this.cameraRenderFramebuffer != null) {
            WorldRenderPass.CAMERA.close();
            WorldRenderPass.CAMERA = null;
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

    /**
     * destroys everything the Renderer has allocated
     */
    public void destroy() {
        destroyBuffers();
    }
}
