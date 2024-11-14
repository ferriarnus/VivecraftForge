package org.vivecraft.client_vr.gameplay;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.client.utils.ScaleHelper;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.ItemTags;
import org.vivecraft.client_vr.MethodHolder;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.extensions.GameRendererExtension;
import org.vivecraft.client_vr.extensions.PlayerExtension;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.gameplay.trackers.Tracker;
import org.vivecraft.client_vr.gameplay.trackers.VehicleTracker;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.VRServerPerms;

import java.util.ArrayList;

public class VRPlayer {
    private final Minecraft mc = Minecraft.getInstance();
    private final ClientDataHolderVR dh = ClientDataHolderVR.getInstance();

    // loop start
    // just latest polling data, origin = 0,0,0, rotation = 0, scaleXZ = walkMultiplier
    public VRData vrdata_room_pre;
    // handle server messages
    // latest polling data but last tick's origin, rotation, scale
    public VRData vrdata_world_pre;
    // tick here
    // recalc here in the odd case the walk multiplier changed
    public VRData vrdata_room_post;
    // this is used for rendering and the server. _render is interpolated between this and _pre.
    public VRData vrdata_world_post;
    // interpolate here between post and pre
    public VRData vrdata_world_render;

    private final ArrayList<Tracker> trackers = new ArrayList<>();
    public float worldScale = this.dh.vrSettings.overrides.getSetting(VRSettings.VrOptions.WORLD_SCALE).getFloat();
    private float rawWorldScale = this.dh.vrSettings.overrides.getSetting(VRSettings.VrOptions.WORLD_SCALE).getFloat();
    private boolean teleportOverride = false;
    public boolean teleportWarning = false;
    public boolean vrSwitchWarning = false;
    public int chatWarningTimer = -1;
    public Vec3 roomOrigin = Vec3.ZERO;

    // based on a heuristic of which locomotion type was last used
    private boolean isFreeMoveCurrent = true;

    // for overriding the world scale settings with wonder foods.
    public double wfMode = 0.0D;
    public int wfCount = 0;

    public int roomScaleMovementDelay = 0;
    private boolean initDone = false;
    public boolean onTick;

    public void registerTracker(Tracker tracker) {
        this.trackers.add(tracker);
    }

    public VRPlayer() {
        this.vrdata_room_pre = new VRData(
            Vec3.ZERO,
            this.dh.vrSettings.walkMultiplier,
            1.0F,
            0.0F);
        this.vrdata_room_post = new VRData(
            Vec3.ZERO,
            this.dh.vrSettings.walkMultiplier,
            1.0F,
            0.0F);
        this.vrdata_world_post = new VRData(
            Vec3.ZERO,
            this.dh.vrSettings.walkMultiplier,
            1.0F,
            0.0F);
        this.vrdata_world_pre = new VRData(
            Vec3.ZERO,
            this.dh.vrSettings.walkMultiplier,
            1.0F,
            0.0F);
    }

    public VRData getVRDataWorld() {
        return this.vrdata_world_render != null ? this.vrdata_world_render : this.vrdata_world_pre;
    }

    public static VRPlayer get() {
        return ClientDataHolderVR.getInstance().vrPlayer;
    }

    public static Vec3 roomToWorldPos(Vector3fc pos, VRData data) {
        Vector3f out = pos.mul(data.worldScale, new Vector3f());
        out = out.rotateY(data.rotation_radians);
        return data.origin.add(out.x, out.y, out.z);
    }

    public static Vector3f worldToRoomPos(Vec3 pos, VRData data) {
        Vector3f out = MathUtils.subtractToVector3f(pos, data.origin);
        out = out.div(data.worldScale);
        return out.rotateY(-data.rotation_radians);
    }

    public void postPoll() {
        this.vrdata_room_pre = new VRData(
            Vec3.ZERO,
            this.dh.vrSettings.walkMultiplier,
            1.0F,
            0.0F);

        GuiHandler.processGui();
        KeyboardHandler.processGui();
        RadialHandler.processGui();
    }

