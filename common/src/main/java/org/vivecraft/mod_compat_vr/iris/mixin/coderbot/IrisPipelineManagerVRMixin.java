package org.vivecraft.mod_compat_vr.iris.mixin.coderbot;

import net.coderbot.iris.pipeline.PipelineManager;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shaderpack.DimensionId;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.shadows.ShadowRenderTargets;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Desc;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassManager;
import org.vivecraft.client_xr.render_pass.RenderPassType;
import org.vivecraft.client_xr.render_pass.WorldRenderPass;
import org.vivecraft.mod_compat_vr.iris.extensions.PipelineManagerExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Pseudo
@Mixin(net.coderbot.iris.pipeline.PipelineManager.class)
public class IrisPipelineManagerVRMixin implements PipelineManagerExtension {

    @Shadow(remap = false)
    private void resetTextureState() {}

    @Shadow(remap = false)
    private WorldRenderingPipeline pipeline;

    @Final
    @Shadow(remap = false)
    private Function<Object, WorldRenderingPipeline> pipelineFactory;

    @Unique
    private ShadowRenderTargets vivecraft$shadowRenderTargets;

    @Override
    @Unique
    public ShadowRenderTargets vivecraft$getShadowRenderTargets() {
        return this.vivecraft$shadowRenderTargets;
    }

    @Override
    @Unique
    public void vivecraft$setShadowRenderTargets(Object targets) {
        this.vivecraft$shadowRenderTargets = (ShadowRenderTargets) targets;
    }

    @Unique
    private final Map<Object, Map<RenderPass, WorldRenderingPipeline>> vivecraft$vrPipelinesPerDimension = new HashMap<>();

    @Unique
    private WorldRenderingPipeline vivecraft$vanillaPipeline;

    @Unique
    private Map<RenderPass, WorldRenderingPipeline> vivecraft$vrPipelinesCurrentDimension;

    @Unique
    private WorldRenderPass vivecraft$currentWorldRenderPass = null;

