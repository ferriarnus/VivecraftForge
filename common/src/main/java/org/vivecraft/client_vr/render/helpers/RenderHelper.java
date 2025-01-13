package org.vivecraft.client_vr.render.helpers;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.gameplay.trackers.TelescopeTracker;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.mixin.client.blaze3d.RenderSystemAccessor;

import java.util.function.Supplier;

public class RenderHelper {

    private static final ClientDataHolderVR DATA_HOLDER = ClientDataHolderVR.getInstance();
    private static final Minecraft MC = Minecraft.getInstance();

    public static final ResourceLocation WHITE_TEXTURE = new ResourceLocation("vivecraft:textures/white.png");
    public static final ResourceLocation BLACK_TEXTURE = new ResourceLocation("vivecraft:textures/black.png");

    private static int POLY_BLEND_SRC_A;
    private static int POLY_BLEND_DST_A;
    private static int POLY_BLEND_SRC_RGB;
    private static int POLY_BLEND_DST_RGB;
    private static boolean POLY_BLEND;
    private static boolean POLY_TEX;
    private static boolean POLY_LIGHT;
    private static boolean POLY_CULL;

    /**
     * gets the rotation matrix for the given RenderPass
     *
     * @param renderPass RenderPass to get the rotation matrix for
     */
    public static Matrix4f getVRModelView(RenderPass renderPass) {
        if (renderPass == RenderPass.CENTER && DATA_HOLDER.vrSettings.displayMirrorCenterSmooth > 0.0F) {
            return MathUtils.toMcMat4(new org.joml.Matrix4f().rotation(MCVR.get().hmdRotHistory
                .averageRotation(DATA_HOLDER.vrSettings.displayMirrorCenterSmooth)));
        } else {
            return MathUtils.toMcMat4(
                DATA_HOLDER.vrPlayer.vrdata_world_render.getEye(renderPass).getMatrix().transpose());
        }
    }

    /**
     * Applies the rotation from the given RenderPass to the given PoseStack
     *
     * @param renderPass RenderPass rotation to use
     * @param poseStack  PoseStack to apply the rotation to
     */
    public static void applyVRModelView(RenderPass renderPass, PoseStack poseStack) {
        Matrix4f modelView = getVRModelView(renderPass);
        poseStack.last().pose().multiply(modelView);
        poseStack.last().normal().mul(new Matrix3f(modelView));
    }

    /**
     * Gets the camera position of the given RenderPass.
     * If the RenderPass is CENTER the position is smoothed over time if that setting is on
     *
     * @param renderPass pass to get the camera position for
     * @param vrData     vrData to get it from
     * @return camera position
     */
    public static Vec3 getSmoothCameraPosition(RenderPass renderPass, VRData vrData) {
        if (DATA_HOLDER.currentPass == RenderPass.CENTER && DATA_HOLDER.vrSettings.displayMirrorCenterSmooth > 0.0F) {
            Vector3f pos = MCVR.get().hmdHistory.averagePosition(DATA_HOLDER.vrSettings.displayMirrorCenterSmooth)
                .mul(vrData.worldScale)
                .rotateY(vrData.rotation_radians);
            return new Vec3(pos.x + vrData.origin.x, pos.y + vrData.origin.y, pos.z + vrData.origin.z);
        } else {
            return vrData.getEye(renderPass).getPosition();
        }
    }

    /**
     * Applies the offset for the LEFT and RIGHT RenderPass from the headset position
     * Other RenderPasses do nothing
     *
     * @param renderPass RenderPass to apply the offset for
     * @param poseStack  PoseStack to apply the offset to
     */
    public static void applyStereo(RenderPass renderPass, PoseStack poseStack) {
        if (renderPass == RenderPass.LEFT || renderPass == RenderPass.RIGHT) {
            Vec3 eye = DATA_HOLDER.vrPlayer.vrdata_world_render.getEye(renderPass).getPosition()
                .subtract(DATA_HOLDER.vrPlayer.vrdata_world_render.getEye(RenderPass.CENTER)
                    .getPosition());
            poseStack.translate(-eye.x, -eye.y, -eye.z);
        }
    }

