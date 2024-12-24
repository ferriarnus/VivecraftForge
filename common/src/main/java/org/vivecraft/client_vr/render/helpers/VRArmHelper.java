package org.vivecraft.client_vr.render.helpers;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11C;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.gameplay.trackers.BowTracker;
import org.vivecraft.client_vr.gameplay.trackers.ClimbTracker;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.mod_compat_vr.shaders.ShadersHelper;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;

public class VRArmHelper {

    private static final ClientDataHolderVR DATA_HOLDER = ClientDataHolderVR.getInstance();
    private static final Minecraft MC = Minecraft.getInstance();

    private static final Vec3i TP_UNLIMITED_COLOR = new Vec3i(173, 216, 230);
    private static final Vec3i TP_LIMITED_COLOR = new Vec3i(205, 169, 205);
    private static final Vec3i TP_INVALID_COLOR = new Vec3i(83, 83, 83);

    /**
     * @return if first person hands should be rendered in the current RenderPass
     */
    public static boolean shouldRenderHands() {
        if (ClientDataHolderVR.VIEW_ONLY) {
            return false;
        } else if (DATA_HOLDER.currentPass == RenderPass.THIRD) {
            return DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY;
        } else {
            return DATA_HOLDER.currentPass != RenderPass.CAMERA;
        }
    }

    /**
     * renders the VR hands
     * @param partialTick current partial tick
     * @param renderMain if the main hand should be rendered
     * @param renderOff if the offhand should be rendered
     * @param menuHandMain if the right hand should render as the menu hand
     * @param menuHandOff if the left hand should render as the menu hand
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderVRHands(
        float partialTick, boolean renderMain, boolean renderOff, boolean menuHandMain, boolean menuHandOff,
        PoseStack poseStack)
    {
        MC.getProfiler().push("hands");
        ClientDataHolderVR.IS_FP_HAND = true;

        // backup projection matrix, not doing that breaks sodium water on 1.19.3
        RenderSystem.backupProjectionMatrix();

        if (renderMain) {
            // set main hand active, for the attack cooldown transparency
            ClientDataHolderVR.IS_MAIN_HAND = true;

            ((GameRendererExtension) MC.gameRenderer).vivecraft$resetProjectionMatrix(partialTick);

            if (menuHandMain) {
                renderMainMenuHand(0, false, poseStack);
            } else {
                PoseStack newPoseStack = new PoseStack();
                newPoseStack.last().pose().identity();
                RenderHelper.applyVRModelView(DATA_HOLDER.currentPass, newPoseStack);
                renderVRHand_Main(newPoseStack, partialTick);
            }

            ClientDataHolderVR.IS_MAIN_HAND = false;
        }

        if (renderOff) {
            ((GameRendererExtension) MC.gameRenderer).vivecraft$resetProjectionMatrix(partialTick);
            if (menuHandOff) {
                renderMainMenuHand(1, false, poseStack);
            } else {
                PoseStack newPoseStack = new PoseStack();
                newPoseStack.last().pose().identity();
                RenderHelper.applyVRModelView(DATA_HOLDER.currentPass, newPoseStack);
                renderVRHand_Offhand(newPoseStack, partialTick, true);
            }
        }

        RenderSystem.restoreProjectionMatrix();
        ClientDataHolderVR.IS_FP_HAND = false;
        MC.getProfiler().pop();
    }

    /**
     * renders a main menu hand for the specified controller, which is a gray box
     * @param c controller to render the hand for
     * @param depthAlways if depth testing should be disabled for rendering
     * @param poseStack PoseStack for positioning
     */
    public static void renderMainMenuHand(int c, boolean depthAlways, PoseStack poseStack) {

        poseStack.pushPose();
        poseStack.setIdentity();
        RenderHelper.applyVRModelView(DATA_HOLDER.currentPass, poseStack);
        RenderHelper.setupRenderingAtController(c, poseStack);

        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();

        if (MC.getOverlay() == null) {
            MC.getTextureManager().bindForSetup(new ResourceLocation("vivecraft:textures/white.png"));
            RenderSystem.setShaderTexture(0, new ResourceLocation("vivecraft:textures/white.png"));
        }

        if (depthAlways && c == 0) {
            RenderSystem.depthFunc(GL11C.GL_ALWAYS);
        } else {
            RenderSystem.depthFunc(GL11C.GL_LEQUAL);
        }

        Vec3i color = new Vec3i(64, 64, 64);
        byte alpha = (byte) 255;

        Vec3 start = Vec3.ZERO;
        Vec3 end = new Vec3(0D, 0D, 0.18D);

        if (MC.level != null) {
            // make the hands darker in dim places
            float light = (float) MC.level.getMaxLocalRawBrightness(
                BlockPos.containing(DATA_HOLDER.vrPlayer.vrdata_world_render.hmd.getPosition()));

            int minLight = ShadersHelper.ShaderLight();

            if (light < (float) minLight) {
                light = (float) minLight;
            }

            float lightPercent = light / (float) MC.level.getMaxLightLevel();
            color = new Vec3i(Mth.floor(color.getX() * lightPercent),
                Mth.floor(color.getY() * lightPercent),
                Mth.floor(color.getZ() * lightPercent));
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        tesselator.getBuilder().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);

        RenderHelper.renderBox(tesselator.getBuilder(), start, end, -0.02F, 0.02F, -0.0125F, 0.0125F, color, alpha, poseStack);

        BufferUploader.drawWithShader(tesselator.getBuilder().end());

        poseStack.popPose();

        RenderSystem.depthFunc(GL11C.GL_LEQUAL);
    }

