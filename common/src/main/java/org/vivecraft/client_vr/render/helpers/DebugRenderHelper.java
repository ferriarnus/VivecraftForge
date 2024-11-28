package org.vivecraft.client_vr.render.helpers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vivecraft.client.VRPlayersClient;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.gameplay.trackers.TelescopeTracker;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.common.utils.MathUtils;

import java.util.ArrayList;
import java.util.List;

public class DebugRenderHelper {

    private static final ClientDataHolderVR DATA_HOLDER = ClientDataHolderVR.getInstance();
    private static final Minecraft MC = Minecraft.getInstance();

    private static final Vector3f RED = new Vector3f(1F,0F,0F);
    private static final Vector3f GREEN = new Vector3f(0F,1F,0F);
    private static final Vector3f BLUE = new Vector3f(0F,0F,1F);

    public static void renderDebug(PoseStack poseStack, float partialTick) {
        if (DATA_HOLDER.vrSettings.renderDeviceAxes) {
            renderDeviceAxes(poseStack, DATA_HOLDER.vrPlayer.vrdata_world_render);
        }
        if (DATA_HOLDER.vrSettings.renderVrPlayerAxes) {
            renderPlayerAxes(poseStack, partialTick);
        }
    }

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
                }
            }
            if (bufferbuilder != null) {
                BufferUploader.drawWithShader(bufferbuilder.end());
            }
        }
    }

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

        list.forEach(p -> addAxes(poseStack, bufferbuilder, data, p));

        BufferUploader.drawWithShader(bufferbuilder.end());
    }

    private static void addAxes(
        PoseStack poseStack, BufferBuilder bufferbuilder, VRData data, VRData.VRDevicePose pose)
    {
        Vector3f position = pose.getPosition()
            .subtract(RenderHelper.getSmoothCameraPosition(DATA_HOLDER.currentPass, data)).toVector3f();
        Vector3f forward = pose.getDirection().mul(0.25F);
        Vector3f up = pose.getCustomVector(MathUtils.UP).mul(0.25F);
        Vector3f right = pose.getCustomVector(MathUtils.RIGHT).mul(0.25F);

        addLine(poseStack, bufferbuilder, position, forward, BLUE);
        addLine(poseStack, bufferbuilder, position, up, GREEN);
        addLine(poseStack, bufferbuilder, position, right, RED);
    }

    private static void addAxes(
        PoseStack poseStack, BufferBuilder bufferbuilder, Vector3fc playerPos, Vector3fc devicePos, Vector3fc dir,
        Quaternionfc rot)
    {
        Vector3f position = playerPos.add(devicePos, new Vector3f());

        Vector3f forward = dir.mul(0.25F, new Vector3f());
        Vector3f up = rot.transform(MathUtils.UP, new Vector3f()).mul(0.25F);
        Vector3f right = rot.transform(MathUtils.RIGHT, new Vector3f()).mul(0.25F);

        addLine(poseStack, bufferbuilder, position, forward, BLUE);
        addLine(poseStack, bufferbuilder, position, up, GREEN);
        addLine(poseStack, bufferbuilder, position, right, RED);
    }

    private static void addLine(PoseStack poseStack, BufferBuilder bufferbuilder, Vector3f position, Vector3f dir, Vector3f color) {
        bufferbuilder.vertex(poseStack.last().pose(), position.x, position.y, position.z)
            .color(color.x, color.y, color.z, 0.0F).endVertex();
        bufferbuilder.vertex(poseStack.last().pose(), position.x, position.y, position.z)
            .color(color.x, color.y, color.z, 1.0F).endVertex();
        bufferbuilder.vertex(poseStack.last().pose(), position.x + dir.x, position.y + dir.y, position.z + dir.z)
            .color(color.x, color.y, color.z, 1.0F).endVertex();
        bufferbuilder.vertex(poseStack.last().pose(), position.x + dir.x, position.y + dir.y, position.z + dir.z)
            .color(color.x, color.y, color.z, 0.0F).endVertex();
    }

}
