package org.vivecraft.mixin.client_vr;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.WindowExtension;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.provider.MCVR;

@Mixin(MouseHandler.class)
public class MouseHandlerVRMixin {

    @Shadow
    private boolean mouseGrabbed;
    @Final
    @Shadow
    private Minecraft minecraft;

    @WrapOperation(method = "onPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"))
    private boolean vivecraft$checkNull(LocalPlayer instance, Operation<Boolean> original) {
        return instance != null && original.call(instance);
    }


    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void vivecraft$noTurnStanding(CallbackInfo ci) {
        if (!VRState.VR_RUNNING) {
            return;
        }

        if (!ClientDataHolderVR.getInstance().vrSettings.seated) {
            // call the tutorial before canceling
            // controller movement
            int mainController = ClientDataHolderVR.getInstance().vrSettings.reverseHands ? 1 : 0;
            float deltaMovement = (float) MCVR.get().controllerForwardHistory[mainController].averagePosition(0.2).normalize().dot(MCVR.get().controllerForwardHistory[mainController].averagePosition(1.0).normalize());

            this.minecraft.getTutorial().onMouse(1F - deltaMovement, 0);
            ci.cancel();
        }
    }

    // cancel after tutorial call
    @Inject(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/tutorial/Tutorial;onMouse(DD)V", shift = At.Shift.AFTER), cancellable = true)
    private void vivecraft$noTurnSeated(CallbackInfo ci) {
        if (!VRState.VR_RUNNING) {
            return;
        }

        ci.cancel();
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void vivecraft$noMouseGrab(CallbackInfo ci) {
        if (!VRState.VR_RUNNING) {
            return;
        }

        if (!ClientDataHolderVR.getInstance().vrSettings.seated) {
            this.mouseGrabbed = true;
            ci.cancel();
        }
    }

    @Inject(method = "releaseMouse", at = @At(value = "HEAD"), cancellable = true)
    private void vivecraft$noMouseReleaseMovement(CallbackInfo ci) {
        if (!VRState.VR_RUNNING) {
            return;
        }

        if (!ClientDataHolderVR.getInstance().vrSettings.seated) {
            this.mouseGrabbed = false;
            ci.cancel();
        }
    }

    // we change the screen size different from window size, so need to modify the mouse position on grab/release
    @ModifyArg(method = {"grabMouse", "releaseMouse"}, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(JIDD)V"), index = 2)
    private double vivecraft$modifyXCenter(double x) {
        return VRState.VR_RUNNING
               ? (double) ((WindowExtension) (Object) this.minecraft.getWindow()).vivecraft$getActualScreenWidth() / 2
               : x;
    }

    @ModifyArg(method = {"grabMouse", "releaseMouse"}, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(JIDD)V"), index = 3)
    private double vivecraft$modifyYCenter(double y) {
        return VRState.VR_RUNNING
               ? (double) ((WindowExtension) (Object) this.minecraft.getWindow()).vivecraft$getActualScreenHeight() / 2
               : y;
    }

    // we change the screen size different from window size, so adapt move events to the emulated size
    @ModifyVariable(method = "onMove", at = @At(value = "HEAD"), ordinal = 0, argsOnly = true)
    private double vivecraft$modifyX(double x) {
        if (VRState.VR_RUNNING) {
            x *= GuiHandler.GUI_WIDTH / (double) ((WindowExtension) (Object) this.minecraft.getWindow()).vivecraft$getActualScreenWidth();
        }
        return x;
    }

    @ModifyVariable(method = "onMove", at = @At(value = "HEAD"), ordinal = 1, argsOnly = true)
    private double vivecraft$modifyY(double y) {
        if (VRState.VR_RUNNING) {
            y *= (double) GuiHandler.GUI_HEIGHT / (double) ((WindowExtension) (Object) this.minecraft.getWindow()).vivecraft$getActualScreenHeight();
        }
        return y;
    }
}