    /**
     * Gets the position of the given controller/tracker in world space.
     * For controllers (0, 1), this positions the seated controllers.
     * Other stuff is just forwarded to the world_render vrData
     *
     * @param c controller/tracker to get the position for
     * @return position of the given controller
     */
    public static Vec3 getControllerRenderPos(int c) {
        if (DATA_HOLDER.vrSettings.seated && c < 2) {
            // only do the seated override for the controllers, not trackers

            int mainHand = InteractionHand.MAIN_HAND.ordinal();
            if (DATA_HOLDER.vrSettings.reverseHands) {
                c = 1 - c;
                mainHand = InteractionHand.OFF_HAND.ordinal();
            }

            // handle telescopes, allow for double scoping
            if (MC.player != null && MC.level != null &&
                TelescopeTracker.isTelescope(MC.player.getUseItem()) &&
                TelescopeTracker.isTelescope(c == mainHand ? MC.player.getMainHandItem() : MC.player.getOffhandItem()))
            {
                // move the controller in front of the eye when using the spyglass
                VRData.VRDevicePose eye = c == 0 ? DATA_HOLDER.vrPlayer.vrdata_world_render.eye0 :
                    DATA_HOLDER.vrPlayer.vrdata_world_render.eye1;

                Vector3f dir = DATA_HOLDER.vrPlayer.vrdata_world_render.hmd.getDirection()
                    .mul(0.2F * DATA_HOLDER.vrPlayer.vrdata_world_render.worldScale);

                return eye.getPosition().add(dir.x, dir.y, dir.z);
            } else {
                // general case
                // no worldScale in the main menu
                float worldScale = MC.player != null && MC.level != null ?
                    DATA_HOLDER.vrPlayer.vrdata_world_render.worldScale : 1.0F;

                Vector3f dir = DATA_HOLDER.vrPlayer.vrdata_world_render.hmd.getDirection();
                dir.rotateY(Mth.DEG_TO_RAD * (c == 0 ? -35.0F : 35.0F));
                dir.y = 0F;
                dir.normalize();
                return DATA_HOLDER.vrPlayer.vrdata_world_render.hmd.getPosition().add(
                    dir.x * 0.3D * worldScale,
                    -0.4D * worldScale,
                    dir.z * 0.3D * worldScale);
            }
        } else {
            return DATA_HOLDER.vrPlayer.vrdata_world_render.getController(c).getPosition();
        }
    }

    /**
     * sets up the poseStack to render at the given controller/tracker
     *
     * @param c         controller/tracker to render at
     * @param poseStack PoseStack to apply the position to
     */
    public static void setupRenderingAtController(int c, PoseStack poseStack) {
        Vec3 aimSource = getControllerRenderPos(c);
        aimSource = aimSource.subtract(
            getSmoothCameraPosition(DATA_HOLDER.currentPass, DATA_HOLDER.vrPlayer.getVRDataWorld()));
        // move from head to hand origin.
        poseStack.translate(aimSource.x, aimSource.y, aimSource.z);

        float sc = DATA_HOLDER.vrPlayer.vrdata_world_render.worldScale;

        // handle telescopes in seated, allow for double scoping
        if (DATA_HOLDER.vrSettings.seated && MC.player != null && MC.level != null &&
            TelescopeTracker.isTelescope(MC.player.getUseItem()) &&
            TelescopeTracker.isTelescope(c == 0 ? MC.player.getMainHandItem() : MC.player.getOffhandItem()))
        {
            poseStack.mulPoseMatrix(
                MathUtils.toMcMat4(DATA_HOLDER.vrPlayer.vrdata_world_render.hmd.getMatrix().invert().transpose()));
            poseStack.mulPose(com.mojang.math.Vector3f.XP.rotationDegrees(90F));
            // move to the eye center, seems to be magic numbers that work for the vive at least
            poseStack.translate((c == (DATA_HOLDER.vrSettings.reverseHands ? 1 : 0) ? 0.075F : -0.075F) * sc,
                -0.025F * sc,
                0.0325F * sc);
        } else {
            poseStack.mulPoseMatrix(MathUtils.toMcMat4(
                DATA_HOLDER.vrPlayer.vrdata_world_render.getController(c).getMatrix().invert().transpose()));
        }

        poseStack.scale(sc, sc, sc);
    }

