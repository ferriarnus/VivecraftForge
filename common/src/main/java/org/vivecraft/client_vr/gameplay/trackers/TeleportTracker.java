package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.vivecraft.api.client.Tracker;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.extensions.PlayerExtension;
import org.vivecraft.client_vr.gameplay.VRMovementStyle;
import org.vivecraft.client_vr.render.helpers.RenderHelper;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.data.BlockTags;

import java.util.Random;

public class TeleportTracker implements Tracker {
    private float teleportEnergy;
    private Vec3 movementTeleportDestination = Vec3.ZERO;
    private Direction movementTeleportDestinationSideHit;
    public double movementTeleportProgress;
    public double movementTeleportDistance;
    private int movementTeleportTimer = 0;
    private final Vec3[] movementTeleportArc = new Vec3[50];
    public int movementTeleportArcSteps = 0;
    public double lastTeleportArcDisplayOffset = 0.0D;
    public VRMovementStyle vrMovementStyle;
    protected Minecraft mc;
    protected ClientDataHolderVR dh;

    public TeleportTracker(Minecraft mc, ClientDataHolderVR dh) {
        this.mc = mc;
        this.dh = dh;
        this.vrMovementStyle = new VRMovementStyle();
    }

    public float getTeleportEnergy() {
        return this.teleportEnergy;
    }

    public boolean isAiming() {
        return this.movementTeleportProgress > 0.0D;
    }

    public Vec3 getDestination() {
        return this.movementTeleportDestination;
    }

    @Override
    public boolean isActive(LocalPlayer p) {
        if (p == null) {
            return false;
        } else if (this.mc.gameMode == null) {
            return false;
        } else if (!p.isAlive()) {
            return false;
        } else {
            return !p.isSleeping();
        }
    }

    @Override
    public void reset(LocalPlayer player) {
        this.movementTeleportDestination = Vec3.ZERO;
        this.movementTeleportArcSteps = 0;
        this.movementTeleportProgress = 0.0D;
    }

