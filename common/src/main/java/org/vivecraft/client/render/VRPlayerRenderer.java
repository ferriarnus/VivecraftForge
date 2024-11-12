package org.vivecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.render.RenderPass;

import java.util.UUID;

public class VRPlayerRenderer extends PlayerRenderer {
    // Vanilla model
    private static final LayerDefinition VR_LAYER_DEF = LayerDefinition.create(
        VRPlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
    private static final LayerDefinition VR_LAYER_DEF_SLIM = LayerDefinition.create(
        VRPlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64);

    // split arms model
    private static LayerDefinition VR_LAYER_DEF_ARMS;
    private static LayerDefinition VR_LAYER_DEF_ARMS_SLIM;

    // split arms/legs model
    private static LayerDefinition VR_LAYER_DEF_ARMS_LEGS;
    private static LayerDefinition VR_LAYER_DEF_ARMS_LEGS_SLIM;

    static {
        // need to make these not final, because they change depending on settings
        createLayers();
    }

    public static void createLayers() {
        // split arms model
        VR_LAYER_DEF_ARMS = LayerDefinition.create(
            VRPlayerModel_WithArms.createMesh(CubeDeformation.NONE, false), 64, 64);
        VR_LAYER_DEF_ARMS_SLIM = LayerDefinition.create(
            VRPlayerModel_WithArms.createMesh(CubeDeformation.NONE, true), 64, 64);

        // split arms/legs model
        VR_LAYER_DEF_ARMS_LEGS = LayerDefinition.create(
            VRPlayerModel_WithArmsLegs.createMesh(CubeDeformation.NONE, false), 64, 64);
        VR_LAYER_DEF_ARMS_LEGS_SLIM = LayerDefinition.create(
            VRPlayerModel_WithArmsLegs.createMesh(CubeDeformation.NONE, true), 64, 64);
    }

    public enum ModelType {
        VANILLA,
        SPLIT_ARMS,
        SPLIT_ARMS_LEGS
    }

    public VRPlayerRenderer(EntityRendererProvider.Context context, boolean slim, ModelType type) {
        super(context, slim);
        this.model = switch (type) {
            case VANILLA -> new VRPlayerModel<>(slim ? VR_LAYER_DEF_SLIM.bakeRoot() : VR_LAYER_DEF.bakeRoot(), slim);
            case SPLIT_ARMS ->
                new VRPlayerModel_WithArms<>(slim ? VR_LAYER_DEF_ARMS_SLIM.bakeRoot() : VR_LAYER_DEF_ARMS.bakeRoot(),
                    slim);
            case SPLIT_ARMS_LEGS -> new VRPlayerModel_WithArmsLegs<>(
                slim ? VR_LAYER_DEF_ARMS_LEGS_SLIM.bakeRoot() : VR_LAYER_DEF_ARMS_LEGS.bakeRoot(), slim);
        };

        this.addLayer(new HMDLayer(this));
    }

    /**
     * @param renderLayer RenderLayer to check
     * @return if a layer of the given class is already registered
     */
    public boolean hasLayerType(RenderLayer<?,?> renderLayer) {
        return this.layers.stream().anyMatch(layer -> layer.getClass() == renderLayer.getClass());
    }

    @Override
    public void render(AbstractClientPlayer player, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        VRPlayersClient.RotInfo rotInfo = VRPlayersClient.getInstance().getRotationsForPlayer(player.getUUID());
        if (rotInfo != null) {
            float scale = rotInfo.heightScale;
            if (VRState.VR_RUNNING && player == Minecraft.getInstance().player) {
                scale *= rotInfo.worldScale;
            }

            poseStack.scale(scale, scale, scale);
        }

        super.render(player, entityYaw, partialTick, poseStack, buffer, packedLight);

        poseStack.popPose();
    }

    @Override
    public Vec3 getRenderOffset(AbstractClientPlayer player, float partialTick) {
        // idk why we do this anymore
        // this changes the offset to only apply when swimming, instead of crouching
        return player.isVisuallySwimming() ? new Vec3(0.0D, -0.125D, 0.0D) : Vec3.ZERO;
    }

    @Override
    public void setModelProperties(AbstractClientPlayer player) {
        super.setModelProperties(player);

        // no crouch hip movement when roomscale crawling
        this.getModel().crouching &= !player.isVisuallySwimming();

        if (player == Minecraft.getInstance().player &&
            ClientDataHolderVR.getInstance().currentPass == RenderPass.CAMERA &&
            ClientDataHolderVR.getInstance().cameraTracker.isQuickMode() &&
            ClientDataHolderVR.getInstance().grabScreenShot)
        {
            // player hands block the camera, so disable them for the screenshot
            hideHands();
        }
        if (player == Minecraft.getInstance().player &&
            ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
            (ClientDataHolderVR.getInstance().currentPass == RenderPass.LEFT ||
                ClientDataHolderVR.getInstance().currentPass == RenderPass.RIGHT ||
                ClientDataHolderVR.getInstance().currentPass == RenderPass.CENTER
            ))
        {
            // hide the head or you won't see anything
            this.getModel().head.visible = false;
            this.getModel().hat.visible = false;

            // hide arms when using the VR arms
            if (!ClientDataHolderVR.getInstance().vrSettings.shouldRenderModelArms) {
                hideHands();
            }
        }
    }

    private void hideHands() {
        if (this.getModel() instanceof VRPlayerModel_WithArms<?> armsModel) {
            armsModel.leftHand.visible = false;
            armsModel.rightHand.visible = false;
            armsModel.leftHandSleeve.visible = false;
            armsModel.rightHandSleeve.visible = false;
        } else {
            getModel().leftArm.visible = false;
            getModel().rightArm.visible = false;
            getModel().leftSleeve.visible = false;
            getModel().rightSleeve.visible = false;
        }
    }

    @Override
    protected void setupRotations(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        UUID uuid = player.getUUID();
        if (ClientDataHolderVR.getInstance().currentPass != RenderPass.GUI &&
            VRPlayersClient.getInstance().isTracked(uuid))
        {
            if (player == Minecraft.getInstance().player) {
                rotationYaw = ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().getBodyYaw();
            } else {
                VRPlayersClient.RotInfo rotInfo = VRPlayersClient.getInstance().getRotationsForPlayer(uuid);
                rotationYaw = Mth.RAD_TO_DEG * rotInfo.getBodyYawRad();
            }
        }

        // vanilla below here
        super.setupRotations(player, poseStack, ageInTicks, rotationYaw, partialTick);
    }
}
