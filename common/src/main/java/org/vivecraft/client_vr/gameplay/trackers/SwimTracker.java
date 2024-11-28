package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client_vr.ClientDataHolderVR;

public class SwimTracker extends Tracker {
    private static final double FRICTION = 0.9F;
    private static final double RISE_SPEED = 0.005F;
    private static final double SWIM_SPEED = 1.3F;

    private Vec3 motion = Vec3.ZERO;
    private double lastDist;

    public SwimTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        if (this.dh.vrSettings.seated) {
            return false;
        } else if (!this.dh.vrSettings.realisticSwimEnabled) {
            return false;
        } else if (this.mc.screen != null) {
            return false;
        } else if (player == null || !player.isAlive()) {
            return false;
        } else if (this.mc.gameMode == null) {
            return false;
        } else if (!player.isInWater() && !player.isInLava()) {
            return false;
        } else if (player.zza > 0.0F) {
            return false;
        } else if (player.xxa > 0.0F) {
            return false;
        }
        return true;
    }

    @Override
    public void doProcess(LocalPlayer player) {
        // swim
        Vec3 controllerR = this.dh.vrPlayer.vrdata_world_pre.getController(0).getPosition();
        Vec3 controllerL = this.dh.vrPlayer.vrdata_world_pre.getController(1).getPosition();
        Vec3 middle = controllerL.subtract(controllerR).scale(0.5D).add(controllerR);

        Vec3 hmdPos = this.dh.vrPlayer.vrdata_world_pre.getHeadPivot().subtract(0.0D, 0.3D, 0.0D);
        Vec3 moveDir = middle.subtract(hmdPos).normalize()
            .add(this.dh.vrPlayer.vrdata_world_pre.hmd.getDirection())
            .scale(0.5D);

        Vec3 controllerDir = this.dh.vrPlayer.vrdata_world_pre.getController(0).getCustomVector(new Vec3(0.0D, 0.0D, -1.0D))
            .add(this.dh.vrPlayer.vrdata_world_pre.getController(1).getCustomVector(new Vec3(0.0D, 0.0D, -1.0D)))
            .scale(0.5D);

        double dirFactor = controllerDir.add(moveDir).length() / 2.0D;
        double distance = hmdPos.distanceTo(middle);
        double distDelta = this.lastDist - distance;

        if (distDelta > 0.0D) {
            Vec3 velocity = moveDir.scale(distDelta * SWIM_SPEED * dirFactor);
            this.motion = this.motion.add(velocity.scale(0.15D));
        }

        this.lastDist = distance;
        player.setSwimming(this.motion.length() > 0.3D);
        player.setSprinting(this.motion.length() > 1.0D);
        player.push(this.motion.x, this.motion.y, this.motion.z);
        this.motion = this.motion.scale(FRICTION);
    }
}