    @Override
    public void doProcess(LocalPlayer player) {
        Random random = new Random();

        if (this.teleportEnergy < 100.0F) {
            this.teleportEnergy++;
        }

        boolean doTeleport = false;
        Vec3 destination = null;
        boolean bindingTeleport = VivecraftVRMod.INSTANCE.keyTeleport.isDown() && this.dh.vrPlayer.isTeleportEnabled();
        boolean seatedTeleport = this.dh.vrSettings.seated && !this.dh.vrPlayer.getFreeMove() &&
            (player.input.forwardImpulse != 0.0F || player.input.leftImpulse != 0.0F);

        if ((bindingTeleport || seatedTeleport) && !player.isPassenger()) {
            destination = this.movementTeleportDestination;

            if (this.vrMovementStyle.teleportOnRelease || (this.movementTeleportTimer >= 0 &&
                (destination.x != 0.0D || destination.y != 0.0D || destination.z != 0.0D)
            ))
            {
                // start tp sound
                if (this.movementTeleportTimer == 0 && this.vrMovementStyle.startTeleportingSound != null) {
                    player.playSound(this.vrMovementStyle.startTeleportingSound,
                        this.vrMovementStyle.startTeleportingSoundVolume,
                        1.0F / (random.nextFloat() * 0.4F + 1.2F) + 0.5F);
                }

                this.movementTeleportTimer++;

                // tp particles
                if (this.movementTeleportTimer > 0) {
                    if (this.vrMovementStyle.teleportOnRelease) {
                        this.movementTeleportProgress = 1.0D;
                    } else {
                        Vec3 playerPos = player.position();
                        double dist = destination.distanceTo(playerPos);
                        this.movementTeleportProgress = (double) this.movementTeleportTimer / (dist + 3.0D);
                    }

                    if (destination.x != 0.0D || destination.y != 0.0D || destination.z != 0.0D) {

                        // spark at dest point
                        if (this.vrMovementStyle.destinationSparkles) {
                            // TODO
                            // player.world.spawnParticle("instantSpell", dest.x, dest.y, dest.z, 0, 1.0, 0);
                        }

                        // cloud of sparks moving past you
                        if (this.vrMovementStyle.airSparkles) {

                            Vec3 eyeCenterPos = this.dh.vrPlayer.vrdata_world_pre.hmd.getPosition();
                            if (!this.vrMovementStyle.teleportOnRelease) {
                                eyeCenterPos = player.position();
                            }

                            Vec3 motionDir = destination.subtract(eyeCenterPos).normalize();
                            Vec3 forward = player.getLookAngle();

                            Vec3 right = forward.cross(MathUtils.UP_D);
                            Vec3 up = right.cross(forward);

                            for (int iParticle = 0; iParticle < 3; iParticle++) {
                                double forwardDist = random.nextDouble() + 3.5D;
                                double upDist = random.nextDouble() * 2.5D;
                                double rightDist = random.nextDouble() * 4.0D - 2.0D;

                                Vec3 sparkPos = new Vec3(
                                    eyeCenterPos.x + forward.x * forwardDist,
                                    eyeCenterPos.y + forward.y * forwardDist,
                                    eyeCenterPos.z + forward.z * forwardDist);
                                sparkPos = sparkPos.add(right.x * rightDist, right.y * rightDist, right.z * rightDist);
                                sparkPos.add(up.x * upDist, up.y * upDist, up.z * upDist);
                                double speed = -0.6D;
                                /*
                                Particle particle = new ParticleVRTeleportFX(
                                        player.level(),
                                        sparkPos.x, sparkPos.y, sparkPos.z,
                                        motionDir.x * speed, motionDir.y * speed, motionDir.z * speed,
                                        1.0f);
                                this.mc.particleEngine.createParticle(particle);
                                */
                            }
                        }
                    }
                } else if (!this.vrMovementStyle.teleportOnRelease) {
                    this.movementTeleportProgress = 0.0D;
                }

                if (!this.vrMovementStyle.teleportOnRelease && this.movementTeleportProgress >= 1.0D) {
                    // for not on release teleports, we tp when the progress is full
                    doTeleport = true;
                }
            }
        } else { // not holding down teleport key
            if (this.vrMovementStyle.teleportOnRelease && this.movementTeleportProgress >= 1.0D) {
                destination = this.movementTeleportDestination;
                doTeleport = true;
            }

            this.movementTeleportTimer = 0;
            this.movementTeleportProgress = 0.0D;
        }

        // execute teleport
        if (doTeleport && destination != null &&
            (destination.x != 0.0D || destination.y != 0.0D || destination.z != 0.0D))
        {
            this.movementTeleportDistance = destination.distanceTo(player.position());

            // execute teleport
            if (!this.dh.vrPlayer.isTeleportSupported()) {
                String command = "tp " + destination.x + " " + destination.y + " " + destination.z;
                player.connection.sendCommand(command);
            } else {
                if (ClientNetworking.SERVER_SUPPORTS_DIRECT_TELEPORT) {
                    ((PlayerExtension) player).vivecraft$setTeleported(true);
                }

                player.moveTo(destination.x, destination.y, destination.z);
            }

            this.doTeleportCallback();

            if (this.movementTeleportDistance > 0.0D && this.vrMovementStyle.endTeleportingSound != null) {
                player.playSound(this.vrMovementStyle.endTeleportingSound,
                    this.vrMovementStyle.endTeleportingSoundVolume, 1.0F);
            } else {
                ((PlayerExtension) player).vivecraft$stepSound(BlockPos.containing(destination), destination);
            }
        }
    }

