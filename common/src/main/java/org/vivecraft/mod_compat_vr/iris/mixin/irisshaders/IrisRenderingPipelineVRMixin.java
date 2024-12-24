package org.vivecraft.mod_compat_vr.iris.mixin.irisshaders;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_xr.render_pass.RenderPassType;
import org.vivecraft.mod_compat_vr.iris.IrisHelper;
import org.vivecraft.mod_compat_vr.iris.extensions.PipelineManagerExtension;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

@Pseudo
@Mixin(IrisRenderingPipeline.class)
public class IrisRenderingPipelineVRMixin {

    @Shadow(remap = false)
    private ShadowRenderTargets shadowRenderTargets;

    // make this mutable, or WrapOperation doesn't work
    @Final
    @Mutable
    @Shadow(remap = false)
    private Supplier<ShadowRenderTargets> shadowTargetsSupplier;

    @Final
    @Shadow(remap = false)
    private Set<GlImage> customImages;

    @Shadow(remap = false)
    private ShaderStorageBufferHolder shaderStorageBufferHolder;

    // store shadowTargets of the first pipeline
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void vivecraft$storeShadowTargets(ProgramSet programSet, CallbackInfo ci) {
        IrisHelper.SLOW_MODE =
            ClientDataHolderVR.getInstance().vrSettings.disableShaderOptimization || !this.customImages.isEmpty() ||
                this.shaderStorageBufferHolder != null;

        if (!IrisHelper.SLOW_MODE &&
            ((PipelineManagerExtension) Iris.getPipelineManager()).vivecraft$getShadowRenderTargets() == null)
        {
            ((PipelineManagerExtension) Iris.getPipelineManager()).vivecraft$setShadowRenderTargets(
                this.shadowRenderTargets);
        }
    }

    @WrapOperation(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;shadowTargetsSupplier:Ljava/util/function/Supplier;", ordinal = 0), remap = false)
    private void vivecraft$onlyOneShadowRenderTarget(
        IrisRenderingPipeline instance, Supplier<ShadowRenderTargets> value, Operation<Void> original)
    {
        Supplier<ShadowRenderTargets> wrappedSupplier = () -> {
            if (!IrisHelper.SLOW_MODE && !RenderPassType.isVanilla() && this.shadowRenderTargets == null &&
                ((PipelineManagerExtension) Iris.getPipelineManager()).vivecraft$getShadowRenderTargets() != null)
            {
                return (ShadowRenderTargets) ((PipelineManagerExtension) Iris.getPipelineManager()).vivecraft$getShadowRenderTargets();
            } else {
                return value.get();
            }
        };
        original.call(instance, wrappedSupplier);
    }

    @ModifyReturnValue(method = "shouldDisableVanillaEntityShadows()Z", at = @At("RETURN"), remap = false)
    private boolean vivecraft$shouldDisableEntityShadows(boolean noEntityShadows) {
        return noEntityShadows || (!IrisHelper.SLOW_MODE && !RenderPassType.isVanilla() &&
            ((PipelineManagerExtension) Iris.getPipelineManager()).vivecraft$getShadowRenderTargets() != null
        );
    }

    /**
     * needed because shadowRenderTargets never gets set for sub pipelines
     * this should give the own shadow targets, for the main pipeline and the first RenderPass,
     * and for all other pipelines the one from the first RenderPass
     * no min, because iris 1.8 only has 2, and those might also get removed, and are not needed
     */
    @Group(name = "reroute shadowRenderTargets", max = 4)
    @ModifyArg(method = {
        "lambda$new$6",
        "lambda$new$7",
        "lambda$new$9",
        "lambda$new$11"
    }, at = @At(value = "INVOKE", target = "Ljava/util/Objects;requireNonNull(Ljava/lang/Object;)Ljava/lang/Object;"), remap = false, expect = 0)
    private Object vivecraft$rerouteShadowTarget(Object obj) {
        // make sure we only change ShadowRenderTargets, since this might also inject into other lambdas
        if (!IrisHelper.SLOW_MODE && !RenderPassType.isVanilla() && obj instanceof ShadowRenderTargets || obj == null) {
            return Objects.requireNonNullElse(((PipelineManagerExtension) Iris.getPipelineManager()).vivecraft$getShadowRenderTargets(), obj);
        } else {
            return obj;
        }
    }
}
