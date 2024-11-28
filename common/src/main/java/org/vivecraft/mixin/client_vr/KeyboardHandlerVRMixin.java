package org.vivecraft.mixin.client_vr;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.settings.VRHotkeys;

import java.io.File;
import java.util.function.Consumer;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerVRMixin {

    @Final
    @Shadow
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At(value = "FIELD", target = "Lnet/minecraft/client/KeyboardHandler;debugCrashKeyTime:J", ordinal = 0), cancellable = true)
    private void vivecraft$handleVivecraftKeys(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
            if (org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler.SHOWING) {
                org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler.setOverlayShowing(false);

                // close chat with the keyboard
                if (this.minecraft.screen instanceof ChatScreen) {
                    this.minecraft.screen.onClose();
                }
                ci.cancel();
            }

            if (RadialHandler.isShowing()) {
                RadialHandler.setOverlayShowing(false, null);
                ci.cancel();
            }
        }

        if (VRHotkeys.handleKeyboardInputs(key, scanCode, action, modifiers)) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V"))
    private void vivecraft$markScreenshot(
        File gameDirectory, RenderTarget buffer, Consumer<Component> messageConsumer, Operation<Void> original)
    {
        if (!VRState.VR_RUNNING) {
            original.call(gameDirectory, buffer, messageConsumer);
        } else {
            ClientDataHolderVR.getInstance().grabScreenShot = true;
        }
    }

    @Inject(method = "keyPress", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;hideGui:Z", ordinal = 1, shift = At.Shift.AFTER))
    private void vivecraft$saveHideGuiOption(CallbackInfo ci) {
        ClientDataHolderVR.getInstance().vrSettings.saveOptions();
    }
}