    public void preTick() {
        this.onTick = true;
        this.vrdata_world_pre = new VRData(
            this.roomOrigin,
            this.dh.vrSettings.walkMultiplier,
            this.worldScale,
            Mth.DEG_TO_RAD * this.dh.vrSettings.worldRotation);

        VRSettings.ServerOverrides.Setting worldScaleOverride = this.dh.vrSettings.overrides.getSetting(VRSettings.VrOptions.WORLD_SCALE);

        // adjust world scale
        float scaleSetting = worldScaleOverride.getFloat();

        if (MethodHolder.isInMenuRoom()) {
            this.worldScale = 1.0F;
        } else {
            if (this.wfCount > 0 && !this.mc.isPaused()) {
                if (this.wfCount < 40) {
                    this.rawWorldScale = (float) (this.rawWorldScale - this.wfMode);

                    if (this.wfMode > 0.0D) {
                        // go back.
                        if (this.rawWorldScale < scaleSetting) {
                            this.rawWorldScale = scaleSetting;
                        }
                    } else if (this.wfMode < 0.0D) {
                        if (this.rawWorldScale > scaleSetting) {
                            this.rawWorldScale = scaleSetting;
                        }
                    }
                } else {
                    // shrink or grow
                    this.rawWorldScale = (float) (this.rawWorldScale + this.wfMode);

                    // clamp wonder foods to server set worldscale limit to not cheat
                    if (this.wfMode > 0.0D) {
                        if (this.rawWorldScale > Mth.clamp(20.0F, worldScaleOverride.getValueMin(), worldScaleOverride.getValueMax())) {
                            this.rawWorldScale = Mth.clamp(20.0F, worldScaleOverride.getValueMin(), worldScaleOverride.getValueMax());
                        }
                    } else if (this.wfMode < 0.0D && this.rawWorldScale < Mth.clamp(0.1F, worldScaleOverride.getValueMin(), worldScaleOverride.getValueMax())) {
                        this.rawWorldScale = Mth.clamp(0.1F, worldScaleOverride.getValueMin(), worldScaleOverride.getValueMax());
                    }
                }

                this.wfCount--;
            } else {
                this.rawWorldScale = scaleSetting;
            }

            this.worldScale = this.rawWorldScale;

            // scale world with player size
            this.worldScale *= ScaleHelper.getEntityEyeHeightScale(this.mc.player, this.mc.getFrameTime());
            // limit scale
            // min is minClip + player position indicator offset
            this.worldScale = Mth.clamp(this.worldScale, 0.025F, 100F);

            // check that nobody tries to bypass the server set worldscale limit it with a runtime worldscale
            if (this.mc.level != null && this.mc.isLocalServer() && (worldScaleOverride.isValueMinOverridden() || worldScaleOverride.isValueMaxOverridden())) {
                // a vr runtime worldscale also scales the distance between the eyes, so that can be used to calculate it
                float measuredIPD = ClientDataHolderVR.getInstance().vr.getEyePosition(RenderPass.LEFT)
                    .sub(ClientDataHolderVR.getInstance().vr.getEyePosition(RenderPass.RIGHT)).length();
                float queriedIPD = ClientDataHolderVR.getInstance().vr.getIPD();

                float runtimeWorldScale = queriedIPD / measuredIPD;

                float actualWorldScale = this.rawWorldScale * runtimeWorldScale;

                // check with slight wiggle room in case there is some imprecision
                if (actualWorldScale < worldScaleOverride.getValueMin() * 0.99F || actualWorldScale > worldScaleOverride.getValueMax() * 1.01F) {
                    VRSettings.LOGGER.info("VIVECRAFT: disconnected user from server. runtime IPD: {}, measured IPD: {}, runtime worldscale: {}", queriedIPD, measuredIPD, runtimeWorldScale);
                    this.mc.level.disconnect();
                    this.mc.disconnect(new DisconnectedScreen(new JoinMultiplayerScreen(new TitleScreen()),
                        Component.translatable("vivecraft.message.worldscaleOutOfRange.title"),
                        Component.translatable("vivecraft.message.worldscaleOutOfRange",
                            Component.literal("%.2fx".formatted(worldScaleOverride.getValueMin())).withStyle(style -> style.withColor(ChatFormatting.GREEN)),
                            Component.literal("%.2fx".formatted(worldScaleOverride.getValueMax())).withStyle(style -> style.withColor(ChatFormatting.GREEN)),
                            Component.literal(ClientDataHolderVR.getInstance().vr.getRuntimeName()).withStyle(style -> style.withColor(ChatFormatting.GOLD)))));
                }
            }
        }

        if (this.dh.vrSettings.seated && !MethodHolder.isInMenuRoom()) {
            this.dh.vrSettings.worldRotation = this.dh.vr.seatedRot;
        }
    }

