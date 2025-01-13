package org.vivecraft.mixin.client_vr.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
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
    @Final
    private Font font;

    @Shadow
    private int textColorUneditable;

    @Shadow
    public abstract int getInnerWidth();

    @Shadow
    private String suggestion;

    @Shadow
    private String value;

    public EditBoxVRMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderButton", at = @At(value = "INVOKE", target = "Ljava/lang/String;length()I", ordinal = 1))
    private void vivecraft$renderKeyboardHint(
        PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci,
        @Local String content, @Local(ordinal = 5) int xPos, @Local(ordinal = 6) int yPos)
    {
        if (VRState.VR_RUNNING && !ClientDataHolderVR.getInstance().vrSettings.seated && !KeyboardHandler.SHOWING &&
            content.isEmpty())
        {
            if ((this.value.isEmpty() && (this.suggestion == null || this.suggestion.isEmpty())) || this.isFocused()) {
                // limit text to field size
                String fullString = I18n.get("vivecraft.message.openKeyboard");
                String cutString = this.font.plainSubstrByWidth(fullString, this.getInnerWidth());
                this.font.drawShadow(poseStack, fullString.equals(cutString) ? cutString : cutString + "...", xPos,
                    yPos, this.textColorUneditable);
            }
        }
    }

    @Inject(method = "setFocus", at = @At("HEAD"))
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

    @ModifyExpressionValue(method = "mouseClicked", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/EditBox;canLoseFocus:Z"))
    private boolean vivecraft$openKeyboard(boolean canLoseFocus, @Local boolean hovered) {
        if (VRState.VR_RUNNING && hovered) {
            KeyboardHandler.setOverlayShowing(true);
        }
        return canLoseFocus || !this.isFocused();
    }
}
