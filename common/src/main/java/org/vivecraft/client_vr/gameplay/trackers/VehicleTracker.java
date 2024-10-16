package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.FoodOnAStickItem;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.vivecraft.client.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.ItemTags;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.settings.VRSettings;

public class VehicleTracker extends Tracker {
    private float PreMount_World_Rotation;
    public Vec3 Premount_Pos_Room = new Vec3(0.0D, 0.0D, 0.0D);
    public float vehicleInitialRotation = 0.0F;
    public int rotationCooldown = 0;
    private double rotationTarget = 0.0D;
    private int minecartStupidityCounter;
    public int dismountCooldown = 0;

    public VehicleTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        if (player == null) {
            return false;
        } else if (this.mc.gameMode == null) {
            return false;
        } else {
            return player.isAlive();
        }
    }

    @Override
    public void reset(LocalPlayer player) {
        this.minecartStupidityCounter = 2;
        super.reset(player);
    }

    public double getVehicleFloor(Entity vehicle, double original) {
//        if (vehicle instanceof AbstractHorse) {
        return original; //horses are fine.
//        } else {
//            return vehicle.getY();
//        }
    }

    public static Vector3f getSteeringDirection(LocalPlayer player) {
        Entity entity = player.getVehicle();
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

        if (entity instanceof AbstractHorse || entity instanceof Boat) {
            if (player.zza > 0) {
                if (dataHolder.vrSettings.vrFreeMoveMode == VRSettings.FreeMove.HMD) {
                    return dataHolder.vrPlayer.vrdata_world_pre.hmd.getDirection();
                } else {
                    return dataHolder.vrPlayer.vrdata_world_pre.getController(0).getDirection();
                }
            }
        } else if (entity instanceof Mob mob && mob.isControlledByLocalInstance()) {
            //pigs and striders
            int c = (player.getMainHandItem().getItem() instanceof FoodOnAStickItem ||
                player.getMainHandItem().is(ItemTags.VIVECRAFT_FOOD_STICKS)
            ) ? 0 : 1;
            VRData.VRDevicePose con = dataHolder.vrPlayer.vrdata_world_pre.getController(c);
            return MathUtils.subtractToVector3f(con.getPosition(), entity.position())
                .add(con.getDirection().mul(0.3F))
                .normalize();
        }

        // ignore other vehicles
        return null;
    }

    @Override
    public void doProcess(LocalPlayer player) {
        if (!this.mc.isPaused()) {
            // do vehicle rotation, which rotates around a different point.
            if (this.dismountCooldown > 0) {
                this.dismountCooldown--;
            }

            if (this.rotationCooldown > 0) {
                this.rotationCooldown--;
            }

            if (this.dh.vrSettings.vehicleRotation && this.mc.player.isPassenger() && this.rotationCooldown == 0) {
                Entity entity = this.mc.player.getVehicle();
                this.rotationTarget = entity.getYRot();

                if (entity instanceof AbstractHorse abstracthorse && !this.dh.horseTracker.isActive(this.mc.player)) {

                    if (abstracthorse.isControlledByLocalInstance() && abstracthorse.isSaddled()) {
                        return;
                    }

                    this.rotationTarget = abstracthorse.yBodyRot;
                } else if (entity instanceof Mob mob) {

                    if (mob.isControlledByLocalInstance()) {
                        return;
                    }

                    this.rotationTarget = mob.yBodyRot;
                }

                boolean smooth = true;
                float smoothIncrement = 10.0F;

                if (entity instanceof Minecart minecart) {
                    // what a pain in my ass
                    if (this.shouldMinecartTurnView(minecart)) {
                        if (this.minecartStupidityCounter > 0) {
                            this.minecartStupidityCounter--;
                        }
                    } else {
                        this.minecartStupidityCounter = 3;
                    }

                    this.rotationTarget = this.getMinecartRenderYaw(minecart);

                    if (this.minecartStupidityCounter > 0) {
                        // do nothing
                        this.vehicleInitialRotation = (float) this.rotationTarget;
                    }

                    double speed = this.mineCartSpeed(minecart);
                    smoothIncrement = 200.0F * (float) (speed * speed);

                    if (smoothIncrement < 10.0F) {
                        smoothIncrement = 10.0F;
                    }
                }

                float difference = this.dh.vrPlayer.rotDiff_Degrees((float) this.rotationTarget, this.vehicleInitialRotation);

                if (smooth) {
                    if (difference > smoothIncrement) {
                        difference = smoothIncrement;
                    }

                    if (difference < -smoothIncrement) {
                        difference = -smoothIncrement;
                    }
                }

                this.dh.vrSettings.worldRotation += difference;
                this.dh.vrSettings.worldRotation %= 360.0F;
                this.dh.vr.seatedRot = this.dh.vrSettings.worldRotation;

                this.vehicleInitialRotation -= difference;
                this.vehicleInitialRotation %= 360.0F;
            } else {
                this.minecartStupidityCounter = 3;

                if (this.mc.player.isPassenger()) {
                    this.vehicleInitialRotation = this.mc.player.getVehicle().getYRot();
                }
            }
        }
    }

    public void onStartRiding(Entity vehicle) {
        this.PreMount_World_Rotation = this.dh.vrPlayer.vrdata_world_pre.rotation_radians;
        Vec3 camPos = this.dh.vrPlayer.vrdata_room_pre.getHeadPivot();
        this.Premount_Pos_Room = new Vec3(camPos.x, 0.0D, camPos.z);
        this.dismountCooldown = 5;

        if (this.dh.vrSettings.vehicleRotation) {
            float end = this.dh.vrPlayer.vrdata_world_pre.hmd.getYaw();
            float start = vehicle.getYRot() % 360.0F;
            this.vehicleInitialRotation = this.dh.vrSettings.worldRotation;
            this.rotationCooldown = 2;

            if (vehicle instanceof Minecart) {
                // don't align player with minecart, it doesn't have a 'front'
                return;
            }

            float difference = this.dh.vrPlayer.rotDiff_Degrees(start, end);
            this.dh.vrSettings.worldRotation = (float) (Math.toDegrees(this.dh.vrPlayer.vrdata_world_pre.rotation_radians) + (double) difference);
            this.dh.vrSettings.worldRotation %= 360.0F;
            this.dh.vr.seatedRot = this.dh.vrSettings.worldRotation;
        }
    }

    public void onStopRiding() {
        this.dh.swingTracker.disableSwing = 10;
        this.dh.sneakTracker.sneakCounter = 0;

        /*
        if (this.dh.vrSettings.vehicleRotation) {
            // I don't wanna do this anymore.
            // I think its more confusing to get off the thing and not know where you're looking
            this.dh.vrSettings.worldRotation = PreMount_World_Rotation;
            this.dh.vr.seatedRot = PreMount_World_Rotation;
        }
        */
    }

    private float getMinecartRenderYaw(Minecart entity) {
        Vec3 speed = new Vec3(
            entity.getX() - entity.xOld,
            entity.getY() - entity.yOld,
            entity.getZ() - entity.zOld);

        float speedYaw = (float) Math.toDegrees(Math.atan2(-speed.x, speed.z));
        return this.shouldMinecartTurnView(entity) ? -180.0F + speedYaw : this.vehicleInitialRotation;
    }

    private double mineCartSpeed(Minecart entity) {
        Vec3 speed = new Vec3(entity.getDeltaMovement().x, 0.0D, entity.getDeltaMovement().z);
        return speed.length();
    }

    private boolean shouldMinecartTurnView(Minecart entity) {
        Vec3 speed = new Vec3(
            entity.getX() - entity.xOld,
            entity.getY() - entity.yOld,
            entity.getZ() - entity.zOld);
        return speed.length() > 0.001D;
    }

    public boolean canRoomscaleDismount(LocalPlayer player) {
        return player.zza == 0.0F && player.xxa == 0.0F &&
            player.isPassenger() && player.getVehicle().onGround() &&
            this.dismountCooldown == 0;
    }
}
