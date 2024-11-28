package org.vivecraft.mixin.client_vr.blaze3d.platform;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.WindowExtension;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;

@Mixin(Window.class)
public abstract class WindowVRMixin implements WindowExtension {

    @Shadow
    private int width;

    @Shadow
    private int height;

    // TODO: this doesn't disable sodium extras adaptive sync
    @ModifyVariable(method = "updateVsync", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private boolean vivecraft$overwriteVsync(boolean vsync) {
        return VRState.VR_RUNNING ? false : vsync;
    }

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void vivecraft$getVivecraftWidth(CallbackInfoReturnable<Integer> cir) {
        if (VRState.VR_RUNNING) {
            cir.setReturnValue(Minecraft.getInstance().getMainRenderTarget().viewWidth);
        }
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void vivecraft$getVivecraftHeight(CallbackInfoReturnable<Integer> cir) {
        if (VRState.VR_RUNNING) {
            cir.setReturnValue(Minecraft.getInstance().getMainRenderTarget().viewHeight);
        }
    }

    @Inject(method = "getScreenWidth", at = @At("HEAD"), cancellable = true)
    private void vivecraft$getVivecraftScreenWidth(CallbackInfoReturnable<Integer> cir) {
        if (VRState.VR_RUNNING) {
            cir.setReturnValue(GuiHandler.GUI_WIDTH);
        }
    }

    @Inject(method = "getScreenHeight", at = @At("HEAD"), cancellable = true)
    private void vivecraft$getVivecraftScreenHeight(CallbackInfoReturnable<Integer> cir) {
        if (VRState.VR_RUNNING) {
            cir.setReturnValue(GuiHandler.GUI_HEIGHT);
        }
    }


    @Inject(method = "getGuiScaledHeight", at = @At("HEAD"), cancellable = true)
    private void vivecraft$getScaledHeight(CallbackInfoReturnable<Integer> cir) {
        if (VRState.VR_RUNNING) {
            cir.setReturnValue(
                Minecraft.getInstance().screen == null && ClientDataHolderVR.getInstance().vrSettings.hudMaxScale ?
                    GuiHandler.SCALED_HEIGHT_MAX : GuiHandler.SCALED_HEIGHT);
        }
    }

    @Inject(method = "getGuiScaledWidth", at = @At("HEAD"), cancellable = true)
    private void vivecraft$getScaledWidth(CallbackInfoReturnable<Integer> cir) {
        if (VRState.VR_RUNNING) {
            cir.setReturnValue(
                Minecraft.getInstance().screen == null && ClientDataHolderVR.getInstance().vrSettings.hudMaxScale ?
                    GuiHandler.SCALED_WIDTH_MAX : GuiHandler.SCALED_WIDTH);
        }
    }

    @Inject(method = "getGuiScale", at = @At("HEAD"), cancellable = true)
    private void vivecraft$getScaleFactor(CallbackInfoReturnable<Double> cir) {
        if (VRState.VR_RUNNING) {
            cir.setReturnValue(
                Minecraft.getInstance().screen == null && ClientDataHolderVR.getInstance().vrSettings.hudMaxScale ?
                    (double) GuiHandler.GUI_SCALE_FACTOR_MAX : (double) GuiHandler.GUI_SCALE_FACTOR);
        }
    }

    @Inject(method = "onResize", at = @At("HEAD"))
    private void vivecraft$resizeFrameBuffers(CallbackInfo ci) {
        if (VRState.VR_INITIALIZED) {
            ClientDataHolderVR.getInstance().vrRenderer.resizeFrameBuffers("Main Window Resized");
        }
    }

    @Override
    @Unique
    public int vivecraft$getActualScreenHeight() {
        return this.height;
    }

    @Override
    @Unique
    public int vivecraft$getActualScreenWidth() {
        return this.width;
    }
}