    public void updateTeleportDestinations(LocalPlayer player) {
        // called every frame
        this.mc.getProfiler().push("updateTeleportDestinations");

        // TODO: why is that comment here
        // no teleporting if on a server that disallows teleporting

        if (this.vrMovementStyle.arcAiming) {
            this.movementTeleportDestination = Vec3.ZERO;

            if (this.movementTeleportProgress > 0.0D) {
                this.updateTeleportArc(player);
            }
        } else {
            /*
            TODO: check to readd?
            // non-arc modes.
            Vec3 start = RenderHelper.getControllerRenderPos(1);
            Vec3 aimDir = this.dh.vrPlayer.vrdata_world_render.getController(1).getDirection();

            // setup teleport forwards to the mouse cursor
            double movementTeleportDistance = 250.0;
            Vec3 movementTeleportPos = start.add(
                aimDir.x * movementTeleportDistance,
                aimDir.y * movementTeleportDistance,
                aimDir.z * movementTeleportDistance);

            BlockHitResult collision = this.mc.level.clip(new ClipContext(start, movementTeleportPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));

            Vec3 traceDir = start.subtract(movementTeleportPos).normalize();
            Vec3 reverseEpsilon = new Vec3(-traceDir.x * 0.02, -traceDir.y * 0.02, -traceDir.z * 0.02);

            // don't update while charging up a teleport
            if (movementTeleportProgress > 0.0D) {
                if (collision.getType() != HitResult.Type.MISS) {
                    checkAndSetTeleportDestination(player, start, collision, reverseEpsilon);
                }
            }
            */
        }

        this.mc.getProfiler().pop();
    }

    private void updateTeleportArc(LocalPlayer player) {

        int controller = this.dh.vrSettings.seated ? 0 : 1;

        Vec3 start = RenderHelper.getControllerRenderPos(controller);

        VRData.VRDevicePose controllerWorld = this.dh.vrPlayer.vrdata_world_render.getController(controller);

        int maxSteps = 50;
        this.movementTeleportArc[0] = new Vec3(
            start.x,
            start.y,
            start.z);

        this.movementTeleportArcSteps = 1;

        // calculate gravity vector for arc
        final float gravityAcceleration = 0.098F;

        Vector3f gravity = controllerWorld.getMatrix().rotateZ(controllerWorld.getRollRad())
            .transformDirection(MathUtils.DOWN, new Vector3f()).mul(gravityAcceleration);

        // calculate initial move step
        final float speed = 0.5F;
        Vector3f velocity = controllerWorld.getDirection().mul(speed);

        Vec3 pos = new Vec3(start.x, start.y, start.z);

        for (int i = this.movementTeleportArcSteps; i < maxSteps && i * 4 <= this.teleportEnergy; i++) {
            Vec3 newPos = new Vec3(
                pos.x + velocity.x,
                pos.y + velocity.y,
                pos.z + velocity.z);

            BlockHitResult blockhitresult = this.mc.level.clip(
                new ClipContext(pos,
                    newPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.ANY,
                    player));

            if (blockhitresult.getType() != HitResult.Type.MISS) {
                this.movementTeleportArc[i] = blockhitresult.getLocation();

                this.movementTeleportArcSteps = i + 1;

                this.checkAndSetTeleportDestination(player, start, blockhitresult);

                Vec3 diff = this.mc.player.position().subtract(this.movementTeleportDestination);

                double yDiff = diff.y;
                this.movementTeleportDistance = diff.length();
                double xzDiff = Math.sqrt(diff.x * diff.x + diff.z * diff.z);

                boolean ok = !player.isShiftKeyDown() || !(yDiff > 0.2D);

                if (!player.getAbilities().mayfly && ClientNetworking.isLimitedSurvivalTeleport()) {
                    if (ClientNetworking.getTeleportDownLimit() > 0 &&
                        yDiff > ClientNetworking.getTeleportDownLimit() + 0.2D)
                    {
                        ok = false;
                    } else if (ClientNetworking.getTeleportUpLimit() > 0 &&
                        -yDiff > ClientNetworking.getTeleportUpLimit() *
                            ((PlayerExtension) player).vivecraft$getMuhJumpFactor() + 0.2D)
                    {
                        ok = false;
                    } else if (ClientNetworking.getTeleportHorizLimit() > 0 &&
                        xzDiff > ClientNetworking.getTeleportHorizLimit() *
                            ((PlayerExtension) player).vivecraft$getMuhSpeedFactor() + 0.2D)
                    {
                        ok = false;
                    }
                }

                if (!ok) {
                    // u fail
                    this.movementTeleportDestination = Vec3.ZERO;
                    this.movementTeleportDistance = 0.0D;
                }

                break;
            }

            pos = new Vec3(newPos.x, newPos.y, newPos.z);
            this.movementTeleportArc[i] = new Vec3(newPos.x, newPos.y, newPos.z);
            this.movementTeleportArcSteps = i + 1;
            velocity = velocity.add(gravity);
        }
    }

