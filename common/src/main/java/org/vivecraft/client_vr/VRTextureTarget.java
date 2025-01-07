package org.vivecraft.client_vr;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
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

    public VRTextureTarget(String name, int width, int height, int colorid, int index) {
        super(true);
        this.name = name;
        RenderSystem.assertOnGameThreadOrInit();
        this.resize(width, height, Minecraft.ON_OSX);
        ((RenderTargetExtension) this).vivecraft$setColorid(colorid);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBufferId);
        GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, colorid, 0, index);
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
