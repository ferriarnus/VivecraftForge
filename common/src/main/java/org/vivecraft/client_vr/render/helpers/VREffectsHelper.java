package org.vivecraft.client_vr.render.helpers;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Triple;
import org.joml.*;
import org.lwjgl.opengl.GL11C;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.Xevents;
import org.vivecraft.client.gui.VivecraftClickEvent;
import org.vivecraft.client.gui.settings.GuiOtherHUDSettings;
import org.vivecraft.client.gui.settings.GuiRenderOpticsSettings;
import org.vivecraft.client.utils.ClientUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.MethodHolder;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.extensions.LevelRendererExtension;
import org.vivecraft.client_vr.extensions.PlayerExtension;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.gameplay.trackers.TelescopeTracker;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.mod_compat_vr.ShadersHelper;
import org.vivecraft.mod_compat_vr.immersiveportals.ImmersivePortalsHelper;
import org.vivecraft.mod_compat_vr.optifine.OptifineHelper;

import java.lang.Math;
import java.util.Calendar;
import java.util.Optional;
import java.util.stream.Stream;

public class VREffectsHelper {

    private static final ClientDataHolderVR DATA_HOLDER = ClientDataHolderVR.getInstance();
    private static final Minecraft MC = Minecraft.getInstance();

    /**
     * checks if the given position is inside a block that blocks vision
     * @param pos position to check
     * @return if vision is blocked
     */
    public static boolean isInsideOpaqueBlock(Vec3 pos) {
        if (MC.level == null) {
            return false;
        } else {
            BlockPos blockpos = BlockPos.containing(pos);
            return MC.level.getBlockState(blockpos).isSolidRender(MC.level, blockpos);
        }
    }

    /**
     * check if the given position is 'dist' near a block that blocks vision
     * @param pos position to check
     * @param dist distance where it should still count as inside the block
     * @return null if there is no block, else a triple containing 1.0F,
     *          BlockState and BlockPos of the blocking block
     */
    public static Triple<Float, BlockState, BlockPos> getNearOpaqueBlock(Vec3 pos, double dist) {
        if (MC.level == null) {
            return null;
        } else {
            AABB aabb = new AABB(pos.subtract(dist, dist, dist), pos.add(dist, dist, dist));
            Stream<BlockPos> stream = BlockPos.betweenClosedStream(aabb).filter((bp) ->
                MC.level.getBlockState(bp).isSolidRender(MC.level, bp));
            Optional<BlockPos> optional = stream.findFirst();
            return optional.map(blockPos -> Triple.of(1.0F, MC.level.getBlockState(blockPos), blockPos)).orElse(null);
        }
    }

    /**
     * draws the spyglass view of the given controller
     * @param poseStack PoseStack for positioning
     * @param c controller index for the scope
     */
    public static void drawScopeFB(PoseStack poseStack, int c) {
        poseStack.pushPose();
        RenderSystem.enableDepthTest();

        if (c == 0) {
            DATA_HOLDER.vrRenderer.telescopeFramebufferR.bindRead();
            RenderSystem.setShaderTexture(0, DATA_HOLDER.vrRenderer.telescopeFramebufferR.getColorTextureId());
        } else {
            DATA_HOLDER.vrRenderer.telescopeFramebufferL.bindRead();
            RenderSystem.setShaderTexture(0, DATA_HOLDER.vrRenderer.telescopeFramebufferL.getColorTextureId());
        }

        // size of the back of the spyglass 2/16
        float scale = 0.125F;

        float alpha = TelescopeTracker.viewPercent(c);
        // draw spyglass view
        RenderHelper.drawSizedQuadFullbrightSolid(720.0F, 720.0F, scale, new float[]{alpha, alpha, alpha, 1}, poseStack.last().pose());

        // draw spyglass overlay
        RenderSystem.setShaderTexture(0, new ResourceLocation("textures/misc/spyglass_scope.png"));
        RenderSystem.enableBlend();
        // slight offset to not cause z fighting
        poseStack.translate(0.0F, 0.0F, 0.00001F);
        // get light at the controller position
        int light = LevelRenderer.getLightColor(MC.level, BlockPos.containing(
            DATA_HOLDER.vrPlayer.vrdata_world_render.getController(c).getPosition()));
        // draw the overlay, and flip it vertically
        RenderHelper.drawSizedQuadWithLightmapCutout(720.0F, 720.0F, scale, light, poseStack.last().pose(), true);

        poseStack.popPose();
    }

    private static boolean WAS_STENCIL_ON;

