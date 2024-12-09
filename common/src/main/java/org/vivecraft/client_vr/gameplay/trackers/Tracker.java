package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.vivecraft.client_vr.ClientDataHolderVR;

import javax.annotation.Nullable;

public abstract class Tracker {
    public Minecraft mc;
    public ClientDataHolderVR dh;

    public Tracker(Minecraft mc, ClientDataHolderVR dh) {
        this.mc = mc;
        this.dh = dh;
    }

    public abstract boolean isActive(@Nullable LocalPlayer player);

    public abstract void doProcess(@Nullable LocalPlayer player);

    public boolean itemInUse(@Nullable LocalPlayer player) {
        return false;
    }

    public void reset(@Nullable LocalPlayer player) {}

    public void idleTick(@Nullable LocalPlayer player) {}

    public EntryPoint getEntryPoint() {
        return EntryPoint.LIVING_UPDATE;
    }

    public enum EntryPoint {
        LIVING_UPDATE,
        SPECIAL_ITEMS
    }
}