    /**
     * renders the main minecraft hand
     * @param poseStack PoseStack for positioning
     * @param partialTick current partial tick
     */
    public static void renderVRHand_Main(PoseStack poseStack, float partialTick) {
        // don't render claws with model arms
        if (DATA_HOLDER.climbTracker.isClimbeyClimb() &&
            ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf &&
            ClientDataHolderVR.getInstance().vrSettings.modelArmsMode == VRSettings.ModelArmsMode.COMPLETE)
        {
            return;
        }

        poseStack.pushPose();
        RenderHelper.setupRenderingAtController(0, poseStack);
        ItemStack item = MC.player.getMainHandItem();
        ItemStack override = null; // physicalGuiManager.getHeldItemOverride();

        if (override != null) {
            item = override;
        }

        // climbey override
        if (DATA_HOLDER.climbTracker.isClimbeyClimb() && !ClimbTracker.isClaws(item) && override == null) {
            item = MC.player.getOffhandItem();
        }

        // Roomscale bow override
        item = getBowOverride(item, InteractionHand.MAIN_HAND);

        if (OptifineHelper.isOptifineLoaded() && OptifineHelper.isShaderActive()) {
            // if we don't do this shaders render the hands wrong
            OptifineHelper.beginEntities();
        }

        MC.gameRenderer.lightTexture().turnOnLightLayer();

        MultiBufferSource.BufferSource bufferSource = MC.renderBuffers().bufferSource();
        MC.gameRenderer.itemInHandRenderer.renderArmWithItem(MC.player, partialTick,
            0.0F, InteractionHand.MAIN_HAND, MC.player.getAttackAnim(partialTick), item, 0.0F,
            poseStack, bufferSource,
            MC.getEntityRenderDispatcher().getPackedLightCoords(MC.player, partialTick));

        bufferSource.endBatch();

        MC.gameRenderer.lightTexture().turnOffLightLayer();

        if (OptifineHelper.isOptifineLoaded() && OptifineHelper.isShaderActive()) {
            // undo the thing we did before
            OptifineHelper.endEntities();
        }

        poseStack.popPose();
    }