    public void postTick() {
        // translational position change due only to scale. - move room to fix entity in place during scale changes.
        Vector3f scaleOffset = this.vrdata_world_pre.hmd.getScalePositionOffset(this.worldScale);
        this.roomOrigin = this.roomOrigin.subtract(scaleOffset.x, scaleOffset.y, scaleOffset.z);
        //

        // Handle all room translations up to this point and then rotate it around the hmd.
        float end = this.dh.vrSettings.worldRotation;
        float start = Mth.RAD_TO_DEG * this.vrdata_world_pre.rotation_radians;
        this.rotateOriginAround(-end + start, this.vrdata_world_pre.getNewHeadPivot(this.roomOrigin, this.worldScale));
        //

        this.vrdata_room_post = new VRData(
            Vec3.ZERO,
            this.dh.vrSettings.walkMultiplier,
            1.0F,
            0.0F);
        this.vrdata_world_post = new VRData(
            this.roomOrigin,
            this.dh.vrSettings.walkMultiplier,
            this.worldScale,
            Mth.DEG_TO_RAD * this.dh.vrSettings.worldRotation);

        // Vivecraft - setup the player entity with the correct view for the logic tick.
        this.doPermanentLookOverride(this.mc.player, this.vrdata_world_post);
        //

        ClientNetworking.sendVRPlayerPositions(this);
        this.onTick = false;
    }

    public void preRender(float partialTick) {
        // do some interpolatin'

        float interpolatedWorldScale = Mth.lerp(partialTick, this.vrdata_world_pre.worldScale,
            this.vrdata_world_post.worldScale);

        float end = this.vrdata_world_post.rotation_radians;
        float start = this.vrdata_world_pre.rotation_radians;

        float difference = Math.abs(end - start);

        if (difference > Mth.PI) {
            if (end > start) {
                start = start + Mth.TWO_PI;
            } else {
                end = end + Mth.TWO_PI;
            }
        }

        float interpolatedWorldRotation_Radians = Mth.lerp(partialTick, start, end);

        Vec3 interpolatedRoomOrigin = MathUtils.vecDLerp(this.vrdata_world_pre.origin, this.vrdata_world_post.origin, partialTick);

        this.vrdata_world_render = new VRData(
            interpolatedRoomOrigin,
            this.dh.vrSettings.walkMultiplier,
            interpolatedWorldScale,
            interpolatedWorldRotation_Radians);

        // handle special items
        for (Tracker tracker : this.trackers) {
            if (tracker.getEntryPoint() == Tracker.EntryPoint.SPECIAL_ITEMS) {
                tracker.idleTick(this.mc.player);

                if (tracker.isActive(this.mc.player)) {
                    tracker.doProcess(this.mc.player);
                } else {
                    tracker.reset(this.mc.player);
                }
            }
        }

        this.dh.leftMenuHand = MethodHolder.isInMenuRoom() || this.mc.screen != null || KeyboardHandler.SHOWING;
        this.dh.rightMenuHand =
            this.dh.leftMenuHand || (this.dh.interactTracker.hotbar >= 0 && this.dh.vrSettings.vrTouchHotbar);
    }

    public void postRender(float partialTick) {
    }

    public void setRoomOrigin(double x, double y, double z, boolean reset) {
        if (reset && this.vrdata_world_pre != null) {
            this.vrdata_world_pre.origin = new Vec3(x, y, z);
        }

        this.roomOrigin = new Vec3(x, y, z);
    }