    /**
     * stores the current render state and sets it up for polygon rendering
     * TODO: remove legacy stuff
     *
     * @param enable if true: stores the old state and sets up polyrending.
     *               if false: restores the previously stored render state.
     */
    public static void setupPolyRendering(boolean enable) {
        // boolean flag = Config.isShaders(); TODO
        boolean flag = false;

        if (enable) {
            POLY_BLEND_SRC_A = GlStateManager.BLEND.srcAlpha;
            POLY_BLEND_DST_A = GlStateManager.BLEND.dstAlpha;
            POLY_BLEND_SRC_RGB = GlStateManager.BLEND.srcRgb;
            POLY_BLEND_DST_RGB = GlStateManager.BLEND.dstRgb;
            POLY_BLEND = GL11C.glIsEnabled(GL11C.GL_BLEND);
            POLY_TEX = true;
            POLY_LIGHT = false;
            POLY_CULL = true;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableTexture();
            // GlStateManager._disableLighting();
            RenderSystem.disableCull();

            if (flag) {
                // this.prog = Shaders.activeProgram; TODO
                // Shaders.useProgram(Shaders.ProgramTexturedLit);
            }
        } else {
            RenderSystem.blendFuncSeparate(POLY_BLEND_SRC_RGB, POLY_BLEND_DST_RGB, POLY_BLEND_SRC_A,
                POLY_BLEND_DST_A);

            if (!POLY_BLEND) {
                RenderSystem.disableBlend();
            }

            if (POLY_TEX) {
                RenderSystem.enableTexture();
            }

            if (POLY_LIGHT) {
                // GlStateManager._enableLighting();
            }

            if (POLY_CULL) {
                RenderSystem.enableCull();
            }

            // if (flag && this.polytex) {
            //     Shaders.useProgram(this.prog); TODO
            // }
        }
    }

    /**
     * renders the given screen to the current main target and generates mipmaps for it
     *
     * @param partialTick partial tick for the screen rendering
     * @param screen      the Screen to render
     * @param maxGuiScale if set, renders the screen at max gui scale
     */
    public static void drawScreen(float partialTick, Screen screen, boolean maxGuiScale) {
        // setup modelview for screen rendering
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.setIdentity();
        poseStack.translate(0.0F, 0.0F, -2000.0F);
        RenderSystem.applyModelViewMatrix();

        double guiScale = maxGuiScale ? GuiHandler.GUI_SCALE_FACTOR_MAX : MC.getWindow().getGuiScale();

        // set gui scale to make the scissor work, that checks the window gui scale
        int backupGuiScale = GuiHandler.GUI_SCALE_FACTOR;
        GuiHandler.GUI_SCALE_FACTOR = (int) guiScale;

        Matrix4f guiProjection = Matrix4f.orthographic(
            0.0F, (float) (MC.getMainRenderTarget().width / guiScale),
            0.0F, (float) (MC.getMainRenderTarget().height / guiScale),
            1000.0F, 3000.0F);
        RenderSystem.setProjectionMatrix(guiProjection);

        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE);