    /**
     * renders the offhand minecraft hand
     * @param poseStack PoseStack for positioning
     * @param partialTick current partial tick
     * @param renderTeleport if the teleport arc should be rendered
     */
    public static void renderVRHand_Offhand(PoseStack poseStack, float partialTick, boolean renderTeleport) {
        // don't render claws with model arms
        if (!ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf ||
            ClientDataHolderVR.getInstance().vrSettings.modelArmsMode != VRSettings.ModelArmsMode.COMPLETE ||
            !DATA_HOLDER.climbTracker.isClimbeyClimb())
        {
            poseStack.pushPose();
            RenderHelper.setupRenderingAtController(1, poseStack);
            ItemStack item = MC.player.getOffhandItem();
            ItemStack override = null; // physicalGuiManager.getOffhandOverride();

            if (override != null) {
                item = override;
            }

            // climbey override
            if (DATA_HOLDER.climbTracker.isClimbeyClimb() && !ClimbTracker.isClaws(item) && override == null) {
                item = MC.player.getMainHandItem();
            }

            // Roomscale bow override
            item = getBowOverride(item, InteractionHand.OFF_HAND);

            if (OptifineHelper.isOptifineLoaded() && OptifineHelper.isShaderActive()) {
                // if we don't do this shaders render the hands wrong
                OptifineHelper.beginEntities();
            }

            MC.gameRenderer.lightTexture().turnOnLightLayer();

            MultiBufferSource.BufferSource bufferSource = MC.renderBuffers().bufferSource();
            MC.gameRenderer.itemInHandRenderer.renderArmWithItem(MC.player, partialTick,
                0.0F, InteractionHand.OFF_HAND, MC.player.getAttackAnim(partialTick), item, 0.0F,
                poseStack, bufferSource,
                MC.getEntityRenderDispatcher().getPackedLightCoords(MC.player, partialTick));

            bufferSource.endBatch();

            MC.gameRenderer.lightTexture().turnOffLightLayer();

            if (OptifineHelper.isOptifineLoaded() && OptifineHelper.isShaderActive()) {
                // undo the thing we did before
                OptifineHelper.endEntities();
            }

            // back to hmd rendering
            poseStack.popPose();
        }

        // teleport arc
        if (renderTeleport) {
            poseStack.pushPose();
            poseStack.setIdentity();
            RenderHelper.applyVRModelView(DATA_HOLDER.currentPass, poseStack);

            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

            // TP energy
            if (ClientNetworking.isLimitedSurvivalTeleport() && !DATA_HOLDER.vrPlayer.getFreeMove() &&
                MC.gameMode.hasMissTime() &&
                DATA_HOLDER.teleportTracker.vrMovementStyle.arcAiming &&
                !DATA_HOLDER.bowTracker.isActive(MC.player))
            {
                poseStack.pushPose();
                RenderHelper.setupRenderingAtController(1, poseStack);

                Vec3 start = new Vec3(0.0D, 0.005D, 0.03D);
                float max = 0.03F;
                float size;

                if (DATA_HOLDER.teleportTracker.isAiming()) {
                    size = 2.0F * (DATA_HOLDER.teleportTracker.getTeleportEnergy() -
                        4.0F * (float) DATA_HOLDER.teleportTracker.movementTeleportDistance) / 100.0F * max;
                } else {
                    size = 2.0F * DATA_HOLDER.teleportTracker.getTeleportEnergy() / 100.0F * max;
                }

                // TODO SHADERS use a shader with lightmaps
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                MC.getTextureManager().bindForSetup(new ResourceLocation("vivecraft:textures/white.png"));
                RenderSystem.setShaderTexture(0, new ResourceLocation("vivecraft:textures/white.png"));

                if (size > 0.0F) {
                    // tp energy quad, slightly above the max energy quad
                    RenderHelper.renderFlatQuad(start.add(0.0D, 0.05001D, 0.0D), size, size, 0.0F,
                        TP_LIMITED_COLOR.getX(), TP_LIMITED_COLOR.getY(), TP_LIMITED_COLOR.getZ(), 128, poseStack);
                }
                // max energy quad
                RenderHelper.renderFlatQuad(start.add(0.0D, 0.05D, 0.0D), max, max, 0.0F, TP_LIMITED_COLOR.getX(),
                    TP_LIMITED_COLOR.getY(), TP_LIMITED_COLOR.getZ(), 50, poseStack);

                poseStack.popPose();
            }

            if (DATA_HOLDER.teleportTracker.isAiming()) {
                // renders from the head
                RenderSystem.enableDepthTest();

                if (DATA_HOLDER.teleportTracker.vrMovementStyle.arcAiming) {
                    renderTeleportArc(poseStack);
                } /* else {
                    renderTeleportLine(poseStack);
                }*/
            }

            RenderSystem.defaultBlendFunc();

            poseStack.popPose();
        }
    }

    /**
     * returns the hold item based on the roomscale bow state
     * @param itemStack the original item in the hand
     * @param interactionHand hand that should be checked
     * @return the overridden item, based on bow state
     */
    private static ItemStack getBowOverride(ItemStack itemStack, InteractionHand interactionHand) {
        if (DATA_HOLDER.vrSettings.reverseShootingEye && ClientNetworking.supportsReversedBow()) {
            // reverse bow hands
            interactionHand = interactionHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        }

        if (interactionHand == InteractionHand.MAIN_HAND) {
            // main hand has the ammo
            if (BowTracker.isHoldingBow(MC.player, InteractionHand.MAIN_HAND)) {
                // do ammo override
                ItemStack ammo = MC.player.getProjectile(MC.player.getMainHandItem());

                if (ammo != ItemStack.EMPTY && !DATA_HOLDER.bowTracker.isNotched()) {
                    // render the arrow in right, left hand will check for and render bow.
                    itemStack = ammo;
                } else {
                    itemStack = ItemStack.EMPTY;
                }
            } else if (BowTracker.isHoldingBow(MC.player, InteractionHand.OFF_HAND) &&
                DATA_HOLDER.bowTracker.isNotched())
            {
                // don't render a hand item if the bow is notched
                itemStack = ItemStack.EMPTY;
            }
        } else {
            // offhand has the bow
            if (BowTracker.isHoldingBow(MC.player, InteractionHand.MAIN_HAND)) {
                itemStack = MC.player.getMainHandItem();
            }
        }

        return itemStack;
    }

