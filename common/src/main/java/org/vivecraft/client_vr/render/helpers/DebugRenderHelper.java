package org.vivecraft.client_vr.render.helpers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Triple;
import org.joml.*;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client.gui.screens.FBTCalibrationScreen;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.gameplay.trackers.TelescopeTracker;
import org.vivecraft.client_vr.provider.DeviceSource;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.common.network.FBTMode;
import org.vivecraft.common.utils.MathUtils;

import java.util.ArrayList;
import java.util.List;

public class DebugRenderHelper {

    private static final ClientDataHolderVR DATA_HOLDER = ClientDataHolderVR.getInstance();
    private static final Minecraft MC = Minecraft.getInstance();

    private static final Vector3fc RED = new Vector3f(1F,0F,0F);
    private static final Vector3fc GREEN = new Vector3f(0F,1F,0F);
    private static final Vector3fc BLUE = new Vector3f(0F,0F,1F);
    private static final Vector3fc DARK_GRAY = new Vector3f(0.25F);

    /**
     * renders debug stuff
     * @param poseStack PoseStack to use for positioning
     * @param partialTick current partial tick
     */
    public static void renderDebug(PoseStack poseStack, float partialTick) {
        if (DATA_HOLDER.vrSettings.renderDeviceAxes) {
            renderDeviceAxes(poseStack, DATA_HOLDER.vrPlayer.getVRDataWorld());
        }

        if (DATA_HOLDER.vrSettings.renderVrPlayerAxes) {
            renderPlayerAxes(poseStack, partialTick);
        }

        if (DATA_HOLDER.vrSettings.renderTrackerPositions || MC.screen instanceof FBTCalibrationScreen) {
            boolean showNames = true;
            if (MC.screen instanceof FBTCalibrationScreen fbtScreen) {
                showNames = fbtScreen.isCalibrated();
            }
            renderTackerPositions(poseStack, showNames);
        }
    }

    /**
     * renders all available remote devices from all players
     * @param poseStack PoseStack to use for positioning
     * @param partialTick current partial tick
     */
    public static void renderPlayerAxes(PoseStack poseStack, float partialTick) {
        if (MC.player != null) {
            BufferBuilder bufferbuilder = null;
            Vec3 camPos = RenderHelper
                .getSmoothCameraPosition(DATA_HOLDER.currentPass, DATA_HOLDER.vrPlayer.getVRDataWorld());

            for(Player p : MC.player.level().players()) {
                if (VRPlayersClient.getInstance().isVRPlayer(p)) {
                    VRPlayersClient.RotInfo info = VRPlayersClient.getInstance().getRotationsForPlayer(p.getUUID());

                    if (bufferbuilder == null) {
                        RenderSystem.setShader(GameRenderer::getPositionColorShader);
                        bufferbuilder = Tesselator.getInstance().getBuilder();
                        bufferbuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
                    }

                    Vector3f playerPos = p.getPosition(partialTick).subtract(camPos).toVector3f();
                    if (p == MC.player) {
                        playerPos = ((GameRendererExtension) MC.gameRenderer).vivecraft$getRvePos(partialTick).subtract(camPos).toVector3f();
                    }

                    if (p != MC.player || DATA_HOLDER.currentPass == RenderPass.THIRD) {
                        addAxes(poseStack, bufferbuilder, playerPos, info.headPos, info.headRot, info.headQuat);
                    }
                    if (!info.seated) {
                        addAxes(poseStack, bufferbuilder, playerPos, info.mainHandPos, info.mainHandRot,
                            info.mainHandQuat);
                        addAxes(poseStack, bufferbuilder, playerPos, info.offHandPos, info.offHandRot,
                            info.offHandQuat);
                    }
                    if (info.fbtMode != FBTMode.ARMS_ONLY) {
                        addAxes(poseStack, bufferbuilder, playerPos, info.waistPos, info.waistQuat);
                        addAxes(poseStack, bufferbuilder, playerPos, info.rightFootPos, info.rightFootQuat);
                        addAxes(poseStack, bufferbuilder, playerPos, info.leftFootPos, info.leftFootQuat);
                    }
                    if (info.fbtMode == FBTMode.WITH_JOINTS) {
                        addAxes(poseStack, bufferbuilder, playerPos, info.rightElbowPos, info.rightElbowQuat);
                        addAxes(poseStack, bufferbuilder, playerPos, info.leftElbowPos, info.leftElbowQuat);
                        addAxes(poseStack, bufferbuilder, playerPos, info.rightKneePos, info.rightKneeQuat);
                        addAxes(poseStack, bufferbuilder, playerPos, info.leftKneePos, info.leftKneeQuat);
                    }

                }
            }
            if (bufferbuilder != null) {
                BufferUploader.drawWithShader(bufferbuilder.end());
            }
        }
    }