    // set room
    public void snapRoomOriginToPlayerEntity(LocalPlayer player, boolean reset, boolean instant) {
        if (Thread.currentThread().getName().equals("Server thread") ||
            player == null || player.position() == Vec3.ZERO ||
            // avoid relocating the view while roomscale dismounting.
            this.dh.sneakTracker.sneakCounter > 0)
        {
            return;
        }

        Vec3 camPos;
        if (instant) {
            camPos = this.vrdata_world_pre.getNewHeadPivot(this.roomOrigin, this.worldScale).subtract(this.roomOrigin);
        } else {
            camPos = this.vrdata_world_pre.getHeadPivot().subtract(this.vrdata_world_pre.origin);
        }

        double x = player.getX() - camPos.x;
        double y = player.getZ() - camPos.z;
        double z = player.getY() + ((PlayerExtension) player).vivecraft$getRoomYOffsetFromPose();
        this.setRoomOrigin(x, z, y, reset);
    }

    // calculate the shortest difference between 2 angles.
    public float rotDiff_Degrees(float start, float end) {
        double x = Math.toRadians(end);
        double y = Math.toRadians(start);
        return (float) Math.toDegrees(Math.atan2(Math.sin(x - y), Math.cos(x - y)));
    }

    public void rotateOriginAround(float degrees, Vec3 origin) {
        Vec3 point = this.roomOrigin;

        // reverse rotate.
        float rads = Mth.DEG_TO_RAD * degrees;

        if (rads != 0.0F) {
            this.setRoomOrigin(
                Mth.cos(rads) * (point.x - origin.x) - Mth.sin(rads) * (point.z - origin.z) + origin.x,
                point.y,
                Mth.sin(rads) * (point.x - origin.x) + Mth.cos(rads) * (point.z - origin.z) + origin.z,
                false);
        }
    }

    public void tick(LocalPlayer player, Minecraft mc) {
        if (!((PlayerExtension) player).vivecraft$getInitFromServer()) return;

        if (!this.initDone) {
            VRSettings.LOGGER.info("""
                Vivecraft: <Debug info start>
                Room object: {}
                Room origin: {}
                Hmd position room: {}
                Hmd position world: {}
                Hmd Projection Left: {}
                Hmd Projection Right: {}
                <Debug info end>
                """, this,
                this.vrdata_world_pre.origin,
                this.vrdata_room_pre.hmd.getPosition(),
                this.vrdata_world_pre.hmd.getPosition(),
                this.dh.vrRenderer.eyeProj[0],
                this.dh.vrRenderer.eyeProj[1]);
            this.initDone = true;
        }

        this.doPlayerMoveInRoom(player);

        for (Tracker tracker : this.trackers) {
            if (tracker.getEntryPoint() == Tracker.EntryPoint.LIVING_UPDATE) {
                tracker.idleTick(mc.player);

                if (tracker.isActive(mc.player)) {
                    tracker.doProcess(mc.player);
                } else {
                    tracker.reset(mc.player);
                }
            }
        }

        if (player.isPassenger()) {
            Entity entity = mc.player.getVehicle();

            if (entity instanceof AbstractHorse abstracthorse) {
                if (abstracthorse.isControlledByLocalInstance() &&
                    abstracthorse.isSaddled() &&
                    !this.dh.horseTracker.isActive(mc.player))
                {
                    abstracthorse.yBodyRot = this.vrdata_world_pre.getBodyYaw();
                    this.dh.vehicleTracker.rotationCooldown = 10;
                }
            } else if (entity instanceof Mob mob) {
                // pigs and striders.
                if (mob.isControlledByLocalInstance()) {
                    mob.yBodyRot = this.vrdata_world_pre.getBodyYaw();
                    this.dh.vehicleTracker.rotationCooldown = 10;
                }
            }
        }
    }

