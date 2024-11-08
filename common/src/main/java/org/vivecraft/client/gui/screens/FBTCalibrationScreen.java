package org.vivecraft.client.gui.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.MethodHolder;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.render.helpers.RenderHelper;
import org.vivecraft.client_vr.settings.AutoCalibration;
import org.vivecraft.common.utils.MathUtils;

public class FBTCalibrationScreen extends Screen {

    private final Screen parent;

    private final boolean wasFbtCalibrated;
    private final boolean wasFbtExtendedCalibrated;
    private boolean calibrated = false;

    private boolean rightHandAtPosition = false;
    private boolean leftHandAtPosition = false;

    private Vector3f rightHand = new Vector3f();
    private Vector3f leftHand = new Vector3f();

    private float yaw;

    public FBTCalibrationScreen(Screen parent) {
        super(Component.translatable("vivecraft.options.screen.fbtcalibration"));
        this.parent = parent;
        this.wasFbtCalibrated = ClientDataHolderVR.getInstance().vrSettings.fbtCalibrated;
        this.wasFbtExtendedCalibrated = ClientDataHolderVR.getInstance().vrSettings.fbtExtendedCalibrated;
        ClientDataHolderVR.getInstance().vrSettings.fbtCalibrated = false;
        ClientDataHolderVR.getInstance().vrSettings.fbtExtendedCalibrated = false;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), p -> {
                this.minecraft.setScreen(this.parent);
            })
            .pos(this.width / 2 - 75, this.height - 32)
            .width(150)
            .build());
        if (VRState.VR_RUNNING) {
            this.yaw = ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_post.hmd.getYawRad();
        }
    }

    @Override
    public void removed() {
        if (!this.calibrated) {
            // restore previous state when canceling
            ClientDataHolderVR.getInstance().vrSettings.fbtCalibrated = this.wasFbtCalibrated;
            ClientDataHolderVR.getInstance().vrSettings.fbtExtendedCalibrated = this.wasFbtExtendedCalibrated;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("vivecraft.message.fbtcalibration.1"),
            this.width / 2, 30, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("vivecraft.message.fbtcalibration.2"),
            this.width / 2, 40, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("vivecraft.message.fbtcalibration.3"),
            this.width / 2, 50, 0xFFFFFF);

        checkPosition();

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        Vec3i color = new Vec3i(128, 64, 64);
        Vec3i colorActive = new Vec3i(64, 128, 64);
        byte alpha = (byte) 200;

        if (this.leftHandAtPosition && this.rightHandAtPosition) {
            color = colorActive;
        }

        // move to screen center
        float min = Math.min(guiGraphics.guiWidth(), guiGraphics.guiHeight()) / (4F * 16F);
        poseStack.translate(guiGraphics.guiWidth() / 2F, guiGraphics.guiHeight() - 32F, 0);
        poseStack.scale(min, -min, min);
        poseStack.mulPose(Axis.YP.rotation(Mth.PI));

        BufferBuilder builder = Tesselator.getInstance().getBuilder();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // arms outline
        builder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        builder.vertex(poseStack.last().pose(), 4, 24, -100)
            .color(1F, 1F, 1F, 1F).endVertex();
        builder.vertex(poseStack.last().pose(), 16, 24, -100)
            .color(1F, 1F, 1F, 1F).endVertex();
        builder.vertex(poseStack.last().pose(), 16, 20, -100)
            .color(1F, 1F, 1F, 1F).endVertex();
        builder.vertex(poseStack.last().pose(), 4, 20, -100)
            .color(1F, 1F, 1F, 1F).endVertex();
        builder.vertex(poseStack.last().pose(), 4, 24, -100)
            .color(1F, 1F, 1F, 1F).endVertex();

        // connecting line
        builder.vertex(poseStack.last().pose(), 4, 24, -100)
            .color(1F, 1F, 1F, 0F).endVertex();
        builder.vertex(poseStack.last().pose(), -4, 24, -100)
            .color(1F, 1F, 1F, 0F).endVertex();

        builder.vertex(poseStack.last().pose(), -4, 24, -100)
            .color(1F, 1F, 1F, 1F).endVertex();
        builder.vertex(poseStack.last().pose(), -16, 24, -100)
            .color(1F, 1F, 1F, 1F).endVertex();
        builder.vertex(poseStack.last().pose(), -16, 20, -100)
            .color(1F, 1F, 1F, 1F).endVertex();
        builder.vertex(poseStack.last().pose(), -4, 20, -100)
            .color(1F, 1F, 1F, 1F).endVertex();
        builder.vertex(poseStack.last().pose(), -4, 24, -100)
            .color(1F, 1F, 1F, 1F).endVertex();

        BufferUploader.drawWithShader(builder.end());

        if (VRState.VR_RUNNING) {
            poseStack.mulPose(Axis.YP.rotation(
                this.yaw - ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_post.hmd.getYawRad()));
        }

        // body overlay
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // legs
        RenderHelper.renderBox(builder,
            new Vec3(2, 0, 0), new Vec3(2, 12, 0),
            4, 4, color, alpha, poseStack);
        RenderHelper.renderBox(builder,
            new Vec3(-2, 0, 0), new Vec3(-2, 12, 0),
            4, 4, color, alpha, poseStack);
        // body
        RenderHelper.renderBox(builder,
            new Vec3(0, 12, 0), new Vec3(0, 24, 0),
            8, 4, color, alpha, poseStack);

        // head
        RenderHelper.renderBox(builder,
            new Vec3(0, 24, 0), new Vec3(0, 32, 0),
            8, 8, color, alpha, poseStack);

        // arms
        RenderHelper.renderBox(builder,
            new Vec3(6, 22, 0).subtract(this.leftHand.x*2F, this.leftHand.y*2F, this.leftHand.z*2F),
            new Vec3(6, 22, 0).add(this.leftHand.x*10F, this.leftHand.y*10F, this.leftHand.z*10F),
            4, 4, this.leftHandAtPosition ? colorActive : color, (byte) 200, poseStack);
        RenderHelper.renderBox(builder,
            new Vec3(-6, 22, 0).subtract(this.rightHand.x*2F, this.rightHand.y*2F, this.rightHand.z*2F),
            new Vec3(-6, 22, 0).add(this.rightHand.x*10F, this.rightHand.y*10F, this.rightHand.z*10F),
            4, 4, this.rightHandAtPosition ? colorActive : color, (byte) 200, poseStack);

        BufferUploader.drawWithShader(builder.end());

        if (VRState.VR_RUNNING) {
            // TODO FBT these bindings are not what I thought they would be
            if (this.leftHandAtPosition && this.rightHandAtPosition &&
                (GuiHandler.KEY_LEFT_CLICK.isDown() &&GuiHandler.KEY_RIGHT_CLICK.isDown()) ||
                (MethodHolder.isKeyDown(GLFW.GLFW_KEY_ENTER)))
            {
                AutoCalibration.calibrateManual();
                ClientDataHolderVR.getInstance().vr.calibrateFBT();
                ClientDataHolderVR.getInstance().vrSettings.saveOptions();
                this.calibrated = true;
                this.minecraft.setScreen(this.parent);
            }
        }
    }

    private void checkPosition() {
        if (!VRState.VR_RUNNING) {
            this.rightHand.set(MathUtils.DOWN);
            this.leftHand.set(MathUtils.DOWN);
            return;
        }

        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

        Vector3f hmdPosAvg = dataHolder.vr.hmdPivotHistory.averagePosition(0.5D);

        float height = hmdPosAvg.y / AutoCalibration.DEFAULT_HEIGHT;
        float scale = height * 0.9375F * dataHolder.vrPlayer.getVRDataWorld().worldScale;

        this.rightHand = dataHolder.vrPlayer.vrdata_room_post.getController(0).getPositionF()
            .sub(hmdPosAvg.x , 1.375F * scale, hmdPosAvg.z)
            .rotateY(this.yaw)
            .add(scale * 0.375F, 0F, 0F)
            .normalize();
        this.leftHand = dataHolder.vrPlayer.vrdata_room_post.getController(1).getPositionF()
            .sub(hmdPosAvg.x , 1.375F * scale, hmdPosAvg.z)
            .rotateY(this.yaw)
            .add(-scale * 0.375F, 0F, 0F)
            .normalize();

        boolean rightHandNew = this.rightHand.dot(MathUtils.RIGHT) > 0.9F;
        boolean leftHandNew = this.leftHand.dot(MathUtils.LEFT) > 0.9F;

        if (!this.rightHandAtPosition && rightHandNew) {
            dataHolder.vr.triggerHapticPulse(ControllerType.RIGHT, 0.01F, 100, 1F);
        }

        if (!this.leftHandAtPosition && leftHandNew) {
            dataHolder.vr.triggerHapticPulse(ControllerType.LEFT, 0.01F, 100, 1F);
        }

        this.rightHandAtPosition = rightHandNew;
        this.leftHandAtPosition = leftHandNew;
    }
}
