package org.vivecraft.mixin.client_vr.gui.screens;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.gui.screens.FBTCalibrationScreen;
import org.vivecraft.client.gui.settings.GuiQuickCommandsInGame;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.settings.AutoCalibration;
import org.vivecraft.client_vr.settings.VRHotkeys;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_vr.utils.external.jkatvr;

@Mixin(value = PauseScreen.class, priority = 900)
public abstract class PauseScreenVRMixin extends Screen {

    protected PauseScreenVRMixin(Component component) {
        super(component);
    }

    @Inject(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;", ordinal = 4))
    private void vivecraft$addTopButtons(CallbackInfo ci, @Local GridLayout.RowHelper rowHelper) {
        if (!VRState.VR_ENABLED || !ClientDataHolderVR.getInstance().vrSettings.modifyPauseMenu) {
            return;
        }
        // reset row to above
        // we hide 2 buttons but keep them in, so need to reset the RowHelper
        try {
            rowHelper.addChild(null, -2);
        } catch (IllegalArgumentException ignored) {
            // RowHelper doesn't actually allow negative offsets, but it does update the index before throwing this exception
        }

        // on a multiplayer server also add the social button
        if (!Minecraft.getInstance().isMultiplayerServer()) {
            rowHelper.addChild(new Button.Builder(Component.translatable("vivecraft.gui.chat"),
                (p) -> this.minecraft.setScreen(new ChatScreen(""))).width(98).build());
        } else {
            GridLayout gridWidgetChat_Social = new GridLayout();
            gridWidgetChat_Social.defaultCellSetting().paddingRight(1);
            GridLayout.RowHelper rowHelperChat_Social = gridWidgetChat_Social.createRowHelper(2);
            rowHelperChat_Social.addChild(new Button.Builder(Component.translatable("vivecraft.gui.chat"),
                (p) -> this.minecraft.setScreen(new ChatScreen(""))).width(48).build());

            rowHelperChat_Social.addChild(new Button.Builder(Component.translatable("vivecraft.gui.social"),
                (p) -> this.minecraft.setScreen(new SocialInteractionsScreen())).width(48).build());
            rowHelper.addChild(gridWidgetChat_Social);
        }

        rowHelper.addChild(new Button.Builder(Component.translatable("vivecraft.gui.commands"),
            (p) -> this.minecraft.setScreen(new GuiQuickCommandsInGame(this))).width(98).build());
    }

    // use the disconnect button as an anchor, and shift by -3 to shift before the addChild call
    @Inject(method = "createPauseMenu", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/client/gui/screens/PauseScreen;disconnectButton:Lnet/minecraft/client/gui/components/Button;", shift = At.Shift.BY, by = -3))
    private void vivecraft$addLowerButtons(CallbackInfo ci, @Local GridLayout.RowHelper rowHelper) {
        if (!VRState.VR_ENABLED || !ClientDataHolderVR.getInstance().vrSettings.modifyPauseMenu) {
            return;
        }
        GridLayout gridWidgetOverlay_Profiler = new GridLayout();
        gridWidgetOverlay_Profiler.defaultCellSetting().paddingRight(1);
        GridLayout.RowHelper rowHelperOverlay_Profiler = gridWidgetOverlay_Profiler.createRowHelper(2);
        rowHelperOverlay_Profiler.addChild(new Button.Builder(Component.translatable("vivecraft.gui.overlay"),
            (p) -> {
                this.minecraft.gui.getDebugOverlay().toggleOverlay();
                this.minecraft.setScreen(null);
            }).width(48).build());

        rowHelperOverlay_Profiler.addChild(new Button.Builder(Component.translatable("vivecraft.gui.profiler"),
            (p) -> {
                this.minecraft.gui.getDebugOverlay().toggleProfilerChart();
                this.minecraft.setScreen(null);
            }).width(48).build());

        rowHelper.addChild(gridWidgetOverlay_Profiler);

        rowHelper.addChild(new Button.Builder(Component.translatable("vivecraft.gui.screenshot"),
            (p) -> {
                this.minecraft.setScreen(null);
                ClientDataHolderVR.getInstance().grabScreenShot = true;
            }).width(98).build());

        if (!ClientDataHolderVR.getInstance().vrSettings.seated) {
            if (ClientDataHolderVR.getInstance().vr.hasFBT()) {
                rowHelper.addChild(new Button.Builder(
                    Component.translatable("vivecraft.options.screen.fbtcalibration.button"),
                        (p) -> this.minecraft.setScreen(new FBTCalibrationScreen(this)))
                    .width(98).build());
            } else {
                rowHelper.addChild(new Button.Builder(Component.translatable("vivecraft.gui.calibrateheight"),
                    (p) -> {
                        AutoCalibration.calibrateManual();
                        ClientDataHolderVR.getInstance().vrSettings.saveOptions();
                        this.minecraft.setScreen(null);
                    }).width(98).build());
            }
        }

        if (ClientDataHolderVR.KAT_VR) {
            rowHelper.addChild(new Button.Builder(Component.translatable("vivecraft.gui.alignkatwalk"),
                (p) -> {
                    jkatvr.resetYaw(ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_pre.hmd.getYaw());
                    this.minecraft.setScreen(null);
                }).width(98).build());
        }

        if (ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON ||
            ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY)
        {
            rowHelper.addChild(new Button.Builder(Component.translatable("vivecraft.gui.movethirdpersoncam"), (p) ->
            {
                if (!VRHotkeys.isMovingThirdPersonCam()) {
                    VRHotkeys.startMovingThirdPersonCam(1, VRHotkeys.Triggerer.MENUBUTTON);
                } else if (VRHotkeys.getMovingThirdPersonCamTriggerer() == VRHotkeys.Triggerer.MENUBUTTON) {
                    VRHotkeys.stopMovingThirdPersonCam();
                    ClientDataHolderVR.getInstance().vrSettings.saveOptions();
                }
            }).width(98).build());
        }
    }

    // hide buttons that we replace
    @WrapOperation(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;", ordinal = 2))
    private LayoutElement vivecraft$hideFeedback(
        GridLayout.RowHelper rowHelper, LayoutElement child, Operation<LayoutElement> original)
    {
        ((Button) child).visible = !VRState.VR_ENABLED || !ClientDataHolderVR.getInstance().vrSettings.modifyPauseMenu;
        return original.call(rowHelper, child);
    }

    @WrapOperation(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;", ordinal = 3))
    private LayoutElement vivecraft$hideReportBugs(
        GridLayout.RowHelper rowHelper, LayoutElement child, Operation<LayoutElement> original)
    {
        ((Button) child).visible = !VRState.VR_ENABLED || !ClientDataHolderVR.getInstance().vrSettings.modifyPauseMenu;
        return original.call(rowHelper, child);
    }
}
