package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.common.utils.math.Quaternion;

public class RowTracker extends Tracker {
    private static final double TRANSMISSION_EFFICIENCY = 0.9D;

    public double[] forces = new double[]{0.0D, 0.0D};
    public float LOar;
    public float ROar;
    public float FOar;

    private final Vec3[] lastUWPs = new Vec3[2];

    public RowTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        if (this.dh.vrSettings.seated) {
            return false;
        } else if (!this.dh.vrSettings.realisticRowEnabled) {
            return false;
        } else if (player == null || !player.isAlive()) {
            return false;
        } else if (this.mc.gameMode == null) {
            return false;
        } else if (this.mc.options.keyUp.isDown()) { // important
            return false;
        } else if (!(player.getVehicle() instanceof Boat)) {
            return false;
        } else {
            return !this.dh.bowTracker.isNotched();
        }
    }

    public boolean isRowing() {
        return this.ROar + this.LOar + this.FOar > 0.0F;
    }

    @Override
    public void reset(LocalPlayer player) {
        this.LOar = 0.0F;
        this.ROar = 0.0F;
        this.FOar = 0.0F;
    }

    @Override
    public void doProcess(LocalPlayer player) {
        double c0Move = this.dh.vr.controllerHistory[0].averageSpeed(0.5D);
        double c1Move = this.dh.vr.controllerHistory[1].averageSpeed(0.5D);

        final float minSpeed = 0.5F;
        final float maxSpeed = 2.0F;

        this.ROar = (float) Math.max(c0Move - minSpeed, 0.0D);
        this.LOar = (float) Math.max(c1Move - minSpeed, 0.0D);

        this.FOar = this.ROar > 0.0F && this.LOar > 0.0F ? (this.ROar + this.LOar) / 2.0F : 0.0F;

        if (this.FOar > maxSpeed) {
            this.FOar = maxSpeed;
        }

        if (this.ROar > maxSpeed) {
            this.ROar = maxSpeed;
        }

        if (this.LOar > maxSpeed) {
            this.LOar = maxSpeed;
        }

        //TODO: Backwards paddlin'
    }

    public void doProcessFinaltransmithastofixthis(LocalPlayer player) {
        Boat boat = (Boat) player.getVehicle();
        Quaternion boatRot = (new Quaternion(boat.getXRot(), -(boat.getYRot() % 360.0F), 0.0F)).normalized();

        for (int paddle = 0; paddle <= 1; paddle++) {
            if (this.isPaddleUnderWater(paddle, boat)) {
                Vec3 arm2Pad = this.getArmToPaddleVector(paddle, boat);
                Vec3 attach = this.getAttachmentPoint(paddle, boat);
                Vec3 underWaterPoint = attach.add(arm2Pad.normalize()).subtract(boat.position());

                if (this.lastUWPs[paddle] != null) {
                    Vec3 forceVector = this.lastUWPs[paddle].subtract(underWaterPoint); // intentionally reverse
                    forceVector = forceVector.subtract(boat.getDeltaMovement());

                    Vec3 forward = boatRot.multiply(new Vec3(0.0D, 0.0D, 1.0D));

                    //scalar projection onto forward vector
                    double force = forceVector.dot(forward) * TRANSMISSION_EFFICIENCY / 5.0D;

                    if ((force < 0.0D && this.forces[paddle] > 0.0D) || (force > 0.0D && this.forces[paddle] < 0.0D)) {
                        this.forces[paddle] = 0.0D;
                    } else {
                        this.forces[paddle] = Math.min(Math.max(force, -0.1D), 0.1D);
                    }
                }

                this.lastUWPs[paddle] = underWaterPoint;
            } else {
                this.forces[paddle] = 0.0D;
                this.lastUWPs[paddle] = null;
            }
        }
    }

    private Vec3 getArmToPaddleVector(int paddle, Boat boat) {
        Vec3 attachAbs = this.getAttachmentPoint(paddle, boat);
        Vec3 armAbs = this.getAbsArmPos(paddle == 0 ? 1 : 0);
        return attachAbs.subtract(armAbs);
    }

    private Vec3 getAttachmentPoint(int paddle, Boat boat) {
        Vec3 attachmentPoint = new Vec3((paddle == 0 ? 9.0F : -9.0F) / 16.0F, 0.625D, 0.1875D); // values from ModelBoat
        Quaternion boatRot = (new Quaternion(boat.getXRot(), -(boat.getYRot() % 360.0F), 0.0F)).normalized();
        return boat.position().add(boatRot.multiply(attachmentPoint));
    }

    private Vec3 getAbsArmPos(int side) {
        Vec3 arm = this.dh.vr.controllerHistory[side].averagePosition(0.1D);
        Quaternion worldRot = new Quaternion(0.0F, this.dh.vrSettings.worldRotation, 0.0F);
        return this.dh.vrPlayer.roomOrigin.add(worldRot.multiply(arm));
    }

    private boolean isPaddleUnderWater(int paddle, Boat boat) {
        Vec3 attachAbs = this.getAttachmentPoint(paddle, boat);
        Vec3 armToPaddle = this.getArmToPaddleVector(paddle, boat).normalize();
        BlockPos blockPos = BlockPos.containing(attachAbs.add(armToPaddle));
        // TODO: liquid is deprecated
        return boat.level().getBlockState(blockPos).liquid();
    }
}
