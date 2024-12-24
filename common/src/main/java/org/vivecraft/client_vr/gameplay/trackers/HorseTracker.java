package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.settings.VRSettings;

public class HorseTracker extends Tracker {
    private static final double BOOST_TRIGGER = 1.4D;
    private static final double PULL_TRIGGER = 0.8D;
    private static final int MAX_SPEED_LEVEL = 3;
    private static final long COOL_DOWN_MILLIS = 500L;
    private static final double TURN_SPEED = 6.0D;
    private static final double BODY_TURN_SPEED = 0.2D;
    private static final double BASE_SPEED = 0.2D;

    private int speedLevel = 0;
    private long lastBoostMillis = -1L;
    private Horse horse = null;
    private final ModelInfo info = new ModelInfo();

    public HorseTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        if (true) {
            // this tracker is currently unused
            return false;
        } else if (this.dh.vrSettings.seated) {
            return false;
        } else if (player == null || !player.isAlive()) {
            return false;
        } else if (this.mc.gameMode == null) {
            return false;
        } else if (this.mc.options.keyUp.isDown()) {
            return false;
        } else if (!(player.getVehicle() instanceof AbstractHorse)) {
            return false;
        } else {
            return !this.dh.bowTracker.isNotched();
        }
    }

    @Override
    public void reset(LocalPlayer player) {
        if (this.horse != null) {
            this.horse.setNoAi(false);
        }
    }

    @Override
    public void doProcess(LocalPlayer player) {
        this.horse = (Horse) player.getVehicle();
        this.horse.setNoAi(true);
        float absYaw = (this.horse.getYRot() + 360.0F) % 360.0F;
        float absYawOffset = (this.horse.yBodyRot + 360.0F) % 360.0F;

        Vector3f speedLeft = this.dh.vr.controllerHistory[1].netMovement(0.1D).mul(10.0F);
        Vector3f speedRight = this.dh.vr.controllerHistory[0].netMovement(0.1D).mul(10.0F);
        float speedDown = Math.min(-speedLeft.y, -speedRight.y);

        if (speedDown > BOOST_TRIGGER) {
            this.doBoost();
        }

        Vector3f back = MathUtils.BACK.rotateY(-this.horse.yBodyRot, new Vector3f());
        Vector3f left = MathUtils.LEFT.rotateY(-this.horse.yBodyRot, new Vector3f());
        Vector3f right = MathUtils.RIGHT.rotateY(-this.horse.yBodyRot, new Vector3f());

        Vector3f roomPosL = this.dh.vr.controllerHistory[1].latest().rotateY(VRSettings.INSTANCE.worldRotation, new Vector3f());
        Vector3f roomPosR = this.dh.vr.controllerHistory[0].latest().rotateY(VRSettings.INSTANCE.worldRotation, new Vector3f());
        Vec3 posL = VRPlayer.get().roomOrigin.add(roomPosL.x, roomPosL.y, roomPosL.z);
        Vec3 posR = VRPlayer.get().roomOrigin.add(roomPosR.x, roomPosR.y, roomPosR.z);

        Vector3f offsetL = MathUtils.subtractToVector3f(posL, this.info.leftReinPos);
        Vector3f offsetR = MathUtils.subtractToVector3f(posR, this.info.leftReinPos);

        double distanceL = offsetL.dot(back) + offsetL.dot(left);
        double distanceR = offsetR.dot(back) + offsetR.dot(right);

        if (this.speedLevel < 0) {
            this.speedLevel = 0;
        }

        if (distanceL > PULL_TRIGGER + 0.3D &&
            distanceR > PULL_TRIGGER + 0.3D &&
            Math.abs(distanceR - distanceL) < 0.1D)
        {
            if (this.speedLevel == 0 && System.currentTimeMillis() > this.lastBoostMillis + COOL_DOWN_MILLIS) {
                this.speedLevel = -1;
            } else {
                this.doBreak();
            }
        } else {
            double pullL = 0.0D;
            double pullR = 0.0D;

            if (distanceL > PULL_TRIGGER) {
                pullL = distanceL - PULL_TRIGGER;
            }

            if (distanceR > PULL_TRIGGER) {
                pullR = distanceR - PULL_TRIGGER;
            }

            this.horse.setYRot((float) (absYaw + (pullR - pullL) * TURN_SPEED));
        }

        this.horse.yBodyRot = (float) MathUtils.lerpMod(absYawOffset, absYaw, BODY_TURN_SPEED, 360.0D);
        this.horse.yHeadRot = absYaw;

        Vec3 movement = new Vec3(0.0D, 0.0D, this.speedLevel * BASE_SPEED).yRot(-this.horse.yBodyRot);
        this.horse.setDeltaMovement(movement.x, this.horse.getDeltaMovement().y, movement.z);
    }

    private boolean doBoost() {
        if (this.speedLevel >= MAX_SPEED_LEVEL) {
            return false;
        } else if (System.currentTimeMillis() < this.lastBoostMillis + COOL_DOWN_MILLIS) {
            return false;
        } else {
            // System.out.println("Boost");
            this.speedLevel++;
            this.lastBoostMillis = System.currentTimeMillis();
            return true;
        }
    }

    private boolean doBreak() {
        if (this.speedLevel <= 0) {
            return false;
        } else if (System.currentTimeMillis() < this.lastBoostMillis + COOL_DOWN_MILLIS) {
            return false;
        } else {
            System.out.println("Breaking");

            this.speedLevel--;
            this.lastBoostMillis = System.currentTimeMillis();
            return true;
        }
    }

    public ModelInfo getModelInfo() {
        return this.info;
    }

    public static class ModelInfo {
        public Vec3 leftReinPos = Vec3.ZERO;
        public Vec3 rightReinPos = Vec3.ZERO;
    }
}