    // not really a callback anymore, is it?
    private void doTeleportCallback() {
        this.dh.swingTracker.disableSwing = 3;

        if (ClientNetworking.isLimitedSurvivalTeleport()) {
            this.mc.player.causeFoodExhaustion((float) (this.movementTeleportDistance / 16.0D * 1.2D));

            if (this.mc.gameMode.hasMissTime() && this.vrMovementStyle.arcAiming) {
                this.teleportEnergy -= (float) this.movementTeleportDistance * 4.0F;
            }
        }

        this.mc.player.fallDistance = 0.0F;
        this.movementTeleportTimer = -1;
    }

    /**
     * looks for a valid place to stand on the block that the trace collided with
     *
     * @param player    Player to check the position for
     * @param start     aim start position, in world space
     * @param collision block hit position to check
     * @return if the BlockHitResult is valid to stand in
     */
    private boolean checkAndSetTeleportDestination(LocalPlayer player, Vec3 start, BlockHitResult collision) {
        BlockPos blockpos = collision.getBlockPos();
        BlockState blockState = player.level().getBlockState(blockpos);

        if (!this.mc.level.getFluidState(blockpos).isEmpty()) {
            Vec3 hitVec = new Vec3(collision.getLocation().x, blockpos.getY(), collision.getLocation().z);
            Vec3 offset = hitVec.subtract(player.getX(), player.getBoundingBox().minY, player.getZ());

            AABB aabb = player.getBoundingBox().move(offset.x, offset.y, offset.z);

            boolean emptySpotReq = this.mc.level.noCollision(player, aabb);

            if (!emptySpotReq) {
                Vec3 center = Vec3.atBottomCenterOf(blockpos);
                offset = center.subtract(player.getX(), player.getBoundingBox().minY, player.getZ());
                aabb = player.getBoundingBox().move(offset.x, offset.y, offset.z);
                emptySpotReq = this.mc.level.noCollision(player, aabb);
            }

            float ex = 0.0F;

            if (this.dh.vrSettings.seated) {
                ex = 0.5F;
            }

            if (emptySpotReq) {
                this.movementTeleportDestination = new Vec3(aabb.getCenter().x, aabb.minY + ex, aabb.getCenter().z);
                this.movementTeleportDestinationSideHit = collision.getDirection();
                return true;
            }
        } else if (collision.getDirection() != Direction.UP) {
            // sides
            // require arc hitting top of block.
            // unless ladder or vine or creative or limits off.
            if (blockState.getBlock() instanceof LadderBlock ||
                blockState.getBlock() instanceof VineBlock ||
                blockState.is(BlockTags.VIVECRAFT_CLIMBABLE))
            {
                Vec3 dest = new Vec3(blockpos.getX() + 0.5D, blockpos.getY() + 0.5D, blockpos.getZ() + 0.5D);
                Block block = this.mc.level.getBlockState(blockpos.below()).getBlock();

                if (block == blockState.getBlock()) {
                    dest = dest.add(0.0D, -1.0D, 0.0D);
                }

                this.movementTeleportDestination = dest.scale(1.0D);
                this.movementTeleportDestinationSideHit = collision.getDirection();
                return true; // really should check if the block above is passable. Maybe later.
            }

            if (!player.getAbilities().mayfly && ClientNetworking.isLimitedSurvivalTeleport()) {
                return false; // if creative, check if can hop on top.
            }
        }

        BlockPos hitBlock = collision.getBlockPos().below();

        for (int i = 0; i < 2; i++) {
            blockState = player.level().getBlockState(hitBlock);

            if (!blockState.getCollisionShape(this.mc.level, hitBlock).isEmpty()) {
                double height = blockState.getCollisionShape(this.mc.level, hitBlock).max(Direction.Axis.Y);

                Vec3 hitVec = new Vec3(collision.getLocation().x, (double) hitBlock.getY() + height,
                    collision.getLocation().z);
                Vec3 offset = hitVec.subtract(player.getX(), player.getBoundingBox().minY, player.getZ());
                AABB aabb = player.getBoundingBox().move(offset.x, offset.y, offset.z);

                double ex = 0.0D;

                if (blockState.getBlock() == Blocks.SOUL_SAND || blockState.getBlock() == Blocks.HONEY_BLOCK) {
                    ex = 0.05D;
                }

                boolean emptySpotReq = this.mc.level.noCollision(player, aabb) &&
                    !this.mc.level.noCollision(player, aabb.inflate(0.0D, 0.125D + ex, 0.0D));

                if (!emptySpotReq) {
                    Vec3 center = Vec3.upFromBottomCenterOf(hitBlock, height);
                    offset = center.subtract(player.getX(), player.getBoundingBox().minY, player.getZ());
                    aabb = player.getBoundingBox().move(offset.x, offset.y, offset.z);
                    emptySpotReq = this.mc.level.noCollision(player, aabb) &&
                        !this.mc.level.noCollision(player, aabb.inflate(0.0D, 0.125D + ex, 0.0D));
                }

                if (emptySpotReq) {
                    Vec3 dest = new Vec3(aabb.getCenter().x, hitBlock.getY() + height, aabb.getCenter().z);
                    this.movementTeleportDestination = dest.scale(1.0D);
                    return true;
                }
            }
            hitBlock = hitBlock.above();
        }
        return false;
    }

