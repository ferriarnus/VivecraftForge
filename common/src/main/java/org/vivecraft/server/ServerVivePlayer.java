package org.vivecraft.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vivecraft.common.network.*;
import org.vivecraft.common.utils.MathUtils;

import javax.annotation.Nullable;

public class ServerVivePlayer {
    // player movement state
    @Nullable
    public VrPlayerState vrPlayerState;
    // how much the player is drawing the roomscale bow
    public float draw;
    public float worldScale = 1.0F;
    public float heightScale = 1.0F;
    public Limb activeLimb = Limb.MAIN_HAND;
    public boolean crawling;
    // if the player has VR active
    private boolean isVR = false;
    // offset set during aimFix to keep the original data positions
    public Vec3 offset = Vec3.ZERO;
    // player this data belongs to
    public ServerPlayer player;
    // network protocol this player is communicating with
    public int networkVersion = CommonNetworkHelper.MAX_SUPPORTED_NETWORK_VERSION;

    public ServerVivePlayer(ServerPlayer player) {
        this.player = player;
    }

    /**
     * transforms the local {@code direction} vector on limb {@code limb} into world space
     * @param limb limb to get the direction on, if not available, will use the MAIN_HAND
     * @param direction local direction to transform
     * @return direction in world space
     */
    public Vec3 getLimbVectorCustom(Limb limb, Vector3fc direction) {
        if (this.vrPlayerState != null) {
            if (this.isSeated() || (this.vrPlayerState.fbtMode() == FBTMode.ARMS_ONLY && limb.ordinal() > 1)) {
                limb = Limb.MAIN_HAND;
            }

            Pose limbPose = switch (limb) {
                case OFF_HAND -> this.vrPlayerState.offHand();
                case LEFT_FOOT -> this.vrPlayerState.leftFoot();
                case RIGHT_FOOT -> this.vrPlayerState.rightFoot();
                default -> this.vrPlayerState.mainHand();
            };

            return new Vec3(limbPose.orientation().transform(direction, new Vector3f()));
        } else {
            return this.player.getLookAngle();
        }
    }

    /**
     * @param limb limb to get the direction from, if not available, will use the MAIN_HAND
     * @return forward direction of the given controller
     */
    public Vec3 getLimbDir(Limb limb) {
        return this.getLimbVectorCustom(limb, MathUtils.BACK);
    }

    /**
     * @return the direction the player is aiming, accounts for the roomscale bow
     */
    public Vec3 getAimDir() {
        if (!this.isSeated() && this.draw > 0.0F) {
            return this.getLimbPos(this.activeLimb.opposite())
                .subtract(this.getLimbPos(this.activeLimb)).normalize();
        } else {
            return this.getLimbDir(this.activeLimb);
        }
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
     * @param limb limb to get the position for, if not available, will use the MAIN_HAND
     * @param realPosition if true disables the seated override
     * @return controller position in world space
     */
    public Vec3 getLimbPos(Limb limb, boolean realPosition) {
        if (this.vrPlayerState != null) {
            if (this.vrPlayerState.fbtMode() == FBTMode.ARMS_ONLY && limb.ordinal() > 1) {
                limb = Limb.MAIN_HAND;
            }

            // in seated the realPosition is at the head,
            // so reconstruct the seated position when wanting the visual position
            if (this.isSeated() && !realPosition) {
                Vec3 dir = this.getHMDDir();
                dir = dir.yRot(Mth.DEG_TO_RAD * (limb == Limb.MAIN_HAND ? -35.0F : 35.0F));
                dir = new Vec3(dir.x, 0.0D, dir.z);
                dir = dir.normalize();
                return this.getHMDPos().add(
                    dir.x * 0.3F * this.worldScale,
                    -0.4F * this.worldScale,
                    dir.z * 0.3F * this.worldScale);
            }

            Vector3fc conPos = switch(limb) {
                case OFF_HAND -> this.vrPlayerState.offHand().position();
                case LEFT_FOOT -> this.vrPlayerState.leftFoot().position();
                case RIGHT_FOOT -> this.vrPlayerState.rightFoot().position();
                default -> this.vrPlayerState.mainHand().position();
            };

            return this.player.position().add(
                this.offset.x + conPos.x(),
                this.offset.y + conPos.y(),
                this.offset.z + conPos.z());
        } else {
            return this.player.position().add(0.0D, 1.62D, 0.0D);
        }
    }

    /**
     * @param limb to get the position for, if not available, will use the MAIN_HAND
     * @return controller position in world space
     */
    public Vec3 getLimbPos(Limb limb) {
        return getLimbPos(limb, false);
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
