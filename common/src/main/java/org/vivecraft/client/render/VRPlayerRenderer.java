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
import org.vivecraft.client_vr.render.RenderPass;

import java.util.UUID;

public class VRPlayerRenderer extends PlayerRenderer {
    // TODO FBT re add final
    private static LayerDefinition VR_LAYER_DEF = LayerDefinition.create(VRPlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
    private static LayerDefinition VR_LAYER_DEF_ARMS = LayerDefinition.create(VRPlayerModel_WithArms.createMesh(CubeDeformation.NONE, false), 64, 64);
    private static LayerDefinition VR_LAYER_DEF_SLIM = LayerDefinition.create(VRPlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64);
    private static LayerDefinition VR_LAYER_DEF_ARMS_SLIM = LayerDefinition.create(VRPlayerModel_WithArms.createMesh(CubeDeformation.NONE, true), 64, 64);

    public VRPlayerRenderer(EntityRendererProvider.Context context, boolean slim, boolean seated) {
        super(context, slim);
        this.model = seated ?
            new VRPlayerModel<>(slim ? VR_LAYER_DEF_SLIM.bakeRoot() : VR_LAYER_DEF.bakeRoot(), slim) :
            new VRPlayerModel_WithArms<>(slim ? VR_LAYER_DEF_ARMS_SLIM.bakeRoot() : VR_LAYER_DEF_ARMS.bakeRoot(), slim);

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

        // TODO TEMP
        VR_LAYER_DEF = LayerDefinition.create(VRPlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
        VR_LAYER_DEF_ARMS = LayerDefinition.create(VRPlayerModel_WithArms.createMesh(CubeDeformation.NONE, false), 64, 64);
        //VRLayerDef_legs = LayerDefinition.create(VRPlayerModel_WithArmsAndLegs.createMesh(CubeDeformation.NONE, false), 64, 64);
        VR_LAYER_DEF_SLIM = LayerDefinition.create(VRPlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64);
        VR_LAYER_DEF_ARMS_SLIM = LayerDefinition.create(VRPlayerModel_WithArms.createMesh(CubeDeformation.NONE, true), 64, 64);
        //VRLayerDef_legs_slim = LayerDefinition.create(VRPlayerModel_WithArmsAndLegs.createMesh(CubeDeformation.NONE, true), 64, 64);
        if (model.slim) {
            /*if (model.getClass() == VRPlayerModel_WithArmsAndLegs.class) {
                model = new VRPlayerModel_WithArmsAndLegs<>(VRLayerDef_legs_slim.bakeRoot(), true);
            } else*/ if (model.getClass() == VRPlayerModel_WithArms.class) {
                model = new VRPlayerModel_WithArms<>(VR_LAYER_DEF_ARMS_SLIM.bakeRoot(), true);
            } else if (model.getClass() == VRPlayerModel.class) {
                model = new VRPlayerModel<>(VR_LAYER_DEF_SLIM.bakeRoot(), true);
            }
        } else {
            /*if (model.getClass() == VRPlayerModel_WithArmsAndLegs.class) {
                model = new VRPlayerModel_WithArmsAndLegs<>(VRLayerDef_legs.bakeRoot(), false);
            } else */if (model.getClass() == VRPlayerModel_WithArms.class) {
                model = new VRPlayerModel_WithArms<>(VR_LAYER_DEF_ARMS.bakeRoot(), false);
            } else if (model.getClass() == VRPlayerModel.class) {
                model = new VRPlayerModel<>(VR_LAYER_DEF.bakeRoot(), false);
            }
        }

        //TODO TEMP end

        poseStack.pushPose();

        VRPlayersClient.RotInfo rotInfo = VRPlayersClient.getInstance().getRotationsForPlayer(player.getUUID());
        if (rotInfo != null) {
            poseStack.scale(rotInfo.heightScale, rotInfo.heightScale, rotInfo.heightScale);
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
            if (this.getModel() instanceof VRPlayerModel_WithArms<?> armsModel) {
                armsModel.leftHand.visible = false;
                armsModel.rightHand.visible = false;
                armsModel.leftSleeve.visible = false;
                armsModel.rightSleeve.visible = false;
            } else {
                getModel().leftArm.visible = false;
                getModel().rightArm.visible = false;
                getModel().leftSleeve.visible = false;
                getModel().rightSleeve.visible = false;
            }
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

            // hide arms when doing using the VR arms
            if (!ClientDataHolderVR.getInstance().vrSettings.shouldRenderModelArms) {
                if (this.getModel() instanceof VRPlayerModel_WithArms<?> armsModel) {
                    armsModel.leftHand.visible = false;
                    armsModel.leftSleeve.visible = false;
                    armsModel.rightHand.visible = false;
                    armsModel.rightSleeve.visible = false;
                } else {
                    getModel().leftArm.visible = false;
                    getModel().rightArm.visible = false;
                    getModel().leftSleeve.visible = false;
                    getModel().rightSleeve.visible = false;
                }
            }
        }
    }

    @Override
    protected void setupRotations(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        UUID uuid = player.getUUID();
        VRPlayersClient.RotInfo rotInfo = null;
        boolean isMainPlayer = player == Minecraft.getInstance().player;
        if (ClientDataHolderVR.getInstance().currentPass != RenderPass.GUI && VRPlayersClient.getInstance().isTracked(uuid)) {
            if (isMainPlayer) {
                rotationYaw = ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().getBodyYaw();
            } else {
                rotInfo = VRPlayersClient.getInstance().getRotationsForPlayer(uuid);
                rotationYaw = Mth.RAD_TO_DEG * rotInfo.getBodyYawRad();
            }
        }

        super.setupRotations(player, poseStack, ageInTicks, rotationYaw, partialTick);
        if (player.isVisuallySwimming()) {
            //poseStack.translate(0.0F, 1.0F, 0.3F);
        }
        /*float swim = player.getSwimAmount(partialTick);
        if ((rotInfo == null && !isMainPlayer) || (!player.isVisuallySwimming() && swim == 0F)) {
            //vanilla here
            super.setupRotations(player, poseStack, ageInTicks, rotationYaw, partialTick);
        } else {
            float headOffset;
            if (isMainPlayer) {
                headOffset = (float) (ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().hmd.getPosition().y -
                    player.getPosition(partialTick).y
                );
            } else {
                headOffset = rotInfo.headPos.y();
            }
            //Vector3f offset = new Vector3f();
            //float xRot = ModelUtils.getSwimOffset(player, rotationYaw, headOffset, swim, player.getViewXRot(partialTick), offset);
            //poseStack.translate(offset.x, offset.y, offset.z);
            boolean inWater = player.isInWater();
            boolean swimming = player.isVisuallySwimming();
            // do a custom swim, to be able to move the player consistently so the head is in the same place
            float viewRot = player.getViewXRot(partialTick);
            float xRot = inWater ? -90.0F - player.getViewXRot(partialTick) : -90.0F;
            xRot = Mth.lerp(swim, 0.0F, xRot);

            // custom offset
            Vector3f v = new Vector3f(-1.55F, 0.0F, 0.0F);
            v.rotateZ(Mth.DEG_TO_RAD * (xRot + 90));
            v.rotateY(Mth.DEG_TO_RAD * (-rotationYaw - 90));
            float offset = 0.0F;
            if (inWater) {
                // only offset the model in water
                if (isMainPlayer) {
                    offset = (float) (ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld().hmd.getPosition().y -
                        player.getPosition(partialTick).y
                    );
                } else {
                    offset = rotInfo.headPos.y();
                }
            }

            poseStack.translate(v.x, v.y + offset, v.z);

            // when stopping swimming the model is offset by 1 block
            if (!player.isVisuallySwimming()) {
                //poseStack.translate(0F, -1F, 0F);
            }

            // Vanilla LivingEntity rotation
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(xRot));
        }*/
    }
}