    /**
     * enables stencil test, and draws the stencil, if enabled for the current RenderPass
     */
    public static void drawEyeStencil() {
        if (DATA_HOLDER.vrSettings.vrUseStencil) {
            WAS_STENCIL_ON = GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST);
            if (WAS_STENCIL_ON && !DATA_HOLDER.showedStencilMessage && DATA_HOLDER.vrSettings.showChatMessageStencil) {
                DATA_HOLDER.showedStencilMessage = true;
                MC.gui.getChat().addMessage(Component.translatable("vivecraft.messages.stencil",
                    Component.translatable("vivecraft.messages.3options",
                            Component.translatable("options.title"),
                            Component.translatable("vivecraft.options.screen.main"),
                            Component.translatable("vivecraft.options.screen.stereorendering"))
                        .withStyle(style -> style.withClickEvent(new VivecraftClickEvent(VivecraftClickEvent.VivecraftAction.OPEN_SCREEN, new GuiRenderOpticsSettings(null)))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("vivecraft.messages.openSettings")))
                            .withColor(ChatFormatting.GREEN)
                            .withItalic(true)),
                    Component.translatable("vivecraft.messages.3options",
                            Component.translatable("options.title"),
                            Component.translatable("vivecraft.options.screen.main"),
                            Component.translatable("vivecraft.options.screen.guiother"))
                        .withStyle(style -> style.withClickEvent(new VivecraftClickEvent(VivecraftClickEvent.VivecraftAction.OPEN_SCREEN, new GuiOtherHUDSettings(null)))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("vivecraft.messages.openSettings")))
                            .withColor(ChatFormatting.GREEN)
                            .withItalic(true))
                ));
            }

            // don't touch the stencil if we don't use it
            // stencil only for left/right VR view
            if ((DATA_HOLDER.currentPass == RenderPass.LEFT || DATA_HOLDER.currentPass == RenderPass.RIGHT) &&
                (!ImmersivePortalsHelper.isLoaded() || !ImmersivePortalsHelper.isRenderingPortal()))
            {
                DATA_HOLDER.vrRenderer.doStencil(false);
            }
        }
    }

    /**
     * disables the stencil pass if it was enabled by us
     */
    public static void disableStencilTest() {
        // if we did enable the stencil test, disable it
        if (!WAS_STENCIL_ON) {
            GL11C.glDisable(GL11C.GL_STENCIL_TEST);
        }
    }

    // textures for the panorama menu
    private static final ResourceLocation CUBE_FRONT = new ResourceLocation("textures/gui/title/background/panorama_0.png");
    private static final ResourceLocation CUBE_RIGHT = new ResourceLocation("textures/gui/title/background/panorama_1.png");
    private static final ResourceLocation CUBE_BACK = new ResourceLocation("textures/gui/title/background/panorama_2.png");
    private static final ResourceLocation CUBE_LEFT = new ResourceLocation("textures/gui/title/background/panorama_3.png");
    private static final ResourceLocation CUBE_UP = new ResourceLocation("textures/gui/title/background/panorama_4.png");
    private static final ResourceLocation CUBE_DOWN = new ResourceLocation("textures/gui/title/background/panorama_5.png");
    private static final ResourceLocation DIRT = new ResourceLocation("minecraft:textures/block/dirt.png");
    private static final ResourceLocation GRASS = new ResourceLocation("minecraft:textures/block/grass_block_top.png");

    /**
     * renders a 100^3 cubemap and a dirt/grass floor
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderMenuPanorama(PoseStack poseStack) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.clear(GL11C.GL_COLOR_BUFFER_BIT | GL11C.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);

        poseStack.pushPose();

        // translate by half of the cube size
        poseStack.translate(-50F, -50F, -50.0F);

        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();

        Matrix4f matrix = poseStack.last().pose();

        // down
        RenderSystem.setShaderTexture(0, CUBE_DOWN);
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        bufferbuilder.vertex(matrix, 0, 0, 0)
            .uv(0, 0).color(255, 255, 255, 255).normal(0, 1, 0).endVertex();
        bufferbuilder.vertex(matrix, 0, 0, 100)
            .uv(0, 1).color(255, 255, 255, 255).normal(0, 1, 0).endVertex();
        bufferbuilder.vertex(matrix, 100, 0, 100)
            .uv(1, 1).color(255, 255, 255, 255).normal(0, 1, 0).endVertex();
        bufferbuilder.vertex(matrix, 100, 0, 0)
            .uv(1, 0).color(255, 255, 255, 255).normal(0, 1, 0).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());

        // up
        RenderSystem.setShaderTexture(0, CUBE_UP);
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        bufferbuilder.vertex(matrix, 0, 100, 100)
            .uv(0, 0).color(255, 255, 255, 255).normal(0, -1, 0).endVertex();
        bufferbuilder.vertex(matrix, 0, 100, 0)
            .uv(0, 1).color(255, 255, 255, 255).normal(0, -1, 0).endVertex();
        bufferbuilder.vertex(matrix, 100, 100, 0)
            .uv(1, 1).color(255, 255, 255, 255).normal(0, -1, 0).endVertex();
        bufferbuilder.vertex(matrix, 100, 100, 100)
            .uv(1, 0).color(255, 255, 255, 255).normal(0, -1, 0).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());

        // left
        RenderSystem.setShaderTexture(0, CUBE_LEFT);
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        bufferbuilder.vertex(matrix, 0, 0, 0)
            .uv(1, 1).color(255, 255, 255, 255).normal(1, 0, 0).endVertex();
        bufferbuilder.vertex(matrix, 0, 100, 0)
            .uv(1, 0).color(255, 255, 255, 255).normal(1, 0, 0).endVertex();
        bufferbuilder.vertex(matrix, 0, 100, 100)
            .uv(0, 0).color(255, 255, 255, 255).normal(1, 0, 0).endVertex();
        bufferbuilder.vertex(matrix, 0, 0, 100)
            .uv(0, 1).color(255, 255, 255, 255).normal(1, 0, 0).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());

        // right
        RenderSystem.setShaderTexture(0, CUBE_RIGHT);
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        bufferbuilder.vertex(matrix, 100, 0, 0)
            .uv(0, 1).color(255, 255, 255, 255).normal(-1, 0, 0).endVertex();
        bufferbuilder.vertex(matrix, 100, 0, 100)
            .uv(1, 1).color(255, 255, 255, 255).normal(-1, 0, 0).endVertex();
        bufferbuilder.vertex(matrix, 100, 100, 100)
            .uv(1, 0).color(255, 255, 255, 255).normal(-1, 0, 0).endVertex();
        bufferbuilder.vertex(matrix, 100, 100, 0)
            .uv(0, 0).color(255, 255, 255, 255).normal(-1, 0, 0).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());

        // front
        RenderSystem.setShaderTexture(0, CUBE_FRONT);
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        bufferbuilder.vertex(matrix, 0, 0, 0)
            .uv(0, 1).color(255, 255, 255, 255).normal(0, 0, 1).endVertex();
        bufferbuilder.vertex(matrix, 100, 0, 0)
            .uv(1, 1).color(255, 255, 255, 255).normal(0, 0, 1).endVertex();
        bufferbuilder.vertex(matrix, 100, 100, 0)
            .uv(1, 0).color(255, 255, 255, 255).normal(0, 0, 1).endVertex();
        bufferbuilder.vertex(matrix, 0, 100, 0)
            .uv(0, 0).color(255, 255, 255, 255).normal(0, 0, 1).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());

        //back
        RenderSystem.setShaderTexture(0, CUBE_BACK);
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        bufferbuilder.vertex(matrix, 0, 0, 100).
            uv(1, 1).color(255, 255, 255, 255).normal(0, 0, -1).endVertex();
        bufferbuilder.vertex(matrix, 0, 100, 100)
            .uv(1, 0).color(255, 255, 255, 255).normal(0, 0, -1).endVertex();
        bufferbuilder.vertex(matrix, 100, 100, 100)
            .uv(0, 0).color(255, 255, 255, 255).normal(0, 0, -1).endVertex();
        bufferbuilder.vertex(matrix, 100, 0, 100)
            .uv(0, 1).color(255, 255, 255, 255).normal(0, 0, -1).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());

        poseStack.popPose();

        // render floor
        Vector2fc area = DATA_HOLDER.vr.getPlayAreaSize();
        if (area == null) {
            area = new Vector2f(2, 2);
        }
        // render two floors, grass at room size, and dirt a bit bigger
        for (int i = 0; i < 2; i++) {
            float width = area.x() + i * 2;
            float length = area.y() + i * 2;

            poseStack.pushPose();
            RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);

            int r, g, b;
            if (i == 0) {
                RenderSystem.setShaderTexture(0, GRASS);
                // plains grass color, but a bit darker
                r = 114;
                g = 148;
                b = 70;
            } else {
                RenderSystem.setShaderTexture(0, DIRT);
                r = g = b = 128;
            }
            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);

            // offset so the floor is centered
            poseStack.translate(-width * 0.5F, 0.0F, -length * 0.5F);

            matrix = poseStack.last().pose();

            final int repeat = 4; // texture wraps per meter

            bufferbuilder
                .vertex(matrix, 0, 0.005f * -i, 0)
                .uv(0, 0)
                .color(r, g, b, 255)
                .normal(0, 1, 0).endVertex();
            bufferbuilder
                .vertex(matrix, 0, 0.005f * -i, length)
                .uv(0, repeat * length)
                .color(r, g, b, 255)
                .normal(0, 1, 0).endVertex();
            bufferbuilder
                .vertex(matrix, width, 0.005f * -i, length)
                .uv(repeat * width, repeat * length)
                .color(r, g, b, 255)
                .normal(0, 1, 0).endVertex();
            bufferbuilder
                .vertex(matrix, width, 0.005f * -i, 0)
                .uv(repeat * width, 0)
                .color(r, g, b, 255)
                .normal(0, 1, 0).endVertex();

            BufferUploader.drawWithShader(bufferbuilder.end());
            poseStack.popPose();
        }
    }

    /**
     * renders a dirt cube, slightly bigger than the room size
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderJrbuddasAwesomeMainMenuRoomNew(PoseStack poseStack) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.clear(GL11C.GL_COLOR_BUFFER_BIT | GL11C.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, Screen.BACKGROUND_LOCATION);

        int repeat = 4; // texture wraps per meter
        float height = 2.5F;
        float oversize = 1.3F; // how much bigger than the room

        Vector2fc area = DATA_HOLDER.vr.getPlayAreaSize();
        if (area == null) {
            area = new Vector2f(2, 2);
        }

        float width = area.x() + oversize;
        float length = area.y() + oversize;

        float r, g, b, a;
        r = g = b = 0.8f;
        a = 1.0f;

        poseStack.pushPose();

        // offset so the room is centered
        poseStack.translate(-width * 0.5F, 0.0F, -length * 0.5F);

        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();

        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);

        Matrix4f matrix = poseStack.last().pose();

        // floor
        bufferbuilder.vertex(matrix, 0, 0, 0)
            .uv(0, 0).color(r, g, b, a).normal(0, 1, 0).endVertex();
        bufferbuilder.vertex(matrix, 0, 0, length)
            .uv(0, repeat * length).color(r, g, b, a).normal(0, 1, 0).endVertex();
        bufferbuilder.vertex(matrix, width, 0, length)
            .uv(repeat * width, repeat * length).color(r, g, b, a).normal(0, 1, 0).endVertex();
        bufferbuilder.vertex(matrix, width, 0, 0)
            .uv(repeat * width, 0).color(r, g, b, a).normal(0, 1, 0).endVertex();

        // ceiling
        bufferbuilder.vertex(matrix, 0, height, length)
            .uv(0, 0).color(r, g, b, a).normal(0, -1, 0).endVertex();
        bufferbuilder.vertex(matrix, 0, height, 0)
            .uv(0, repeat * length).color(r, g, b, a).normal(0, -1, 0).endVertex();
        bufferbuilder.vertex(matrix, width, height, 0)
            .uv(repeat * width, repeat * length).color(r, g, b, a).normal(0, -1, 0).endVertex();
        bufferbuilder.vertex(matrix, width, height, length)
            .uv(repeat * width, 0).color(r, g, b, a).normal(0, -1, 0).endVertex();

        // left
        bufferbuilder.vertex(matrix, 0, 0, 0)
            .uv(0, 0).color(r, g, b, a).normal(1, 0, 0).endVertex();
        bufferbuilder.vertex(matrix, 0, height, 0)
            .uv(0, repeat * height).color(r, g, b, a).normal(1, 0, 0).endVertex();
        bufferbuilder.vertex(matrix, 0, height, length)
            .uv(repeat * length, repeat * height).color(r, g, b, a).normal(1, 0, 0).endVertex();
        bufferbuilder.vertex(matrix, 0, 0, length)
            .uv(repeat * length, 0).color(r, g, b, a).normal(1, 0, 0).endVertex();

        // right
        bufferbuilder.vertex(matrix, width, 0, 0)
            .uv(0, 0).color(r, g, b, a).normal(-1, 0, 0).endVertex();
        bufferbuilder.vertex(matrix, width, 0, length)
            .uv(repeat * length, 0).color(r, g, b, a).normal(-1, 0, 0).endVertex();
        bufferbuilder.vertex(matrix, width, height, length)
            .uv(repeat * length, repeat * height).color(r, g, b, a).normal(-1, 0, 0).endVertex();
        bufferbuilder.vertex(matrix, width, height, 0)
            .uv(0, repeat * height).color(r, g, b, a).normal(-1, 0, 0).endVertex();

        // front
        bufferbuilder.vertex(matrix, 0, 0, 0)
            .uv(0, 0).color(r, g, b, a).normal(0, 0, 1).endVertex();
        bufferbuilder.vertex(matrix, width, 0, 0)
            .uv(repeat * width, 0).color(r, g, b, a).normal(0, 0, 1).endVertex();
        bufferbuilder.vertex(matrix, width, height, 0)
            .uv(repeat * width, repeat * height).color(r, g, b, a).normal(0, 0, 1).endVertex();
        bufferbuilder.vertex(matrix, 0, height, 0)
            .uv(0, repeat * height).color(r, g, b, a).normal(0, 0, 1).endVertex();

        // back
        bufferbuilder.vertex(matrix, 0, 0, length).
            uv(0, 0).color(r, g, b, a).normal(0, 0, -1).endVertex();
        bufferbuilder.vertex(matrix, 0, height, length)
            .uv(0, repeat * height).color(r, g, b, a).normal(0, 0, -1).endVertex();
        bufferbuilder.vertex(matrix, width, height, length)
            .uv(repeat * width, repeat * height).color(r, g, b, a).normal(0, 0, -1).endVertex();
        bufferbuilder.vertex(matrix, width, 0, length)
            .uv(repeat * width, 0).color(r, g, b, a).normal(0, 0, -1).endVertex();

        BufferUploader.drawWithShader(bufferbuilder.end());
        poseStack.popPose();
    }

    /**
     * renders the loaded menuworld and a room floor quad
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderTechjarsAwesomeMainMenuRoom(PoseStack poseStack) {
        RenderSystem.enableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // use irl time for sky, or fast forward
        int tzOffset = Calendar.getInstance().get(Calendar.ZONE_OFFSET);
        DATA_HOLDER.menuWorldRenderer.time = DATA_HOLDER.menuWorldRenderer.fastTime ?
            (long) (DATA_HOLDER.menuWorldRenderer.ticks * 10L + 10.0F * MC.getFrameTime()) :
            (long) ((System.currentTimeMillis() + tzOffset - 21600000) / 86400000D * 24000D);

        // clear sky
        DATA_HOLDER.menuWorldRenderer.fogRenderer.setupFogColor();
        RenderSystem.clear(GL11C.GL_COLOR_BUFFER_BIT | GL11C.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        DATA_HOLDER.menuWorldRenderer.updateLightmap();
        // render world
        DATA_HOLDER.menuWorldRenderer.render(poseStack);

        // render room floor
        Vector2fc area = DATA_HOLDER.vr.getPlayAreaSize();
        if (area == null) {
            area = new Vector2f(2, 2);
        }

        float width = area.x();
        float length = area.y();

        RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);
        RenderSystem.setShaderTexture(0, Screen.BACKGROUND_LOCATION);
        float sun = DATA_HOLDER.menuWorldRenderer.getSkyDarken();
        RenderSystem.setShaderColor(sun, sun, sun, 0.3f);

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();

        poseStack.pushPose();

        poseStack.translate(-width / 2.0F, 0.0F, -length / 2.0F);

        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();

        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);

        Matrix4f matrix = poseStack.last().pose();

        bufferbuilder
            .vertex(matrix, 0, 0.005f, 0)
            .uv(0, 0)
            .color(1f, 1f, 1f, 1f)
            .normal(0, 1, 0).endVertex();
        bufferbuilder
            .vertex(matrix, 0, 0.005f, length)
            .uv(0, 4 * length)
            .color(1f, 1f, 1f, 1f)
            .normal(0, 1, 0).endVertex();
        bufferbuilder
            .vertex(matrix, width, 0.005f, length)
            .uv(4 * width, 4 * length)
            .color(1f, 1f, 1f, 1f)
            .normal(0, 1, 0).endVertex();
        bufferbuilder
            .vertex(matrix, width, 0.005f, 0)
            .uv(4 * width, 0)
            .color(1f, 1f, 1f, 1f)
            .normal(0, 1, 0).endVertex();

        BufferUploader.drawWithShader(bufferbuilder.end());

        poseStack.popPose();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.defaultBlendFunc();
    }

    /**
     * renders the vivecraft stuff into separate buffers for the fabulous settings
     * this includes hands, vr shadow, gui, camera widgets and other stuff
     * @param partialTick current partial tick
     * @param levelRenderer LevelRenderer that holds the framebuffers for rendering
     * @param menuHandRight if the right hand should be a menu hand
     * @param menuHandLeft if the left hand should be a menu hand
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderVRFabulous(float partialTick, LevelRenderer levelRenderer, boolean menuHandRight, boolean menuHandLeft, PoseStack poseStack) {
        if (DATA_HOLDER.currentPass == RenderPass.SCOPEL || DATA_HOLDER.currentPass == RenderPass.SCOPER) {
            // skip for spyglass
            return;
        }

        // make sure other stuff is finished drawing, or they will render on our buffers.
        // mainly an issue with iris and the crumbling effect.
        MC.renderBuffers().bufferSource().endBatch();

        MC.getProfiler().popPush("VR");
        renderCrosshairAtDepth(!DATA_HOLDER.vrSettings.useCrosshairOcclusion, poseStack);

        // switch to VR Occluded buffer, and copy main depth for occlusion
        MC.getMainRenderTarget().unbindWrite();
        RenderTarget occluded = ((LevelRendererExtension) levelRenderer).vivecraft$getAlphaSortVROccludedFramebuffer();
        occluded.clear(Minecraft.ON_OSX);
        occluded.copyDepthFrom(MC.getMainRenderTarget());
        occluded.bindWrite(true);

        if (shouldOccludeGui()) {
            renderGuiAndShadow(poseStack, partialTick, false, false);
        }

        // switch to VR UnOccluded buffer, no depth copy
        RenderTarget unOccluded = ((LevelRendererExtension) levelRenderer).vivecraft$getAlphaSortVRUnoccludedFramebuffer();
        unOccluded.clear(Minecraft.ON_OSX);
        unOccluded.bindWrite(true);

        if (!shouldOccludeGui()) {
            renderGuiAndShadow(poseStack, partialTick, false, false);
        }

        renderVRSelfEffects(partialTick);
        VRWidgetHelper.renderVRThirdPersonCamWidget();
        VRWidgetHelper.renderVRHandheldCameraWidget();

        boolean renderHands = VRArmHelper.shouldRenderHands();
        VRArmHelper.renderVRHands(partialTick, renderHands && menuHandRight, renderHands && menuHandLeft, true, true, poseStack);

        // switch to VR hands buffer
        RenderTarget hands = ((LevelRendererExtension) levelRenderer).vivecraft$getAlphaSortVRHandsFramebuffer();
        hands.clear(Minecraft.ON_OSX);
        hands.copyDepthFrom(MC.getMainRenderTarget());
        hands.bindWrite(true);

        VRArmHelper.renderVRHands(partialTick, renderHands && !menuHandRight, renderHands && !menuHandLeft, false, false, poseStack);

        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        // rebind the original buffer
        MC.getMainRenderTarget().bindWrite(true);
    }

    /**
     * renders the vivecraft stuff, for fast and fancy setting, separated into 2 passes
     * one before and one after translucents.
     * this includes hands, vr shadow, gui, camera widgets and other stuff
     * @param partialTick current partial tick
     * @param secondPass if it's the second pass. first pass renders opaque stuff, second translucent stuff
     * @param menuHandRight if the right hand should be a menu hand
     * @param menuHandLeft if the left hand should be a menu hand
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderVrFast(float partialTick, boolean secondPass, boolean menuHandRight, boolean menuHandLeft,
        PoseStack poseStack) {
        if (DATA_HOLDER.currentPass == RenderPass.SCOPEL || DATA_HOLDER.currentPass == RenderPass.SCOPER) {
            // skip for spyglass
            return;
        }
        MC.getProfiler().popPush("VR");
        MC.gameRenderer.lightTexture().turnOffLightLayer();

        if (!secondPass) {
            renderCrosshairAtDepth(!DATA_HOLDER.vrSettings.useCrosshairOcclusion, poseStack);
            VRWidgetHelper.renderVRThirdPersonCamWidget();
            VRWidgetHelper.renderVRHandheldCameraWidget();
        } else {
            renderGuiAndShadow(poseStack, partialTick, !shouldOccludeGui(), true);
        }

        // render hands in second pass when gui is open
        boolean renderHandsSecond = RadialHandler.isShowing() || KeyboardHandler.SHOWING || Minecraft.getInstance().screen != null;
        if (secondPass == renderHandsSecond) {
            // should render hands in second pass if menus are open, else in the first pass
            // only render the hands only once
            VRArmHelper.renderVRHands(partialTick, VRArmHelper.shouldRenderHands(), VRArmHelper.shouldRenderHands(), menuHandRight, menuHandLeft, poseStack);
        }

        renderVRSelfEffects(partialTick);
    }

    /**
     * @return if the gui should be occluded
     */
    private static boolean shouldOccludeGui() {
        if (DATA_HOLDER.currentPass == RenderPass.THIRD || DATA_HOLDER.currentPass == RenderPass.CAMERA) {
            return true;
        } else {
            Vec3 pos = DATA_HOLDER.vrPlayer.vrdata_world_render.getEye(DATA_HOLDER.currentPass).getPosition();
            return DATA_HOLDER.vrSettings.hudOcclusion &&
                !MethodHolder.isInMenuRoom() &&
                MC.screen == null &&
                !KeyboardHandler.SHOWING &&
                !RadialHandler.isShowing() &&
                !isInsideOpaqueBlock(pos);
        }
    }

    /**
     * renders the guis (current screen/hud, radial and keyboard) and player shadow in the correct order
     * @param poseStack PoseStack to use for positioning
     * @param partialTick current partial tick
     * @param depthAlways if the depth test should be disabled
     * @param shadowFirst if the player shadow should be rendered first
     */
    private static void renderGuiAndShadow(PoseStack poseStack, float partialTick, boolean depthAlways, boolean shadowFirst) {
        if (shadowFirst) {
            VREffectsHelper.renderVrShadow(partialTick, depthAlways, poseStack);
        }
        if (Minecraft.getInstance().screen != null || !KeyboardHandler.SHOWING) {
            renderGuiLayer(partialTick, depthAlways, poseStack);
        }
        if (!shadowFirst) {
            VREffectsHelper.renderVrShadow(partialTick, depthAlways, poseStack);
        }

        if (KeyboardHandler.SHOWING) {
            if (DATA_HOLDER.vrSettings.physicalKeyboard) {
                renderPhysicalKeyboard(partialTick, poseStack);
            } else {
                render2D(partialTick, KeyboardHandler.FRAMEBUFFER, KeyboardHandler.POS_ROOM,
                    KeyboardHandler.ROTATION_ROOM, depthAlways, poseStack);
            }
        }

        if (RadialHandler.isShowing()) {
            render2D(partialTick, RadialHandler.FRAMEBUFFER, RadialHandler.POS_ROOM,
                RadialHandler.ROTATION_ROOM, depthAlways, poseStack);
        }
    }

    /**
     * renders the player position indicator
     * @param partialTick current partial tick
     * @param depthAlways if the depth test should be disabled
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderVrShadow(float partialTick, boolean depthAlways, PoseStack poseStack) {
        if (DATA_HOLDER.currentPass == RenderPass.THIRD || DATA_HOLDER.currentPass == RenderPass.CAMERA) {
            return;
        }
        if (!MC.player.isAlive()) return;
        if (MC.player.getVehicle() != null) return;
        // no indicator when swimming/crawling
        if (((PlayerExtension) MC.player).vivecraft$getRoomYOffsetFromPose() < 0.0D) return;

        MC.getProfiler().push("vr shadow");
        AABB aabb = MC.player.getBoundingBox();

        if (DATA_HOLDER.vrSettings.vrShowBlueCircleBuddy && aabb != null) {
            // disable culling to show it from below and above
            RenderSystem.disableCull();

            poseStack.pushPose();
            poseStack.setIdentity();
            RenderHelper.applyVRModelView(DATA_HOLDER.currentPass, poseStack);

            Vec3 cameraPos = RenderHelper.getSmoothCameraPosition(DATA_HOLDER.currentPass, DATA_HOLDER.vrPlayer.vrdata_world_render);

            Vec3 interpolatedPlayerPos = ((GameRendererExtension) MC.gameRenderer).vivecraft$getRvePos(partialTick);

            Vec3 pos = interpolatedPlayerPos.subtract(cameraPos).add(0.0D, 0.005D, 0.0D);

            RenderHelper.setupPolyRendering(true);
            RenderSystem.enableDepthTest();

            if (depthAlways) {
                RenderSystem.depthFunc(GL11C.GL_ALWAYS);
            } else {
                RenderSystem.depthFunc(GL11C.GL_LEQUAL);
            }

            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            MC.getTextureManager().bindForSetup(new ResourceLocation("vivecraft:textures/white.png"));
            RenderSystem.setShaderTexture(0, new ResourceLocation("vivecraft:textures/white.png"));

            RenderHelper.renderFlatQuad(pos, (float) (aabb.maxX - aabb.minX), (float) (aabb.maxZ - aabb.minZ),
                0.0F, 0, 0, 0, 64, poseStack);

            // reset render state
            RenderSystem.depthFunc(GL11C.GL_LEQUAL);
            RenderSystem.enableCull();
            RenderHelper.setupPolyRendering(false);
            poseStack.popPose();
        }
        MC.getProfiler().pop();
    }

    /**
     * renders effects around the player, includes burning animation and totem of undying
     * @param partialTick current partial tick
     */
    private static void renderVRSelfEffects(float partialTick) {
        // only render the fire in first person, other views have the burning entity
        if (DATA_HOLDER.currentPass != RenderPass.THIRD && DATA_HOLDER.currentPass != RenderPass.CAMERA &&
            !MC.player.isSpectator() && MC.player.isOnFire() && !Xevents.renderFireOverlay(MC.player, new PoseStack()))
        {
            VREffectsHelper.renderFireInFirstPerson();
        }

        // totem of undying
        MC.gameRenderer.renderItemActivationAnimation(0, 0, partialTick);
    }

    /**
     * renders the fire when the player is burning
     */
    public static void renderFireInFirstPerson() {
        PoseStack posestack = new PoseStack();
        RenderHelper.applyVRModelView(DATA_HOLDER.currentPass, posestack);
        RenderHelper.applyStereo(DATA_HOLDER.currentPass, posestack);
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();

        if (DATA_HOLDER.currentPass == RenderPass.THIRD || DATA_HOLDER.currentPass == RenderPass.CAMERA) {
            RenderSystem.depthFunc(GL11C.GL_LEQUAL);
        } else {
            RenderSystem.depthFunc(GL11C.GL_ALWAYS);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        TextureAtlasSprite fireSprite = ModelBakery.FIRE_1.sprite();

        if (OptifineHelper.isOptifineLoaded()) {
            OptifineHelper.markTextureAsActive(fireSprite);
        }

        // code adapted from net.minecraft.client.renderer.ScreenEffectRenderer.renderFire

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, fireSprite.atlasLocation());
        float uMin = fireSprite.getU0();
        float uMax = fireSprite.getU1();
        float uMid = (uMin + uMax) / 2.0F;

        float vMin = fireSprite.getV0();
        float vMax = fireSprite.getV1();
        float vMid = (vMin + vMax) / 2.0F;

        float ShrinkRatio = fireSprite.uvShrinkRatio();

        float u0 = Mth.lerp(ShrinkRatio, uMin, uMid);
        float u1 = Mth.lerp(ShrinkRatio, uMax, uMid);
        float v0 = Mth.lerp(ShrinkRatio, vMin, vMid);
        float v1 = Mth.lerp(ShrinkRatio, vMax, vMid);

        float width = 0.3F;
        float headHeight = (float) (DATA_HOLDER.vrPlayer.vrdata_world_render.getHeadPivot().y - ((GameRendererExtension) MC.gameRenderer).vivecraft$getRveY());

        for (int i = 0; i < 4; i++) {
            posestack.pushPose();
            posestack.mulPose(Axis.YP.rotationDegrees(
                i * 90.0F - DATA_HOLDER.vrPlayer.vrdata_world_render.getBodyYaw()));
            posestack.translate(0.0D, -headHeight, 0.0D);

            Matrix4f matrix = posestack.last().pose();
            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferbuilder.vertex(matrix, -width, 0.0F, -width)
                .uv(u1, v1).color(1.0F, 1.0F, 1.0F, 0.9F).endVertex();
            bufferbuilder.vertex(matrix, width, 0.0F, -width)
                .uv(u0, v1).color(1.0F, 1.0F, 1.0F, 0.9F).endVertex();
            bufferbuilder.vertex(matrix, width, headHeight, -width)
                .uv(u0, v0).color(1.0F, 1.0F, 1.0F, 0.9F).endVertex();
            bufferbuilder.vertex(matrix, -width, headHeight, -width)
                .uv(u1, v0).color(1.0F, 1.0F, 1.0F, 0.9F).endVertex();
            BufferUploader.drawWithShader(bufferbuilder.end());

            posestack.popPose();
        }

        RenderSystem.depthFunc(GL11C.GL_LEQUAL);
        RenderSystem.disableBlend();
    }

    /**
     * renders the physical touch keyboard
     * @param partialTick current partial tick
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderPhysicalKeyboard(float partialTick, PoseStack poseStack) {
        if (DATA_HOLDER.bowTracker.isDrawing) return;

        MC.getProfiler().push("renderPhysicalKeyboard");
        ((GameRendererExtension) MC.gameRenderer).vivecraft$resetProjectionMatrix(partialTick);
        poseStack.pushPose();
        poseStack.setIdentity();

        RenderHelper.applyVRModelView(DATA_HOLDER.currentPass, poseStack);

        MC.getProfiler().push("applyPhysicalKeyboardModelView");
        Vec3 eye = RenderHelper.getSmoothCameraPosition(DATA_HOLDER.currentPass, DATA_HOLDER.vrPlayer.vrdata_world_render);

        //convert previously calculated coords to world coords
        Vec3 keyboardPos = VRPlayer.roomToWorldPos(KeyboardHandler.POS_ROOM, DATA_HOLDER.vrPlayer.vrdata_world_render);

        Matrix4f keyboardRot = new Matrix4f().rotationY(DATA_HOLDER.vrPlayer.vrdata_world_render.rotation_radians)
            .mul(KeyboardHandler.ROTATION_ROOM);

        // offset from eye to keyboard pos
        poseStack.translate((float) (keyboardPos.x - eye.x),
            (float) (keyboardPos.y - eye.y),
            (float) (keyboardPos.z - eye.z));

        poseStack.mulPoseMatrix(keyboardRot);

        float scale = DATA_HOLDER.vrPlayer.vrdata_world_render.worldScale;
        poseStack.scale(scale, scale, scale);

        // pop apply modelview
        MC.getProfiler().pop();

        KeyboardHandler.PHYSICAL_KEYBOARD.render(poseStack);
        poseStack.popPose();
        // pop render
        MC.getProfiler().pop();
    }

    /**
     * sets up the render state to render screens into the world.
     * this resets the given PoseStack, the ModelViewStack and projection matrix
     * @param partialTick current partial tick
     * @param poseStack PoseStack to reset
     */
    private static void setupScreenRendering(float partialTick, PoseStack poseStack) {
        // remove nausea effect from projection matrix, for vanilla, and poseStack for iris
        ((GameRendererExtension) MC.gameRenderer).vivecraft$resetProjectionMatrix(partialTick);
        poseStack.pushPose();
        poseStack.setIdentity();
        RenderHelper.applyVRModelView(DATA_HOLDER.currentPass, poseStack);

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
    }

    /**
     * pops the reseted PoseStack and ModelViewStack
     * @param poseStack PoseStack to reset
     */
    private static void finishScreenRendering(PoseStack poseStack) {
        poseStack.popPose();
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
    }

    /**
     * Renders the given RenderTarget into the world at the given location.
     * @param framebuffer RenderTarget to render into the world
     * @param depthAlways if the depth test should be disabled
     * @param noFog disables for, used to render menus without for in lava
     * @param pos position to render the RenderTarget at
     * @param poseStack PoseStack to use for positioning
     */
    private static void renderScreen(RenderTarget framebuffer, boolean depthAlways, boolean noFog, Vec3 pos, PoseStack poseStack) {
        framebuffer.bindRead();
        // disable culling to sho the screen from both sides
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, framebuffer.getColorTextureId());

        // cache fog distance
        float fogStart = RenderSystem.getShaderFogStart();
        float[] color = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
        if (!MethodHolder.isInMenuRoom()) {
            if (MC.screen == null) {
                color[3] = DATA_HOLDER.vrSettings.hudOpacity;
            }
            if (noFog || MC.screen != null) {
                // disable fog for menus
                RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            }

            if (MC.player != null && MC.player.isShiftKeyDown()) {
                color[3] *= 0.75F;
            }

            if (!ShadersHelper.isShaderActive() || DATA_HOLDER.vrSettings.shaderGUIRender != VRSettings.ShaderGUIRender.BEFORE_TRANSLUCENT_SOLID) {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE_MINUS_DST_ALPHA, GlStateManager.DestFactor.ONE);
            }
        } else {
            // enable blend for overlay transition in menuworld to not be jarring
            RenderSystem.enableBlend();
        }

        if (depthAlways) {
            RenderSystem.depthFunc(GL11C.GL_ALWAYS);
        } else {
            RenderSystem.depthFunc(GL11C.GL_LEQUAL);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        if (MC.level != null) {
            if (isInsideOpaqueBlock(pos) || ((GameRendererExtension) MC.gameRenderer).vivecraft$isInBlock() > 0.0F) {
                pos = DATA_HOLDER.vrPlayer.vrdata_world_render.hmd.getPosition();
            }

            int minLight = ShadersHelper.ShaderLight();
            int light = ClientUtils.getCombinedLightWithMin(MC.level, BlockPos.containing(pos), minLight);

            RenderHelper.drawSizedQuadWithLightmapCutout(
                (float) MC.getWindow().getGuiScaledWidth(), (float) MC.getWindow().getGuiScaledHeight(),
                1.5F, light, color, poseStack.last().pose(), false);
        } else {
            RenderHelper.drawSizedQuad(
                (float) MC.getWindow().getGuiScaledWidth(), (float) MC.getWindow().getGuiScaledHeight(),
                1.5F, color, poseStack.last().pose());
        }

        // reset fog
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.depthFunc(GL11C.GL_LEQUAL);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
    }

    /**
     * renders the GUI/HUD buffer into the world
     * @param partialTick current partial tick
     * @param depthAlways if the depth test should be disabled
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderGuiLayer(float partialTick, boolean depthAlways, PoseStack poseStack) {
        if (DATA_HOLDER.bowTracker.isDrawing) return;
        if (MC.screen == null && MC.options.hideGui) return;
        if (RadialHandler.isShowing()) return;

        MC.getProfiler().push("GuiLayer");

        setupScreenRendering(partialTick, poseStack);

        // MAIN MENU ENVIRONMENT
        if (MethodHolder.isInMenuRoom()) {
            // render the screen always on top in the menu room to prevent z fighting
            depthAlways = true;

            poseStack.pushPose();
            Vec3 eye = RenderHelper.getSmoothCameraPosition(DATA_HOLDER.currentPass, DATA_HOLDER.vrPlayer.vrdata_world_render);
            poseStack.translate(DATA_HOLDER.vrPlayer.vrdata_world_render.origin.x - eye.x,
                DATA_HOLDER.vrPlayer.vrdata_world_render.origin.y - eye.y,
                DATA_HOLDER.vrPlayer.vrdata_world_render.origin.z - eye.z);

            // remove world rotation or the room doesn't align with the screen
            poseStack.mulPose(Axis.YN.rotation(-DATA_HOLDER.vrPlayer.vrdata_world_render.rotation_radians));

            if (DATA_HOLDER.menuWorldRenderer.isReady()) {
                try {
                    renderTechjarsAwesomeMainMenuRoom(poseStack);
                } catch (Exception e) {
                    VRSettings.LOGGER.error("Vivecraft: Error rendering main menu world, unloading to prevent more errors: ", e);
                    DATA_HOLDER.menuWorldRenderer.destroy();
                }
            } else {
                if (DATA_HOLDER.vrSettings.menuWorldFallbackPanorama) {
                    renderMenuPanorama(poseStack);
                } else {
                    renderJrbuddasAwesomeMainMenuRoomNew(poseStack);
                }
            }
            poseStack.popPose();
        }

        Vec3 guiPos = GuiHandler.applyGUIModelView(DATA_HOLDER.currentPass, poseStack);

        renderScreen(GuiHandler.GUI_FRAMEBUFFER, depthAlways, false, guiPos, poseStack);

        finishScreenRendering(poseStack);
        MC.getProfiler().pop();
    }

    /**
     * renders the given RenderTarget into the world, ath the given location with the give rotation
     * @param partialTick current partial tick
     * @param framebuffer RenderTarget to render into the world
     * @param pos position to render the RenderTarget at, in VR room space
     * @param rot rotation to rotate the screen, in VR room space
     * @param depthAlways if the depth test should be disabled
     * @param poseStack PoseStack to use for positioning
     */
    public static void render2D(float partialTick, RenderTarget framebuffer, Vector3fc pos, Matrix4f rot, boolean depthAlways, PoseStack poseStack) {
        if (DATA_HOLDER.bowTracker.isDrawing) return;

        MC.getProfiler().push("render2D");

        setupScreenRendering(partialTick, poseStack);

        MC.getProfiler().push("apply2DModelView");

        Vec3 eye = RenderHelper.getSmoothCameraPosition(DATA_HOLDER.currentPass, DATA_HOLDER.vrPlayer.vrdata_world_render);

        Vec3 worldPos = VRPlayer.roomToWorldPos(pos, DATA_HOLDER.vrPlayer.vrdata_world_render);

        Matrix4f worldRotation = new Matrix4f().rotationY(DATA_HOLDER.vrPlayer.vrdata_world_render.rotation_radians)
            .mul(rot);

        poseStack.translate(worldPos.x - eye.x, worldPos.y - eye.y, worldPos.z - eye.z);
        poseStack.mulPoseMatrix(worldRotation);

        float scale = GuiHandler.GUI_SCALE * DATA_HOLDER.vrPlayer.vrdata_world_render.worldScale;
        poseStack.scale(scale, scale, scale);

        // pop modelview
        MC.getProfiler().pop();

        renderScreen(framebuffer, depthAlways, true, worldPos, poseStack);

        finishScreenRendering(poseStack);
        // pop render
        MC.getProfiler().pop();
    }

    /**
     * if the face is inside a block, this renders a black square, and rerenders the gui and hands
     * @param partialTick current partial tick
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderFaceOverlay(float partialTick, PoseStack poseStack) {
        if (((GameRendererExtension) MC.gameRenderer).vivecraft$isInBlock() > 0.0F) {
            renderFaceInBlock();

            renderGuiAndShadow(poseStack, partialTick, true, true);

            VRArmHelper.renderVRHands(partialTick, true, true, true, true, poseStack);
        }
    }

    /**
     * renders a fullscreen black quad, to block the screen
     */
    public static void renderFaceInBlock() {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0f);

        RenderSystem.depthFunc(GL11C.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        // render a big quad 2 meters in front
        // identity matrix
        Matrix4f mat = new Matrix4f();
        bufferbuilder.vertex(mat, -100.F, -100.F, -2.0F).endVertex();
        bufferbuilder.vertex(mat, 100.F, -100.F, -2.0F).endVertex();
        bufferbuilder.vertex(mat, 100.F, 100.F, -2.0F).endVertex();
        bufferbuilder.vertex(mat, -100.F, 100.F, -2.0F).endVertex();
        tesselator.end();
        RenderSystem.depthFunc(GL11C.GL_LEQUAL);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * @return if the crosshair should be rendered
     */
    private static boolean shouldRenderCrosshair() {
        if (ClientDataHolderVR.VIEW_ONLY) {
            return false;
        } else if (MC.level == null) {
            return false;
        } else if (MC.screen != null) {
            return false;
        } else if (DATA_HOLDER.vrSettings.renderInGameCrosshairMode == VRSettings.RenderPointerElement.NEVER ||
            (DATA_HOLDER.vrSettings.renderInGameCrosshairMode == VRSettings.RenderPointerElement.WITH_HUD &&
                MC.options.hideGui
            ))
        {
            return false;
        } else if (DATA_HOLDER.currentPass != RenderPass.LEFT &&
            DATA_HOLDER.currentPass != RenderPass.RIGHT &&
            DATA_HOLDER.currentPass != RenderPass.CENTER)
        {
            // it doesn't look very good
            return false;
        } else if (KeyboardHandler.SHOWING) {
            return false;
        } else if (RadialHandler.isUsingController(ControllerType.RIGHT)) {
            return false;
        } else if (GuiHandler.GUI_POS_ROOM != null) {
            // don't show it, when a screen is open, or a popup
            return false;
        } else if (DATA_HOLDER.bowTracker.isNotched()) {
            return false;
        } else if (
            DATA_HOLDER.vr.getInputAction(VivecraftVRMod.INSTANCE.keyVRInteract).isEnabledRaw(ControllerType.RIGHT) ||
                VivecraftVRMod.INSTANCE.keyVRInteract.isDown(ControllerType.RIGHT))
        {
            return false;
        } else if (
            DATA_HOLDER.vr.getInputAction(VivecraftVRMod.INSTANCE.keyClimbeyGrab).isEnabledRaw(ControllerType.RIGHT) ||
                VivecraftVRMod.INSTANCE.keyClimbeyGrab.isDown(ControllerType.RIGHT))
        {
            return false;
        } else if (DATA_HOLDER.teleportTracker.isAiming()) {
            return false;
        } else if (DATA_HOLDER.climbTracker.isGrabbingLadder(0)) {
            return false;
        } else {
            return !(DATA_HOLDER.vrPlayer.worldScale > 15.0F);
        }
    }

    /**
     * renders the crosshair
     * @param depthAlways if the depth test should be disabled
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderCrosshairAtDepth(boolean depthAlways, PoseStack poseStack) {
        if (!shouldRenderCrosshair()) return;

        MC.getProfiler().push("crosshair");

        Vec3 crosshairRenderPos = ((GameRendererExtension) MC.gameRenderer).vivecraft$getCrossVec();
        Vec3 crossDistance = crosshairRenderPos.subtract(DATA_HOLDER.vrPlayer.vrdata_world_render.getController(0).getPosition());

        //scooch closer a bit for light calc.
        crosshairRenderPos = crosshairRenderPos.add(crossDistance.normalize().scale(-0.01D));

        poseStack.pushPose();
        poseStack.setIdentity();
        RenderHelper.applyVRModelView(DATA_HOLDER.currentPass, poseStack);

        Vec3 translate = crosshairRenderPos.subtract(MC.getCameraEntity().position());
        poseStack.translate(translate.x, translate.y, translate.z);

        if (MC.hitResult != null && MC.hitResult.getType() == HitResult.Type.BLOCK) {
            // if there is a block hit, make the crosshair parallel to the block
            BlockHitResult blockhitresult = (BlockHitResult) MC.hitResult;

            switch (blockhitresult.getDirection()) {
                case DOWN -> {
                    poseStack.mulPose(Axis.YP.rotationDegrees(DATA_HOLDER.vrPlayer.vrdata_world_render.getController(0).getYaw()));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                }
                case UP -> {
                    poseStack.mulPose(Axis.YP.rotationDegrees(-DATA_HOLDER.vrPlayer.vrdata_world_render.getController(0).getYaw()));
                    poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                }
                case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
                case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
        } else {
            // if there is no block hit, make it face the controller
            poseStack.mulPose(Axis.YP.rotationDegrees(-DATA_HOLDER.vrPlayer.vrdata_world_render.getController(0).getYaw()));
            poseStack.mulPose(Axis.XP.rotationDegrees(-DATA_HOLDER.vrPlayer.vrdata_world_render.getController(0).getPitch()));
        }

        float scale = (float) (0.125F * DATA_HOLDER.vrSettings.crosshairScale * Math.sqrt(DATA_HOLDER.vrPlayer.vrdata_world_render.worldScale));
        if (DATA_HOLDER.vrSettings.crosshairScalesWithDistance) {
            float depthScale = 0.3F + 0.2F * (float) crossDistance.length();
            scale *= depthScale;
        }
        poseStack.scale(scale, scale, scale);

        MC.gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        if (depthAlways) {
            RenderSystem.depthFunc(GL11C.GL_ALWAYS);
        } else {
            RenderSystem.depthFunc(GL11C.GL_LEQUAL);
        }


        // white crosshair, with blending
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend(); // Fuck it, we want a proper crosshair
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        int light = LevelRenderer.getLightColor(MC.level, BlockPos.containing(crosshairRenderPos));
        float brightness = 1.0F;

        if (MC.hitResult == null || MC.hitResult.getType() == HitResult.Type.MISS) {
            brightness = 0.5F;
        }

        TextureAtlasSprite crosshairSprite = Minecraft.getInstance().getGuiSprites().getSprite(Gui.CROSSHAIR_SPRITE);
        RenderSystem.setShaderTexture(0, crosshairSprite.atlasLocation());

        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();

        RenderSystem.setShader(GameRenderer::getRendertypeEntityCutoutNoCullShader);
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);

        bufferbuilder.vertex(poseStack.last().pose(), -1.0F, 1.0F, 0.0F)
            .color(brightness, brightness, brightness, 1.0F)
            .uv(crosshairSprite.getU0(), crosshairSprite.getV1())
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
            .normal(0.0F, 0.0F, 1.0F).endVertex();
        bufferbuilder.vertex(poseStack.last().pose(), 1.0F, 1.0F, 0.0F)
            .color(brightness, brightness, brightness, 1.0F)
            .uv(crosshairSprite.getU1(), crosshairSprite.getV1())
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
            .normal(0.0F, 0.0F, 1.0F).endVertex();
        bufferbuilder.vertex(poseStack.last().pose(), 1.0F, -1.0F, 0.0F)
            .color(brightness, brightness, brightness, 1.0F)
            .uv(crosshairSprite.getU1(), crosshairSprite.getV0())
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
            .normal(0.0F, 0.0F, 1.0F).endVertex();
        bufferbuilder.vertex(poseStack.last().pose(), -1.0F, -1.0F, 0.0F)
            .color(brightness, brightness, brightness, 1.0F)
            .uv(crosshairSprite.getU0(), crosshairSprite.getV0())
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
            .normal(0.0F, 0.0F, 1.0F).endVertex();

        BufferUploader.drawWithShader(bufferbuilder.end());

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthFunc(GL11C.GL_LEQUAL);
        poseStack.popPose();
        MC.getProfiler().pop();
    }
}
