package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.VRPlayer;

public class EatingTracker extends Tracker {
    private static final float MOUTH_TO_EYE_DISTANCE = 0.0F;
    private static final float THRESHOLD = 0.25F;
    private static final long EAT_TIME = 2100L;

    public boolean[] eating = new boolean[2];
    private long eatStart;

    public EatingTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    public boolean isEating() {
        return this.eating[0] || this.eating[1];
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
    public void reset(LocalPlayer player) {
        this.eating[0] = false;
        this.eating[1] = false;
    }

    @Override
    public void doProcess(LocalPlayer player) {
        VRPlayer vrplayer = this.dh.vrPlayer;
        Vec3 hmdPos = vrplayer.vrdata_room_pre.hmd.getPosition();
        Vec3 mouthPos = vrplayer.vrdata_room_pre.getController(0).getCustomVector(new Vec3(0.0D, -MOUTH_TO_EYE_DISTANCE, 0.0D)).add(hmdPos);

        for (int c = 0; c < 2; c++) {

            Vec3 controllerPos = this.dh.vr.controllerHistory[c].averagePosition(0.333D).add(vrplayer.vrdata_room_pre.getController(c).getCustomVector(new Vec3(0.0D, 0.0D, -0.1D)));
            controllerPos = controllerPos.add(this.dh.vrPlayer.vrdata_room_pre.getController(c).getDirection().scale(0.1D));

            if (mouthPos.distanceTo(controllerPos) < THRESHOLD) {
                ItemStack itemstack = c == 0 ? player.getMainHandItem() : player.getOffhandItem();
                if (itemstack == ItemStack.EMPTY) {
                    continue;
                }

                int crunchiness = 0;

                if (itemstack.getUseAnimation() == UseAnim.DRINK) { // that's how liquid works.
                    if (vrplayer.vrdata_room_pre.getController(c).getCustomVector(new Vec3(0, 1, 0)).y > 0) {
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
                    //Minecraft.getInstance().physicalGuiManager.preClickAction();

                    if (this.mc.gameMode.useItem(player, c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND).consumesAction()) {
                        this.mc.gameRenderer.itemInHandRenderer.itemUsed(c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
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
}
