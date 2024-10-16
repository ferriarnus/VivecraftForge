package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.vivecraft.client.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;

public class SwimTracker extends Tracker {
    private static final float FRICTION = 0.9F;
    private static final float RISE_SPEED = 0.005F;
    private static final float SWIM_SPEED = 1.3F;

    private Vector3f motion = new Vector3f();
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

        Vector3f moveDir = MathUtils.subtractToVector3f(middle, hmdPos).normalize()
            .add(this.dh.vrPlayer.vrdata_world_pre.hmd.getDirection())
            .mul(0.5F);

        Vector3f controllerDir = this.dh.vrPlayer.vrdata_world_pre.getController(0).getCustomVector(MathUtils.BACK)
            .add(this.dh.vrPlayer.vrdata_world_pre.getController(1).getCustomVector(MathUtils.BACK))
            .mul(0.5F);

        float dirFactor = controllerDir.add(moveDir).length() * 0.5F;
        double distance = hmdPos.distanceTo(middle);
        double distDelta = this.lastDist - distance;

        if (distDelta > 0.0D) {
            Vector3f velocity = moveDir.mul((float) distDelta * SWIM_SPEED * dirFactor).mul(0.15F);
            this.motion = this.motion.add(velocity);
        }

        this.lastDist = distance;
        player.setSwimming(this.motion.length() > 0.3D);
        player.setSprinting(this.motion.length() > 1.0D);
        player.push(this.motion.x, this.motion.y, this.motion.z);
        this.motion = this.motion.mul(FRICTION);
    }
}
