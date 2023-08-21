package org.vivecraft.client_vr.gameplay.trackers;

import org.vivecraft.api.client.Tracker;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client.network.ClientNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import org.vivecraft.client.utils.ClientUtils;
import org.vivecraft.client.utils.ScaleHelper;
import org.vivecraft.common.network.packet.c2s.CrawlPayloadC2S;

public class CrawlTracker implements Tracker {
    private boolean wasCrawling;
    public boolean crawling;
    public boolean crawlsteresis;
    protected Minecraft mc;
    protected ClientDataHolderVR dh;

    public CrawlTracker(Minecraft mc, ClientDataHolderVR dh) {
        this.mc = mc;
        this.dh = dh;
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        if (this.dh.vrSettings.seated) {
            return false;
        } else if (!this.dh.vrSettings.allowCrawling) {
            return false;
        } else if (!ClientNetworking.SERVER_ALLOWS_CRAWLING) {
            return false;
        } else if (!player.isAlive()) {
            return false;
        } else if (player.isSpectator()) {
            return false;
        } else if (player.isSleeping()) {
            return false;
        } else {
            return !player.isPassenger();
        }
    }

    @Override
    public void reset(LocalPlayer player) {
        this.crawling = false;
        this.crawlsteresis = false;
        this.updateState(player);
    }

    @Override
    public void doProcess(LocalPlayer player) {
        float scaledWorldScale = this.dh.vrPlayer.worldScale /
            ScaleHelper.getEntityEyeHeightScale(player, ClientUtils.getCurrentPartialTick());
        this.crawling = this.dh.vr.hmdPivotHistory.averagePosition(0.2F).y * scaledWorldScale + 0.1F <
            this.dh.vrSettings.crawlThreshold;
        this.updateState(player);
    }

    @Override
    public TrackerTickType tickType() {
        return TrackerTickType.PER_TICK;
    }

    private void updateState(LocalPlayer player) {
        if (this.crawling != this.wasCrawling) {
            if (this.crawling) {
                player.setPose(Pose.SWIMMING);
                this.crawlsteresis = true;
            }

            if (ClientNetworking.SERVER_ALLOWS_CRAWLING) {
                ClientNetworking.sendServerPacket(new CrawlPayloadC2S(this.crawling));
            }

            this.wasCrawling = this.crawling;
        }

        if (!this.crawling && player.getPose() != Pose.SWIMMING) {
            this.crawlsteresis = false;
        }
    }
}