    /**
     * renders the teleport arc
     * @param poseStack PoseStack for positioning
     */
    public static void renderTeleportArc(PoseStack poseStack) {
        if (DATA_HOLDER.teleportTracker.vrMovementStyle.showBeam &&
            DATA_HOLDER.teleportTracker.isAiming() &&
            DATA_HOLDER.teleportTracker.movementTeleportArcSteps > 1)
        {
            MC.getProfiler().push("teleportArc");

            RenderSystem.enableCull();
            // TODO SHADERS use a shader with lightmaps
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            // to make shaders work
            MC.getTextureManager().bindForSetup(new ResourceLocation("vivecraft:textures/white.png"));
            RenderSystem.setShaderTexture(0, new ResourceLocation("vivecraft:textures/white.png"));

            Tesselator tesselator = Tesselator.getInstance();
            tesselator.getBuilder().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);

            double VOffset = DATA_HOLDER.teleportTracker.lastTeleportArcDisplayOffset;
            Vec3 dest = DATA_HOLDER.teleportTracker.getDestination();
            boolean validLocation = dest.x != 0.0D || dest.y != 0.0D || dest.z != 0.0D;

            byte alpha = (byte) 255;
            Vec3i color;

            if (!validLocation) {
                // invalid location
                color = TP_INVALID_COLOR;
                alpha = (byte) 128;
            } else {
                if (ClientNetworking.isLimitedSurvivalTeleport() && !MC.player.getAbilities().mayfly) {
                    color = TP_LIMITED_COLOR;
                } else {
                    color = TP_UNLIMITED_COLOR;
                }

                VOffset = Util.getMillis() * 0.001D
                    * (double) DATA_HOLDER.teleportTracker.vrMovementStyle.textureScrollSpeed * 0.6D;
                DATA_HOLDER.teleportTracker.lastTeleportArcDisplayOffset = VOffset;
            }

            float segmentHalfWidth = DATA_HOLDER.teleportTracker.vrMovementStyle.beamHalfWidth * 0.15F;
            int segments = DATA_HOLDER.teleportTracker.movementTeleportArcSteps - 1;

            if (DATA_HOLDER.teleportTracker.vrMovementStyle.beamGrow) {
                segments = (int) (segments * DATA_HOLDER.teleportTracker.movementTeleportProgress);
            }

            double segmentProgress = 1.0D / (double) segments;

            Vec3 cameraPosition = RenderHelper.getSmoothCameraPosition(DATA_HOLDER.currentPass, DATA_HOLDER.vrPlayer.getVRDataWorld());

            // arc
            for (int i = 0; i < segments; i++) {
                double progress = (double) i / (double) segments + VOffset * segmentProgress;
                int progressBase = Mth.floor(progress);
                progress -= progressBase;

                Vec3 start = DATA_HOLDER.teleportTracker
                    .getInterpolatedArcPosition((float) (progress - segmentProgress *  0.4D))
                    .subtract(cameraPosition);

                Vec3 end = DATA_HOLDER.teleportTracker.getInterpolatedArcPosition((float) progress)
                    .subtract(cameraPosition);

                float shift = (float) progress * 2.0F;
                RenderHelper.renderBox(tesselator.getBuilder(), start, end, -segmentHalfWidth, segmentHalfWidth, (-1.0F + shift) * segmentHalfWidth, (1.0F + shift) * segmentHalfWidth, color, alpha, poseStack);
            }

            tesselator.end();

            // hit indicator
            if (validLocation && DATA_HOLDER.teleportTracker.movementTeleportProgress >= 1.0D) {
                // disable culling to show the hit from both sides
                RenderSystem.disableCull();
                Vec3 targetPos = (new Vec3(dest.x, dest.y, dest.z)).subtract(cameraPosition);
                float offset = 0.01F;
                double x = 0.0D;
                double y = 0.0D;
                double z = 0.0D;

                y += offset;

                RenderHelper.renderFlatQuad(targetPos.add(x, y, z), 0.6F, 0.6F, 0.0F, (int) (color.getX() * 1.03D),
                    (int) (color.getY() * 1.03D), (int) (color.getZ() * 1.03D), 64, poseStack);

                y += offset;

                RenderHelper.renderFlatQuad(targetPos.add(x, y, z), 0.4F, 0.4F, 0.0F, (int) (color.getX() * 1.04D),
                    (int) (color.getY() * 1.04D), (int) (color.getZ() * 1.04D), 64, poseStack);

                y += offset;

                RenderHelper.renderFlatQuad(targetPos.add(x, y, z), 0.2F, 0.2F, 0.0F, (int) (color.getX() * 1.05D),
                    (int) (color.getY() * 1.05D), (int) (color.getZ() * 1.05D), 64, poseStack);
                RenderSystem.enableCull();
            }

            MC.getProfiler().pop();
        }
    }
}
