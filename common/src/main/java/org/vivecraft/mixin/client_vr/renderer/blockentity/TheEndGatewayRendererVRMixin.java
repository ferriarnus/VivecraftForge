package org.vivecraft.mixin.client_vr.renderer.blockentity;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.TheEndGatewayRenderer;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.render.VRShaders;
import org.vivecraft.client_xr.render_pass.RenderPassType;

@Mixin(TheEndGatewayRenderer.class)
public class TheEndGatewayRendererVRMixin {

    @Unique
    private static final RenderType vivecraft$END_GATEWAY_VR =
        RenderType
            .create(
                "end_portal",
                DefaultVertexFormat.POSITION,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(VRShaders::getRendertypeEndGatewayVrShader))
                    .setTextureState(
                        RenderStateShard
                            .MultiTextureStateShard
                            .builder()
                            .add(TheEndPortalRenderer.END_SKY_LOCATION, false, false)
                            .add(TheEndPortalRenderer.END_PORTAL_LOCATION, false, false)
                            .build())
                    .createCompositeState(false));

    @Inject(method = "renderType", at = @At("HEAD"), cancellable = true)
    private void vivecraft$VRShaderOverride(CallbackInfoReturnable<RenderType> cir) {
        if (!RenderPassType.isVanilla()) {
            cir.setReturnValue(vivecraft$END_GATEWAY_VR);
        }
    }
}
