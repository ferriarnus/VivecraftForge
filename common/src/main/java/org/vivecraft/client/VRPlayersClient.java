package org.vivecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client.extensions.SparkParticleExtension;
import org.vivecraft.client.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.common.network.VrPlayerState;
import org.vivecraft.common.utils.math.Quaternion;
import org.vivecraft.common.utils.math.Vector3;

import java.util.*;

public class VRPlayersClient {

    private static VRPlayersClient INSTANCE;

    private final Minecraft mc;
    private final Map<UUID, RotInfo> vivePlayers = new HashMap<>();
    private final Map<UUID, RotInfo> vivePlayersLast = new HashMap<>();
    private final Map<UUID, RotInfo> vivePlayersReceived = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, Integer> donors = new HashMap<>();
    private final Random rand = new Random();
    public boolean debug = false;

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
    }

    private VRPlayersClient() {
        this.mc = Minecraft.getInstance();
    }

    public boolean isVRPlayer(Player player) {
        return this.vivePlayers.containsKey(player.getUUID());
    }

    public void disableVR(UUID player) {
        this.vivePlayers.remove(player);
        this.vivePlayersLast.remove(player);
        this.vivePlayersReceived.remove(player);
    }

    public void update(UUID uuid, VrPlayerState vrPlayerState, float worldScale, float heightScale, boolean localPlayer) {
        if (!localPlayer && this.mc.player.getUUID().equals(uuid))
            return; // Don't update local player from server packet

        Vector3 forward = new Vector3(0.0F, 0.0F, -1.0F);
        Vector3 hmdDir = vrPlayerState.hmd().orientation().multiply(forward);
        Vector3 controller0Dir = vrPlayerState.controller0().orientation().multiply(forward);
        Vector3 controller1Dir = vrPlayerState.controller1().orientation().multiply(forward);

        RotInfo rotInfo = new RotInfo();
        rotInfo.reverse = vrPlayerState.reverseHands();
        rotInfo.seated = vrPlayerState.seated();

        rotInfo.hmd = this.donors.getOrDefault(uuid, 0);

        rotInfo.leftArmRot = new Vec3(controller1Dir.getX(), controller1Dir.getY(), controller1Dir.getZ());
        rotInfo.rightArmRot = new Vec3(controller0Dir.getX(), controller0Dir.getY(), controller0Dir.getZ());
        rotInfo.headRot = new Vec3(hmdDir.getX(), hmdDir.getY(), hmdDir.getZ());
        rotInfo.Headpos = vrPlayerState.hmd().position();
        rotInfo.leftArmPos = vrPlayerState.controller1().position();
        rotInfo.rightArmPos = vrPlayerState.controller0().position();
        rotInfo.leftArmQuat = vrPlayerState.controller1().orientation();
        rotInfo.rightArmQuat = vrPlayerState.controller0().orientation();
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

        this.vivePlayersReceived.put(uuid, rotInfo);
    }

    public void update(UUID uuid, VrPlayerState vrPlayerState, float worldScale, float heightScale) {
        this.update(uuid, vrPlayerState, worldScale, heightScale, false);
    }

    public void tick() {
        this.vivePlayersLast.putAll(this.vivePlayers);

        this.vivePlayers.putAll(this.vivePlayersReceived);

        Level level = Minecraft.getInstance().level;

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
                        Vec3 look = player.getLookAngle();

                        if (rotInfo != null) {
                            look = rotInfo.leftArmPos.subtract(rotInfo.rightArmPos).yRot((-(float) Math.PI / 2F));

                            if (rotInfo.reverse) {
                                look = look.scale(-1.0D);
                            } else if (rotInfo.seated) {
                                look = rotInfo.rightArmRot;
                            }

                            // Hands are at origin or something, usually happens if they don't track
                            if (look.length() < 1.0E-4F) {
                                look = rotInfo.headRot;
                            }
                        }
                        look = look.scale(0.1F);

                        // Use hmd pos for self, so we don't have butt sparkles in face
                        Vec3 pos = rotInfo != null && player == this.mc.player ?
                            rotInfo.Headpos.add(player.position()) : player.getEyePosition(1.0F);

                        Particle particle = this.mc.particleEngine.createParticle(
                            ParticleTypes.FIREWORK,
                            pos.x + (player.isShiftKeyDown() ? -look.x * 3.0D : 0.0D) +
                                (this.rand.nextFloat() - 0.5F) * 0.02F,
                            pos.y - (player.isShiftKeyDown() ? 1.0F : 0.8F) +
                                (this.rand.nextFloat() - 0.5F) * 0.02F,
                            pos.z + (player.isShiftKeyDown() ? -look.z * 3.0D : 0.0D) +
                                (this.rand.nextFloat() - 0.5F) * 0.02F,
                            -look.x + (this.rand.nextFloat() - 0.5D) * 0.01F,
                            (this.rand.nextFloat() - 0.05F) * 0.05F,
                            -look.z + (this.rand.nextFloat() - 0.5D) * 0.01F);

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
        if (this.debug) {
            uuid = this.mc.player.getUUID();
        }

        RotInfo rotInfo = this.vivePlayers.get(uuid);

        if (rotInfo != null && this.vivePlayersLast.containsKey(uuid)) {
            RotInfo lastRotInfo = this.vivePlayersLast.get(uuid);
            RotInfo lerpRotInfo = new RotInfo();
            float partialTick = Minecraft.getInstance().getFrameTime();

            lerpRotInfo.reverse = rotInfo.reverse;
            lerpRotInfo.seated = rotInfo.seated;
            lerpRotInfo.hmd = rotInfo.hmd;
            lerpRotInfo.leftArmPos = MathUtils.vecLerp(lastRotInfo.leftArmPos, rotInfo.leftArmPos, partialTick);
            lerpRotInfo.rightArmPos = MathUtils.vecLerp(lastRotInfo.rightArmPos, rotInfo.rightArmPos, partialTick);
            lerpRotInfo.Headpos = MathUtils.vecLerp(lastRotInfo.Headpos, rotInfo.Headpos, partialTick);
            lerpRotInfo.leftArmQuat = rotInfo.leftArmQuat;
            lerpRotInfo.rightArmQuat = rotInfo.rightArmQuat;
            lerpRotInfo.headQuat = rotInfo.headQuat;

            Vector3 forward = new Vector3(0.0F, 0.0F, -1.0F);
            lerpRotInfo.leftArmRot = MathUtils.vecLerp(lastRotInfo.leftArmRot,
                MathUtils.convertToVector3d(lerpRotInfo.leftArmQuat.multiply(forward)), partialTick);
            lerpRotInfo.rightArmRot = MathUtils.vecLerp(lastRotInfo.rightArmRot,
                MathUtils.convertToVector3d(lerpRotInfo.rightArmQuat.multiply(forward)), partialTick);
            lerpRotInfo.headRot = MathUtils.vecLerp(lastRotInfo.headRot,
                MathUtils.convertToVector3d(lerpRotInfo.headQuat.multiply(forward)), partialTick);
            lerpRotInfo.heightScale = rotInfo.heightScale;
            lerpRotInfo.worldScale = rotInfo.worldScale;
            return lerpRotInfo;
        } else {
            return rotInfo;
        }
    }

    public static RotInfo getMainPlayerRotInfo(VRData data) {
        RotInfo rotInfo = new RotInfo();
        Quaternion leftQuat = new Quaternion(data.getController(1).getMatrix());
        Quaternion rightQuat = new Quaternion(data.getController(0).getMatrix());
        Quaternion hmdQuat = new Quaternion(data.hmd.getMatrix());

        rotInfo.headQuat = hmdQuat;
        rotInfo.leftArmQuat = leftQuat;
        rotInfo.rightArmQuat = rightQuat;
        rotInfo.seated = ClientDataHolderVR.getInstance().vrSettings.seated;

        rotInfo.leftArmPos = data.getController(1).getPosition();
        rotInfo.rightArmPos = data.getController(0).getPosition();
        rotInfo.Headpos = data.hmd.getPosition();
        return rotInfo;
    }

    public boolean isTracked(UUID uuid) {
        this.debug = false;
        return this.debug || this.vivePlayers.containsKey(uuid);
    }

    /**
     * @return the yaw of the direction the head is oriented in, no matter their pitch
     * Is not the same as the hmd yaw. Creates better results at extreme pitches
     * Simplified: Takes hmd-forward when looking at horizon, takes hmd-up when looking at ground.
     * */
    public static float getFacingYaw(RotInfo rotInfo) {
        Vec3 facingVec = getOrientVec(rotInfo.headQuat);
        return (float) Math.toDegrees(Math.atan2(facingVec.x, facingVec.z));
    }

    public static Vec3 getOrientVec(Quaternion quat) {
        Vec3 facingPlaneNormal = quat.multiply(new Vec3(0.0D, 0.0D, -1.0D))
            .cross(quat.multiply(new Vec3(0.0D, 1.0D, 0.0D))).normalize();
        return (new Vec3(0.0D, 1.0D, 0.0D)).cross(facingPlaneNormal).normalize();
    }

    public static class RotInfo {
        public boolean seated;
        public boolean reverse;
        public int hmd = 0;
        public Quaternion leftArmQuat;
        public Quaternion rightArmQuat;
        public Quaternion headQuat;
        public Vec3 leftArmRot;
        public Vec3 rightArmRot;
        public Vec3 headRot;
        public Vec3 leftArmPos;
        public Vec3 rightArmPos;
        public Vec3 Headpos;
        public float worldScale;
        public float heightScale;

        public double getBodyYawRadians() {
            Vec3 diff = this.leftArmPos.subtract(this.rightArmPos).yRot((-(float) Math.PI / 2F));

            if (this.reverse) {
                diff = diff.scale(-1.0D);
            }

            if (this.seated) {
                diff = this.rightArmRot;
            }

            Vec3 avg = MathUtils.vecLerp(diff, this.headRot, 0.5D);
            return Math.atan2(-avg.x, avg.z);
        }
    }
}