    /**
     * renders all available device axes using the provided VRData
     * @param poseStack PoseStack to use for positioning
     * @param data VRData to get the devices from
     */
    public static void renderDeviceAxes(PoseStack poseStack, VRData data) {
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        bufferbuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        List<VRData.VRDevicePose> list = new ArrayList<>();

        list.add(data.c2);

        if (DATA_HOLDER.currentPass == RenderPass.THIRD) {
            list.add(data.hmd);
        }

        if (DATA_HOLDER.cameraTracker.isVisible()) {
            list.add(data.cam);
        }

        if (MC.player != null && TelescopeTracker.isTelescope(MC.player.getMainHandItem()) && TelescopeTracker.isViewing(0)) {
            list.add(data.t0);
        } else {
            list.add(MC.player != null && MC.player.isShiftKeyDown() ? data.h0 : data.c0);
        }

        if (MC.player != null && TelescopeTracker.isTelescope(MC.player.getOffhandItem()) && TelescopeTracker.isViewing(0)) {
            list.add(data.t1);
        } else {
            list.add(MC.player != null && MC.player.isShiftKeyDown() ? data.h1 :data.c1);
        }

        if (data.fbtMode != FBTMode.ARMS_ONLY) {
            list.add(data.waist);
            list.add(data.foot_left);
            list.add(data.foot_right);
        }
        if (data.fbtMode == FBTMode.WITH_JOINTS) {
            list.add(data.elbow_left);
            list.add(data.knee_left);
            list.add(data.elbow_right);
            list.add(data.knee_right);
        }

        list.forEach(p -> addAxes(poseStack, bufferbuilder, data, p));

        BufferUploader.drawWithShader(bufferbuilder.end());
    }

    private static void renderTackerPositions(PoseStack poseStack, boolean showNames) {
        VRData data = DATA_HOLDER.vrPlayer.getVRDataWorld();
        Vec3 camPos = RenderHelper.getSmoothCameraPosition(DATA_HOLDER.currentPass, data);
        Quaternionf orientation = data.getEye(DATA_HOLDER.currentPass).getMatrix()
            .getNormalizedRotation(new Quaternionf())
            .rotateY(Mth.PI);

        Component[] labels = new Component[]{
            Component.translatable("vivecraft.toasts.point_controller.right"),
            Component.translatable("vivecraft.toasts.point_controller.left"),
            Component.translatable("vivecraft.messages.tracker.camera"),
            Component.translatable("vivecraft.messages.tracker.waist"),
            Component.translatable("vivecraft.messages.tracker.rightFoot"),
            Component.translatable("vivecraft.messages.tracker.leftFoot"),
            Component.translatable("vivecraft.messages.tracker.rightElbow"),
            Component.translatable("vivecraft.messages.tracker.leftElbow"),
            Component.translatable("vivecraft.messages.tracker.rightKnee"),
            Component.translatable("vivecraft.messages.tracker.leftKnee")
        };

        // show all trackers
        for (Triple<DeviceSource, Integer, Matrix4fc> tracker : MCVR.get().getTrackers()) {
            Vector3f pos = tracker.getRight().getTranslation(new Vector3f());
            Vec3 trackerPos = VRPlayer.roomToWorldPos(pos, data).subtract(camPos);
            pos.set((float) trackerPos.x, (float) trackerPos.y, (float) trackerPos.z);

            if (showNames) {
                if (tracker.getMiddle() >= 0) {
                    addNamedCube(poseStack, pos, orientation, Component.translatable("vivecraft.formatting.name_value",
                            Component.literal(tracker.getLeft().source.toString()), labels[tracker.getMiddle()]), 0.05F,
                        DARK_GRAY);
                } else {
                    addNamedCube(poseStack, pos, orientation, Component.translatable("vivecraft.formatting.name_value",
                        Component.literal(tracker.getLeft().source.toString() + tracker.getLeft().deviceIndex),
                        Component.translatable("vivecraft.messages.tracker.unknown")), 0.05F, DARK_GRAY);
                }
            } else {
                addCube(poseStack, pos, 0.05F, DARK_GRAY);
            }
        }
        MC.renderBuffers().bufferSource().endLastBatch();
    }

