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
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.client.extensions.SparkParticleExtension;
import org.vivecraft.client.utils.ClientUtils;
import org.vivecraft.client.utils.ModelUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.render.helpers.RenderHelper;
import org.vivecraft.client_vr.settings.AutoCalibration;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.api_impl.data.VRPoseImpl;
import org.vivecraft.common.api_impl.data.VRBodyPartDataImpl;
import org.vivecraft.api.data.FBTMode;
import org.vivecraft.common.network.VrPlayerState;
import org.vivecraft.common.utils.MathUtils;

import java.util.*;

public class ClientVRPlayers {

    private static ClientVRPlayers INSTANCE;

    private final Minecraft mc;
    private final Map<UUID, RotInfo> vivePlayers = new HashMap<>();
    private final Map<UUID, RotInfo> vivePlayersLast = new HashMap<>();
    private final Map<UUID, RotInfo> vivePlayersReceived = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, Integer> donors = new HashMap<>();

    private static long LOCAL_PLAYER_ROT_INFO_FRAME_INDEX = -1;
    private static RotInfo LOCAL_PLAYER_ROT_INFO;

    private final Random rand = new Random();

    public static ClientVRPlayers getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClientVRPlayers();
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

    private ClientVRPlayers() {
        this.mc = Minecraft.getInstance();
    }

    /**
     * checks if there is VR data for the given player
     *
     * @param player Player to check
     * @return true if data is available
     */
    public boolean isVRPlayer(Entity player) {
        return isVRPlayer(player.getUUID());
    }

    /**
     * checks if there is VR data for the given UUID of a player
     *
     * @param uuid UUID to check
     * @return true if data is available
     */
    public boolean isVRPlayer(UUID uuid) {
        return this.vivePlayers.containsKey(uuid) ||
            (VRState.VR_RUNNING && this.mc.player != null && uuid.equals(this.mc.player.getUUID()));
    }

    /**
     * checks if the given player is in VR and using seated mode, without lerping the RotInfo
     *
     * @param uuid UUID of the player
     * @return if the player is in VR and using seated modes
     */
    public boolean isVRAndSeated(UUID uuid) {
        return (this.vivePlayers.containsKey(uuid) && this.vivePlayers.get(uuid).seated) ||
            (VRState.VR_RUNNING && this.mc.player != null && uuid.equals(this.mc.player.getUUID()) &&
                ClientDataHolderVR.getInstance().vrSettings.seated
            );
    }