    public void doPlayerMoveInRoom(LocalPlayer player) {

        if (this.roomScaleMovementDelay > 0) {
            this.roomScaleMovementDelay--;
            return;
        } else if (player == null ||
            // jrbudda : prevent falling off things or walking up blocks while moving in room scale.
            player.isShiftKeyDown() ||
            player.isSleeping() ||
            this.dh.jumpTracker.isjumping() ||
            this.dh.climbTracker.isGrabbingLadder() ||
            !player.isAlive())
        {
            return;
        }

        Vec3 newHeadPivot = this.vrdata_world_pre.getNewHeadPivot(this.roomOrigin, this.worldScale);

        if (this.dh.vrSettings.realisticDismountEnabled && this.dh.vehicleTracker.canRoomscaleDismount(player)) {
            Vec3 mountPos = player.getVehicle().position();
            double distance = Math.sqrt((newHeadPivot.x - mountPos.x) * (newHeadPivot.x - mountPos.x) +
                (newHeadPivot.z - mountPos.z) * (newHeadPivot.z - mountPos.z));

            if (distance > 1.0D) {
                this.dh.sneakTracker.sneakCounter = 5;
            }
            return;
        }

        // move player's X/Z coords as the HMD moves around the room
        float playerHalfWidth = player.getBbWidth() / 2.0F;
        float playerHeight = player.getBbHeight();

        // OK this is the first place I've found where we really need to update the VR data before doing this calculation.
        double x = newHeadPivot.x;
        double y = player.getY();
        double z = newHeadPivot.z;

        // create bounding box at dest position
        AABB bb = new AABB(
            x - playerHalfWidth,
            y,
            z - playerHalfWidth,
            x + playerHalfWidth,
            y + playerHeight,
            z + playerHalfWidth);

        Vec3 torso = new Vec3(x, y, z);

        if (this.mc.level.noCollision(player, bb)) {
            // no collision
            // don't call setPosition style functions to avoid shifting room origin
            player.setPosRaw(x, !this.dh.vrSettings.simulateFalling ? y : player.getY(), z);
            player.setBoundingBox(bb);
            player.fallDistance = 0.0F;
        } else if (
            ((this.dh.vrSettings.walkUpBlocks && ((PlayerExtension) player).vivecraft$getMuhJumpFactor() == 1.0F) ||
                (this.dh.climbTracker.isGrabbingLadder() && this.dh.vrSettings.realisticClimbEnabled)
            ) && player.fallDistance == 0.0F)
        {
            // collision, test for climbing up a block
            // is the player significantly inside a block?
            float climbShrink = player.getDimensions(player.getPose()).width * 0.45F;
            double shrunkClimbHalfWidth = playerHalfWidth - climbShrink;

            AABB bbClimb = new AABB(
                torso.x - shrunkClimbHalfWidth,
                bb.minY,
                torso.z - shrunkClimbHalfWidth,
                torso.x + shrunkClimbHalfWidth,
                bb.maxY,
                torso.z + shrunkClimbHalfWidth);

            // colliding with a block
            if (!this.mc.level.noCollision(player, bbClimb)) {
                int extra = 0;

                if (player.onClimbable() && this.dh.vrSettings.realisticClimbEnabled) {
                    extra = 6;
                }

                for (int i = 0; i <= 10 + extra; i++) {
                    bb = bb.move(0.0D, 0.1D, 0.0D);

                    if (this.mc.level.noCollision(player, bb)) {
                        // free spot, move player there
                        player.setPosRaw(x, bb.minY, z);
                        player.setBoundingBox(bb);

                        Vec3 dest = this.roomOrigin.add(0.0, 0.1F * (i + 1), 0.0);
                        this.setRoomOrigin(dest.x, dest.y, dest.z, false);

                        player.fallDistance = 0.0F;
                        ((PlayerExtension) player).vivecraft$stepSound(BlockPos.containing(player.position()),
                            player.position());
                        break;
                    }
                }
            }
        }
    }

    // use simple neck modeling to estimate torso location
    public Vec3 getEstimatedTorsoPosition(double x, double y, double z) {
        Entity player = this.mc.player;
        Vec3 look = player.getLookAngle();
        Vec3 forward = (new Vec3(look.x, 0.0D, look.z)).normalize();
        float factor = (float) look.y * 0.25F;
        return new Vec3(
            x + forward.x * factor,
            y + forward.y * factor,
            z + forward.z * factor);
    }

    public void blockDust(
        double x, double y, double z, int count, BlockPos bp, BlockState bs, float scale, float velScale)
    {
        for (int i = 0; i < count; i++) {
            TerrainParticle terrainparticle = new TerrainParticle(this.mc.level, x, y, z, 0.0D, 0.0D, 0.0D, bs);
            terrainparticle.setPower(velScale);
            // TODO: check
            // minecraft.particleEngine.add(terrainparticle.init(bp).scale(scale));
            this.mc.particleEngine.add(terrainparticle.scale(scale));
        }
    }