    /**
     * renders forward, upo and right axes using the {@code poseStack} position and orientation
     * @param poseStack PoseStack to use for positioning
     */
    public static void renderLocalAxes(PoseStack poseStack) {
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        bufferbuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        Vector3f position = new Vector3f();

        addLine(poseStack, bufferbuilder, position, MathUtils.BACK, BLUE);
        addLine(poseStack, bufferbuilder, position, MathUtils.UP, GREEN);
        addLine(poseStack, bufferbuilder, position, MathUtils.RIGHT, RED);

        BufferUploader.drawWithShader(bufferbuilder.end());
    }

    /**
     * adds device axes to the {@code bufferBuilder} for the given VRDevicePose
     * @param poseStack PoseStack to use for positioning
     * @param bufferBuilder BufferBuilder to use, needs to be in DEBUG_LINE_STRIP and POSITION_COLOR mode
     * @param data VRData to get camera position from
     * @param pose VRDevicePose to ge the orientation and position from.
     */
    private static void addAxes(
        PoseStack poseStack, BufferBuilder bufferBuilder, VRData data, VRData.VRDevicePose pose)
    {
        Vector3f position = pose.getPosition()
            .subtract(RenderHelper.getSmoothCameraPosition(DATA_HOLDER.currentPass, data)).toVector3f();

        float scale = 0.25F * DATA_HOLDER.vrPlayer.worldScale;

        Vector3f forward = pose.getDirection().mul(scale);
        Vector3f up = pose.getCustomVector(MathUtils.UP).mul(scale);
        Vector3f right = pose.getCustomVector(MathUtils.RIGHT).mul(scale);

        addLine(poseStack, bufferBuilder, position, forward, BLUE);
        addLine(poseStack, bufferBuilder, position, up, GREEN);
        addLine(poseStack, bufferBuilder, position, right, RED);
    }

    /**
     * adds device axes to the {@code bufferBuilder} for the given VRDevicePose, without dedicated direction vector
     * @param poseStack PoseStack to use for positioning
     * @param bufferBuilder BufferBuilder to use, needs to be in DEBUG_LINE_STRIP and POSITION_COLOR mode
     * @param playerPos player position, relative to the camera
     * @param devicePos device position, relative to the player
     * @param rot device rotation
     */
    private static void addAxes(
        PoseStack poseStack, BufferBuilder bufferBuilder, Vector3fc playerPos, Vector3fc devicePos, Quaternionfc rot)
    {
        addAxes(poseStack, bufferBuilder, playerPos, devicePos, rot.transform(MathUtils.BACK, new Vector3f()), rot);
    }

    /**
     * adds device axes to the {@code bufferBuilder} for the given VRDevicePose
     * @param poseStack PoseStack to use for positioning
     * @param bufferBuilder BufferBuilder to use, needs to be in DEBUG_LINE_STRIP and POSITION_COLOR mode
     * @param playerPos player position, relative to the camera
     * @param devicePos device position, relative to the player
     * @param dir device forward direction
     * @param rot device rotation
     */
    private static void addAxes(
        PoseStack poseStack, BufferBuilder bufferBuilder, Vector3fc playerPos, Vector3fc devicePos, Vector3fc dir,
        Quaternionfc rot)
    {
        Vector3f position = playerPos.add(devicePos, new Vector3f());

        float scale = 0.25F * DATA_HOLDER.vrPlayer.worldScale;

        Vector3f forward = dir.mul(scale, new Vector3f());
        Vector3f up = rot.transform(MathUtils.UP, new Vector3f()).mul(scale);
        Vector3f right = rot.transform(MathUtils.RIGHT, new Vector3f()).mul(scale);

        addLine(poseStack, bufferBuilder, position, forward, BLUE);
        addLine(poseStack, bufferBuilder, position, up, GREEN);
        addLine(poseStack, bufferBuilder, position, right, RED);
    }