        screen.render(new PoseStack(), 0, 0, partialTick);

        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE);

        // reset gui scale
        GuiHandler.GUI_SCALE_FACTOR = backupGuiScale;
        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();

        if (DATA_HOLDER.vrSettings.guiMipmaps) {
            // update mipmaps for Gui layer
            MC.mainRenderTarget.bindRead();
            GL30C.glGenerateMipmap(GL30C.GL_TEXTURE_2D);
            MC.mainRenderTarget.unbindRead();
        }
    }


    /**
     * draws the crosshair at the specified location on the screen
     *
     * @param poseStack GuiGraphics to render with
     * @param mouseX    x coordinate in screen pixel coordinates
     * @param mouseY    y coordinate in screen pixel coordinates
     */
    public static void drawMouseMenuQuad(PoseStack poseStack, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        // Turns out all we needed was some blendFuncSeparate magic :)
        // Also color DestFactor of ZERO produces better results with non-white crosshairs
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);

        float size = 15.0F * Math.max(ClientDataHolderVR.getInstance().vrSettings.menuCrosshairScale,
            1.0F / (float) MC.getWindow().getGuiScale());

        int x = (int) (mouseX - size * 0.5F + 1);
        int y = (int) (mouseY - size * 0.5F + 1);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, Gui.GUI_ICONS_LOCATION);

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(x, y, 0).uv(0, 0).endVertex();
        bufferBuilder.vertex(x, y + size, 0).uv(0, 15F / 265F).endVertex();
        bufferBuilder.vertex(x + size, y + size, 0).uv(15F / 265F, 15F / 265F).endVertex();
        bufferBuilder.vertex(x + size, y, 0).uv(15F / 265F, 0).endVertex();
        bufferBuilder.end();
        BufferUploader.end(bufferBuilder);

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * draws a quad with the PositionTex shader, to be used when <b>not</b> in a world
     *
     * @param displayWidth  texture width
     * @param displayHeight texture height
     * @param size          size of the quad
     * @param color         color of the quad, expects an array of length 4 for: r, g, b, a
     * @param matrix        matrix to position the screen with
     */
    public static void drawSizedQuad(
        float displayWidth, float displayHeight, float size, float[] color, Matrix4f matrix)
    {
        float sizeX = size * 0.5F;
        float sizeY = sizeX * displayHeight / displayWidth;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);

        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder
            .vertex(matrix, -sizeX, -sizeY, 0)
            .uv(0.0F, 0.0F)
            .endVertex();
        bufferbuilder
            .vertex(matrix, sizeX, -sizeY, 0)
            .uv(1.0F, 0.0F)
            .endVertex();
        bufferbuilder
            .vertex(matrix, sizeX, sizeY, 0)
            .uv(1.0F, 1.0F)
            .endVertex();
        bufferbuilder
            .vertex(matrix, -sizeX, sizeY, 0)
            .uv(0.0F, 1.0F)
            .endVertex();
        bufferbuilder.end();
        BufferUploader.end(bufferbuilder);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * draws a quad with the EntityCutout shader and no color modifier, to be used when <b>in</b> a world
     *
     * @param displayWidth  texture width
     * @param displayHeight texture height
     * @param size          size of the quad
     * @param packedLight   block and sky light packed into an int
     * @param matrix        matrix to use to
     * @param flipY         if the texture should be flipped vertically
     */
    public static void drawSizedQuadWithLightmapCutout(
        float displayWidth, float displayHeight, float size, int packedLight, Matrix4f matrix, boolean flipY)
    {
        drawSizedQuadWithLightmapCutout(displayWidth, displayHeight, size, packedLight, new float[]{1, 1, 1, 1}, matrix,
            flipY);
    }

    /**
     * draws a quad with the EntityCutout shader, to be used when <b>in</b> a world
     *
     * @param displayWidth  texture width
     * @param displayHeight texture height
     * @param size          size of the quad
     * @param packedLight   block and sky light packed into an int
     * @param color         color of the quad, expects an array of length 4 for: r, g, b, a
     * @param matrix        matrix to use to
     * @param flipY         if the texture should be flipped vertically
     */
    public static void drawSizedQuadWithLightmapCutout(
        float displayWidth, float displayHeight, float size, int packedLight, float[] color, Matrix4f matrix,
        boolean flipY)
    {
        drawSizedQuadWithLightmap(displayWidth, displayHeight, size, packedLight, color, matrix,
            GameRenderer::getRendertypeEntityCutoutNoCullShader, flipY);
    }

    /**
     * draws a quad with the EntitySolid shader at full brightness, to be used when <b>in</b> a world
     *
     * @param displayWidth  texture width
     * @param displayHeight texture height
     * @param size          size of the quad
     * @param color         color of the quad, expects an array of length 4 for: r, g, b, a
     * @param matrix        matrix to use to
     */
    public static void drawSizedQuadFullbrightSolid(
        float displayWidth, float displayHeight, float size, float[] color, Matrix4f matrix)
    {
        RenderSystem.disableBlend();
        drawSizedQuadWithLightmap(displayWidth, displayHeight, size, LightTexture.FULL_BRIGHT, color, matrix,
            GameRenderer::getRendertypeEntitySolidShader, false);
    }

    /**
     * draws a quad with the EntityCutout shader, to be used when <b>in</b> a world
     *
     * @param displayWidth  texture width
     * @param displayHeight texture height
     * @param size          size of the quad
     * @param packedLight   block and sky light packed into an int
     * @param color         color of the quad, expects an array of length 4 for: r, g, b, a
     * @param matrix        matrix to use to for positioning
     * @param shader        a shader supplier dor what shader to use, needs to be one of the entity shaders
     * @param flipY         if the texture should be flipped vertically
     */
    public static void drawSizedQuadWithLightmap(
        float displayWidth, float displayHeight, float size, int packedLight, float[] color, Matrix4f matrix,
        Supplier<ShaderInstance> shader, boolean flipY)
    {
        float sizeX = size * 0.5F;
        float sizeY = sizeX * displayHeight / displayWidth;

        RenderSystem.setShader(shader);
        MC.gameRenderer.lightTexture().turnOnLightLayer();
        MC.gameRenderer.overlayTexture().setupOverlayColor();
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);

        // store old lights
        com.mojang.math.Vector3f light0Old = RenderSystemAccessor.getShaderLightDirections()[0];
        com.mojang.math.Vector3f light1Old = RenderSystemAccessor.getShaderLightDirections()[1];

        // set lights to front
        RenderSystem.setShaderLights(new com.mojang.math.Vector3f(0, 0, 1), new com.mojang.math.Vector3f(0, 0, 1));
        RenderSystem.setupShaderLights(RenderSystem.getShader());

        bufferbuilder.vertex(matrix, -sizeX, -sizeY, 0)
            .color(color[0], color[1], color[2], color[3])
            .uv(0.0F, flipY ? 1.0F : 0.0F)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
            .normal(0, 0, 1)
            .endVertex();
        bufferbuilder.vertex(matrix, sizeX, -sizeY, 0)
            .color(color[0], color[1], color[2], color[3])
            .uv(1.0F, flipY ? 1.0F : 0.0F)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
            .normal(0, 0, 1)
            .endVertex();
        bufferbuilder.vertex(matrix, sizeX, sizeY, 0)
            .color(color[0], color[1], color[2], color[3])
            .uv(1.0F, flipY ? 0.0F : 1.0F)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
            .normal(0, 0, 1)
            .endVertex();
        bufferbuilder.vertex(matrix, -sizeX, sizeY, 0)
            .color(color[0], color[1], color[2], color[3])
            .uv(0.0F, flipY ? 0.0F : 1.0F)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight)
            .normal(0, 0, 1)
            .endVertex();
        bufferbuilder.end();
        BufferUploader.end(bufferbuilder);

        MC.gameRenderer.lightTexture().turnOffLightLayer();

        // reset lights
        if (light0Old != null && light1Old != null) {
            RenderSystem.setShaderLights(light0Old, light1Old);
            RenderSystem.setupShaderLights(RenderSystem.getShader());
        }
    }

    /**
     * draws a colored quad
     *
     * @param pos       center position of the quad
     * @param width     width of the quad
     * @param height    height of the quad
     * @param yaw       y rotation of the quad
     * @param r         red 0-255
     * @param g         green 0-255
     * @param b         blue 0-255
     * @param a         alpha 0-255
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderFlatQuad(
        Vec3 pos, float width, float height, float yaw, int r, int g, int b, int a, PoseStack poseStack)
    {
        Tesselator tesselator = Tesselator.getInstance();
        tesselator.getBuilder().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);

        Vec3 offset = (new Vec3(width * 0.5F, 0.0, height * 0.5F))
            .yRot(Mth.DEG_TO_RAD * -yaw);

        Matrix4f matrix = poseStack.last().pose();

        tesselator.getBuilder().vertex(matrix, (float) (pos.x + offset.x), (float) pos.y, (float) (pos.z + offset.z))
            .color(r, g, b, a).normal(0.0F, 1.0F, 0.0F).endVertex();
        tesselator.getBuilder().vertex(matrix, (float) (pos.x + offset.x), (float) pos.y, (float) (pos.z - offset.z))
            .color(r, g, b, a).normal(0.0F, 1.0F, 0.0F).endVertex();
        tesselator.getBuilder().vertex(matrix, (float) (pos.x - offset.x), (float) pos.y, (float) (pos.z - offset.z))
            .color(r, g, b, a).normal(0.0F, 1.0F, 0.0F).endVertex();
        tesselator.getBuilder().vertex(matrix, (float) (pos.x - offset.x), (float) pos.y, (float) (pos.z + offset.z))
            .color(r, g, b, a).normal(0.0F, 1.0F, 0.0F).endVertex();
        tesselator.end();
    }

    /**
     * adds a box to the given Tesselator
     *
     * @param consumer  VertexConsumer to use
     * @param start     start of the box, combined with end gives the axis the box is on
     * @param end       end of the box, combined with start gives the axis the box is on
     * @param xSize     X size of the box
     * @param ySize     Y size of the box
     * @param color     color of the box 0-255 per component
     * @param alpha     transparency of the box 0-255
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderBox(
        VertexConsumer consumer, Vec3 start, Vec3 end, float xSize, float ySize, Vec3i color, byte alpha,
        PoseStack poseStack)
    {
        renderBox(consumer, start, end, -xSize * 0.5F, xSize * 0.5F, -ySize * 0.5F, ySize * 0.5F, color, alpha,
            poseStack);
    }

    /**
     * adds a box to the given Tesselator
     *
     * @param consumer  VertexConsumer to use
     * @param start     start of the box, combined with end gives the axis the box is on
     * @param end       end of the box, combined with start gives the axis the box is on
     * @param minX      X- size of the box
     * @param maxX      X+ size of the box
     * @param minY      Y- size of the box
     * @param maxY      Y+ size of the box
     * @param color     color of the box 0-255 per component
     * @param alpha     transparency of the box 0-255
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderBox(
        VertexConsumer consumer, Vec3 start, Vec3 end, float minX, float maxX, float minY, float maxY, Vec3i color,
        byte alpha, PoseStack poseStack)
    {
        Vec3 forward = start.subtract(end).normalize();
        Vec3 right = forward.cross(MathUtils.UP_D);
        if (right.lengthSqr() == 0) {
            right = MathUtils.LEFT_D;
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward);

        Vec3 left = right.scale(minX);
        right = right.scale(maxX);

        Vec3 down = up.scale(minY);
        up = up.scale(maxY);

        Vec3 upNormal = up.normalize();
        Vec3 rightNormal = right.normalize();

        Vec3 backRightBottom = start.add(right.x + down.x, right.y + down.y, right.z + down.z);
        Vec3 backRightTop = start.add(right.x + up.x, right.y + up.y, right.z + up.z);
        Vec3 backLeftBottom = start.add(left.x + down.x, left.y + down.y, left.z + down.z);
        Vec3 backLeftTop = start.add(left.x + up.x, left.y + up.y, left.z + up.z);

        Vec3 frontRightBottom = end.add(right.x + down.x, right.y + down.y, right.z + down.z);
        Vec3 frontRightTop = end.add(right.x + up.x, right.y + up.y, right.z + up.z);
        Vec3 frontLeftBottom = end.add(left.x + down.x, left.y + down.y, left.z + down.z);
        Vec3 frontLeftTop = end.add(left.x + up.x, left.y + up.y, left.z + up.z);

        Matrix4f matrix = poseStack.last().pose();

        addVertex(consumer, matrix, backRightBottom, color, alpha, forward);
        addVertex(consumer, matrix, backLeftBottom, color, alpha, forward);
        addVertex(consumer, matrix, backLeftTop, color, alpha, forward);
        addVertex(consumer, matrix, backRightTop, color, alpha, forward);

        forward.reverse();
        addVertex(consumer, matrix, frontLeftBottom, color, alpha, forward);
        addVertex(consumer, matrix, frontRightBottom, color, alpha, forward);
        addVertex(consumer, matrix, frontRightTop, color, alpha, forward);
        addVertex(consumer, matrix, frontLeftTop, color, alpha, forward);

        addVertex(consumer, matrix, frontRightBottom, color, alpha, rightNormal);
        addVertex(consumer, matrix, backRightBottom, color, alpha, rightNormal);
        addVertex(consumer, matrix, backRightTop, color, alpha, rightNormal);
        addVertex(consumer, matrix, frontRightTop, color, alpha, rightNormal);

        rightNormal.reverse();
        addVertex(consumer, matrix, backLeftBottom, color, alpha, rightNormal);
        addVertex(consumer, matrix, frontLeftBottom, color, alpha, rightNormal);
        addVertex(consumer, matrix, frontLeftTop, color, alpha, rightNormal);
        addVertex(consumer, matrix, backLeftTop, color, alpha, rightNormal);

        addVertex(consumer, matrix, backLeftTop, color, alpha, upNormal);
        addVertex(consumer, matrix, frontLeftTop, color, alpha, upNormal);
        addVertex(consumer, matrix, frontRightTop, color, alpha, upNormal);
        addVertex(consumer, matrix, backRightTop, color, alpha, upNormal);

        upNormal.reverse();
        addVertex(consumer, matrix, frontLeftBottom, color, alpha, upNormal);
        addVertex(consumer, matrix, backLeftBottom, color, alpha, upNormal);
        addVertex(consumer, matrix, backRightBottom, color, alpha, upNormal);
        addVertex(consumer, matrix, frontRightBottom, color, alpha, upNormal);
    }

    /**
     * adds a Vertex with the DefaultVertexFormat.POSITION_COLOR_NORMAL format to the buffer builder
     *
     * @param consumer BufferBuilder to add the vertex to
     * @param matrix   matrix to use for positioning the vertex
     * @param pos      position of the vertex
     * @param color    color of the vertex 0-255
     * @param alpha    transparency of the vertex 0-255
     * @param normal   normal of the vertex
     */
    private static void addVertex(
        VertexConsumer consumer, Matrix4f matrix, Vec3 pos, Vec3i color, int alpha, Vec3 normal)
    {
        consumer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
            .color(color.getX(), color.getY(), color.getZ(), alpha)
            .normal((float) normal.x, (float) normal.y, (float) normal.z)
            .endVertex();
    }

    /**
     * checks if there were any opengl errors since this was last called
     *
     * @param errorSection name of the section that is checked, this gets logged if there are any errors
     * @return error string if there was one
     */
    public static String checkGLError(String errorSection) {
        int error = GlStateManager._getError();
        if (error != 0) {
            String errorString = switch (error) {
                case GL11C.GL_INVALID_ENUM -> "invalid enum";
                case GL11C.GL_INVALID_VALUE -> "invalid value";
                case GL11C.GL_INVALID_OPERATION -> "invalid operation";
                case GL11C.GL_STACK_OVERFLOW -> "stack overflow";
                case GL11C.GL_STACK_UNDERFLOW -> "stack underflow";
                case GL11C.GL_OUT_OF_MEMORY -> "out of memory";
                case GL30C.GL_INVALID_FRAMEBUFFER_OPERATION -> "framebuffer is not complete";
                default -> "unknown error";
            };
            VRSettings.LOGGER.error("Vivecraft: ########## GL ERROR ##########");
            VRSettings.LOGGER.error("Vivecraft: @ {}", errorSection);
            VRSettings.LOGGER.error("Vivecraft: {}: {}", error, errorString);
            return errorString;
        } else {
            return "";
        }
    }
}
