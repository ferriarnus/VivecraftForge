package org.vivecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vivecraft.client.extensions.SparkParticleExtension;
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

        Vector3fc hmdDir = vrPlayerState.hmd().orientation().transform(MathUtils.BACK, new Vector3f());
        Vector3fc controller0Dir = vrPlayerState.controller0().orientation().transform(MathUtils.BACK, new Vector3f());
        Vector3fc controller1Dir = vrPlayerState.controller1().orientation().transform(MathUtils.BACK, new Vector3f());

        RotInfo rotInfo = new RotInfo();
        rotInfo.reverse = vrPlayerState.reverseHands();
        rotInfo.seated = vrPlayerState.seated();

        rotInfo.hmd = this.donors.getOrDefault(uuid, 0);

        rotInfo.leftArmRot = controller1Dir;
        rotInfo.rightArmRot = controller0Dir;
        rotInfo.headRot = hmdDir;

        rotInfo.leftArmPos = vrPlayerState.controller1().position();
        rotInfo.rightArmPos = vrPlayerState.controller0().position();
        rotInfo.headPos = vrPlayerState.hmd().position();

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
                        Vector3f look = player.getLookAngle().toVector3f();
                        if (rotInfo != null) {
                            look = rotInfo.leftArmPos.sub(rotInfo.rightArmPos, look).rotateY(-Mth.HALF_PI);

                            if (rotInfo.reverse) {
                                look = look.mul(-1.0F);
                            } else if (rotInfo.seated) {
                                look.set(rotInfo.rightArmRot);
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
        if (this.debug) {
            uuid = this.mc.player.getUUID();
        }

        RotInfo newRotInfo = this.vivePlayers.get(uuid);

        if (newRotInfo != null && this.vivePlayersLast.containsKey(uuid)) {
            RotInfo lastRotInfo = this.vivePlayersLast.get(uuid);
            RotInfo lerpRotInfo = new RotInfo();
            float partialTick = Minecraft.getInstance().getFrameTime();

            lerpRotInfo.reverse = newRotInfo.reverse;
            lerpRotInfo.seated = newRotInfo.seated;
            lerpRotInfo.hmd = newRotInfo.hmd;

            lerpRotInfo.leftArmPos = lastRotInfo.leftArmPos.lerp(newRotInfo.leftArmPos, partialTick, new Vector3f());
            lerpRotInfo.rightArmPos = lastRotInfo.rightArmPos.lerp(newRotInfo.rightArmPos, partialTick, new Vector3f());
            lerpRotInfo.headPos = lastRotInfo.headPos.lerp(newRotInfo.headPos, partialTick, new Vector3f());

            lerpRotInfo.leftArmQuat = newRotInfo.leftArmQuat;
            lerpRotInfo.rightArmQuat = newRotInfo.rightArmQuat;
            lerpRotInfo.headQuat = newRotInfo.headQuat;

            lerpRotInfo.leftArmRot = lastRotInfo.leftArmRot.lerp(newRotInfo.leftArmRot, partialTick, new Vector3f());
            lerpRotInfo.rightArmRot = lastRotInfo.rightArmRot.lerp(newRotInfo.rightArmRot, partialTick, new Vector3f());
            lerpRotInfo.headRot = lastRotInfo.headRot.lerp(newRotInfo.headRot, partialTick, new Vector3f());

            lerpRotInfo.heightScale = newRotInfo.heightScale;
            lerpRotInfo.worldScale = newRotInfo.worldScale;
            return lerpRotInfo;
        } else {
            return newRotInfo;
        }
    }

    public static RotInfo getMainPlayerRotInfo(VRData data) {
        RotInfo rotInfo = new RotInfo();

        rotInfo.headQuat = data.hmd.getMatrix().getNormalizedRotation(new Quaternionf());
        rotInfo.leftArmQuat = data.getController(1).getMatrix().getNormalizedRotation(new Quaternionf());
        rotInfo.rightArmQuat = data.getController(0).getMatrix().getNormalizedRotation(new Quaternionf());
        rotInfo.seated = ClientDataHolderVR.getInstance().vrSettings.seated;

        rotInfo.leftArmPos = data.getController(1).getPositionF();
        rotInfo.rightArmPos = data.getController(0).getPositionF();
        rotInfo.headPos = data.hmd.getPositionF();
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
        public boolean reverse;
        public int hmd = 0;
        public Quaternionfc leftArmQuat;
        public Quaternionfc rightArmQuat;
        public Quaternionfc headQuat;
        // body rotations in world space
        public Vector3fc leftArmRot;
        public Vector3fc rightArmRot;
        public Vector3fc headRot;
        // body positions in player local world space
        public Vector3fc leftArmPos;
        public Vector3fc rightArmPos;
        public Vector3fc headPos;
        public float worldScale;
        public float heightScale;

        // TODO MATH make the same for VRDATA and this
        public float getBodyYawRad() {
            Vector3f diff = this.leftArmPos.sub(this.rightArmPos, new Vector3f()).rotateY(-Mth.HALF_PI);

            if (this.reverse) {
                diff = diff.mul(-1.0F);
            }

            if (this.seated) {
                diff.set(this.rightArmRot);
            }

            diff.lerp(this.headRot, 0.5F, diff);
            return (float) Math.atan2(-diff.x, diff.z);
        }
    }
}
