package org.vivecraft.mixin.client_vr.tutorial;

import net.minecraft.client.tutorial.PunchTreeTutorialStepInstance;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;

@Mixin(PunchTreeTutorialStepInstance.class)
public class PunchTreeTutorialStepInstanceVRMixin {
    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/TutorialToast;<init>(Lnet/minecraft/client/gui/components/toasts/TutorialToast$Icons;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;Z)V"), index = 2)
    private Component vivecraft$alterDescription(Component description) {
        if (!VRState.VR_RUNNING || ClientDataHolderVR.getInstance().vrSettings.seated) {
            return description;
        } else {
            return Component.translatable("tutorial.find_tree.description");
        }
    }
}
