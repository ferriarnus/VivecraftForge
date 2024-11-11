package org.vivecraft.mixin.client_vr.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.EntityRenderDispatcherVRExtension;
import org.vivecraft.client_vr.gameplay.trackers.BowTracker;
import org.vivecraft.client_vr.gameplay.trackers.ClimbTracker;
import org.vivecraft.client_vr.gameplay.trackers.TelescopeTracker;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.VRArmRenderer;
import org.vivecraft.client_vr.render.VivecraftItemRendering;
import org.vivecraft.client_vr.render.helpers.VREffectsHelper;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;

@Mixin(value = ItemInHandRenderer.class, priority = 999)
public abstract class ItemInHandRendererVRMixin {

    @Final
    @Shadow
    private Minecraft minecraft;
    @Final
    @Shadow
    private EntityRenderDispatcher entityRenderDispatcher;
    @Final
    @Shadow
    private ItemRenderer itemRenderer;
    @Shadow
    private float oMainHandHeight;
    @Shadow
    private float mainHandHeight;
    @Shadow
    private float oOffHandHeight;
    @Shadow
    private float offHandHeight;

    @Shadow
    public abstract void renderItem(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int seed);

    @Shadow
    protected abstract void renderMap(PoseStack poseStack, MultiBufferSource buffer, int combinedLight, ItemStack stack);

    @Shadow
    protected abstract void renderPlayerArm(PoseStack poseStack, MultiBufferSource buffer, int combinedLight, float equippedProgress, float swingProgress, HumanoidArm side);