    /**
     * checks if the given player is in VR and using reversed hands, without lerping the RotInfo
     *
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
        if (!localPlayer && this.mc.player.getUUID().equals(uuid) &&
            ClientDataHolderVR.getInstance().vrSettings.mainPlayerDataSource != VRSettings.DataSource.SERVER)
        {
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
                        float xzOffset = player.isShiftKeyDown() ? 6F : 0F;
                        float yOffset = player.isShiftKeyDown() ? 0.6F : 0.8F;

                        Vec3 pos = player.position();
                        Vector3f look;
                        if (rotInfo != null) {
                            look = MathUtils.FORWARD.rotateY(-rotInfo.getBodyYawRad(), new Vector3f());
                            if (player.isVisuallySwimming() && (player.isInWater() || rotInfo.fbtMode == FBTMode.ARMS_ONLY)) {
                                yOffset = 0.3F * rotInfo.heightScale;
                                xzOffset = 14f * rotInfo.heightScale;

                                if (player.isInWater()) {
                                    Vector3f pivot = rotInfo.headQuat.transform(0F, -0.2F, 0.1F, new Vector3f())
                                        .add(rotInfo.headPos);
                                    pos = pos.add(pivot.x, pivot.y, pivot.z);
                                }
                            } else {
                                if (rotInfo.fbtMode != FBTMode.ARMS_ONLY) {
                                    // use waist
                                    pos = pos.add(rotInfo.waistPos.x(), rotInfo.waistPos.y(), rotInfo.waistPos.z());
                                    yOffset = 0F;
                                } else {
                                    Vector3f pivot = rotInfo.headQuat.transform(0F, -0.2F, 0.1F, new Vector3f())
                                        .add(rotInfo.headPos);
                                    float bend = ModelUtils.getBendProgress(player, rotInfo, pivot);
                                    if (ClientDataHolderVR.getInstance().vrSettings.playerModelType ==
                                        VRSettings.PlayerModelType.SPLIT_ARMS_LEGS)
                                    {
                                        yOffset = -0.7F * Mth.cos(bend*Mth.HALF_PI) * rotInfo.heightScale;
                                        xzOffset = bend * 14f * rotInfo.heightScale;
                                    } else {
                                        yOffset = -0.7F * Mth.cos(bend*Mth.PI) * rotInfo.heightScale;
                                        xzOffset = 14f * rotInfo.heightScale * Mth.sin(bend * Mth.PI);
                                    }
                                    pos = pos.add(pivot.x, pivot.y, pivot.z);
                                }
                            }
                        } else {
                            look = MathUtils.FORWARD.rotateY(-Mth.DEG_TO_RAD * player.yBodyRot, new Vector3f());
                        }
                        look = look.mul(0.05F);

                        Particle particle = this.mc.particleEngine.createParticle(
                            ParticleTypes.FIREWORK,
                            pos.x - look.x * xzOffset + (this.rand.nextFloat() - 0.5F) * 0.02F,
                            pos.y + yOffset + (this.rand.nextFloat() - 0.5F) * 0.02F,
                            pos.z - look.z * xzOffset + (this.rand.nextFloat() - 0.5F) * 0.02F,
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

    public int getHMD(UUID uuid) {
        return this.donors.getOrDefault(uuid, 0);
    }

    /**
     * gets the latest clientside player data, use this when not rendering, i.e. on tick
     * @param uuid uuid of the player to get the data for
     * @return latest available player data
     */
    public RotInfo getLatestRotationsForPlayer(UUID uuid) {
        return this.vivePlayers.containsKey(uuid) ? this.vivePlayers.get(uuid) : this.vivePlayersLast.get(uuid);
    }

