package org.vivecraft.client_vr.provider.nullvr;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.VRRenderer;
import org.vivecraft.client_vr.render.RenderConfigException;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.helpers.RenderHelper;
import org.vivecraft.client_vr.settings.VRSettings;

public class NullVRStereoRenderer extends VRRenderer {

    protected int LeftEyeTextureId = -1;
    protected int RightEyeTextureId = -1;
    public RenderTarget framebufferEye0;
    public RenderTarget framebufferEye1;

    public NullVRStereoRenderer(MCVR vr) {
        super(vr);
    }

    @Override
    public Tuple<Integer, Integer> getRenderTextureSizes() {
        if (this.resolution == null) {
            this.resolution = new Tuple<>(2048, 2048);
            VRSettings.LOGGER.info("Vivecraft: NullVR Render Res {}x{}", this.resolution.getA(),
                this.resolution.getB());
            this.ss = -1.0F;
            VRSettings.LOGGER.info("Vivecraft: NullVR Supersampling: {}", this.ss);
        }
        return this.resolution;
    }

    @Override
    protected Matrix4f getProjectionMatrix(int eyeType, float nearClip, float farClip) {
        return new Matrix4f().setPerspective(Mth.DEG_TO_RAD * 110.0F, 1.0F, nearClip, farClip);
    }

    @Override
    public void createRenderTexture(int lwidth, int lheight) throws RenderConfigException {
        this.LeftEyeTextureId = GlStateManager._genTexture();
        int i = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.bindTexture(this.LeftEyeTextureId);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, lwidth, lheight, 0, GL11.GL_RGBA, GL11.GL_INT,
            null);

        RenderSystem.bindTexture(i);
        this.RightEyeTextureId = GlStateManager._genTexture();
        i = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.bindTexture(this.RightEyeTextureId);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, lwidth, lheight, 0, GL11.GL_RGBA, GL11.GL_INT,
            null);
        RenderSystem.bindTexture(i);
        this.lastError = RenderHelper.checkGLError("create VR textures");
    }

    @Override
    public void endFrame() {}

    @Override
    public boolean providesStencilMask() {
        return false;
    }

    @Override
    public RenderTarget getLeftEyeTarget() {
        return framebufferEye0;
    }

    @Override
    public RenderTarget getRightEyeTarget() {
        return framebufferEye1;
    }


    @Override
    public float[] getStencilMask(RenderPass eye) {
        return null;
    }

    @Override
    public String getName() {
        return "NullVR";
    }

    @Override
    protected void destroyBuffers() {
        super.destroyBuffers();

        if (this.framebufferEye0 != null) {
            this.framebufferEye0.destroyBuffers();
            this.framebufferEye0 = null;
        }

        if (this.framebufferEye1 != null) {
            this.framebufferEye1.destroyBuffers();
            this.framebufferEye1 = null;
        }

        if (this.LeftEyeTextureId > -1) {
            TextureUtil.releaseTextureId(this.LeftEyeTextureId);
            this.LeftEyeTextureId = -1;
        }

        if (this.RightEyeTextureId > -1) {
            TextureUtil.releaseTextureId(this.RightEyeTextureId);
            this.RightEyeTextureId = -1;
        }
    }
}
