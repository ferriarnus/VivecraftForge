package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.AutoCalibration;
import org.vivecraft.client_vr.settings.VRSettings;

public class JumpTracker extends Tracker {
    // in room space
    public Vector3f[] latchStart = new Vector3f[]{new Vector3f(), new Vector3f()};

    // in world space
    public Vec3[] latchStartOrigin = new Vec3[]{Vec3.ZERO, Vec3.ZERO};
    public Vec3[] latchStartPlayer = new Vec3[]{Vec3.ZERO, Vec3.ZERO};
    private boolean c0Latched = false;
    private boolean c1Latched = false;

    public JumpTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    /**
     * @param player Player to check
     * @return if the given {@code player} can use climbey jump
     */
    public boolean isClimbeyJump(LocalPlayer player) {
        return this.isActive(player) && hasClimbeyJumpEquipped(player);
    }

    /**
     * @param player Player to check
     * @return if the given {@code player} has jump boots equipped
     */
    public static boolean hasClimbeyJumpEquipped(Player player) {
        return ClientNetworking.SERVER_ALLOWS_CLIMBEY && isBoots(player.getItemBySlot(EquipmentSlot.FEET));
    }

    /**
     * @param itemStack ItemStack to check
     * @return if the given {@code itemStack} is a jump boots item
     */
    public static boolean isBoots(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        } else if (!itemStack.hasCustomHoverName()) {
            return false;
        } else if (itemStack.getItem() != Items.LEATHER_BOOTS) {
            return false;
        } else if (!itemStack.hasTag() || !itemStack.getTag().getBoolean("Unbreakable")) {
            return false;
        } else {
            return itemStack.getHoverName().getString().equals("Jump Boots") ||
                (itemStack.getHoverName().getContents() instanceof TranslatableContents translatableContent &&
                    translatableContent.getKey().equals("vivecraft.item.jumpboots")
                );
        }
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        if (this.dh.vrSettings.seated) {
            return false;
        } else if (!this.dh.vrPlayer.getFreeMove() && !this.dh.vrSettings.simulateFalling) {
            return false;
        } else if (this.dh.vrSettings.realisticJumpEnabled == VRSettings.RealisticJump.OFF) {
            return false;
        } else if (player == null || !player.isAlive()) {
            return false;
        } else if (this.mc.gameMode == null) {
            return false;
        } else if (player.isInWater() || player.isInLava() || !player.onGround()) {
            return false;
        } else {
            return !player.isShiftKeyDown() && !player.isPassenger();
        }
    }

    public boolean isjumping() {
        return this.c1Latched || this.c0Latched;
    }

    @Override
    public void idleTick(LocalPlayer player) {
        this.dh.vr.getInputAction(VivecraftVRMod.INSTANCE.keyClimbeyJump).setEnabled(hasClimbeyJumpEquipped(player) &&
            (this.isActive(player) ||
                (ClimbTracker.hasClimbeyClimbEquipped(player) && this.dh.climbTracker.isGrabbingLadder())
            ));
    }

    @Override
    public void reset(LocalPlayer player) {
        this.c1Latched = false;
        this.c0Latched = false;
    }

    @Override
    public void doProcess(LocalPlayer player) {
        boolean climbeyEquipped = hasClimbeyJumpEquipped(player);

        if (climbeyEquipped) {
            boolean[] ok = new boolean[2];

            for (int c = 0; c < 2; c++) {
                ok[c] = VivecraftVRMod.INSTANCE.keyClimbeyJump.isDown();
            }

            boolean jump = false;

            if (!ok[0] && this.c0Latched) {
                // let go right
                this.dh.vr.triggerHapticPulse(0, 200);
                jump = true;
            }

            Vector3f now = this.dh.vrPlayer.vrdata_room_pre.getController(0).getPositionF().lerp(
                this.dh.vrPlayer.vrdata_room_pre.getController(1).getPositionF(),
                0.5F
            );

            if (ok[0] && !this.c0Latched) {
                // grabbed right
                this.latchStart[0].set(now);
                this.latchStartOrigin[0] = this.dh.vrPlayer.vrdata_world_pre.origin;
                this.latchStartPlayer[0] = this.mc.player.position();
                this.dh.vr.triggerHapticPulse(0, 1000);
            }

            if (!ok[1] && this.c1Latched) {
                // let go left
                this.dh.vr.triggerHapticPulse(1, 200);
                jump = true;
            }

            if (ok[1] && !this.c1Latched) {
                // grabbed left
                this.latchStart[1].set(now);
                this.latchStartOrigin[1] = this.dh.vrPlayer.vrdata_world_pre.origin;
                this.latchStartPlayer[1] = this.mc.player.position();
                this.dh.vr.triggerHapticPulse(1, 1000);
            }

            this.c0Latched = ok[0];
            this.c1Latched = ok[1];

            int c = 0;
            Vector3f delta = now.sub(this.latchStart[c], new Vector3f());
            delta = delta.rotateY(this.dh.vrPlayer.vrdata_world_pre.rotation_radians);

            if (!jump && this.isjumping()) {
                // bzzzzzz
                this.dh.vr.triggerHapticPulse(0, 200);
                this.dh.vr.triggerHapticPulse(1, 200);
            }

            if (jump) {
                this.dh.climbTracker.forceActivate = true;
                Vector3f movement = this.dh.vr.controllerHistory[0].netMovement(0.3D)
                    .add(this.dh.vr.controllerHistory[1].netMovement(0.3D));

                float speed = this.dh.vr.controllerHistory[0].averageSpeed(0.3D) + this.dh.vr.controllerHistory[1].averageSpeed(0.3D) * 0.5F;

                movement.mul(0.33F * speed);

                // cap
                final float limit = 0.66F;

                if (movement.length() > limit) {
                    movement.mul(limit / movement.length());
                }

                if (player.hasEffect(MobEffects.JUMP)) {
                    movement.mul(player.getEffect(MobEffects.JUMP).getAmplifier() + 1.5F);
                }

                movement.rotateY(this.dh.vrPlayer.vrdata_world_pre.rotation_radians);
                Vec3 lastPosition = this.mc.player.position().subtract(delta.x, delta.y, delta.z);

                if (delta.y < 0.0F && movement.y < 0.0F) {
                    player.setDeltaMovement(
                        player.getDeltaMovement().x - movement.x * 1.25D,
                        -movement.y,
                        player.getDeltaMovement().z - movement.z * 1.25D);

                    player.xOld = lastPosition.x;
                    player.yOld = lastPosition.y;
                    player.zOld = lastPosition.z;

                    lastPosition = lastPosition.add(player.getDeltaMovement());

                    player.setPos(lastPosition.x, lastPosition.y, lastPosition.z);

                    this.dh.vrPlayer.snapRoomOriginToPlayerEntity(player, false, true);
                    this.mc.player.causeFoodExhaustion(0.3F);
                    this.mc.player.setOnGround(false);
                } else {
                    this.dh.vrPlayer.snapRoomOriginToPlayerEntity(player, false, true);
                }
            } else if (this.isjumping()) {
                Vec3 thing = this.latchStartOrigin[0]
                    .subtract(this.latchStartPlayer[0])
                    .add(this.mc.player.position())
                    .subtract(delta.x, delta.y, delta.z);
                this.dh.vrPlayer.setRoomOrigin(thing.x, thing.y, thing.z, false);
            }
        }
        if ((!climbeyEquipped || ClientDataHolderVR.getInstance().vrSettings.realisticJumpEnabled == VRSettings.RealisticJump.ON) &&
            this.dh.vr.hmdPivotHistory.netMovement(0.25D).y > 0.1D &&
            this.dh.vr.hmdPivotHistory.latest().y() - AutoCalibration.getPlayerHeight() > this.dh.vrSettings.jumpThreshold)
        {
            player.jumpFromGround();
        }
    }
}