    public void updateFreeMove() {
        if (this.dh.teleportTracker.isAiming()) {
            this.isFreeMoveCurrent = false;
        }

        // during login input can be null, and can cause a weird async crash, if not checked
        if (this.mc.player.input != null &&
            (this.mc.player.input.forwardImpulse != 0.0F || this.mc.player.input.leftImpulse != 0.0F))
        {
            this.isFreeMoveCurrent = true;
        }

        this.updateTeleportKeys();
    }

    /**
     * Again with the weird logic, see {@link #isTeleportEnabled()}
     */
    public boolean getFreeMove() {
        if (this.dh.vrSettings.seated) {
            return this.dh.vrSettings.seatedFreeMove || !this.isTeleportEnabled();
        } else {
            return this.isFreeMoveCurrent || this.dh.vrSettings.forceStandingFreeMove;
        }
    }

    public String toString() {
        return ("""
            VRPlayer:
                origin: %s
                rotation: %.3f
                scale: %.3f
                room_pre: %s
                world_pre: %s
                world_post: %s
                world_render: %s"""
        ).formatted(
            this.roomOrigin,
            this.dh.vrSettings.worldRotation,
            this.worldScale,
            this.vrdata_room_pre,
            this.vrdata_world_pre,
            this.vrdata_world_post,
            this.vrdata_world_render);
    }

    public Vec3 getRightClickLookOverride(Player entity, int c) {
        Vec3 out = entity.getLookAngle();

        if (((GameRendererExtension) this.mc.gameRenderer).vivecraft$getCrossVec() != null) {
            out = entity.getEyePosition(1.0F)
                .subtract(((GameRendererExtension) this.mc.gameRenderer).vivecraft$getCrossVec())
                .normalize().reverse(); // backwards
        }

        ItemStack itemStack = c == 0 ? entity.getMainHandItem() : entity.getOffhandItem();

        if (itemStack.getItem() instanceof SnowballItem ||
            itemStack.getItem() instanceof EggItem ||
            itemStack.getItem() instanceof SpawnEggItem ||
            itemStack.getItem() instanceof PotionItem ||
            itemStack.getItem() instanceof BowItem ||
            (itemStack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(itemStack)) ||
            itemStack.is(ItemTags.VIVECRAFT_THROW_ITEMS)
        )
        {
            //use r_hand aim

            VRData data = this.dh.vrPlayer.vrdata_world_pre;
            out = new Vec3(data.getController(c).getDirection());
            Vector3fc aim = this.dh.bowTracker.getAimVector();

            if (this.dh.bowTracker.isNotched() && aim != null && aim.lengthSquared() > 0.0F) {
                out = new Vec3(-aim.x(), -aim.y(), -aim.z());
            }
        } else if (itemStack.getItem() == Items.BUCKET && this.dh.interactTracker.bukkit[c]) {
            out = entity.getEyePosition(1.0F)
                .subtract(this.dh.vrPlayer.vrdata_world_pre.getController(c).getPosition())
                .normalize().reverse(); // backwards
        }

        return out;
    }

