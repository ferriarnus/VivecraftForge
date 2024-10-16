package org.vivecraft.client_vr.render.helpers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.vivecraft.client.utils.Utils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.gameplay.trackers.CameraTracker;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRHotkeys;
import org.vivecraft.client_vr.settings.VRSettings;

import java.util.function.Function;

public class VRWidgetHelper {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final ClientDataHolderVR DATA_HOLDER = ClientDataHolderVR.getInstance();

    private static final RandomSource RANDOM = RandomSource.create();
    public static boolean DEBUG = false;

    /**
     * renders the third person camcorder
     */
    public static void renderVRThirdPersonCamWidget() {
        if (!DATA_HOLDER.vrSettings.mixedRealityRenderCameraModel) return;
        if (DATA_HOLDER.currentPass == RenderPass.LEFT || DATA_HOLDER.currentPass == RenderPass.RIGHT) {
            if ((DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY ||
                DATA_HOLDER.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON
            ) && (!DATA_HOLDER.vrSettings.displayMirrorUseScreenshotCamera || !DATA_HOLDER.cameraTracker.isVisible()))
            {
                float scale = 0.35F;

                // bigger when interact ready
                if (DATA_HOLDER.interactTracker.isInCamera() && !VRHotkeys.isMovingThirdPersonCam()) {
                    scale *= 1.03F;
                }

                renderVRCameraWidget(-0.748F, -0.438F, -0.06F, scale, RenderPass.THIRD,
                    ClientDataHolderVR.THIRD_PERSON_CAMERA_MODEL, ClientDataHolderVR.THIRD_PERSON_CAMERA_DISPLAY_MODEL,
                    () -> {
                        DATA_HOLDER.vrRenderer.framebufferMR.bindRead();
                        RenderSystem.setShaderTexture(0, DATA_HOLDER.vrRenderer.framebufferMR.getColorTextureId());
                    }, (face) -> {
                        if (face == Direction.NORTH) {
                            return DisplayFace.MIRROR;
                        } else {
                            return face == Direction.SOUTH ? DisplayFace.NORMAL : DisplayFace.NONE;
                        }
                    });
            }
        }
    }

    /**
     * renders the screenshot camera
     */
    public static void renderVRHandheldCameraWidget() {
        if (DATA_HOLDER.currentPass != RenderPass.CAMERA && DATA_HOLDER.cameraTracker.isVisible()) {
            float scale = 0.25F;

            // bigger when interact ready
            if (DATA_HOLDER.interactTracker.isInHandheldCamera() && !DATA_HOLDER.cameraTracker.isMoving()) {
                scale *= 1.03F;
            }

            renderVRCameraWidget(-0.5F, -0.25F, -0.22F, scale, RenderPass.CAMERA,
                CameraTracker.CAMERA_MODEL, CameraTracker.CAMERA_DISPLAY_MODEL, () -> {
                    if (VREffectsHelper.getNearOpaqueBlock(
                        DATA_HOLDER.vrPlayer.vrdata_world_render.getEye(RenderPass.CAMERA).getPosition(),
                        ((GameRendererExtension) MC.gameRenderer).vivecraft$getMinClipDistance()) == null)
                    {
                        DATA_HOLDER.vrRenderer.cameraFramebuffer.bindRead();
                        RenderSystem.setShaderTexture(0, DATA_HOLDER.vrRenderer.cameraFramebuffer.getColorTextureId());
                    } else {
                        RenderSystem.setShaderTexture(0, new ResourceLocation("vivecraft:textures/black.png"));
                    }
                }, (face) -> face == Direction.SOUTH ? DisplayFace.NORMAL : DisplayFace.NONE);
        }
    }

    /**
     * renders a camera model with screen
     * @param offsetX model x offset
     * @param offsetY model y offset
     * @param offsetZ model z offset
     * @param scale size of the model
     * @param renderPass RenderPass this camera shows, the camera will be placed there
     * @param model camera model to render
     * @param displayModel model of the display that shows the camera view
     * @param displayBindFunc function that binds the camera buffer, or something else
     * @param displayFaceFunc function that specifies if the view should be mirrored, normal or not shown at all
     */
    public static void renderVRCameraWidget(float offsetX, float offsetY, float offsetZ, float scale, RenderPass renderPass, ModelResourceLocation model, ModelResourceLocation displayModel, Runnable displayBindFunc, Function<Direction, DisplayFace> displayFaceFunc) {
        // TODO: is this reset really needed?
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.setIdentity();
        RenderHelper.applyVRModelView(DATA_HOLDER.currentPass, poseStack);

        // model position relative to the view position
        Vec3 widgetPosition = DATA_HOLDER.vrPlayer.vrdata_world_render.getEye(renderPass).getPosition();
        Vec3 eye = RenderHelper.getSmoothCameraPosition(DATA_HOLDER.currentPass, DATA_HOLDER.vrPlayer.vrdata_world_render);
        Vec3 widgetOffset = widgetPosition.subtract(eye);

        // orient and scale model
        poseStack.translate(widgetOffset.x, widgetOffset.y, widgetOffset.z);

        poseStack.mulPoseMatrix(DATA_HOLDER.vrPlayer.vrdata_world_render.getEye(renderPass).getMatrix());
        scale = scale * DATA_HOLDER.vrPlayer.vrdata_world_render.worldScale;
        poseStack.scale(scale, scale, scale);

        // show orientation
        if (DEBUG) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            RenderHelper.renderDebugAxes(0, 0, 0, 0.08F);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        }