    /**
     * adds a line from {@code position} in direction {@code dir}, with the given {@code color}
     * @param poseStack PoseStack to use for positioning
     * @param bufferBuilder BufferBuilder to use, needs to be in DEBUG_LINE_STRIP and POSITION_COLOR mode
     * @param position line start position
     * @param dir line end, relative to {@code position}
     * @param color line color
     */
    private static void addLine(
        PoseStack poseStack, BufferBuilder bufferBuilder, Vector3fc position, Vector3fc dir, Vector3fc color)
    {
        bufferBuilder.vertex(poseStack.last().pose(), position.x(), position.y(), position.z())
            .color(color.x(), color.y(), color.z(), 0.0F).endVertex();
        bufferBuilder.vertex(poseStack.last().pose(), position.x(), position.y(), position.z())
            .color(color.x(), color.y(), color.z(), 1.0F).endVertex();
        bufferBuilder.vertex(poseStack.last().pose(), position.x() + dir.x(), position.y() + dir.y(),
                position.z() + dir.z())
            .color(color.x(), color.y(), color.z(), 1.0F).endVertex();
        bufferBuilder.vertex(poseStack.last().pose(), position.x() + dir.x(), position.y() + dir.y(),
                position.z() + dir.z())
            .color(color.x(), color.y(), color.z(), 0.0F).endVertex();
    }

    /**
     * Renders a cube with text lable above it
     * @param poseStack PoseStack to use for positioning
     * @param cubePos position to render the cube at, camera relative
     * @param rot rotation facing the camera, to align the text
     * @param label label of the cube
     * @param size cube size
     * @param color cube color
     */
    private static void addNamedCube(
        PoseStack poseStack, Vector3fc cubePos, Quaternionf rot, Component label, float size, Vector3fc color)
    {
        addCube(poseStack, cubePos, size, color);

        if (label != null) {
            renderTextAtRelativePosition(poseStack, cubePos.x(), cubePos.y(), cubePos.z(), rot, label);
        }
    }

    public static void renderTextAtDevice(PoseStack poseStack, int device, String text) {
        renderTextAtPosition(poseStack, DATA_HOLDER.vrPlayer.getVRDataWorld().getDevice(device).getPosition(), text);
    }

    public static void renderTextAtPosition(PoseStack poseStack, Vec3 position, String text) {
        VRData data = DATA_HOLDER.vrPlayer.getVRDataWorld();
        Vec3 camPos = RenderHelper.getSmoothCameraPosition(DATA_HOLDER.currentPass, data);
        Quaternionf rot = data.getEye(DATA_HOLDER.currentPass).getMatrix()
            .getNormalizedRotation(new Quaternionf())
            .rotateY(Mth.PI);
        Vec3 pos = position.subtract(camPos);

        renderTextAtRelativePosition(poseStack, pos.x, pos.y, pos.z, rot, text);
    }

    public static void renderTextAtRelativePosition(
        PoseStack poseStack, double x, double y, double z, Quaternionf rot, String text)
    {
        renderTextAtRelativePosition(poseStack, x, y, z, rot, Component.literal(text));
    }

    public static void renderTextAtRelativePosition(
        PoseStack poseStack, double x, double y, double z, Quaternionf rot, Component text)
    {
        poseStack.pushPose();
        poseStack.translate(x, y + 0.05F, z);
        poseStack.mulPose(rot);
        poseStack.scale(-0.005F, -0.005F, 0.005F);

        MC.font.drawInBatch(text, MC.font.width(text) * -0.5F, -MC.font.lineHeight, -1, false,
            poseStack.last().pose(), MC.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0,
            LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    /**
     * Renders a cube
     * @param poseStack PoseStack to use for positioning
     * @param position position to render the cube at, camera relative
     * @param size cube size
     * @param color cube color
     */
    private static void addCube(PoseStack poseStack, Vector3fc position, float size, Vector3fc color) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderTexture(0, new ResourceLocation("vivecraft:textures/white.png"));

        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Vec3i iColor = new Vec3i((int) (color.x() * 255), (int) (color.y() * 255), (int) (color.z() * 255));
        Vec3 start = new Vec3(position.x(), position.y(), position.z()).add(MathUtils.FORWARD_D.scale(size * 0.5F));
        Vec3 end =  new Vec3(position.x(), position.y(), position.z()).add(MathUtils.BACK_D.scale(size * 0.5F));
        RenderHelper.renderBox(bufferbuilder, start, end, size, size, iColor, (byte) 255, poseStack);

        BufferUploader.drawWithShader(bufferbuilder.end());
    }
}