    @Inject(method = "preparePipeline", at = @At(value = "INVOKE", target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"), remap = false)
    private void vivecraft$prepareForVanillaPipeline(CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        // this also runs on game startup, when the renderpassManager isn't initialized yet
        if (VRState.VR_INITIALIZED && RenderPassManager.INSTANCE != null) {
            this.vivecraft$currentWorldRenderPass = RenderPassManager.WRP;
            RenderPass currentRenderPass = ClientDataHolderVR.getInstance().currentPass;
            RenderPassManager.setVanillaRenderPass();
            ClientDataHolderVR.getInstance().currentPass = currentRenderPass;
        }
    }

    @Group(name = "generateVRPipelines", min = 1, max = 1)
    @Inject(target = @Desc(value = "preparePipeline", owner = PipelineManager.class, ret = WorldRenderingPipeline.class, args = DimensionId.class), at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", shift = At.Shift.AFTER), remap = false, expect = 0)
    private void vivecraft$generateVRPipelines164(
        DimensionId newDimension, CallbackInfoReturnable<WorldRenderingPipeline> cir)
    {
        vivecraft$generateVRPipelines(newDimension);
    }

    @Group(name = "generateVRPipelines", min = 1, max = 1)
    @Inject(target = @Desc(value = "preparePipeline", owner = PipelineManager.class, ret = WorldRenderingPipeline.class, args = NamespacedId.class), at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", shift = At.Shift.AFTER), remap = false, expect = 0)
    private void vivecraft$generateVRPipelines165(
        NamespacedId newDimension, CallbackInfoReturnable<WorldRenderingPipeline> cir)
    {
        vivecraft$generateVRPipelines(newDimension);
    }

    @Unique
    private void vivecraft$generateVRPipelines(Object newDimension) {
        if (VRState.VR_INITIALIZED) {
            this.vivecraft$vanillaPipeline = this.pipeline;
            if (!this.vivecraft$vrPipelinesPerDimension.containsKey(newDimension)) {
                this.vivecraft$vrPipelinesPerDimension.put(newDimension, new HashMap<>());
                this.vivecraft$vrPipelinesCurrentDimension = this.vivecraft$vrPipelinesPerDimension.get(newDimension);
                // main pipeline also sets this, but we don't want that, since it is unused
                this.vivecraft$shadowRenderTargets = null;

                for (RenderPass renderPass : RenderPass.values()) {
                    VRSettings.LOGGER.info("Vivecraft: Creating VR pipeline for dimension {}, RenderPass {}", newDimension, renderPass);
                    WorldRenderPass worldRenderPass = null;
                    switch (renderPass) {
                        case LEFT, RIGHT -> worldRenderPass = WorldRenderPass.STEREO_XR;
                        case CENTER -> worldRenderPass = WorldRenderPass.CENTER;
                        case THIRD -> worldRenderPass = WorldRenderPass.MIXED_REALITY;
                        case SCOPEL -> worldRenderPass = WorldRenderPass.LEFT_TELESCOPE;
                        case SCOPER -> worldRenderPass = WorldRenderPass.RIGHT_TELESCOPE;
                        case CAMERA -> worldRenderPass = WorldRenderPass.CAMERA;
                        default -> {
                            VRSettings.LOGGER.info("Vivecraft: skipped VR pipeline for dimension {}, RenderPass {}, not used", newDimension, renderPass);
                            continue;
                        }
                    }

                    if (worldRenderPass != null) {
                        RenderPassManager.setWorldRenderPass(worldRenderPass);
                    } else {
                        continue;
                    }

                    WorldRenderingPipeline pipe = this.pipelineFactory.apply(newDimension);
                    this.vivecraft$vrPipelinesPerDimension.get(newDimension).put(renderPass, pipe);
                }
                // set to currently needed renderpass again
                if (this.vivecraft$currentWorldRenderPass != null) {
                    RenderPassManager.setWorldRenderPass(this.vivecraft$currentWorldRenderPass);
                } else if (ClientDataHolderVR.getInstance().currentPass == RenderPass.GUI) {
                    RenderPassManager.setGUIRenderPass();
                } else {
                    RenderPassManager.setVanillaRenderPass();
                }
            }
            this.vivecraft$vrPipelinesCurrentDimension = this.vivecraft$vrPipelinesPerDimension.get(newDimension);

            if (!RenderPassType.isVanilla()) {
                if (ClientDataHolderVR.getInstance().currentPass != null) {
                    this.pipeline = this.vivecraft$vrPipelinesCurrentDimension.get(ClientDataHolderVR.getInstance().currentPass);
                } else {
                    this.pipeline = this.vivecraft$vrPipelinesCurrentDimension.get(RenderPass.LEFT);
                }
            }
        }
    }

    @Group(name = "returnCurrentVRPipeline", min = 1, max = 1)
    @Inject(target = @Desc(value = "preparePipeline", owner = PipelineManager.class, ret = WorldRenderingPipeline.class, args = DimensionId.class), at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), remap = false, cancellable = true, expect = 0)
    private void vivecraft$returnCurrentVRPipeline164(DimensionId newDimension, CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        if (!RenderPassType.isVanilla()) {
            this.pipeline = vivecraft$getCurrentVRPipeline(newDimension);
            cir.setReturnValue(this.pipeline);
        }
    }

    @Group(name = "returnCurrentVRPipeline", min = 1, max = 1)
    @Inject(target = @Desc(value = "preparePipeline", owner = PipelineManager.class, ret = WorldRenderingPipeline.class, args = NamespacedId.class), at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), remap = false, cancellable = true, expect = 0)
    private void vivecraft$returnCurrentVRPipeline165(
        NamespacedId newDimension, CallbackInfoReturnable<WorldRenderingPipeline> cir)
    {
        if (!RenderPassType.isVanilla()) {
            this.pipeline = vivecraft$getCurrentVRPipeline(newDimension);
            cir.setReturnValue(this.pipeline);
        }
    }

    @Unique
    private WorldRenderingPipeline vivecraft$getCurrentVRPipeline(Object key) {
        return this.vivecraft$vrPipelinesPerDimension.get(key).get(ClientDataHolderVR.getInstance().currentPass);
    }

    @Inject(method = "destroyPipeline", at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V"), remap = false)
    private void vivecraft$destroyVRPipelines(CallbackInfo ci) {
        this.vivecraft$vrPipelinesPerDimension.forEach((dimID, map) -> {
            map.forEach((renderPass, pipeline) -> {
                VRSettings.LOGGER.info("Vivecraft: Destroying VR pipeline {}", renderPass);
                resetTextureState();
                pipeline.destroy();
            });
            map.clear();
        });
        this.vivecraft$shadowRenderTargets = null;
        this.vivecraft$vrPipelinesPerDimension.clear();
        this.vivecraft$vanillaPipeline = null;
    }

    @Override
    @Unique
    public Object vivecraft$getVRPipeline(RenderPass pass) {
        return this.vivecraft$vrPipelinesCurrentDimension.get(pass);
    }

    @Override
    @Unique
    public Object vivecraft$getVanillaPipeline() {
        return this.vivecraft$vanillaPipeline;
    }
}
