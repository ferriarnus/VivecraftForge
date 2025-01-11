package org.vivecraft.client_vr;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL30;
import org.vivecraft.client.Xplat;
import org.vivecraft.client.extensions.RenderTargetExtension;

/**
 * extension of a regular RenderTarget that sets Vivecraft features on creation
 */
public class VRTextureTarget extends RenderTarget {

    private final String name;

    public VRTextureTarget(
        String name, int width, int height, boolean useDepth, int texId, boolean linearFilter, boolean mipmaps,
        boolean useStencil)
    {
        super(useDepth);
        this.name = name;
        RenderSystem.assertOnRenderThreadOrInit();
        ((RenderTargetExtension) this).vivecraft$setTexId(texId);
        ((RenderTargetExtension) this).vivecraft$setLinearFilter(linearFilter);
        ((RenderTargetExtension) this).vivecraft$setMipmaps(mipmaps);

        // need to set this first, because the forge/neoforge stencil enabled does a resize
        this.viewWidth = width;
        this.viewHeight = height;

        if (useStencil && !Xplat.enableRenderTargetStencil(this)) {
            // use our stencil only if the modloader doesn't support it
            ((RenderTargetExtension) this).vivecraft$setStencil(true);
        }
        this.resize(width, height);

        this.setClearColor(0, 0, 0, 0);
    }

    public VRTextureTarget(String name, int width, int height, int colorId, int index) {
        super(false);
        this.name = name;
        RenderSystem.assertOnRenderThreadOrInit();
        this.resize(width, height);

        // free the old one when setting a new one
        if (this.colorTextureId != -1) {
            TextureUtil.releaseTextureId(this.colorTextureId);
        }
        this.colorTextureId = colorId;

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.frameBufferId);
        // unset the old GL_COLOR_ATTACHMENT0
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, 0,
            0);
        GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, colorId, 0, index);

        // unbind the framebuffer
        this.unbindRead();
        this.unbindWrite();

        this.setClearColor(0, 0, 0, 0);
    }

    @Override
    public String toString() {
        return """
            
            Vivecraft RenderTarget: %s
            Size: %s x %s
            FB ID: %s
            Tex ID: %s"""
            .formatted(
                this.name,
                this.viewWidth, this.viewHeight,
                this.frameBufferId,
                this.colorTextureId);
    }
}
