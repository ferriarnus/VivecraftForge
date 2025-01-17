package org.vivecraft.client_vr.gameplay.trackers;

import org.vivecraft.api.client.Tracker;
import org.vivecraft.client_vr.ClientDataHolderVR;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.joml.Vector3f;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.client_vr.VRData;

public class EatingTracker implements Tracker {
    private static final float MOUTH_TO_EYE_DISTANCE = 0.0F;
    private static final float THRESHOLD = 0.25F;
    private static final long EAT_TIME = 2100L;

    private final boolean[] eating = new boolean[2];
    private long eatStart;

    protected Minecraft mc;
    protected ClientDataHolderVR dh;

    public EatingTracker(Minecraft mc, ClientDataHolderVR dh) {
        this.mc = mc;
        this.dh = dh;
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        if (ClientDataHolderVR.getInstance().vrSettings.seated) {
            return false;
        } else if (player == null) {
            return false;
        } else if (this.mc.gameMode == null) {
            return false;
        } else if (!player.isAlive()) {
            return false;
        } else if (player.isSleeping()) {
            return false;
        } else {
            if (player.getMainHandItem() != null) {
                UseAnim anim = player.getMainHandItem().getUseAnimation();
                if (anim == UseAnim.EAT || anim == UseAnim.DRINK || anim == UseAnim.TOOT_HORN) {
                    return true;
                }
            }
            if (player.getOffhandItem() != null) {
                UseAnim anim = player.getOffhandItem().getUseAnimation();
                return anim == UseAnim.EAT || anim == UseAnim.DRINK || anim == UseAnim.TOOT_HORN;
            }
            return false;
        }
    }

    @Override
    public boolean itemInUse(LocalPlayer player) {
        return this.eating[0] || this.eating[1];
    }

    @Override
    public void reset(LocalPlayer player) {
        this.eating[0] = false;
        this.eating[1] = false;
    }

    @Override
    public void doProcess(LocalPlayer player) {
        VRData room_pre = this.dh.vrPlayer.vrdata_room_pre;
        Vector3f hmdPos = room_pre.hmd.getPositionF();
        Vector3f mouthPos = room_pre.getController(0).getCustomVector(new Vector3f(0.0F, -MOUTH_TO_EYE_DISTANCE, 0.0F))
            .add(hmdPos);

        for (int c = 0; c < 2; c++) {
            Vector3f controllerPos = this.dh.vr.controllerHistory[c].averagePosition(0.333D)
                .add(room_pre.getController(c).getDirection().mul(0.2F));

            if (mouthPos.distance(controllerPos) < THRESHOLD) {
                ItemStack itemstack = c == 0 ? player.getMainHandItem() : player.getOffhandItem();
                if (itemstack == ItemStack.EMPTY) {
                    continue;
                }

                int crunchiness = 0;

                if (itemstack.getUseAnimation() == UseAnim.DRINK) { // that's how liquid works.
                    if (room_pre.getController(c).getCustomVector(MathUtils.UP).y > 0) {
                        continue;
                    }
                } else if (itemstack.getUseAnimation() == UseAnim.EAT) {
                    crunchiness = 2;
                } else if (itemstack.getUseAnimation() == UseAnim.TOOT_HORN) {
                    crunchiness = 1;
                } else {
                    continue;
                }

                if (!this.eating[c]) {
                    // Minecraft.getInstance().physicalGuiManager.preClickAction();

                    if (this.mc.gameMode.useItem(player, c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND)
                        .consumesAction())
                    {
                        this.mc.gameRenderer.itemInHandRenderer.itemUsed(
                            c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
                        this.eating[c] = true;
                        this.eatStart = Util.getMillis();
                    }
                }

                if (this.eating[c]) {
                    long k = player.getUseItemRemainingTicks();

                    if (k > 0L && k % 5L <= (long) crunchiness) {
                        this.dh.vr.triggerHapticPulse(c, 700);
                    }
                }

                if (Util.getMillis() - this.eatStart > EAT_TIME) {
                    this.eating[c] = false;
                }
            } else {
                this.eating[c] = false;
            }
        }
    }

    @Override
    public TrackerTickType tickType() {
        return TrackerTickType.PER_TICK;
    }
}
