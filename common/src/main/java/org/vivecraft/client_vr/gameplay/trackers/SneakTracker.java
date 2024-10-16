package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.AutoCalibration;

public class SneakTracker extends Tracker {
    public boolean sneakOverride = false;
    public int sneakCounter = 0;

    public SneakTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
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
}
