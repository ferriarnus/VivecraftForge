package org.vivecraft.mixin.client_vr.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.GridWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.vivecraft.client.gui.settings.GuiMainVRSettings;
import org.vivecraft.client_vr.ClientDataHolderVR;

@Mixin(OptionsScreen.class)
public class OptionsScreenVRMixin extends Screen {
    protected OptionsScreenVRMixin(Component component) {
        super(component);
    }

    // replace FOV slider
    /*
    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;createButton(Lnet/minecraft/client/Options;III)Lnet/minecraft/client/gui/components/AbstractWidget;"))
    private AbstractWidget vivecraft$addVivecraftSettings(OptionInstance option, Options options, int i, int j, int k) {
        if (option == options.fov()) {
            return new Button.Builder( Component.translatable("vivecraft.options.screen.main.button"),  (p) ->
                {
                    Minecraft.getInstance().options.save();
                    Minecraft.getInstance().setScreen(new GuiMainVRSettings(this));
                })
                .size( k,  20)
                .pos(i,  j)
                .build();
        } else {
            return option.createButton(options, i, j, k);
        }
    }
    */

    // place below FOV slider
    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/GridWidget$RowHelper;addChild(Lnet/minecraft/client/gui/components/AbstractWidget;I)Lnet/minecraft/client/gui/components/AbstractWidget;"), index = 1)
    private int vivecraft$makeSpacer1wide(int layoutElement) {
        return ClientDataHolderVR.getInstance().vrSettings.vrSettingsButtonEnabled ? 1 : 2;
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/GridWidget$RowHelper;addChild(Lnet/minecraft/client/gui/components/AbstractWidget;I)Lnet/minecraft/client/gui/components/AbstractWidget;"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void vivecraft$addVivecraftSettingsLeft(CallbackInfo ci, GridWidget gridWidget, GridWidget.RowHelper rowHelper) {
        if (ClientDataHolderVR.getInstance().vrSettings.vrSettingsButtonEnabled && ClientDataHolderVR.getInstance().vrSettings.vrSettingsButtonPositionLeft) {
            vivecraft$addVivecraftButton(rowHelper);
        }
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/GridWidget$RowHelper;addChild(Lnet/minecraft/client/gui/components/AbstractWidget;I)Lnet/minecraft/client/gui/components/AbstractWidget;", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void vivecraft$addVivecraftSettingsRight(CallbackInfo ci, GridWidget gridLayout, GridWidget.RowHelper rowHelper) {
        if (ClientDataHolderVR.getInstance().vrSettings.vrSettingsButtonEnabled && !ClientDataHolderVR.getInstance().vrSettings.vrSettingsButtonPositionLeft) {
            vivecraft$addVivecraftButton(rowHelper);
        }
    }

    @Unique
    private void vivecraft$addVivecraftButton(GridWidget.RowHelper rowHelper) {
        rowHelper.addChild(new Button.Builder(Component.translatable("vivecraft.options.screen.main.button"), (p) ->
        {
            Minecraft.getInstance().options.save();
            Minecraft.getInstance().setScreen(new GuiMainVRSettings(this));
        })
            .build());
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/GridWidget;pack()V"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void vivecraft$noBigButtonsPlease(CallbackInfo ci, GridWidget gridWidget, GridWidget.RowHelper rowHelper) {
        for (GuiEventListener listener : gridWidget.children()) {
            if (listener instanceof AbstractWidget child && child.getWidth() > 150 && child instanceof Button button) {
                button.setWidth(150);
            }
        }
    }
}
