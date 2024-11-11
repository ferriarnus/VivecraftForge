package org.vivecraft.client_vr.render.helpers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.gameplay.trackers.TelescopeTracker;
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

    /**
     * renders debug stuff
     * @param poseStack PoseStack to use for positioning
     * @param partialTick current partial tick
     */
    public static void renderDebug(PoseStack poseStack, float partialTick) {
        if (DATA_HOLDER.vrSettings.renderDeviceAxes) {
            renderDeviceAxes(poseStack, DATA_HOLDER.vrPlayer.vrdata_world_render);
        }
        if (DATA_HOLDER.vrSettings.renderVrPlayerAxes) {
            renderPlayerAxes(poseStack, partialTick);
        }

        if (GuiHandler.GUI_POS_WORLD != null) {
            VRData data = DATA_HOLDER.vrPlayer.getVRDataWorld();

            Vector3f guiPos = MathUtils.subtractToVector3f(GuiHandler.GUI_POS_WORLD,
                RenderHelper.getSmoothCameraPosition(DATA_HOLDER.currentPass, data));
            guiPos.add(GuiHandler.GUI_ROTATION_WORLD.transformDirection(GuiHandler.GUI_OFFSET_WORLD, new Vector3f()));

            addCube(poseStack, guiPos, RED);
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
                        addAxes(poseStack, bufferbuilder, playerPos, info.rightArmPos, info.rightArmRot,
                            info.rightArmQuat);
                        addAxes(poseStack, bufferbuilder, playerPos, info.leftArmPos, info.leftArmRot,
                            info.leftArmQuat);
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

        if (DATA_HOLDER.vr.hasFBT()) {
            list.add(data.waist);
            list.add(data.foot_left);
            list.add(data.foot_right);
        }
        if (DATA_HOLDER.vr.hasExtendedFBT()) {
            list.add(data.elbow_left);
            list.add(data.knee_left);
            list.add(data.elbow_right);
            list.add(data.knee_right);
        }

        list.forEach(p -> addAxes(poseStack, bufferbuilder, data, p));

        BufferUploader.drawWithShader(bufferbuilder.end());
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
        Vector3f forward = pose.getDirection().mul(0.25F);
        Vector3f up = pose.getCustomVector(MathUtils.UP).mul(0.25F);
        Vector3f right = pose.getCustomVector(MathUtils.RIGHT).mul(0.25F);

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

        Vector3f forward = dir.mul(0.25F, new Vector3f());
        Vector3f up = rot.transform(MathUtils.UP, new Vector3f()).mul(0.25F);
        Vector3f right = rot.transform(MathUtils.RIGHT, new Vector3f()).mul(0.25F);

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

    private static void addCube(PoseStack poseStack, Vector3fc position, Vector3fc color) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderTexture(0, new ResourceLocation("vivecraft:textures/white.png"));

        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Vec3i iColor = new Vec3i((int) color.x() * 255, (int) color.y() * 255, (int) color.z() * 255);
        Vec3 start = new Vec3(position.x(), position.y(), position.z()).add(MathUtils.FORWARD_D.scale(0.1));
        Vec3 end =  new Vec3(position.x(), position.y(), position.z()).add(MathUtils.BACK_D.scale(0.1));
        RenderHelper.renderBox(bufferbuilder, start, end, 0.2F, 0.2F, iColor, (byte) 255, poseStack);

        BufferUploader.drawWithShader(bufferbuilder.end());
    }
}