    @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    private void vivecraft$overrideArm(
        PoseStack poseStack, MultiBufferSource buffer, int combinedLight, float equippedProgress, float swingProgress,
        HumanoidArm side, CallbackInfo ci)
    {
        if (VRState.VR_RUNNING) {
            if (!ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf ||
                !ClientDataHolderVR.getInstance().vrSettings.shouldRenderModelArms) {
                vivecraft$vrPlayerArm(poseStack, buffer, combinedLight, swingProgress, side);
            }
            ci.cancel();
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void vivecraft$overrideArmItem(
        AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
        ItemStack itemStack, float equippedProgress, PoseStack poseStack, MultiBufferSource buffer, int combinedLight,
        CallbackInfo ci)
    {
        if (VRState.VR_RUNNING) {
            this.vivecraft$vrRenderArmWithItem(player, partialTick, hand, swingProgress, itemStack, poseStack, buffer, combinedLight);
            ci.cancel();
        }
    }

    @Unique
    private void vivecraft$vrRenderArmWithItem(AbstractClientPlayer player, float partialTick, InteractionHand hand, float swingProgress, ItemStack itemStack, PoseStack poseStack, MultiBufferSource buffer, int combinedLight) {
        ClientDataHolderVR dh = ClientDataHolderVR.getInstance();

        boolean mainHand = hand == InteractionHand.MAIN_HAND;
        HumanoidArm side = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        // we need to get this here, because the supplied value is invalid when we call it
        float equippedProgress = this.vivecraft$getEquipProgress(hand, partialTick);

        poseStack.pushPose();

        boolean renderArm = dh.currentPass != RenderPass.THIRD || dh.vrSettings.mixedRealityRenderHands;

        if (dh.currentPass == RenderPass.CAMERA) {
            renderArm = false;
        }
        if (BowTracker.isBow(itemStack) && dh.bowTracker.isActive((LocalPlayer) player)) {
            renderArm = false;
        }
        if (TelescopeTracker.isTelescope(itemStack) &&
            (hand == InteractionHand.OFF_HAND && dh.currentPass == RenderPass.SCOPEL ||
                hand == InteractionHand.MAIN_HAND && dh.currentPass == RenderPass.SCOPER
            ))
        {
            renderArm = false;
        }

        if (renderArm && !player.isInvisible()) {
            this.renderPlayerArm(poseStack, buffer, combinedLight, equippedProgress, swingProgress, side);
        }

        if (!itemStack.isEmpty()) {
            poseStack.pushPose();

            if (player.swingingArm == hand) {
                this.vivecraft$transformFirstPersonVR(poseStack, side, swingProgress);
            }

            VivecraftItemRendering.VivecraftItemTransformType transformType = VivecraftItemRendering.getTransformType(
                itemStack, player, this.itemRenderer);

            boolean useLeftHandModelinLeftHand = false;

            // swap hand for claws, since it's backwards else wise
            if (ClimbTracker.isClaws(itemStack) && dh.vrSettings.reverseHands) {
                mainHand = !mainHand;
            }

            ItemDisplayContext itemDisplayContext;

            // third person transforms for custom model data items, but not spear, shield and crossbow
            boolean hasCMD = itemStack.hasTag() && itemStack.getTag().getInt("CustomModelData") != 0 &&
                transformType != VivecraftItemRendering.VivecraftItemTransformType.Crossbow &&
                transformType != VivecraftItemRendering.VivecraftItemTransformType.Spear &&
                transformType != VivecraftItemRendering.VivecraftItemTransformType.Shield;

            boolean isBow = BowTracker.isBow(itemStack) && dh.bowTracker.isActive((LocalPlayer) player);

            if (ClimbTracker.isClaws(itemStack) || (!isBow &&
                (ClientNetworking.isThirdPersonItems() || (hasCMD && ClientNetworking.isThirdPersonItemsCustom()))
            ))
            {
                useLeftHandModelinLeftHand = true; //test
                VivecraftItemRendering.applyThirdPersonItemTransforms(poseStack, transformType, mainHand, player, equippedProgress, partialTick, itemStack, hand);

                itemDisplayContext = mainHand || !useLeftHandModelinLeftHand ?
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            } else {
                VivecraftItemRendering.applyFirstPersonItemTransforms(poseStack, transformType, mainHand, player, equippedProgress, partialTick, itemStack, hand);

                itemDisplayContext = mainHand || !useLeftHandModelinLeftHand ?
                    ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            }

            ClientDataHolderVR.IS_FP_HAND = true;

            if (transformType == VivecraftItemRendering.VivecraftItemTransformType.Map) {
                RenderSystem.disableCull();
                this.renderMap(poseStack, buffer, combinedLight, itemStack);
            } else if (transformType == VivecraftItemRendering.VivecraftItemTransformType.Telescope) {
                if (dh.currentPass != RenderPass.SCOPEL && dh.currentPass != RenderPass.SCOPER) {
                    poseStack.pushPose();

                    // render item
                    renderItem(player, itemStack, itemDisplayContext, !mainHand && useLeftHandModelinLeftHand, poseStack, buffer, combinedLight);

                    if (ClientNetworking.isThirdPersonItems()) {
                        // account for the -2/16 offset of the third person spyglass transform
                        poseStack.translate(0.0F, 0.219F, 0.0F);
                    } else {
                        poseStack.translate(0.0F, 0.344F, 0.0F);
                    }

                    poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                    if (OptifineHelper.isOptifineLoaded() && OptifineHelper.isShaderActive()) {
                        // this messes stuff up when rendering the quads
                        OptifineHelper.endEntities();
                    }
                    // render scope view
                    VREffectsHelper.drawScopeFB(poseStack, hand == InteractionHand.MAIN_HAND ? 0 : 1);

                    if (OptifineHelper.isOptifineLoaded() && OptifineHelper.isShaderActive()) {
                        OptifineHelper.beginEntities();
                    }

                    poseStack.popPose();
                }
            } else {
                this.renderItem(player, itemStack, itemDisplayContext, !mainHand && useLeftHandModelinLeftHand, poseStack, buffer, combinedLight);
            }

            ClientDataHolderVR.IS_FP_HAND = false;
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    @Unique
    private float vivecraft$getEquipProgress(InteractionHand hand, float partialTick) {
        return hand == InteractionHand.MAIN_HAND ?
            1.0F - Mth.lerp(partialTick, this.oMainHandHeight, this.mainHandHeight) :
            1.0F - Mth.lerp(partialTick, this.oOffHandHeight, this.offHandHeight);
    }

    @Unique
    private void vivecraft$vrPlayerArm(PoseStack poseStack, MultiBufferSource buffer, int combinedLight, float swingProgress, HumanoidArm side) {
        boolean mainHand = side != HumanoidArm.LEFT;
        float offsetDirection = mainHand ? 1.0F : -1.0F;
        AbstractClientPlayer player = this.minecraft.player;

        RenderSystem.setShaderTexture(0, player.getSkin().texture());
        VRArmRenderer vrArmRenderer = ((EntityRenderDispatcherVRExtension) this.entityRenderDispatcher).vivecraft$getArmSkinMap()
            .get(player.getSkin().model().id());

        poseStack.pushPose();

        if (player.swingingArm == InteractionHand.MAIN_HAND && mainHand) {
            this.vivecraft$transformFirstPersonVR(poseStack, side, swingProgress);
        }

        if (player.swingingArm == InteractionHand.OFF_HAND && !mainHand) {
            this.vivecraft$transformFirstPersonVR(poseStack, side, swingProgress);
        }

        poseStack.scale(0.4f, 0.4F, 0.4F);
        boolean slim = player.getSkin().model().id().equals("slim");

            /*
             x offset: (arm x origin + arm x offset + arm x dimension * 0.5) / 16
             z offset: (arm y origin + arm y offset + arm y dimension) / 16
             slim
             x offset: (5 + -1 + 3*0.5) / 16 = 0.34375
             z offset: (-2 + 2.5 + 12) / 16 = 0.78125
             regular
             x offset: (5 - 1 + 4*0.5) / 16 = 0.375
             z offset: (-2 + 2 + 12) / 16 = 0.75
            */

        poseStack.translate((slim ? -0.34375F : -0.375F) * offsetDirection, 0.0F, slim ? 0.78125F : 0.75F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        if (mainHand) {
            vrArmRenderer.renderRightHand(poseStack, buffer, combinedLight, player);
        } else {
            vrArmRenderer.renderLeftHand(poseStack, buffer, combinedLight, player);
        }
        poseStack.popPose();
    }

    @Unique
    private void vivecraft$transformFirstPersonVR(PoseStack poseStack, HumanoidArm side, float swingProgress) {
        if (swingProgress == 0.0F) return;

        switch (ClientDataHolderVR.getInstance().swingType) {
            case Attack -> {
                float forwardRotation;
                if (swingProgress > 0.5F) {
                    forwardRotation = Mth.sin(swingProgress * Mth.PI + Mth.PI);
                } else {
                    forwardRotation = Mth.sin((swingProgress * 3.0F) * Mth.PI);
                }

                poseStack.translate(0.0F, 0.0F, 0.2F);
                poseStack.mulPose(Axis.XP.rotationDegrees(forwardRotation * 30.0F));
                poseStack.translate(0.0F, 0.0F, -0.2F);
            }
            case Interact -> {
                float sideRotation;
                if (swingProgress > 0.5F) {
                    sideRotation = Mth.sin(swingProgress * Mth.PI + Mth.PI);
                } else {
                    sideRotation = Mth.sin((swingProgress * 3.0F) * Mth.PI);
                }

                poseStack.mulPose(Axis.ZP.rotationDegrees((side == HumanoidArm.RIGHT ? -1F : 1F) * sideRotation * 45.0F));
            }
            case Use -> {
                float forwardMovement;
                if (swingProgress > 0.25F) {
                    forwardMovement = Mth.sin((swingProgress / 2.0F) * Mth.PI + Mth.PI);
                } else {
                    forwardMovement = Mth.sin((swingProgress * 2.0F) * Mth.PI);
                }

                poseStack.translate(0.0F, 0.0F, -(1.0F + forwardMovement) * 0.1F);
            }
        }
    }
}
