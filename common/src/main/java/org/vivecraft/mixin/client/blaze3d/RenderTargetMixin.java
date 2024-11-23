package org.vivecraft.mixin.client.blaze3d;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.vivecraft.client.extensions.RenderTargetExtension;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassType;

import java.util.Arrays;

@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin implements RenderTargetExtension {

    @Unique
    private int vivecraft$texid = -1;
    @Unique
    private boolean vivecraft$linearFilter;
    @Unique
    private boolean vivecraft$useStencil = false;

    @Unique
    private boolean vivecraft$loggedSizeError = false;

    @Shadow
    public int frameBufferId;
    @Shadow
    public int width;
    @Shadow
    public int height;
    @Shadow
    public int viewHeight;
    @Shadow
    public int viewWidth;
    @Shadow
    protected int colorTextureId;

    @Shadow
    public abstract void clear(boolean onMacIn);

    @Override
    public void vivecraft$setUseStencil(boolean useStencil) {
        this.vivecraft$useStencil = useStencil;
    }

    @Override
    public boolean vivecraft$getUseStencil() {
        return vivecraft$useStencil;
    }

    @Override
    public void vivecraft$setTextid(int texid) {
        this.vivecraft$texid = texid;
    }

    @Override
    public void vivecraft$setColorid(int colorid) {
        this.colorTextureId = colorid;
    }

    @Override
    public void vivecraft$isLinearFilter(boolean linearFilter) {
        this.vivecraft$linearFilter = linearFilter;
    }

    @Override
    public String toString() {
        String stringbuilder = "\n" +
            "Size:   " + this.viewWidth + " x " + this.viewHeight + "\n" +
            "FB ID:  " + this.frameBufferId + "\n" +
            "Tex ID: " + this.colorTextureId + "\n";
        return stringbuilder;
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextureUtil;generateTextureId()I", remap = false, ordinal = 0), method = "createBuffers")
    public int vivecraft$genTextureId() {
        if (this.vivecraft$texid == -1) {
            return TextureUtil.generateTextureId();
        } else {
            return this.vivecraft$texid;
        }
    }

    @ModifyArg(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V", remap = false, ordinal = 0), method = "createBuffers", index = 2)
    public int vivecraft$modifyTexImage2DInternalformat(int internalformat) {
        return vivecraft$useStencil ? GL30.GL_DEPTH32F_STENCIL8 : internalformat;
    }

    @ModifyArg(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V", remap = false, ordinal = 0), method = "createBuffers", index = 6)
    public int vivecraft$modifyTexImage2DFormat(int format) {
        return vivecraft$useStencil ? GL30.GL_DEPTH_STENCIL : format;
    }

    @ModifyArg(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V", remap = false, ordinal = 0), method = "createBuffers", index = 7)
    public int vivecraft$modifyTexImage2DType(int type) {
        return vivecraft$useStencil ? GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV : type;
    }

    @ModifyConstant(method = "createBuffers", constant = @Constant(intValue = 9728))
    public int vivecraft$changeTextPar(int i) {
        return vivecraft$linearFilter ? GL11.GL_LINEAR : i;
    }

    @ModifyArg(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glFramebufferTexture2D(IIIII)V", remap = false, ordinal = 1), method = "createBuffers", index = 1)
    public int vivecraft$modifyGlFramebufferTexture2DAttachment(int attachment) {
        return vivecraft$useStencil ? GL30.GL_DEPTH_STENCIL_ATTACHMENT : attachment;
    }

    @ModifyArg(method = "clear", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    private boolean vivecraft$noViewportChangeOnClear(boolean changeViewport) {
        // this viewport change doesn't seem to be needed in general,
        // and removing it makes mods not break rendering when they have miss sized RenderTargets

        // we don't care about resizes or buffer creations, those should happen in the Vanilla or GUI pass
        if (RenderPassType.isWorldOnly()) {
            if (!this.vivecraft$loggedSizeError && (this.width != Minecraft.getInstance().getMainRenderTarget().width || this.height != Minecraft.getInstance().getMainRenderTarget().height)) {
                // log a limited StackTrace to find the cause, we don't need to spam the log with full StackTraces
                VRSettings.logger.error("Vivecraft: Mismatched RenderTarget size detected, viewport size change was blocked. MainTarget size: {}x{}, RenderTarget size: {}x{}. RenderPass: {}, Stacktrace: {}",
                    Minecraft.getInstance().getMainRenderTarget().width,
                    Minecraft.getInstance().getMainRenderTarget().height,
                    this.width, this.height, ClientDataHolderVR.getInstance().currentPass,
                    String.join("\n", Arrays.stream(Thread.currentThread().getStackTrace(), 2, 12).map(Object::toString).toArray(String[]::new)));
                this.vivecraft$loggedSizeError = true;
            }
            return false;
        } else {
            return changeViewport;
        }
    }


    public void vivecraft$blitToScreen(ShaderInstance instance, int left, int width, int height, int top, boolean disableBlend, float xCropFactor, float yCropFactor, boolean keepAspect) {
        RenderSystem.assertOnGameThreadOrInit();
        if (!RenderSystem.isInInitPhase()) {
            RenderSystem.recordRenderCall(() -> {
                this.vivecraft$_blitToScreen(instance, left, width, height, top, disableBlend, xCropFactor, yCropFactor, keepAspect);
            });
        } else {
            this.vivecraft$_blitToScreen(instance, left, width, height, top, disableBlend, xCropFactor, yCropFactor, keepAspect);
        }
    }

    @Override
    public void vivecraft$blitFovReduction(ShaderInstance instance, int width, int height) {
        RenderSystem.assertOnRenderThread();
        RenderSystem.colorMask(true, true, true, false);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.viewport(0, 0, width, height);
        RenderSystem.disableBlend();
        Minecraft minecraft = Minecraft.getInstance();
        RenderSystem.setShaderTexture(0, this.colorTextureId);
        if (instance == null) {
            instance = minecraft.gameRenderer.blitShader;
            instance.setSampler("DiffuseSampler", this.colorTextureId);
        } else {
            for (int k = 0; k < RenderSystemAccessor.getShaderTextures().length; ++k) {
                int l = RenderSystem.getShaderTexture(k);
                instance.setSampler("Sampler" + k, l);
            }
        }
        Matrix4f matrix4f = new Matrix4f().setOrtho(0, width, height, 0, 1000.0f, 3000.0f);
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
        if (instance.MODEL_VIEW_MATRIX != null) {
            instance.MODEL_VIEW_MATRIX.set(new Matrix4f().translation(0.0f, 0.0f, -2000.0f));
        }
        if (instance.PROJECTION_MATRIX != null) {
            instance.PROJECTION_MATRIX.set(matrix4f);
        }
        instance.apply();
        float f = width;
        float g = height;
        float h = (float) this.viewWidth / (float) this.width;
        float k = (float) this.viewHeight / (float) this.height;
        Tesselator tesselator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, instance.getVertexFormat());
        if (instance.getVertexFormat() == DefaultVertexFormat.POSITION_TEX) {
            bufferBuilder.vertex(0.0, g, 0.0).uv(0.0f, 0.0f).endVertex();
            bufferBuilder.vertex(f, g, 0.0).uv(h, 0.0f).endVertex();
            bufferBuilder.vertex(f, 0.0, 0.0).uv(h, k).endVertex();
            bufferBuilder.vertex(0.0, 0.0, 0.0).uv(0.0f, k).endVertex();
        } else if (instance.getVertexFormat() == DefaultVertexFormat.POSITION_TEX_COLOR) {
            bufferBuilder.vertex(0.0, g, 0.0).uv(0.0f, 0.0f).color(255, 255, 255, 255).endVertex();
            bufferBuilder.vertex(f, g, 0.0).uv(h, 0.0f).color(255, 255, 255, 255).endVertex();
            bufferBuilder.vertex(f, 0.0, 0.0).uv(h, k).color(255, 255, 255, 255).endVertex();
            bufferBuilder.vertex(0.0, 0.0, 0.0).uv(0.0f, k).color(255, 255, 255, 255).endVertex();
        } else {
            throw new IllegalStateException("Unexpected vertex format " + instance.getVertexFormat());
        }
        BufferUploader.draw(bufferBuilder.end());
        instance.clear();
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
    }

    @Unique
    private void vivecraft$_blitToScreen(ShaderInstance instance, int left, int width, int height, int top, boolean bl, float xCropFactor, float yCropFactor, boolean keepAspect) {
        RenderSystem.assertOnGameThreadOrInit();
        RenderSystem.colorMask(true, true, true, false);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.viewport(left, top, width, height);
        if (bl) {
            RenderSystem.disableBlend();
        }

        Minecraft minecraft = Minecraft.getInstance();

        float f = (float) width / (float) height;
        float f1 = (float) this.viewWidth / (float) this.viewHeight;
        float f2 = (float) width;
        float f3 = (float) height;
        float f4 = 0.0F;
        float f5 = 0.0F;

        if (keepAspect) {
            if (f > f1) {
                float f6 = (float) width / (float) this.viewWidth;
                f4 = 0.0F;
                f2 = (float) width;
                f5 = (float) height / 2.0F - (float) this.viewHeight / 2.0F * f6;
                f3 = (float) height / 2.0F + (float) this.viewHeight / 2.0F * f6;
            } else {
                float f10 = (float) height / (float) this.viewHeight;
                f4 = (float) width / 2.0F - (float) this.viewWidth / 2.0F * f10;
                f2 = (float) width / 2.0F + (float) this.viewWidth / 2.0F * f10;
                f5 = 0.0F;
                f3 = (float) height;
            }
        }

        float f11 = (float) width;
        float f7 = (float) height;
        float f8 = (float) this.viewWidth / (float) this.width;
        float f9 = (float) this.viewHeight / (float) this.height;

        if (instance == null) {
            instance = minecraft.gameRenderer.blitShader;
            instance.setSampler("DiffuseSampler", this.colorTextureId);
        } else {
            for (int k = 0; k < RenderSystemAccessor.getShaderTextures().length; ++k) {
                int l = RenderSystem.getShaderTexture(k);
                instance.setSampler("Sampler" + k, l);
            }
        }
        Matrix4f matrix4f = new Matrix4f().setOrtho(0, (float) width, (float) (height), 0, 1000.0F, 3000.0F);
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);

        if (instance.MODEL_VIEW_MATRIX != null) {
            instance.MODEL_VIEW_MATRIX.set(new Matrix4f().translation(0.0F, 0.0F, -2000.0F));
        }

        if (instance.PROJECTION_MATRIX != null) {
            instance.PROJECTION_MATRIX.set(matrix4f);
        }

        instance.apply();

        Tesselator tesselator = RenderSystem.renderThreadTesselator();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, instance.getVertexFormat());
        bufferbuilder.vertex(f4, f3, 0.0D).uv(xCropFactor, yCropFactor).color(255, 255, 255, 255)
            .endVertex();
        bufferbuilder.vertex(f2, f3, 0.0D).uv(f8 - xCropFactor, yCropFactor)
            .color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(f2, f5, 0.0D).uv(f8 - xCropFactor, f9 - yCropFactor)
            .color(255, 255, 255, 255).endVertex();
        bufferbuilder.vertex(f4, f5, 0.0D).uv(xCropFactor, f9 - yCropFactor)
            .color(255, 255, 255, 255).endVertex();
        BufferUploader.draw(bufferbuilder.end());
        instance.clear();

        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
    }
}
