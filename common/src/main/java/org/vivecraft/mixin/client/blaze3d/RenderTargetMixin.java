package org.vivecraft.mixin.client.blaze3d;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.vivecraft.client.extensions.RenderTargetExtension;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassType;

@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin implements RenderTargetExtension {

    @Shadow
    public int width;
    @Shadow
    public int height;
    @Unique
    private int vivecraft$texId = -1;
    @Unique
    private boolean vivecraft$linearFilter;
    @Unique
    private boolean vivecraft$mipmaps;
    @Unique
    private boolean vivecraft$stencil = false;
    @Unique
    private boolean vivecraft$loggedSizeError = false;

    @Override
    @Unique
    public void vivecraft$setStencil(boolean stencil) {
        this.vivecraft$stencil = stencil;
    }

    @Override
    @Unique
    public boolean vivecraft$hasStencil() {
        return this.vivecraft$stencil;
    }

    @Override
    @Unique
    public void vivecraft$setTexId(int texId) {
        this.vivecraft$texId = texId;
    }

    @Unique
    public void vivecraft$setLinearFilter(boolean linearFilter) {
        this.vivecraft$linearFilter = linearFilter;
    }

    @Override
    @Unique
    public void vivecraft$setMipmaps(boolean mipmaps) {
        this.vivecraft$mipmaps = mipmaps;
    }

    @Override
    @Unique
    public boolean vivecraft$hasMipmaps() {
        return this.vivecraft$mipmaps;
    }

    @WrapOperation(method = "createBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextureUtil;generateTextureId()I", remap = false, ordinal = 0))
    private int vivecraft$fixedTextureId(Operation<Integer> original) {
        return this.vivecraft$texId == -1 ? original.call() : this.vivecraft$texId;
    }

    @ModifyArg(method = "createBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V", remap = false, ordinal = 0), index = 2)
    private int vivecraft$modifyTexImage2DInternalformat(int internalformat) {
        return this.vivecraft$stencil ? GL30.GL_DEPTH32F_STENCIL8 : internalformat;
    }

    @ModifyArg(method = "createBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V", remap = false, ordinal = 0), index = 6)
    private int vivecraft$modifyTexImage2DFormat(int format) {
        return this.vivecraft$stencil ? GL30.GL_DEPTH_STENCIL : format;
    }

    @ModifyArg(method = "createBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V", remap = false, ordinal = 0), index = 7)
    private int vivecraft$modifyTexImage2DType(int type) {
        return this.vivecraft$stencil ? GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV : type;
    }

    @ModifyArg(method = "createBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;setFilterMode(I)V"))
    private int vivecraft$linearFiltering(int filterMode) {
        return this.vivecraft$linearFilter ? GL11.GL_LINEAR : filterMode;
    }

    @ModifyArg(method = "setFilterMode", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texParameter(III)V", remap = false, ordinal = 0), index = 2)
    private int vivecraft$modifyTextureMinFilter(int attachment) {
        if (this.vivecraft$mipmaps) {
            return attachment == GL11.GL_LINEAR ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_NEAREST_MIPMAP_NEAREST;
        } else {
            return attachment;
        }
    }

    @ModifyArg(method = "createBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glFramebufferTexture2D(IIIII)V", remap = false, ordinal = 1), index = 1)
    private int vivecraft$modifyGlFramebufferTexture2DAttachment(int attachment) {
        return this.vivecraft$stencil ? GL30.GL_DEPTH_STENCIL_ATTACHMENT : attachment;
    }

    @ModifyArg(method = "clear", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    private boolean vivecraft$noViewportChangeOnClear(boolean changeViewport) {
        // this viewport change doesn't seem to be needed in general,
        // and removing it makes mods not break rendering when they have miss sized RenderTargets

        // we don't care about resizes or buffer creations, those should happen in the Vanilla or GUI pass
        if (RenderPassType.isWorldOnly()) {
            if (!this.vivecraft$loggedSizeError && (this.width != Minecraft.getInstance().getMainRenderTarget().width ||
                this.height != Minecraft.getInstance().getMainRenderTarget().height
            ))
            {
                // log a limited StackTrace to find the cause, we don't need to spam the log with full StackTraces
                VRSettings.LOGGER.error("Vivecraft: Mismatched RenderTarget size detected, viewport size change was blocked. MainTarget size: {}x{}, RenderTarget size: {}x{}. RenderPass: {}, Stacktrace:",
                    Minecraft.getInstance().getMainRenderTarget().width,
                    Minecraft.getInstance().getMainRenderTarget().height,
                    this.width, this.height, ClientDataHolderVR.getInstance().currentPass,
                    new RuntimeException());
                this.vivecraft$loggedSizeError = true;
            }
            return false;
        } else {
            return changeViewport;
        }
    }
}
