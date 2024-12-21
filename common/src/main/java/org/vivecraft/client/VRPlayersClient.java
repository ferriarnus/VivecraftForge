package org.vivecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vivecraft.client.extensions.SparkParticleExtension;
import org.vivecraft.client.utils.ClientUtils;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.render.helpers.RenderHelper;
import org.vivecraft.client_vr.settings.AutoCalibration;
import org.vivecraft.common.network.FBTMode;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.common.network.VrPlayerState;

import java.util.*;

public class VRPlayersClient {

    private static VRPlayersClient INSTANCE;

    private final Minecraft mc;
    private final Map<UUID, RotInfo> vivePlayers = new HashMap<>();
    private final Map<UUID, RotInfo> vivePlayersLast = new HashMap<>();
    private final Map<UUID, RotInfo> vivePlayersReceived = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, Integer> donors = new HashMap<>();

    private static long LOCAL_PLAYER_ROT_INFO_FRAME_INDEX = -1;
    private static RotInfo LOCAL_PLAYER_ROT_INFO;

    private final Random rand = new Random();

    public static VRPlayersClient getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new VRPlayersClient();
        }

        return INSTANCE;
    }

    public static void clear() {
        if (INSTANCE != null) {
            INSTANCE.vivePlayers.clear();
            INSTANCE.vivePlayersLast.clear();
            INSTANCE.vivePlayersReceived.clear();
        }
        LOCAL_PLAYER_ROT_INFO = null;
        LOCAL_PLAYER_ROT_INFO_FRAME_INDEX = -1;
    }

    private VRPlayersClient() {
        this.mc = Minecraft.getInstance();
    }

    /**
     * checks if there is VR data for the given player
     * @param player Player to check
     * @return true if data is available
     */
    public boolean isVRPlayer(Entity player) {
        return isVRPlayer(player.getUUID());
    }

    /**
     * checks if there is VR data for the given UUID of a player
     * @param uuid UUID to check
     * @return true if data is available
     */
    public boolean isVRPlayer(UUID uuid) {
        return this.vivePlayers.containsKey(uuid) ||
            (VRState.VR_RUNNING && this.mc.player != null && uuid.equals(this.mc.player.getUUID()));
    }

    /**
     * checks if the given player is in VR and using reversed hands, without lerping the RotInfo
     * @param uuid UUID of the player
     * @return if the player is in VR and using reversed hands
     */
    public boolean isVRAndLeftHanded(UUID uuid) {
        return (this.vivePlayers.containsKey(uuid) && this.vivePlayers.get(uuid).leftHanded) ||
            (VRState.VR_RUNNING && this.mc.player != null && uuid.equals(this.mc.player.getUUID()) &&
                ClientDataHolderVR.getInstance().vrSettings.reverseHands
            );
    }

    public void disableVR(UUID player) {
        this.vivePlayers.remove(player);
        this.vivePlayersLast.remove(player);
        this.vivePlayersReceived.remove(player);
    }

    public void update(
        UUID uuid, VrPlayerState vrPlayerState, float worldScale, float heightScale, boolean localPlayer)
    {
        if (!localPlayer && this.mc.player.getUUID().equals(uuid)) {
            return; // Don't update local player from server packet
        }

        Vector3fc hmdDir = vrPlayerState.hmd().orientation().transform(MathUtils.BACK, new Vector3f());
        Vector3fc controller0Dir = vrPlayerState.mainHand().orientation().transform(MathUtils.BACK, new Vector3f());
        Vector3fc controller1Dir = vrPlayerState.offHand().orientation().transform(MathUtils.BACK, new Vector3f());

        RotInfo rotInfo = new RotInfo();
        rotInfo.leftHanded = vrPlayerState.leftHanded();
        rotInfo.seated = vrPlayerState.seated();

        rotInfo.hmd = this.donors.getOrDefault(uuid, 0);

        rotInfo.offHandRot = controller1Dir;
        rotInfo.mainHandRot = controller0Dir;
        rotInfo.headRot = hmdDir;

        rotInfo.offHandPos = vrPlayerState.offHand().position();
        rotInfo.mainHandPos = vrPlayerState.mainHand().position();
        rotInfo.headPos = vrPlayerState.hmd().position();

        rotInfo.offHandQuat = vrPlayerState.offHand().orientation();
        rotInfo.mainHandQuat = vrPlayerState.mainHand().orientation();
        rotInfo.headQuat = vrPlayerState.hmd().orientation();

        rotInfo.worldScale = worldScale;

        if (heightScale < 0.5F) {
            heightScale = 0.5F;
        }
        if (heightScale > 1.5F) {
            heightScale = 1.5F;
        }

        rotInfo.heightScale = heightScale;

        if (rotInfo.seated) {
            rotInfo.heightScale = 1.0F;
        }

        rotInfo.fbtMode = vrPlayerState.fbtMode();
        if (rotInfo.fbtMode != FBTMode.ARMS_ONLY) {
            rotInfo.waistPos = vrPlayerState.waist().position();
            rotInfo.rightFootPos = vrPlayerState.rightFoot().position();
            rotInfo.leftFootPos = vrPlayerState.leftFoot().position();

            rotInfo.waistQuat = vrPlayerState.waist().orientation();
            rotInfo.rightFootQuat = vrPlayerState.rightFoot().orientation();
            rotInfo.leftFootQuat = vrPlayerState.leftFoot().orientation();
        }
        if (rotInfo.fbtMode == FBTMode.WITH_JOINTS) {
            rotInfo.rightKneePos = vrPlayerState.rightKnee().position();
            rotInfo.leftKneePos = vrPlayerState.leftKnee().position();
            rotInfo.rightElbowPos = vrPlayerState.rightElbow().position();
            rotInfo.leftElbowPos = vrPlayerState.leftElbow().position();

            rotInfo.rightKneeQuat = vrPlayerState.rightKnee().orientation();
            rotInfo.leftKneeQuat = vrPlayerState.leftKnee().orientation();
            rotInfo.rightElbowQuat = vrPlayerState.rightElbow().orientation();
            rotInfo.leftElbowQuat = vrPlayerState.leftElbow().orientation();
        }

        this.vivePlayersReceived.put(uuid, rotInfo);
    }

    public void update(UUID uuid, VrPlayerState vrPlayerState, float worldScale, float heightScale) {
        this.update(uuid, vrPlayerState, worldScale, heightScale, false);
    }

    public void tick() {
        this.vivePlayersLast.putAll(this.vivePlayers);

        this.vivePlayers.putAll(this.vivePlayersReceived);

        Level level = this.mc.level;

        if (level != null) {

            // remove players that no longer exist
            Iterator<UUID> iterator = this.vivePlayers.keySet().iterator();
            while (iterator.hasNext()) {
                UUID uuid = iterator.next();

                if (level.getPlayerByUUID(uuid) == null) {
                    iterator.remove();
                    this.vivePlayersLast.remove(uuid);
                    this.vivePlayersReceived.remove(uuid);
                }
            }

            if (!this.mc.isPaused()) {
                for (Player player : level.players()) {
                    // donor butt sparkles
                    if (this.donors.getOrDefault(player.getUUID(), 0) > 3 && this.rand.nextInt(10) < 4) {
                        RotInfo rotInfo = this.vivePlayers.get(player.getUUID());
                        Vector3f look = player.getLookAngle().toVector3f();
                        if (rotInfo != null) {
                            look = rotInfo.offHandPos.sub(rotInfo.mainHandPos, look).rotateY(-Mth.HALF_PI);

                            if (rotInfo.leftHanded) {
                                look = look.mul(-1.0F);
                            } else if (rotInfo.seated) {
                                look.set(rotInfo.mainHandRot);
                            }

                            // Hands are at origin or something, usually happens if they don't track
                            if (look.length() < 1.0E-4F) {
                                look.set(rotInfo.headRot);
                            }
                        }
                        look = look.mul(0.1F);

                        // Use hmd pos for self, so we don't have butt sparkles in face
                        Vec3 pos = rotInfo != null && player == this.mc.player ?
                            player.position().add(rotInfo.headPos.x(), rotInfo.headPos.y(), rotInfo.headPos.z()) :
                            player.getEyePosition(1.0F);

                        Particle particle = this.mc.particleEngine.createParticle(
                            ParticleTypes.FIREWORK,
                            pos.x + (player.isShiftKeyDown() ? -look.x * 3.0D : 0.0D) +
                                (this.rand.nextFloat() - 0.5F) * 0.02F,
                            pos.y - (player.isShiftKeyDown() ? 1.0F : 0.8F) +
                                (this.rand.nextFloat() - 0.5F) * 0.02F,
                            pos.z + (player.isShiftKeyDown() ? -look.z * 3.0D : 0.0D) +
                                (this.rand.nextFloat() - 0.5F) * 0.02F,
                            -look.x + (this.rand.nextFloat() - 0.5F) * 0.01F,
                            (this.rand.nextFloat() - 0.05F) * 0.05F,
                            -look.z + (this.rand.nextFloat() - 0.5F) * 0.01F);

                        if (particle != null) {
                            particle.setColor(0.5F + this.rand.nextFloat() * 0.5F,
                                0.5F + this.rand.nextFloat() * 0.5F,
                                0.5F + this.rand.nextFloat() * 0.5F);

                            ((SparkParticleExtension) particle).vivecraft$setPlayerUUID(player.getUUID());
                        }
                    }
                }
            }
        }
    }

    public void setHMD(UUID uuid, int level) {
        this.donors.put(uuid, level);
    }

    public boolean hasHMD(UUID uuid) {
        return this.donors.containsKey(uuid);
    }

    public RotInfo getRotationsForPlayer(UUID uuid) {
        float partialTick = ClientUtils.getCurrentPartialTick();

        if (VRState.VR_RUNNING && this.mc.player != null && uuid.equals(this.mc.player.getUUID())) {
            return getMainPlayerRotInfo(this.mc.player, partialTick);
        }

        RotInfo newRotInfo = this.vivePlayers.get(uuid);

        if (newRotInfo != null && this.vivePlayersLast.containsKey(uuid)) {
            RotInfo lastRotInfo = this.vivePlayersLast.get(uuid);
            RotInfo lerpRotInfo = new RotInfo();

            lerpRotInfo.leftHanded = newRotInfo.leftHanded;
            lerpRotInfo.seated = newRotInfo.seated;
            lerpRotInfo.hmd = newRotInfo.hmd;

            lerpRotInfo.offHandPos = lastRotInfo.offHandPos.lerp(newRotInfo.offHandPos, partialTick, new Vector3f());
            lerpRotInfo.mainHandPos = lastRotInfo.mainHandPos.lerp(newRotInfo.mainHandPos, partialTick, new Vector3f());
            lerpRotInfo.headPos = lastRotInfo.headPos.lerp(newRotInfo.headPos, partialTick, new Vector3f());

            lerpRotInfo.offHandQuat = newRotInfo.offHandQuat;
            lerpRotInfo.mainHandQuat = newRotInfo.mainHandQuat;
            lerpRotInfo.headQuat = newRotInfo.headQuat;

            lerpRotInfo.offHandRot = lastRotInfo.offHandRot.lerp(newRotInfo.offHandRot, partialTick, new Vector3f());
            lerpRotInfo.mainHandRot = lastRotInfo.mainHandRot.lerp(newRotInfo.mainHandRot, partialTick, new Vector3f());
            lerpRotInfo.headRot = lastRotInfo.headRot.lerp(newRotInfo.headRot, partialTick, new Vector3f());

            lerpRotInfo.heightScale = newRotInfo.heightScale;
            lerpRotInfo.worldScale = newRotInfo.worldScale;

            // use the smallest one, since we can't interpolate missing data
            lerpRotInfo.fbtMode = FBTMode.values()[Math.min(lastRotInfo.fbtMode.ordinal(),
                newRotInfo.fbtMode.ordinal())];

            // check last lastRotInfo since these can be null
            if (lastRotInfo.fbtMode != FBTMode.ARMS_ONLY && newRotInfo.fbtMode != FBTMode.ARMS_ONLY) {
                lerpRotInfo.waistPos = lastRotInfo.waistPos.lerp(newRotInfo.waistPos, partialTick, new Vector3f());
                lerpRotInfo.rightFootPos = lastRotInfo.rightFootPos.lerp(newRotInfo.rightFootPos, partialTick,
                    new Vector3f());
                lerpRotInfo.leftFootPos = lastRotInfo.leftFootPos.lerp(newRotInfo.leftFootPos, partialTick,
                    new Vector3f());

                lerpRotInfo.waistQuat = newRotInfo.waistQuat;
                lerpRotInfo.rightFootQuat = newRotInfo.rightFootQuat;
                lerpRotInfo.leftFootQuat = newRotInfo.leftFootQuat;

                if (lastRotInfo.fbtMode == FBTMode.WITH_JOINTS && newRotInfo.fbtMode == FBTMode.WITH_JOINTS) {
                    lerpRotInfo.leftKneePos = lastRotInfo.leftKneePos.lerp(newRotInfo.leftKneePos, partialTick,
                        new Vector3f());
                    lerpRotInfo.rightKneePos = lastRotInfo.rightKneePos.lerp(newRotInfo.rightKneePos, partialTick,
                        new Vector3f());
                    lerpRotInfo.leftElbowPos = lastRotInfo.leftElbowPos.lerp(newRotInfo.leftElbowPos, partialTick,
                        new Vector3f());
                    lerpRotInfo.rightElbowPos = lastRotInfo.rightElbowPos.lerp(newRotInfo.rightElbowPos, partialTick,
                        new Vector3f());

                    lerpRotInfo.leftKneeQuat = newRotInfo.leftKneeQuat;
                    lerpRotInfo.rightKneeQuat = newRotInfo.rightKneeQuat;
                    lerpRotInfo.leftElbowQuat = newRotInfo.leftElbowQuat;
                    lerpRotInfo.rightElbowQuat = newRotInfo.rightElbowQuat;
                }
            }

            return lerpRotInfo;
        } else {
            return newRotInfo;
        }
    }

    /**
     * returns the RotInfo object for the current client VR state
     * @param player player to center the data around
     * @param partialTick partial tick to get the player position
     * @return up to date RotInfo
     */
    public static RotInfo getMainPlayerRotInfo(LivingEntity player, float partialTick) {
        if (LOCAL_PLAYER_ROT_INFO != null &&
            ClientDataHolderVR.getInstance().frameIndex == LOCAL_PLAYER_ROT_INFO_FRAME_INDEX)
        {
            return LOCAL_PLAYER_ROT_INFO;
        }

        RotInfo rotInfo = new RotInfo();

        VRData data = ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld();

        rotInfo.seated = ClientDataHolderVR.getInstance().vrSettings.seated;
        rotInfo.leftHanded = ClientDataHolderVR.getInstance().vrSettings.reverseHands;
        rotInfo.fbtMode = data.fbtMode;

        rotInfo.heightScale = AutoCalibration.getPlayerHeight() / AutoCalibration.DEFAULT_HEIGHT;
        rotInfo.worldScale = ClientDataHolderVR.getInstance().vrPlayer.worldScale;

        LOCAL_PLAYER_ROT_INFO_FRAME_INDEX = ClientDataHolderVR.getInstance().frameIndex;
        LOCAL_PLAYER_ROT_INFO = rotInfo;

        rotInfo.offHandQuat = data.getController(MCVR.OFFHAND_CONTROLLER).getMatrix()
            .getNormalizedRotation(new Quaternionf());
        rotInfo.mainHandQuat = data.getController(MCVR.MAIN_CONTROLLER).getMatrix()
            .getNormalizedRotation(new Quaternionf());
        rotInfo.headQuat = data.hmd.getMatrix().getNormalizedRotation(new Quaternionf());

        rotInfo.offHandRot = rotInfo.offHandQuat.transform(MathUtils.BACK, new Vector3f());
        rotInfo.mainHandRot = rotInfo.mainHandQuat.transform(MathUtils.BACK, new Vector3f());
        rotInfo.headRot = rotInfo.headQuat.transform(MathUtils.BACK, new Vector3f());

        Vec3 pos;
        if (player == Minecraft.getInstance().player) {
            pos = ((GameRendererExtension)Minecraft.getInstance().gameRenderer).vivecraft$getRvePos(partialTick);
        } else {
            pos = player.getPosition(partialTick);
        }

        rotInfo.offHandPos = MathUtils.subtractToVector3f(
            RenderHelper.getControllerRenderPos(MCVR.OFFHAND_CONTROLLER), pos);
        rotInfo.mainHandPos = MathUtils.subtractToVector3f(
            RenderHelper.getControllerRenderPos(MCVR.MAIN_CONTROLLER), pos);
        rotInfo.headPos = MathUtils.subtractToVector3f(data.hmd.getPosition(), pos);

        if (data.fbtMode != FBTMode.ARMS_ONLY) {
            rotInfo.waistQuat = data.getDevice(MCVR.WAIST_TRACKER).getMatrix()
                .getNormalizedRotation(new Quaternionf());
            rotInfo.rightFootQuat = data.getDevice(MCVR.RIGHT_FOOT_TRACKER).getMatrix()
                .getNormalizedRotation(new Quaternionf());
            rotInfo.leftFootQuat = data.getDevice(MCVR.LEFT_FOOT_TRACKER).getMatrix()
                .getNormalizedRotation(new Quaternionf());

            rotInfo.waistPos = MathUtils.subtractToVector3f(
                data.getDevice(MCVR.WAIST_TRACKER).getPosition(), pos);
            rotInfo.rightFootPos = MathUtils.subtractToVector3f(
                data.getDevice(MCVR.RIGHT_FOOT_TRACKER).getPosition(),
                pos);
            rotInfo.leftFootPos = MathUtils.subtractToVector3f(
                data.getDevice(MCVR.LEFT_FOOT_TRACKER).getPosition(), pos);

            if (data.fbtMode == FBTMode.WITH_JOINTS) {
                rotInfo.leftKneeQuat = data.getDevice(MCVR.LEFT_KNEE_TRACKER).getMatrix()
                    .getNormalizedRotation(new Quaternionf());
                rotInfo.rightKneeQuat = data.getDevice(MCVR.RIGHT_KNEE_TRACKER).getMatrix()
                    .getNormalizedRotation(new Quaternionf());
                rotInfo.leftElbowQuat = data.getDevice(MCVR.LEFT_ELBOW_TRACKER).getMatrix()
                    .getNormalizedRotation(new Quaternionf());
                rotInfo.rightElbowQuat = data.getDevice(MCVR.RIGHT_ELBOW_TRACKER).getMatrix()
                    .getNormalizedRotation(new Quaternionf());

                rotInfo.leftKneePos = MathUtils.subtractToVector3f(
                    data.getDevice(MCVR.LEFT_KNEE_TRACKER).getPosition(), pos);
                rotInfo.rightKneePos = MathUtils.subtractToVector3f(
                    data.getDevice(MCVR.RIGHT_KNEE_TRACKER).getPosition(), pos);
                rotInfo.leftElbowPos = MathUtils.subtractToVector3f(
                    data.getDevice(MCVR.LEFT_ELBOW_TRACKER).getPosition(), pos);
                rotInfo.rightElbowPos = MathUtils.subtractToVector3f(
                    data.getDevice(MCVR.RIGHT_ELBOW_TRACKER).getPosition(), pos);
            }
        }

        return rotInfo;
    }

    /**
     * @return the yaw of the direction the head is oriented in, no matter their pitch
     * Is not the same as the hmd yaw. Creates better results at extreme pitches
     * Simplified: Takes hmd-forward when looking at horizon, takes hmd-up when looking at ground.
     * */
    public static float getFacingYaw(RotInfo rotInfo) {
        Vector3f facingVec = getOrientVec(rotInfo.headQuat);
        return (float) Math.toDegrees(Math.atan2(facingVec.x, facingVec.z));
    }

    public static Vector3f getOrientVec(Quaternionfc quat) {
        Vector3f localFwd = quat.transform(MathUtils.BACK, new Vector3f());
        Vector3f localUp = quat.transform(MathUtils.UP, new Vector3f());

        Vector3f facingPlaneNormal = localFwd.cross(localUp).normalize();
        return MathUtils.UP.cross(facingPlaneNormal, new Vector3f()).normalize();
    }

    public static class RotInfo {
        public boolean seated;
        public boolean leftHanded;
        public int hmd = 0;
        public Quaternionfc offHandQuat;
        public Quaternionfc mainHandQuat;
        public Quaternionfc headQuat;
        // body rotations in world space
        public Vector3fc offHandRot;
        public Vector3fc mainHandRot;
        public Vector3fc headRot;
        // body positions in player local world space
        public Vector3fc offHandPos;
        public Vector3fc mainHandPos;
        public Vector3fc headPos;
        public float worldScale;
        public float heightScale;

        public FBTMode fbtMode;
        public Vector3fc waistPos;
        public Quaternionfc waistQuat;
        public Vector3fc rightFootPos;
        public Quaternionfc rightFootQuat;
        public Vector3fc leftFootPos;
        public Quaternionfc leftFootQuat;

        public Vector3fc rightKneePos;
        public Quaternionfc rightKneeQuat;
        public Vector3fc leftKneePos;
        public Quaternionfc leftKneeQuat;
        public Vector3fc rightElbowPos;
        public Quaternionfc rightElbowQuat;
        public Vector3fc leftElbowPos;
        public Quaternionfc leftElbowQuat;

        /**
         *  IMPORTANT!!! when changing this, also change {@link VRData#getBodyYawRad()}
         */
        public float getBodyYawRad() {
            Vector3f dir = new Vector3f();
            if (this.seated ||
                (this.fbtMode == FBTMode.ARMS_ONLY && this.offHandPos.distanceSquared(this.mainHandPos) == 0.0F))
            {
                // in seated use the head direction
                dir.set(this.headRot);
            } else if (this.fbtMode != FBTMode.ARMS_ONLY) {
                // use average of head and waist
                this.waistQuat.transform(MathUtils.BACK, dir)
                    .lerp(this.headRot, 0.5F);
            } else {
                return MathUtils.bodyYawRad(
                    this.leftHanded ? this.offHandPos : this.mainHandPos,
                    this.leftHanded ? this.mainHandPos : this.offHandPos,
                    this.headRot);
            }
            return (float) Math.atan2(-dir.x, dir.z);
        }
    }
}
