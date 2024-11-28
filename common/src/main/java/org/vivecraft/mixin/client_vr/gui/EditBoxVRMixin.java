package org.vivecraft.mixin.client_vr.gui;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import javax.annotation.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.settings.VRSettings;

@Mixin(EditBox.class)
public abstract class EditBoxVRMixin extends AbstractWidget {

    @Shadow
    @Nullable
    private Component hint;

    @Shadow
    @Final
    private Font font;

    @Shadow
    private int textColorUneditable;

    @Shadow
    public abstract int getInnerWidth();

    public EditBoxVRMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(III)I"))
    private void vivecraft$renderKeyboardHint(
        GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci,
        @Local String content, @Local(ordinal = 4) int xPos, @Local(ordinal = 5) int yPos)
    {
        if (VRState.VR_RUNNING && content.isEmpty() && !ClientDataHolderVR.getInstance().vrSettings.seated && !KeyboardHandler.SHOWING) {
            if (this.hint == null || this.isFocused()) {
                // limit text to field size
                String fullString = I18n.get("vivecraft.message.openKeyboard");
                String cutString = this.font.plainSubstrByWidth(fullString, this.getInnerWidth());
                guiGraphics.drawString(this.font, fullString.equals(cutString) ? cutString : cutString + "...", xPos,
                    yPos, this.textColorUneditable);
            }
        }
    }

    @Inject(method = "setFocused", at = @At("HEAD"))
    private void vivecraft$autoOpenKeyboard(boolean focused, CallbackInfo ci) {
        if (VRState.VR_RUNNING && focused && !(Minecraft.getInstance().screen instanceof InBedChatScreen)) {
            if (ClientDataHolderVR.getInstance().vrSettings.autoOpenKeyboard == VRSettings.AutoOpenKeyboard.ON ||
                (Minecraft.getInstance().screen instanceof ChatScreen &&
                    ClientDataHolderVR.getInstance().vrSettings.autoOpenKeyboard == VRSettings.AutoOpenKeyboard.CHAT
                ))
            {
                KeyboardHandler.setOverlayShowing(true);
            }
        }
    }

    @Inject(method = "onClick", at = @At(value = "HEAD"))
    private void vivecraft$openKeyboard(CallbackInfo ci) {
        if (VRState.VR_RUNNING) {
            KeyboardHandler.setOverlayShowing(true);
        }
    }
}