        // apply model offset
        poseStack.translate(offsetX, offsetY, offsetZ);
        RenderSystem.applyModelViewMatrix();

        // lighting for the model
        BlockPos blockpos = BlockPos.containing(DATA_HOLDER.vrPlayer.vrdata_world_render.getEye(renderPass).getPosition());
        int combinedLight = Utils.getCombinedLightWithMin(MC.level, blockpos, 0);

        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();

        // we use block models, so the camera texture is on the regular block atlas
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        if (MC.level != null) {
            RenderSystem.setShader(GameRenderer::getRendertypeEntityCutoutNoCullShader);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);
        }
        MC.gameRenderer.lightTexture().turnOnLightLayer();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        // render camera model
        bufferBuilder.begin(Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        // TODO why do we use a new poseStack here?
        PoseStack poseStack2 = new PoseStack();
        RenderHelper.applyVRModelView(DATA_HOLDER.currentPass, poseStack2);
        poseStack2.last().pose().identity();
        poseStack2.last().normal().mul(new Matrix3f(DATA_HOLDER.vrPlayer.vrdata_world_render.getEye(renderPass).getMatrix()));

        MC.getBlockRenderer().getModelRenderer().renderModel(poseStack2.last(), bufferBuilder, null, MC.getModelManager().getModel(model), 1.0F, 1.0F, 1.0F, combinedLight, OverlayTexture.NO_OVERLAY);
        tesselator.end();

        // render camera display
        RenderSystem.disableBlend();
        displayBindFunc.run();
        RenderSystem.setShader(GameRenderer::getRendertypeEntitySolidShader);

        bufferBuilder.begin(Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);

        // need to render this manually, because the uvs in the model are for the atlas texture, and not fullscreen
        for (BakedQuad bakedquad : MC.getModelManager().getModel(displayModel).getQuads(null, null, RANDOM)) {
            if (displayFaceFunc.apply(bakedquad.getDirection()) != DisplayFace.NONE && bakedquad.getSprite().contents().name().equals(new ResourceLocation("vivecraft:transparent"))) {
                int[] vertexList = bakedquad.getVertices();
                boolean mirrored = displayFaceFunc.apply(bakedquad.getDirection()) == DisplayFace.MIRROR;
                // make normals point up, so they are always bright
                // TODO: might break with shaders?
                Vector3f normal = poseStack.last().normal().transform(new Vector3f(0.0F, 1.0F, 0.0F));
                int step = vertexList.length / 4;
                bufferBuilder.vertex(
                        Float.intBitsToFloat(vertexList[0]),
                        Float.intBitsToFloat(vertexList[1]),
                        Float.intBitsToFloat(vertexList[2]))
                    .color(1.0F, 1.0F, 1.0F, 1.0F)
                    .uv(mirrored ? 1.0F : 0.0F, 1.0F)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(normal.x, normal.y, normal.z).endVertex();
                bufferBuilder.vertex(
                        Float.intBitsToFloat(vertexList[step]),
                        Float.intBitsToFloat(vertexList[step + 1]),
                        Float.intBitsToFloat(vertexList[step + 2]))
                    .color(1.0F, 1.0F, 1.0F, 1.0F)
                    .uv(mirrored ? 1.0F : 0.0F, 0.0F)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(normal.x, normal.y, normal.z).endVertex();
                bufferBuilder.vertex(
                        Float.intBitsToFloat(vertexList[step * 2]),
                        Float.intBitsToFloat(vertexList[step * 2 + 1]),
                        Float.intBitsToFloat(vertexList[step * 2 + 2]))
                    .color(1.0F, 1.0F, 1.0F, 1.0F)
                    .uv(mirrored ? 0.0F : 1.0F, 0.0F)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(normal.x, normal.y, normal.z).endVertex();
                bufferBuilder.vertex(
                        Float.intBitsToFloat(vertexList[step * 3]),
                        Float.intBitsToFloat(vertexList[step * 3 + 1]),
                        Float.intBitsToFloat(vertexList[step * 3 + 2]))
                    .color(1.0F, 1.0F, 1.0F, 1.0F)
                    .uv(mirrored ? 0.0F : 1.0F, 1.0F)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(normal.x, normal.y, normal.z).endVertex();
            }
        }
        tesselator.end();

        MC.gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.enableBlend();

        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    public enum DisplayFace {
        NONE,
        NORMAL,
        MIRROR
    }
}