    /**
     * does a rough interpolation between arc locations
     *
     * @param progress location of the point on the arc, 0-1
     * @return interpolated point
     */
    public Vec3 getInterpolatedArcPosition(float progress) {
        if (this.movementTeleportArcSteps == 1 || progress <= 0.0f) {
            // not enough points to interpolate or before start
            return new Vec3(
                this.movementTeleportArc[0].x,
                this.movementTeleportArc[0].y,
                this.movementTeleportArc[0].z);
        } else if (progress >= 1.0f) {
            // past end of arc
            return new Vec3(
                this.movementTeleportArc[this.movementTeleportArcSteps - 1].x,
                this.movementTeleportArc[this.movementTeleportArcSteps - 1].y,
                this.movementTeleportArc[this.movementTeleportArcSteps - 1].z);
        } else {
            // which two points are we between?
            float stepFloat = progress * (float) (this.movementTeleportArcSteps - 1);
            int step = (int) Math.floor(stepFloat);

            double deltaX = this.movementTeleportArc[step + 1].x - this.movementTeleportArc[step].x;
            double deltaY = this.movementTeleportArc[step + 1].y - this.movementTeleportArc[step].y;
            double deltaZ = this.movementTeleportArc[step + 1].z - this.movementTeleportArc[step].z;

            float stepProgress = stepFloat - step;

            return new Vec3(
                this.movementTeleportArc[step].x + deltaX * stepProgress,
                this.movementTeleportArc[step].y + deltaY * stepProgress,
                this.movementTeleportArc[step].z + deltaZ * stepProgress);
        }
    }

    @Override
    public TrackerTickType tickType() {
        return TrackerTickType.PER_TICK;
    }
}
