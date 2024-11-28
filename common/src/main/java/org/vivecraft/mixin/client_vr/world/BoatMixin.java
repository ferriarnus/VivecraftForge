package org.vivecraft.mixin.client_vr.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.settings.VRSettings;

@Mixin(Boat.class)
public abstract class BoatMixin extends Entity {

    @Shadow
    private float deltaRotation;
    @Shadow
    private boolean inputLeft;
    @Shadow
    private boolean inputRight;
    @Shadow
    private boolean inputUp;

    public BoatMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @ModifyExpressionValue(method = "controlBoat", at = @At(value = "CONSTANT", args = "floatValue=1F", ordinal = 0))
    private float vivecraft$inputLeft(float leftInput) {
        return VRState.VR_RUNNING ? Minecraft.getInstance().player.input.leftImpulse : leftInput;
    }

    @ModifyExpressionValue(method = "controlBoat", at = @At(value = "CONSTANT", args = "floatValue=1F", ordinal = 1))
    private float vivecraft$inputRight(float rightInput) {
        return VRState.VR_RUNNING ? -Minecraft.getInstance().player.input.leftImpulse : rightInput;
    }

    // LOAD also counts the += so we need to skip those 3
    @ModifyVariable(method = "controlBoat", at = @At(value = "LOAD", ordinal = 3))
    private float vivecraft$modifyAcceleration(float acceleration) {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();
        if (VRState.VR_RUNNING && !dataHolder.vrSettings.seated) {
            // only custom boat controls in standing mode
            if (this.inputUp) {
                //controller-based
                float yaw = dataHolder.vrSettings.vrFreeMoveMode == VRSettings.FreeMove.HMD ?
                    dataHolder.vrPlayer.vrdata_world_pre.hmd.getYaw():
                    dataHolder.vrPlayer.vrdata_world_pre.getController(1).getYaw();
                if (dataHolder.vrSettings.vehicleRotation) {
                    //tank controls
                    float end = this.getYRot() % 360F;
                    float start = yaw;
                    float difference = Math.abs(end - start);

                    if (difference > 180F) {
                        if (end > start) {
                            start += 360F;
                        } else {
                            end += 360F;
                        }
                    }

                    difference = end - start;

                    acceleration = 0F;

                    if (Math.abs(difference) < 30F) {
                        acceleration = 0.04F;
                    } else if (Math.abs(difference) > 150F) {
                        acceleration = -0.005F;
                    } else if (difference != 0) {
                        acceleration = 0.005F;
                    }

                    // smooth out point turning a bit
                    if (Math.abs(difference) > 10F && Math.abs(difference) < 150F) {
                        this.deltaRotation -=
                            Math.signum(difference) * Math.min(1F, Math.max(0F, Math.abs(difference) - 25F) / 40F);
                    }
                } else {
                    //point to move
                    this.setYRot(yaw);
                }
            } else if (dataHolder.rowTracker.isRowing()) {
                //roomscale rowing

                this.deltaRotation += dataHolder.rowTracker.LOar / 1.5F;
                this.deltaRotation -= dataHolder.rowTracker.ROar / 1.5F;

                /*
                this.deltaRotation += dataHolder.rowTracker.forces[0] * 50;
                this.deltaRotation -= dataHolder.rowTracker.forces[1] * 50;
                 */

                if (this.deltaRotation < 0F) {
                    this.inputLeft = true;
                }
                if (this.deltaRotation > 0F) {
                    this.inputRight = true;
                }

                // clamp to vanilla speed
                acceleration = Math.min(0.04F, 0.06F * dataHolder.rowTracker.FOar);
                if (acceleration > 0F) {
                    this.inputUp = true;
                }

                /*
                acceleration = (float) (dataHolder.rowTracker.forces[0] + dataHolder.rowTracker.forces[1]);
                if (acceleration > 0.005F) {
                    this.inputUp = true;
                }
                */

            }
        }
        return acceleration;
    }
}
