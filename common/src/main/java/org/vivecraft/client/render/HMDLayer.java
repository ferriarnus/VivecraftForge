package org.vivecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.vivecraft.client.ClientVRPlayers;

public class HMDLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation DIAMOND_HMD = new ResourceLocation("vivecraft:textures/diamond_hmd.png");
    private static final ResourceLocation GOLD_HMD = new ResourceLocation("vivecraft:textures/gold_hmd.png");
    private static final ResourceLocation BLACK_HMD = new ResourceLocation("vivecraft:textures/black_hmd.png");

    public HMDLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        // check that the model actually is a vrPlayer model, some mods override the model
        if (this.getParentModel().head.visible && this.getParentModel() instanceof VRPlayerModel<?> vrPlayerModel) {
            ClientVRPlayers.RotInfo rotinfo = ClientVRPlayers.getInstance().getRotationsForPlayer(player.getUUID());
            if (rotinfo != null) {
                ResourceLocation hmd = switch (rotinfo.hmd) {
                    case 1 -> BLACK_HMD;
                    case 2 -> GOLD_HMD;
                    case 3, 4 -> DIAMOND_HMD;
                    default -> null;
                };

                if (hmd == null) return;

                poseStack.pushPose();
                VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entitySolid(hmd));
                vrPlayerModel.renderHMD(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
    }
}
