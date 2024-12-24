package org.vivecraft.mixin.client_vr.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client.extensions.RenderTargetExtension;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.MultiPassRenderTarget;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_xr.render_pass.RenderPassManager;
import org.vivecraft.client_xr.render_pass.RenderPassType;
import org.vivecraft.client_xr.render_pass.WorldRenderPass;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

@Mixin(PostChain.class)
public class PostChainVRMixin {

    @Shadow
    @Final
    private RenderTarget screenTarget;

    @Unique
    private final EnumMap<RenderPass, PostChain> vivecraft$VRPostChains = new EnumMap<>(RenderPass.class);

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void vivecraft$createVRChains(
        TextureManager textureManager, ResourceManager resourceManager, RenderTarget screenTarget,
        ResourceLocation name, CallbackInfo ci) throws IOException
    {
        if (VRState.VR_INITIALIZED && this.screenTarget == RenderPassManager.INSTANCE.vanillaRenderTarget) {
            for (RenderPass pass : RenderPass.values()) {
                // create one post chain for each active render pass
                if (pass == RenderPass.GUI || WorldRenderPass.getByRenderPass(pass) == null) continue;
                this.vivecraft$VRPostChains.put(pass,
                    new PostChain(textureManager, resourceManager, WorldRenderPass.getByRenderPass(pass).target, name));
            }
        }
    }

    @Inject(method = "getTempTarget", at = @At("RETURN"), cancellable = true)
    private void vivecraft$getVRTempTarget(String attributeName, CallbackInfoReturnable<RenderTarget> cir) {
        if (VRState.VR_INITIALIZED && !this.vivecraft$VRPostChains.isEmpty()) {
            EnumMap<RenderPass, RenderTarget> vrTargets = new EnumMap<>(RenderPass.class);
            for (Map.Entry<RenderPass, PostChain> entry : this.vivecraft$VRPostChains.entrySet()) {
                vrTargets.put(entry.getKey(), entry.getValue().getTempTarget(attributeName));
            }
            cir.setReturnValue(new MultiPassRenderTarget(cir.getReturnValue(), vrTargets));
        }
    }

    @ModifyVariable(method = "addTempTarget", at = @At(value = "STORE"), ordinal = 0)
    private RenderTarget vivecraft$vrTargetStencil(RenderTarget renderTarget) {
        if (((RenderTargetExtension) this.screenTarget).vivecraft$hasStencil()) {
            ((RenderTargetExtension) renderTarget).vivecraft$setStencil(true);
            renderTarget.resize(renderTarget.width, renderTarget.height, Minecraft.ON_OSX);
        }
        return renderTarget;
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void vivecraft$closeVRChains(CallbackInfo ci) {
        for (PostChain postChain : this.vivecraft$VRPostChains.values()) {
            postChain.close();
        }
        this.vivecraft$VRPostChains.clear();
    }

    @Inject(method = "resize", at = @At("TAIL"))
    private void vivecraft$resizeVRChains(CallbackInfo ci) {
        for (Map.Entry<RenderPass, PostChain> entry : this.vivecraft$VRPostChains.entrySet()) {
            RenderTarget target = WorldRenderPass.getByRenderPass(entry.getKey()).target;
            entry.getValue().resize(target.width, target.height);
        }
    }

    @Inject(method = "process", at = @At(value = "HEAD"), cancellable = true)
    private void vivecraft$renderVRChain(float partialTick, CallbackInfo ci) {
        if (!RenderPassType.isVanilla() &&
            this.vivecraft$VRPostChains.containsKey(ClientDataHolderVR.getInstance().currentPass))
        {
            this.vivecraft$VRPostChains.get(ClientDataHolderVR.getInstance().currentPass).process(partialTick);
            ci.cancel();
        }
    }
}
