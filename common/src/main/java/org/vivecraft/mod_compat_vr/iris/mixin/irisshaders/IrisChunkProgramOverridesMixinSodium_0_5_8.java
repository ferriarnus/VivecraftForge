package org.vivecraft.mod_compat_vr.iris.mixin.irisshaders;

import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkShaderInterface;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisTerrainPass;
import net.irisshaders.iris.pipeline.SodiumTerrainPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.ClassDependentMixin;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_xr.render_pass.RenderPassManager;
import org.vivecraft.client_xr.render_pass.RenderPassType;
import org.vivecraft.client_xr.render_pass.WorldRenderPass;
import org.vivecraft.mod_compat_vr.iris.extensions.IrisChunkProgramOverridesExtension;
import org.vivecraft.mod_compat_vr.iris.extensions.PipelineManagerExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumMap;

@Pseudo
@Mixin(IrisChunkProgramOverrides.class)
@ClassDependentMixin("me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType")
public class IrisChunkProgramOverridesMixinSodium_0_5_8 implements IrisChunkProgramOverridesExtension {

    @Shadow(remap = false)
    @Final
    private EnumMap<IrisTerrainPass, GlProgram<IrisChunkShaderInterface>> programs;

    @Unique
    private final EnumMap<RenderPass, EnumMap<IrisTerrainPass, GlProgram<IrisChunkShaderInterface>>> vivecraft$pipelinePrograms = new EnumMap<>(RenderPass.class);

    @Group(name = "create sodium shaders", min = 1, max = 1)
    @Redirect(method = "getProgramOverride", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/compat/sodium/impl/shader_overrides/IrisChunkProgramOverrides;createShaders(Lnet/irisshaders/iris/pipeline/SodiumTerrainPipeline;Lme/jellysquid/mods/sodium/client/render/chunk/vertex/format/ChunkVertexType;)V"), remap = false, expect = 0)
    private void vivecraft$createAllPipelinesShadersSodium_0_6(
        IrisChunkProgramOverrides instance, SodiumTerrainPipeline sodiumTerrainPipeline,
        ChunkVertexType chunkVertexType)
    {
        try {
            vivecraft$createAllPipelinesShadersSodiumProcessing(
                sodiumTerrainPipeline,
                chunkVertexType,
                instance.getClass().getMethod("createShaders", SodiumTerrainPipeline.class, ChunkVertexType.class)
            );
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            // this shouldn't happen if everything went well
            throw new RuntimeException(e);
        }
    }

    @Override
    @Unique
    public void vivecraft$createAllPipelinesShadersSodiumProcessing(
        Object sodiumTerrainPipeline, Object chunkVertexType,
        Method createShadersMethod) throws InvocationTargetException, IllegalAccessException
    {
        if (VRState.VR_INITIALIZED) {
            WorldRenderPass current = RenderPassManager.WRP;
            RenderPass currentPass = ClientDataHolderVR.getInstance().currentPass;

            RenderPassManager.RENDER_PASS_TYPE = RenderPassType.WORLD_ONLY;
            for (RenderPass renderPass : RenderPass.values()) {
                VRSettings.LOGGER.info("Vivecraft: Creating VR sodium shaders for RenderPass {}", renderPass);

                WorldRenderingPipeline worldPipeline = (WorldRenderingPipeline) ((PipelineManagerExtension) Iris.getPipelineManager()).vivecraft$getVRPipeline(renderPass);
                // GUI and unused renderPasses don't have a pipeline
                if (worldPipeline != null) {
                    SodiumTerrainPipeline sodiumPipeline = worldPipeline.getSodiumTerrainPipeline();
                    EnumMap<IrisTerrainPass, GlProgram<IrisChunkShaderInterface>> renderPassShaders = new EnumMap<>(IrisTerrainPass.class);
                    this.vivecraft$pipelinePrograms.put(renderPass, renderPassShaders);
                    createShadersMethod.invoke(this, sodiumPipeline, chunkVertexType);
                    // programs should have  the shaders for this pass now
                    renderPassShaders.putAll(this.programs);
                    // clear it now, since programs is for the vanilla pass
                    this.programs.clear();
                }
            }

            RenderPassManager.setVanillaRenderPass();
            VRSettings.LOGGER.info("Vivecraft: Creating sodium shaders for vanilla RenderPass");
            createShadersMethod.invoke(this, ((WorldRenderingPipeline) ((PipelineManagerExtension) Iris.getPipelineManager()).vivecraft$getVanillaPipeline()).getSodiumTerrainPipeline(), chunkVertexType);
            if (current != null) {
                RenderPassManager.setWorldRenderPass(current);
                ClientDataHolderVR.getInstance().currentPass = currentPass;
            }
        } else {
            createShadersMethod.invoke(this, sodiumTerrainPipeline, chunkVertexType);
        }
    }

    @Redirect(method = "getProgramOverride", at = @At(value = "INVOKE", target = "Ljava/util/EnumMap;get(Ljava/lang/Object;)Ljava/lang/Object;"), remap = false)
    private Object vivecraft$getVRPipelineShaders(
        EnumMap<IrisTerrainPass, GlProgram<IrisChunkShaderInterface>> instance, Object key)
    {
        // return shader of the current RenderPass
        return !RenderPassType.isVanilla() ?
            this.vivecraft$pipelinePrograms.get(ClientDataHolderVR.getInstance().currentPass).get((IrisTerrainPass) key) : instance.get((IrisTerrainPass) key);
    }

    @Inject(method = "deleteShaders", at = @At("HEAD"), remap = false)
    private void vivecraft$deleteVRPipelineShaders(CallbackInfo ci) {
        if (VRState.VR_INITIALIZED) {
            for (EnumMap<IrisTerrainPass, GlProgram<IrisChunkShaderInterface>> map : this.vivecraft$pipelinePrograms.values()) {
                for (GlProgram<?> program : map.values()) {
                    if (program != null) {
                        program.delete();
                    }
                }
                map.clear();
            }
            this.vivecraft$pipelinePrograms.clear();
        }
    }
}
