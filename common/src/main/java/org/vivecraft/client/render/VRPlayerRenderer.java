package org.vivecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client.render.armor.VRArmorModel_WithArms;
import org.vivecraft.client.render.armor.VRArmorLayer;
import org.vivecraft.client.render.armor.VRArmorModel_WithArmsLegs;
import org.vivecraft.client.utils.ScaleHelper;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.mod_compat_vr.ShadersHelper;
import org.vivecraft.mod_compat_vr.immersiveportals.ImmersivePortalsHelper;

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

        VRArmorLayer.createLayers();
        if (type != ModelType.VANILLA) {
            // remove vanilla armor layer
            this.layers.stream()
                .filter(layer -> layer.getClass() == HumanoidArmorLayer.class)
                .findFirst()
                .ifPresent(this.layers::remove);
            // add split armor layer
            if (type == ModelType.SPLIT_ARMS) {
                this.addLayer(new VRArmorLayer<>(this,
                    new VRArmorModel_WithArms<>(VRArmorLayer.VR_ARMOR_DEF_ARMS_INNER.bakeRoot()),
                    new VRArmorModel_WithArms<>(VRArmorLayer.VR_ARMOR_DEF_ARMS_OUTER.bakeRoot()),
                    context.getModelManager()));
            } else {
                this.addLayer(new VRArmorLayer<>(this,
                    new VRArmorModel_WithArmsLegs<>(VRArmorLayer.VR_ARMOR_DEF_ARMS_LEGS_INNER.bakeRoot()),
                    new VRArmorModel_WithArmsLegs<>(VRArmorLayer.VR_ARMOR_DEF_ARMS_LEGS_OUTER.bakeRoot()),
                    context.getModelManager()));
            }
        }
    }

    /**
     * @param renderLayer RenderLayer to check
     * @return if a layer of the given class is already registered
     */
    public boolean hasLayerType(RenderLayer<?,?> renderLayer) {
        return this.layers.stream().anyMatch(layer -> {
            if (renderLayer.getClass() == HumanoidArmorLayer.class) {
                return layer.getClass() == renderLayer.getClass() || layer.getClass() == VRArmorLayer.class;
            }
            return layer.getClass() == renderLayer.getClass();
        });
    }

    @Override
    public void render(
        AbstractClientPlayer player, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
        int packedLight)
    {

        poseStack.pushPose();

        VRPlayersClient.RotInfo rotInfo = VRPlayersClient.getInstance().getRotationsForPlayer(player.getUUID());
        if (rotInfo != null) {
            float scale = rotInfo.heightScale;
            if (VRState.VR_RUNNING && player == Minecraft.getInstance().player) {
                // remove entity scale, since the entity is already scaled by that before
                scale *= rotInfo.worldScale / ScaleHelper.getEntityEyeHeightScale(player, partialTick);
            }

            if (player.isAutoSpinAttack()) {
                // offset player to head
                float offset = player.getViewXRot(partialTick) / 90F * 0.2F;
                poseStack.translate(0, rotInfo.headPos.y() + offset,0);
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
        if (VRState.VR_RUNNING && player == Minecraft.getInstance().player) {
            return player.isVisuallySwimming() ?
                new Vec3(0.0F, -0.125F * ClientDataHolderVR.getInstance().vrPlayer.worldScale, 0.0F) : Vec3.ZERO;
        } else {
            return player.isVisuallySwimming() ? new Vec3(0.0D, -0.125D, 0.0D) : Vec3.ZERO;
        }
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
            hideHands(true);
        }
        if (player == Minecraft.getInstance().player &&
            ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
            !ShadersHelper.isRenderingShadows() &&
            !(ImmersivePortalsHelper.isLoaded() && ImmersivePortalsHelper.isRenderingPortal()) &&
            RenderPass.isFirstPerson(ClientDataHolderVR.getInstance().currentPass))
        {
            // hide the head or you won't see anything
            this.getModel().head.visible = false;
            this.getModel().hat.visible = false;

            // hide model arms when not using them
            if (ClientDataHolderVR.getInstance().vrSettings.modelArmsMode !=
                VRSettings.ModelArmsMode.COMPLETE)
            {
                // keep the shoulders when in shoulder mode
                hideHands(ClientDataHolderVR.getInstance().vrSettings.modelArmsMode ==
                    VRSettings.ModelArmsMode.OFF);
            } else if (this.getModel() instanceof VRPlayerModel<?> vrModel) {
                if (ClientDataHolderVR.getInstance().menuHandOff) {
                    vrModel.hideHand(player, InteractionHand.OFF_HAND, false);
                }
                if (ClientDataHolderVR.getInstance().menuHandMain) {
                    vrModel.hideHand(player, InteractionHand.MAIN_HAND, false);
                }
            }
        }
    }

    private void hideHands(boolean completeArm) {
        if (this.getModel() instanceof VRPlayerModel<?> vrModel) {
            vrModel.hideLeftArm(completeArm);
            vrModel.hideRightArm(completeArm);
        } else {
            // this is just for the case someone replaces the model
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
            VRPlayersClient.getInstance().isTracked(uuid) || player == Minecraft.getInstance().player)
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