    public void doPermanentLookOverride(LocalPlayer player, VRData data) {
        if (player == null) {
            return;
        }

        // This is used for all sorts of things both client and server side.

        if (player.isPassenger()) {
            // Server-side movement
            // when a passanger make the player look in the vehicle forward direction
            Vector3f dir = VehicleTracker.getSteeringDirection(player);

            if (dir != null) {
                player.setXRot((float) Math.toDegrees(Math.asin(-dir.y / dir.length())));
                player.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
                player.setYHeadRot(player.getYRot());
            }
        } else if (player.isBlocking()) {
            // block direction
            // when blocking, make the player look in the shield hand direction
            if (player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                player.setYRot(data.getController(0).getYaw());
                player.setYHeadRot(player.getYRot());
                player.setXRot(-data.getController(0).getPitch());
            } else {
                player.setYRot(data.getController(1).getYaw());
                player.setYHeadRot(player.getYRot());
                player.setXRot(-data.getController(1).getPitch());
            }
        } else if ((player.isSprinting() && (player.input.jumping || this.mc.options.keyJump.isDown())) ||
            player.isFallFlying() || (player.isSwimming() && player.zza > 0.0F))
        {
            // Server-side movement
            // when swimming/flying adjust player look according to the user setting
            VRSettings.FreeMove freeMoveType =
                player.isFallFlying() && this.dh.vrSettings.vrFreeMoveFlyMode != VRSettings.FreeMove.AUTO ?
                    this.dh.vrSettings.vrFreeMoveFlyMode : this.dh.vrSettings.vrFreeMoveMode;

            if (freeMoveType == VRSettings.FreeMove.CONTROLLER) {
                player.setYRot(data.getController(1).getYaw());
                player.setYHeadRot(player.getYRot());
                player.setXRot(-data.getController(1).getPitch());
            } else {
                player.setYRot(data.hmd.getYaw());
                player.setYHeadRot(player.getYRot());
                player.setXRot(-data.hmd.getPitch());
            }
        } else if (((GameRendererExtension) this.mc.gameRenderer).vivecraft$getCrossVec() != null) {
            //Look AT the crosshair by default, most compatible with mods.
            Vec3 playerToCrosshair = player.getEyePosition(1)
                .subtract(((GameRendererExtension) this.mc.gameRenderer).vivecraft$getCrossVec()); //backwards
            double what = playerToCrosshair.y / playerToCrosshair.length();
            if (what > 1) {
                what = 1;
            }
            if (what < -1) {
                what = -1;
            }
            float pitch = (float) Math.toDegrees(Math.asin(what));
            float yaw = (float) Math.toDegrees(Math.atan2(playerToCrosshair.x, -playerToCrosshair.z));
            player.setXRot(pitch);
            player.setYRot(yaw);
            player.setYHeadRot(yaw);
        } else {
            //use HMD only if no crosshair hit.
            player.setYRot(data.hmd.getYaw());
            player.setYHeadRot(player.getYRot());
            player.setXRot(-data.hmd.getPitch());
        }
    }

    public Vec3 AimedPointAtDistance(VRData source, int controller, double distance) {
        Vec3 controllerPos = source.getController(controller).getPosition();
        Vector3f controllerDir = source.getController(controller).getDirection();
        return controllerPos.add(controllerDir.x * distance, controllerDir.y * distance, controllerDir.z * distance);
    }

    /**
     * copy of {@link Entity#pick(double, float, boolean)}, modified to use the given VRData
     * @param source VRData to base the raytrace off
     * @param controller controller index to trace from
     * @param hitDistance distance to trace
     * @param hitFluids if fluids should be hit
     * @return hit block or miss
     */
    public HitResult rayTraceBlocksVR(VRData source, int controller, double hitDistance, boolean hitFluids) {
        Vec3 start = source.getController(controller).getPosition();
        Vec3 end = this.AimedPointAtDistance(source, controller, hitDistance);
        return this.mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
            hitFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, this.mc.player));
    }

    public boolean isTeleportSupported() {
        return !VRServerPerms.INSTANCE.noTeleportClient;
    }

    public boolean isTeleportOverridden() {
        return this.teleportOverride;
    }

    /**
     * The logic here is a bit weird, because teleport is actually still enabled even in
     * seated free move mode. You could use it by simply binding it in the vanilla controls.
     * However, when free move is forced in standing mode, teleport is outright disabled.
     */
    public boolean isTeleportEnabled() {
        boolean enabled = !VRServerPerms.INSTANCE.noTeleportClient || this.teleportOverride;

        if (this.dh.vrSettings.seated) {
            return enabled;
        } else {
            return enabled && !this.dh.vrSettings.forceStandingFreeMove;
        }
    }

    public void setTeleportOverride(boolean override) {
        this.teleportOverride = override;
        this.updateTeleportKeys();
    }

    public void updateTeleportKeys() {
        this.dh.vr.getInputAction(VivecraftVRMod.INSTANCE.keyTeleport).setEnabled(this.isTeleportEnabled());
        this.dh.vr.getInputAction(VivecraftVRMod.INSTANCE.keyTeleportFallback).setEnabled(!this.isTeleportEnabled());
    }
}
