package org.vivecraft.mixin.client_vr.tutorial;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.MovementTutorialStepInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.provider.MCVR;

import java.util.HashSet;
import java.util.Set;

@Mixin(MovementTutorialStepInstance.class)
public class MovementTutorialStepInstanceVRMixin {

    @Shadow
    private int moveCompleted;

    @Shadow
    private boolean moved;

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/TutorialToast;<init>(Lnet/minecraft/client/gui/components/toasts/TutorialToast$Icons;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;Z)V", ordinal = 0), index = 1)
    private Component vivecraft$alterMovementTitle(Component title) {
        if (!VRState.VR_RUNNING || ClientDataHolderVR.getInstance().vrSettings.seated) {
            return title;
        }
        // find the currently used movement binding
        if (MCVR.get().getInputAction(VivecraftVRMod.INSTANCE.keyFreeMoveStrafe).isActive()) {
            // moveStrafe active
            return new TranslatableComponent("vivecraft.toasts.move1", new TextComponent(MCVR.get()
                .getOriginName(MCVR.get().getInputAction(VivecraftVRMod.INSTANCE.keyFreeMoveStrafe).getLastOrigin()))
                .withStyle(ChatFormatting.BOLD));
        } else if (MCVR.get().getInputAction(VivecraftVRMod.INSTANCE.keyFreeMoveRotate).isActive()) {
            // moveRotate active
            return new TranslatableComponent("vivecraft.toasts.move1", new TextComponent(MCVR.get()
                .getOriginName(MCVR.get().getInputAction(VivecraftVRMod.INSTANCE.keyFreeMoveRotate).getLastOrigin()))
                .withStyle(ChatFormatting.BOLD));
        } else if (MCVR.get().getInputAction(Minecraft.getInstance().options.keyUp).isActive() ||
            MCVR.get().getInputAction(Minecraft.getInstance().options.keyDown).isActive() ||
            MCVR.get().getInputAction(Minecraft.getInstance().options.keyLeft).isActive() ||
            MCVR.get().getInputAction(Minecraft.getInstance().options.keyRight).isActive()
        )
        {
            // individual movement bindings
            Set<String> buttons = new HashSet<>();
            if (MCVR.get().getInputAction(Minecraft.getInstance().options.keyUp).isActive()) {
                buttons.add(MCVR.get()
                    .getOriginName(MCVR.get().getInputAction(Minecraft.getInstance().options.keyUp).getLastOrigin()));
            }
            if (MCVR.get().getInputAction(Minecraft.getInstance().options.keyDown).isActive()) {
                buttons.add(MCVR.get()
                    .getOriginName(MCVR.get().getInputAction(Minecraft.getInstance().options.keyDown).getLastOrigin()));
            }
            if (MCVR.get().getInputAction(Minecraft.getInstance().options.keyLeft).isActive()) {
                buttons.add(MCVR.get()
                    .getOriginName(MCVR.get().getInputAction(Minecraft.getInstance().options.keyLeft).getLastOrigin()));
            }
            if (MCVR.get().getInputAction(Minecraft.getInstance().options.keyRight).isActive()) {
                buttons.add(MCVR.get().getOriginName(
                    MCVR.get().getInputAction(Minecraft.getInstance().options.keyRight).getLastOrigin()));
            }

            String[] stringArray = buttons.toArray(new String[0]);
            return switch (buttons.size()) {
                case 1 -> new TranslatableComponent(
                    "vivecraft.toasts.move1",
                    new TextComponent(stringArray[0]).withStyle(ChatFormatting.BOLD)
                );
                case 2 -> new TranslatableComponent(
                    "vivecraft.toasts.move2",
                    new TextComponent(stringArray[0]).withStyle(ChatFormatting.BOLD),
                    new TextComponent(stringArray[1]).withStyle(ChatFormatting.BOLD)
                );
                case 3 -> new TranslatableComponent(
                    "vivecraft.toasts.move3",
                    new TextComponent(stringArray[0]).withStyle(ChatFormatting.BOLD),
                    new TextComponent(stringArray[1]).withStyle(ChatFormatting.BOLD),
                    new TextComponent(stringArray[2]).withStyle(ChatFormatting.BOLD)
                );
                case 4 -> new TranslatableComponent(
                    "vivecraft.toasts.move4",
                    new TextComponent(stringArray[0]).withStyle(ChatFormatting.BOLD),
                    new TextComponent(stringArray[1]).withStyle(ChatFormatting.BOLD),
                    new TextComponent(stringArray[2]).withStyle(ChatFormatting.BOLD),
                    new TextComponent(stringArray[3]).withStyle(ChatFormatting.BOLD)
                );
                default -> new TextComponent("");
            };
        } else if (MCVR.get().getInputAction(VivecraftVRMod.INSTANCE.keyTeleportFallback).isActive()) {
            // teleport fallback
            return new TranslatableComponent("vivecraft.toasts.move1", new TextComponent(MCVR.get()
                .getOriginName(MCVR.get().getInputAction(VivecraftVRMod.INSTANCE.keyTeleportFallback).getLastOrigin()))
                .withStyle(ChatFormatting.BOLD));
        } else if (MCVR.get().getInputAction(VivecraftVRMod.INSTANCE.keyTeleport).isActive()) {
            // teleport
            return new TranslatableComponent("vivecraft.toasts.teleport", new TextComponent(MCVR.get()
                .getOriginName(MCVR.get().getInputAction(VivecraftVRMod.INSTANCE.keyTeleport).getLastOrigin()))
                .withStyle(ChatFormatting.BOLD));
        }
        return title;
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/TutorialToast;<init>(Lnet/minecraft/client/gui/components/toasts/TutorialToast$Icons;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;Z)V", ordinal = 0), index = 2)
    private Component vivecraft$alterMovementDescription(Component description) {
        if (!VRState.VR_RUNNING || ClientDataHolderVR.getInstance().vrSettings.seated) {
            return description;
        }

        if (MCVR.get().getInputAction(Minecraft.getInstance().options.keyJump).isActive()) {
            return new TranslatableComponent("tutorial.move.description", new TextComponent(MCVR.get()
                .getOriginName(MCVR.get().getInputAction(Minecraft.getInstance().options.keyJump).getLastOrigin()))
                .withStyle(ChatFormatting.BOLD));
        }
        return description;
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/TutorialToast;<init>(Lnet/minecraft/client/gui/components/toasts/TutorialToast$Icons;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;Z)V", ordinal = 1), index = 2)
    private Component vivecraft$alterLookDescription(Component title) {
        if (!VRState.VR_RUNNING || ClientDataHolderVR.getInstance().vrSettings.seated) {
            return title;
        } else {
            return new TranslatableComponent("vivecraft.toasts.point_controller", new TranslatableComponent(
                ClientDataHolderVR.getInstance().vrSettings.reverseHands ? "vivecraft.toasts.point_controller.left" :
                    "vivecraft.toasts.point_controller.right").withStyle(ChatFormatting.BOLD));
        }
    }

    @Inject(method = "onInput", at = @At("TAIL"))
    private void vivecraft$addTeleport(CallbackInfo ci) {
        this.moved |= VivecraftVRMod.INSTANCE.keyTeleport.isDown();
    }

    @Inject(method = "onMouse", at = @At("HEAD"), cancellable = true)
    private void vivecraft$onlyAfterMove(CallbackInfo ci) {
        if (this.moveCompleted == -1) {
            ci.cancel();
        }
    }
}
