package org.vivecraft.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.common.network.CommonNetworkHelper;
import org.vivecraft.common.network.Pose;
import org.vivecraft.common.network.VrPlayerState;

import javax.annotation.Nullable;

public class ServerVivePlayer {
    // player movement state
    @Nullable
    public VrPlayerState vrPlayerState;
    // how much the player is drawing the roomscale bow
    public float draw;
    public float worldScale = 1.0F;
    public float heightScale = 1.0F;
    public byte activeHand = 0;
    public boolean crawling;
    // if the player has VR active
    private boolean isVR = false;
    // offset set during aimFix to keep the original data positions
    public Vec3 offset = new Vec3(0.0D, 0.0D, 0.0D);
    // player this data belongs to
    public ServerPlayer player;
    // network protocol this player is communicating with
    public int networkVersion = CommonNetworkHelper.MAX_SUPPORTED_NETWORK_VERSION;

    public ServerVivePlayer(ServerPlayer player) {
        this.player = player;
    }

    /**
     * transforms the local {@code direction} vector on controller {@code controller} into world space
     * @param controller controller to get the direction on
     * @param direction local direction to transform
     * @return direction in world space
     */
    public Vec3 getControllerVectorCustom(int controller, Vector3fc direction) {
        if (this.isSeated()) {
            controller = 0;
        }

        if (this.vrPlayerState != null) {
            Pose controllerPose = controller == 0 ? this.vrPlayerState.controller0() : this.vrPlayerState.controller1();
            return new Vec3(controllerPose.orientation().transform(direction, new Vector3f()));
        } else {
            return this.player.getLookAngle();
        }
    }

    /**
     * @param controller controller to get the direction from
     * @return forward direction of the given controller
     */
    public Vec3 getControllerDir(int controller) {
        return this.getControllerVectorCustom(controller, MathUtils.BACK);
    }

    /**
     * @return looking direction of the head
     */
    public Vec3 getHMDDir() {
        if (this.vrPlayerState != null) {
            return new Vec3(this.vrPlayerState.hmd().orientation().transform(MathUtils.BACK, new Vector3f()));
        } else {
            return this.player.getLookAngle();
        }
    }

    /**
     * @return position of the head, in world space
     */
    public Vec3 getHMDPos() {
        if (this.vrPlayerState != null) {
            Vector3fc hmdPos = this.vrPlayerState.hmd().position();
            return this.player.position().add(
                this.offset.x + hmdPos.x(),
                this.offset.y + hmdPos.y(),
                this.offset.z + hmdPos.z());
        } else {
            return this.player.position().add(0.0D, 1.62D, 0.0D);
        }
    }

    /**
     * @param c controller to get the position for
     * @param realPosition if true disables the seated override
     * @return controller position in world space
     */
    public Vec3 getControllerPos(int c, boolean realPosition) {
        if (this.vrPlayerState != null) {

            // in seated the realPosition is at the head,
            // so reconstruct the seated position when wanting the visual position
            if (this.isSeated() && !realPosition) {
                Vec3 dir = this.getHMDDir();
                dir = dir.yRot((float) Math.toRadians(c == 0 ? -35.0D : 35.0D));
                dir = new Vec3(dir.x, 0.0D, dir.z);
                dir = dir.normalize();
                return this.getHMDPos().add(
                    dir.x * 0.3F * this.worldScale,
                    -0.4F * this.worldScale,
                    dir.z * 0.3F * this.worldScale);
            }

            Vector3fc conPos = c == 0 ?
                this.vrPlayerState.controller0().position() :
                this.vrPlayerState.controller1().position();

            return this.player.position().add(
                this.offset.x + conPos.x(),
                this.offset.y + conPos.y(),
                this.offset.z + conPos.z());
        } else {
            return this.player.position().add(0.0D, 1.62D, 0.0D);
        }
    }

    /**
     * @param c controller to get the position for
     * @return controller position in world space
     */
    public Vec3 getControllerPos(int c) {
        return getControllerPos(c, false);
    }

    /**
     * @return if the player has VR active
     */
    public boolean isVR() {
        return this.isVR;
    }

    /**
     * set VR state of the player
     */
    public void setVR(boolean vr) {
        this.isVR = vr;
    }

    /**
     * @return if the player is using seated VR
     */
    public boolean isSeated() {
        return this.vrPlayerState != null && this.vrPlayerState.seated();
    }
}
