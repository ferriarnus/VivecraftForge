package org.vivecraft.mixin.client_vr;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.joml.Vector2fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction;

@Mixin(KeyboardInput.class)
public class KeyboardInputVRMixin extends Input {

    @Final
    @Shadow
    private Options options;
    @Unique
    private boolean vivecraft$wasAutoSprint = false;
    @Unique
    private boolean vivecraft$wasAnalogMovement = false;

    @Unique
    private float vivecraft$axisToDigital(float value) {
        if (value > 0.5f) {
            return 1F;
        } else if (value < -0.5f) {
            return -1F;
        } else {
            return 0;
        }
    }

    @Unique
    private float vivecraft$getAxisValue(KeyMapping keyBinding) {
        return Math.abs(MCVR.get().getInputAction(keyBinding).getAxis1DUseTracked());
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/KeyboardInput;calculateImpulse(ZZ)F", ordinal = 0))
    private void vivecraft$noMovementWhenClimbing(CallbackInfo ci, @Share("climbing") LocalBooleanRef climbing) {
        if (VRState.VR_RUNNING) {
            climbing.set(!Minecraft.getInstance().player.isInWater() &&
                ClientDataHolderVR.getInstance().climbTracker.isClimbeyClimb() &&
                ClientDataHolderVR.getInstance().climbTracker.isGrabbingLadder());

            this.up = (this.up || VivecraftVRMod.INSTANCE.keyTeleportFallback.isDown()) && !climbing.get();
            this.down &= !climbing.get();
            this.left &= !climbing.get();
            this.right &= !climbing.get();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void vivecraft$analogInput(CallbackInfo ci, @Share("climbing") LocalBooleanRef climbing) {
        if (!VRState.VR_RUNNING) {
            return;
        }

        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

        this.jumping &= Minecraft.getInstance().screen == null && !climbing.get() &&
            (dataHolder.vrPlayer.getFreeMove() || dataHolder.vrSettings.simulateFalling);

        this.shiftKeyDown = Minecraft.getInstance().screen == null &&
            (dataHolder.sneakTracker.sneakCounter > 0 || dataHolder.sneakTracker.sneakOverride || this.shiftKeyDown);

        boolean setMovement = false;
        float forwardAxis = 0.0F;

        if (!climbing.get() && !dataHolder.vrSettings.seated && Minecraft.getInstance().screen == null &&
            !KeyboardHandler.SHOWING)
        {
            // override everything
            Vector2fc moveStrafe = dataHolder.vr.getInputAction(VivecraftVRMod.INSTANCE.keyFreeMoveStrafe).getAxis2DUseTracked();
            Vector2fc moveRotate = dataHolder.vr.getInputAction(VivecraftVRMod.INSTANCE.keyFreeMoveRotate).getAxis2DUseTracked();

            if (moveStrafe.x() != 0.0F || moveStrafe.y() != 0.0F) {
                setMovement = true;
                forwardAxis = moveStrafe.y();

                if (dataHolder.vrSettings.analogMovement) {
                    this.forwardImpulse = moveStrafe.y();
                    this.leftImpulse = -moveStrafe.x();
                } else {
                    this.forwardImpulse = this.vivecraft$axisToDigital(moveStrafe.y());
                    this.leftImpulse = this.vivecraft$axisToDigital(-moveStrafe.x());
                }
            } else if (moveRotate.y() != 0.0F) {
                setMovement = true;
                forwardAxis = moveRotate.y();

                if (dataHolder.vrSettings.analogMovement) {
                    this.forwardImpulse = moveRotate.y();
                    // use left/right key as fallback
                    this.leftImpulse = 0.0F;
                    this.leftImpulse -= vivecraft$getAxisValue(this.options.keyRight);
                    this.leftImpulse += vivecraft$getAxisValue(this.options.keyLeft);
                } else {
                    this.forwardImpulse = this.vivecraft$axisToDigital(moveRotate.y());
                }
            } else if (dataHolder.vrSettings.analogMovement) {
                // neither axis input active, use single key values
                setMovement = true;
                this.forwardImpulse = 0.0F;
                this.leftImpulse = 0.0F;

                forwardAxis = vivecraft$getAxisValue(this.options.keyUp);
                if (forwardAxis == 0.0F) {
                    forwardAxis = vivecraft$getAxisValue(VivecraftVRMod.INSTANCE.keyTeleportFallback);
                }

                this.forwardImpulse += forwardAxis;
                this.forwardImpulse -= vivecraft$getAxisValue(this.options.keyDown);

                this.leftImpulse -= vivecraft$getAxisValue(this.options.keyRight);
                this.leftImpulse += vivecraft$getAxisValue(this.options.keyLeft);

                float deadZone = 0.05F;
                this.forwardImpulse = MathUtils.applyDeadzone(this.forwardImpulse, deadZone);
                this.leftImpulse = MathUtils.applyDeadzone(this.leftImpulse, deadZone);
            }

            if (setMovement) {
                this.vivecraft$wasAnalogMovement = true;
                // just assuming all this below is needed for compatibility.
                this.up = this.forwardImpulse > 0.0F;
                this.down = this.forwardImpulse < 0.0F;
                this.left = this.leftImpulse > 0.0F;
                this.right = this.leftImpulse < 0.0F;
                VRInputAction.setKeyBindState(this.options.keyUp, this.up);
                VRInputAction.setKeyBindState(this.options.keyDown, this.down);
                VRInputAction.setKeyBindState(this.options.keyLeft, this.left);
                VRInputAction.setKeyBindState(this.options.keyRight, this.right);

                if (dataHolder.vrSettings.autoSprint) {
                    // Sprint only works for walk forwards obviously
                    if (forwardAxis >= dataHolder.vrSettings.autoSprintThreshold) {
                        Minecraft.getInstance().player.setSprinting(true);
                        this.vivecraft$wasAutoSprint = true;
                        this.forwardImpulse = 1.0F;
                    } else if (this.forwardImpulse > 0.0F && dataHolder.vrSettings.analogMovement) {
                        // Adjust range so you can still reach full speed while not sprinting
                        this.forwardImpulse = this.forwardImpulse / dataHolder.vrSettings.autoSprintThreshold;
                    }
                }
            }
        }

        if (!setMovement && this.vivecraft$wasAnalogMovement) {
            // stop movement when returning the stick to center
            VRInputAction.setKeyBindState(this.options.keyUp, false);
            VRInputAction.setKeyBindState(this.options.keyDown, false);
            VRInputAction.setKeyBindState(this.options.keyLeft, false);
            VRInputAction.setKeyBindState(this.options.keyRight, false);
        }
        this.vivecraft$wasAnalogMovement = setMovement;

        if (this.vivecraft$wasAutoSprint && forwardAxis < dataHolder.vrSettings.autoSprintThreshold) {
            // stop sprinting when below the threshold and sprinting was active
            Minecraft.getInstance().player.setSprinting(false);
            this.vivecraft$wasAutoSprint = false;
        }
    }

}