    /**
     * gets the clientside interpolated player data, this one should only be called during rendering
     * @param uuid uuid of the player to get the data for
     * @return interpolated data
     */
    public RotInfo getRotationsForPlayer(UUID uuid) {
        float partialTick = ClientUtils.getCurrentPartialTick();

        if (VRState.VR_RUNNING && this.mc.player != null && uuid.equals(this.mc.player.getUUID()) &&
            ClientDataHolderVR.getInstance().vrSettings.mainPlayerDataSource == VRSettings.DataSource.REALTIME)
        {
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

            lerpRotInfo.offHandQuat = lastRotInfo.offHandQuat.nlerp(newRotInfo.offHandQuat, partialTick,
                new Quaternionf());
            lerpRotInfo.mainHandQuat = lastRotInfo.mainHandQuat.nlerp(newRotInfo.mainHandQuat, partialTick,
                new Quaternionf());
            lerpRotInfo.headQuat = lastRotInfo.headQuat.nlerp(newRotInfo.headQuat, partialTick,
                new Quaternionf());

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

                lerpRotInfo.waistQuat = lastRotInfo.waistQuat.nlerp(newRotInfo.waistQuat, partialTick,
                    new Quaternionf());
                lerpRotInfo.rightFootQuat = lastRotInfo.rightFootQuat.nlerp(newRotInfo.rightFootQuat, partialTick,
                    new Quaternionf());
                lerpRotInfo.leftFootQuat = lastRotInfo.leftFootQuat.nlerp(newRotInfo.leftFootQuat, partialTick,
                    new Quaternionf());

                if (lastRotInfo.fbtMode == FBTMode.WITH_JOINTS && newRotInfo.fbtMode == FBTMode.WITH_JOINTS) {
                    lerpRotInfo.leftKneePos = lastRotInfo.leftKneePos.lerp(newRotInfo.leftKneePos, partialTick,
                        new Vector3f());
                    lerpRotInfo.rightKneePos = lastRotInfo.rightKneePos.lerp(newRotInfo.rightKneePos, partialTick,
                        new Vector3f());
                    lerpRotInfo.leftElbowPos = lastRotInfo.leftElbowPos.lerp(newRotInfo.leftElbowPos, partialTick,
                        new Vector3f());
                    lerpRotInfo.rightElbowPos = lastRotInfo.rightElbowPos.lerp(newRotInfo.rightElbowPos, partialTick,
                        new Vector3f());

                    lerpRotInfo.leftKneeQuat = lastRotInfo.leftKneeQuat.nlerp(newRotInfo.leftKneeQuat, partialTick,
                        new Quaternionf());
                    lerpRotInfo.rightKneeQuat = lastRotInfo.rightKneeQuat.nlerp(newRotInfo.rightKneeQuat, partialTick,
                        new Quaternionf());
                    lerpRotInfo.leftElbowQuat = lastRotInfo.leftElbowQuat.nlerp(newRotInfo.leftElbowQuat, partialTick,
                        new Quaternionf());
                    lerpRotInfo.rightElbowQuat = lastRotInfo.rightElbowQuat.nlerp(newRotInfo.rightElbowQuat,
                        partialTick, new Quaternionf());
                }
            }

            return lerpRotInfo;
        } else {
            return newRotInfo;
        }
    }

    /**
     * returns the RotInfo object for the current client VR state
     *
     * @param player      player to center the data around
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

        rotInfo.hmd = INSTANCE.donors.getOrDefault(player.getUUID(), 0);

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
            pos = ((GameRendererExtension) Minecraft.getInstance().gameRenderer).vivecraft$getRvePos(partialTick);
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
     */
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
         * IMPORTANT!!! when changing this, also change {@link VRData#getBodyYawRad()}
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

        public VRPose asVRPose() {
            return new VRPoseImpl(
                makeBodyPartData(this.headPos, this.headRot, this.headQuat),
                makeBodyPartData(this.mainHandPos, this.mainHandRot, this.mainHandQuat),
                makeBodyPartData(this.offHandPos, this.offHandRot, this.offHandQuat),
                makeBodyPartData(this.rightFootPos, this.rightFootQuat, this.fbtMode.bodyPartAvailable(VRBodyPart.RIGHT_FOOT)),
                makeBodyPartData(this.leftFootPos, this.leftFootQuat, this.fbtMode.bodyPartAvailable(VRBodyPart.LEFT_FOOT)),
                makeBodyPartData(this.waistPos, this.waistQuat, this.fbtMode.bodyPartAvailable(VRBodyPart.WAIST)),
                makeBodyPartData(this.rightKneePos, this.rightKneeQuat, this.fbtMode.bodyPartAvailable(VRBodyPart.RIGHT_KNEE)),
                makeBodyPartData(this.leftKneePos, this.leftKneeQuat, this.fbtMode.bodyPartAvailable(VRBodyPart.LEFT_KNEE)),
                makeBodyPartData(this.rightElbowPos, this.rightElbowQuat, this.fbtMode.bodyPartAvailable(VRBodyPart.RIGHT_ELBOW)),
                makeBodyPartData(this.leftElbowPos, this.leftElbowQuat, this.fbtMode.bodyPartAvailable(VRBodyPart.LEFT_ELBOW)),
                this.seated,
                this.leftHanded,
                this.fbtMode
            );
        }
    }

    private static VRBodyPartDataImpl makeBodyPartData(Vector3fc pos, Vector3fc rot, Quaternionfc quat) {
        return new VRBodyPartDataImpl(fromVector3fc(pos), fromVector3fc(rot), quat);
    }

    private static VRBodyPartDataImpl makeBodyPartData(Vector3fc pos, Quaternionfc quat, boolean partAvailable) {
        return partAvailable
            ? new VRBodyPartDataImpl(fromVector3fc(pos), fromVector3fc(quat.transform(MathUtils.BACK, new Vector3f())), quat)
            : null;
    }

    private static Vec3 fromVector3fc(Vector3fc vec) {
        return new Vec3(vec.x(), vec.y(), vec.z());
    }
}
