package org.vivecraft.client_vr.gameplay.trackers;

import org.vivecraft.api.client.Tracker;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.AutoCalibration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class SneakTracker implements Tracker {
    public boolean sneakOverride = false;
    public int sneakCounter = 0;
    protected Minecraft mc;
    protected ClientDataHolderVR dh;

    public SneakTracker(Minecraft mc, ClientDataHolderVR dh) {
        this.mc = mc;
        this.dh = dh;
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        if (this.dh.vrSettings.seated) {
            return false;
        } else if (!this.dh.vrPlayer.getFreeMove() && !this.dh.vrSettings.simulateFalling) {
            return false;
        } else if (!this.dh.vrSettings.realisticSneakEnabled) {
            return false;
        } else if (this.mc.gameMode == null) {
            return false;
        } else if (player == null || !player.isAlive() || !player.onGround()) {
            return false;
        } else {
            return !player.isPassenger();
        }
    }

    @Override
    public void reset(LocalPlayer player) {
        this.sneakOverride = false;
    }

    @Override
    public void doProcess(LocalPlayer player) {
        if (!this.mc.isPaused() && this.dh.sneakTracker.sneakCounter > 0) {
            this.dh.sneakTracker.sneakCounter--;
        }

        this.sneakOverride = AutoCalibration.getPlayerHeight() - this.dh.vr.hmdPivotHistory.latest().y() >
            this.dh.vrSettings.sneakThreshold;
    }

    @Override
    public TrackerTickType tickType() {
        return TrackerTickType.PER_TICK;
    }
}
