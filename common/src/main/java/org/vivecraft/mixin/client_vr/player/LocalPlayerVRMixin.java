package org.vivecraft.mixin.client_vr.player;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.PlayerExtension;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_vr.utils.external.jinfinadeck;
import org.vivecraft.client_vr.utils.external.jkatvr;
import org.vivecraft.common.network.packet.c2s.TeleportPayloadC2S;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerVRMixin extends LocalPlayer_PlayerVRMixin implements PlayerExtension {

    @Unique
    private Vec3 vivecraft$moveMulIn = Vec3.ZERO;

    @Unique
    private boolean vivecraft$initFromServer;

    @Unique
    private boolean vivecraft$teleported;

    @Unique
    private double vivecraft$additionX;

    @Unique
    private double vivecraft$additionZ;

    @Unique
    private final ClientDataHolderVR vivecraft$dataholder = ClientDataHolderVR.getInstance();

    @Final
    @Shadow
    protected Minecraft minecraft;

    @Shadow
    private boolean startedUsingItem;

    @Shadow
    @Final
    public ClientPacketListener connection;

    @Shadow
    private InteractionHand usingItemHand;

    @Shadow
    protected abstract void updateAutoJump(float movementX, float movementZ);

    @Shadow
    public abstract boolean isShiftKeyDown();

    @Shadow
    public abstract InteractionHand getUsedItemHand();

    @Inject(method = "startRiding", at = @At("TAIL"))
    private void vivecraft$startRidingTracker(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (VRState.VR_INITIALIZED && vivecraft$isLocalPlayer(this)) {
            ClientDataHolderVR.getInstance().vehicleTracker.onStartRiding(vehicle);
        }
    }

    @Inject(method = "removeVehicle", at = @At("TAIL"))
    private void vivecraft$stopRidingTracker(CallbackInfo ci) {
        if (VRState.VR_INITIALIZED && vivecraft$isLocalPlayer(this)) {
            ClientDataHolderVR.getInstance().vehicleTracker.onStopRiding();
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V"))
    private void vivecraft$preTick(CallbackInfo ci) {
        if (VRState.VR_RUNNING && vivecraft$isLocalPlayer(this)) {
            ClientDataHolderVR.getInstance().vrPlayer.doPermanentLookOverride((LocalPlayer) (Object) this, ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_pre);
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = At.Shift.AFTER))
    private void vivecraft$postTick(CallbackInfo ci) {
        if (VRState.VR_RUNNING && vivecraft$isLocalPlayer(this)) {
            ClientNetworking.overridePose((LocalPlayer) (Object) this);
            ClientDataHolderVR.getInstance().vrPlayer.doPermanentLookOverride((LocalPlayer) (Object) this, ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_pre);
        }
    }

    @ModifyVariable(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isPassenger()Z"), ordinal = 2)
    private boolean vivecraft$directTeleport(boolean updateRotation) {
        if (this.vivecraft$teleported) {
            updateRotation = true;
            ClientNetworking.sendServerPacket(
                new TeleportPayloadC2S((float) this.getX(), (float) this.getY(), (float) this.getZ()));
        }
        return updateRotation;
    }

    // needed, or the server will spam 'moved too quickly'/'moved wrongly'
    @WrapWithCondition(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"), slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isPassenger()Z")))
    private boolean vivecraft$noMovePacketsOnTeleport(ClientPacketListener instance, Packet packet) {
        return !this.vivecraft$teleported;
    }

    @Inject(method = "sendPosition", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;lastOnGround:Z", shift = At.Shift.AFTER, ordinal = 1))
    private void vivecraft$noAutoJump(CallbackInfo ci) {
        // clear teleport here, after all the packets would be sent
        this.vivecraft$teleported = false;
        if (VRState.VR_RUNNING && ClientDataHolderVR.getInstance().vrSettings.walkUpBlocks) {
            this.minecraft.options.autoJump().set(false);
        }
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;aiStep()V"))
    private void vivecraft$VRPlayerTick(CallbackInfo ci) {
        if (VRState.VR_RUNNING && vivecraft$isLocalPlayer(this)) {
            ClientDataHolderVR.getInstance().vrPlayer.tick((LocalPlayer) (Object) this);
        }
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void vivecraft$overwriteMove(MoverType type, Vec3 pos, CallbackInfo ci) {
        if (!VRState.VR_RUNNING || !vivecraft$isLocalPlayer(this) ||
            Minecraft.getInstance().getCameraEntity() != (Object) this)
        {
            return;
        }
        // stuckSpeedMultiplier gets zeroed in the super call.
        this.vivecraft$moveMulIn = this.stuckSpeedMultiplier;

        if (pos.length() == 0 || this.isPassenger()) {
            super.move(type, pos);
        } else {
            boolean freeMove = VRPlayer.get().getFreeMove();
            boolean doY = freeMove || (ClientDataHolderVR.getInstance().vrSettings.simulateFalling &&
                !this.onClimbable() && !this.isShiftKeyDown());

            if (ClientDataHolderVR.getInstance().climbTracker.isActive((LocalPlayer) (Object) this) &&
                (freeMove || ClientDataHolderVR.getInstance().climbTracker.isGrabbingLadder()))
            {
                doY = true;
            }

            Vec3 roomOrigin = VRPlayer.get().roomOrigin;

            if ((ClientDataHolderVR.getInstance().climbTracker.isGrabbingLadder() || freeMove ||
                ClientDataHolderVR.getInstance().swimTracker.isActive((LocalPlayer) (Object) this)
            ) && (this.zza != 0.0F || this.isFallFlying() || Math.abs(this.getDeltaMovement().x) > 0.01D ||
                Math.abs(this.getDeltaMovement().z) > 0.01D
            ))
            {
                double xOffset = roomOrigin.x - this.getX();
                double zOffset = roomOrigin.z - this.getZ();
                double oldX = this.getX();
                double oldZ = this.getZ();
                super.move(type, pos);

                if (ClientDataHolderVR.getInstance().vrSettings.walkUpBlocks) {
                    this.setMaxUpStep(this.getBlockJumpFactor() == 1.0F ? 1.0F : 0.6F);
                } else {
                    this.setMaxUpStep(0.6F);
                    this.updateAutoJump((float) (this.getX() - oldX), (float) (this.getZ() - oldZ));
                }

                VRPlayer.get().setRoomOrigin(
                    this.getX() + xOffset,
                    this.getY() + this.vivecraft$getRoomYOffsetFromPose(),
                    this.getZ() + zOffset,
                    false);
            } else if (doY) {
                super.move(type, new Vec3(0.0D, pos.y, 0.0D));
                VRPlayer.get().setRoomOrigin(
                    VRPlayer.get().roomOrigin.x,
                    this.getY() + this.vivecraft$getRoomYOffsetFromPose(),
                    VRPlayer.get().roomOrigin.z,
                    false);
            } else {
                // do not move player, VRPlayer.doPlayerMoveInRoom will move him around.
                this.setOnGround(true);
            }
        }
        ci.cancel();
    }

    @ModifyArg(method = "updateAutoJump", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(F)F"))
    private float vivecraft$modifyAutoJumpSin(float original) {
        return VRState.VR_RUNNING ? ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_pre.getBodyYawRad() : original;
    }

    @ModifyArg(method = "updateAutoJump", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;cos(F)F"))
    private float vivecraft$modifyAutoJumpCos(float original) {
        return VRState.VR_RUNNING ? ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_pre.getBodyYawRad() : original;
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void vivecraft$hapticsOnEvent(byte id, CallbackInfo ci) {
        if (VRState.VR_RUNNING && vivecraft$isLocalPlayer(this)) {
            if (id == EntityEvent.DEATH) {
                ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 2000);
                ClientDataHolderVR.getInstance().vr.triggerHapticPulse(1, 2000);
            }
        }
    }

    /**
     * inject into {@link Player#eat(Level, ItemStack)}
     */
    @Override
    protected void vivecraft$beforeEat(Level level, ItemStack food, CallbackInfoReturnable<ItemStack> cir) {
        if (VRState.VR_RUNNING && food.isEdible() && vivecraft$isLocalPlayer(this) &&
            food.getHoverName().getString().equals("EAT ME"))
        {
            ClientDataHolderVR.getInstance().vrPlayer.wfMode = 0.5D;
            ClientDataHolderVR.getInstance().vrPlayer.wfCount = 400;
        }
    }

    /**
     * inject into {@link LivingEntity#releaseUsingItem()}
     */
    @Override
    protected void vivecraft$beforeReleaseUsingItem(CallbackInfo ci) {
        if (VRState.VR_RUNNING && vivecraft$isLocalPlayer(this)) {
            ClientNetworking.sendActiveHand((byte) this.getUsedItemHand().ordinal());
        }
    }

    /**
     * inject into {@link Entity#absMoveTo(double, double, double, float, float)}
     * and {@link Entity#moveTo(double, double, double, float, float)}
     */
    @Override
    protected void vivecraft$afterAbsMoveTo(CallbackInfo ci) {
        if (VRState.VR_RUNNING && vivecraft$isLocalPlayer(this) && this.vivecraft$initFromServer) {
            ClientDataHolderVR.getInstance().vrPlayer.snapRoomOriginToPlayerEntity((LocalPlayer) (Object) this, false, false);
        }
    }

    /**
     * inject into {@link Entity#setPos(double, double, double)}
     */
    @Override
    protected void vivecraft$wrapSetPos(double x, double y, double z, Operation<Void> original) {
        this.vivecraft$initFromServer = true;
        if (!VRState.VR_RUNNING || !vivecraft$isLocalPlayer(this)) {
            original.call(x, y, z);
            return;
        }
        double oldX = this.getX();
        double oldY = this.getY();
        double oldZ = this.getZ();
        original.call(x, y, z);
        double newX = this.getX();
        double newY = this.getY();
        double newZ = this.getZ();

        if (Minecraft.getInstance().getCameraEntity() == (Object) this && this.isPassenger()) {
            ClientDataHolderVR.getInstance().vehicleTracker.updateRiderPos(x, y, z, this.getVehicle());
        } else if (!ClientDataHolderVR.getInstance().vehicleTracker.isRiding()) {
            Vec3 roomOrigin = ClientDataHolderVR.getInstance().vrPlayer.roomOrigin;
            VRPlayer.get().setRoomOrigin(
                roomOrigin.x + (newX - oldX),
                roomOrigin.y + (newY - oldY),
                roomOrigin.z + (newZ - oldZ),
                x + y + z == 0.0D);
        }
    }

    /**
     * inject into {@link Entity#moveRelative(float, Vec3)}
     */
    @Override
    protected Vec3 vivecraft$controllerMovement(Vec3 relative, float amount, float facing, Operation<Vec3> original) {
        if (!VRState.VR_RUNNING || !vivecraft$isLocalPlayer(this)) {
            return original.call(relative, amount, facing);
        }

        double up = relative.y;
        double strafe = relative.x;
        double forward = relative.z;
        VRPlayer vrplayer = this.vivecraft$dataholder.vrPlayer;

        Vec3 movement = Vec3.ZERO;

        if (vrplayer.getFreeMove()) {
            double speed = strafe * strafe + forward * forward;
            double mX = 0.0D;
            double mZ = 0.0D;
            double mY = 0.0D;
            double addFactor = 1.0D;

            if (speed >= (double) 1.0E-4F || ClientDataHolderVR.KAT_VR) {
                speed = Mth.sqrt((float) speed);

                if (speed < 1.0D && !ClientDataHolderVR.KAT_VR) {
                    speed = 1.0D;
                }

                speed = (double) amount / speed;
                strafe = strafe * speed;
                forward = forward * speed;
                Vec3 direction = new Vec3(strafe, 0.0D, forward);
                boolean isFlyingOrSwimming = !this.isPassenger() && (this.getAbilities().flying || this.isSwimming());

                if (ClientDataHolderVR.KAT_VR) {
                    jkatvr.query();
                    speed = jkatvr.getSpeed() * jkatvr.walkDirection() * this.vivecraft$dataholder.vrSettings.movementSpeedMultiplier;
                    direction = new Vec3(0.0D, 0.0D, speed);

                    if (isFlyingOrSwimming) {
                        direction = direction.xRot(vrplayer.vrdata_world_pre.hmd.getPitchRad());
                    }

                    direction = direction.yRot(-jkatvr.getYaw() * Mth.DEG_TO_RAD + vrplayer.vrdata_world_pre.rotation_radians);
                } else if (ClientDataHolderVR.INFINADECK) {
                    jinfinadeck.query();
                    speed = jinfinadeck.getSpeed() * jinfinadeck.walkDirection() * this.vivecraft$dataholder.vrSettings.movementSpeedMultiplier;
                    direction = new Vec3(0.0D, 0.0D, speed);

                    if (isFlyingOrSwimming) {
                        direction = direction.xRot(vrplayer.vrdata_world_pre.hmd.getPitchRad());
                    }

                    direction = direction.yRot(-jinfinadeck.getYaw() * Mth.DEG_TO_RAD + vrplayer.vrdata_world_pre.rotation_radians);
                } else if (this.vivecraft$dataholder.vrSettings.seated) {
                    int c = 0;
                    if (this.vivecraft$dataholder.vrSettings.seatedUseHMD) {
                        c = 1;
                    }

                    if (isFlyingOrSwimming) {
                        direction = direction.xRot(vrplayer.vrdata_world_pre.getController(c).getPitchRad());
                    }

                    direction = direction.yRot(-vrplayer.vrdata_world_pre.getController(c).getYawRad());
                } else {

                    VRSettings.FreeMove freeMoveType = !this.isPassenger() && this.getAbilities().flying && this.vivecraft$dataholder.vrSettings.vrFreeMoveFlyMode != VRSettings.FreeMove.AUTO ? this.vivecraft$dataholder.vrSettings.vrFreeMoveFlyMode : this.vivecraft$dataholder.vrSettings.vrFreeMoveMode;

                    if (isFlyingOrSwimming) {
                        direction = switch (freeMoveType) {
                            case CONTROLLER -> direction.xRot(vrplayer.vrdata_world_pre.getController(1).getPitchRad());
                            case HMD, RUN_IN_PLACE, ROOM -> direction.xRot(vrplayer.vrdata_world_pre.hmd.getPitchRad());
                            default -> direction;
                        };
                    }
                    if (this.vivecraft$dataholder.jumpTracker.isjumping()) {
                        direction = direction.yRot(-vrplayer.vrdata_world_pre.hmd.getYawRad());
                    } else {
                        direction = switch (freeMoveType) {
                            case CONTROLLER -> direction.yRot(-vrplayer.vrdata_world_pre.getController(1).getYawRad());
                            case HMD -> direction.yRot(-vrplayer.vrdata_world_pre.hmd.getYawRad());
                            case RUN_IN_PLACE -> direction.yRot((float) -this.vivecraft$dataholder.runTracker.getYaw())
                                .scale(this.vivecraft$dataholder.runTracker.getSpeed());
                            case ROOM -> direction.yRot(
                                (180.0F + this.vivecraft$dataholder.vrSettings.worldRotation) * Mth.DEG_TO_RAD);
                            default -> direction;
                        };
                    }
                }

                mX = direction.x;
                mY = direction.y;
                mZ = direction.z;

                if (!this.getAbilities().flying && !this.wasTouchingWater) {
                    addFactor = this.vivecraft$dataholder.vrSettings.inertiaFactor.getFactor();
                }

                float yAdd = 1.0F;

                if (this.getAbilities().flying) {
                    yAdd = 5.0F;
                }

                movement = new Vec3(mX * addFactor, mY * (double) yAdd, mZ * addFactor);
                this.vivecraft$additionX = mX;
                this.vivecraft$additionZ = mZ;
            }

            if (!this.getAbilities().flying && !this.wasTouchingWater) {
                this.vivecraft$doDrag();
            }
        }
        return movement;
    }

    /**
     * applies slowdown/speedup based one the inertia setting
     */
    @Unique
    private void vivecraft$doDrag() {
        float friction = 0.91F;

        if (this.onGround()) {
            friction = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement())
                .getBlock().getFriction() * 0.91F;
        }
        double xFactor = friction;
        double zFactor = friction;
        // account for stock drag code we can't change in LivingEntity#travel
        this.setDeltaMovement(
            this.getDeltaMovement().x / xFactor,
            this.getDeltaMovement().y,
            this.getDeltaMovement().z / zFactor);

        double addFactor = this.vivecraft$dataholder.vrSettings.inertiaFactor.getFactor();

        double boundedAdditionX = vivecraft$getBoundedAddition(this.vivecraft$additionX);
        double targetLimitX = (friction * boundedAdditionX) / (1f - friction);
        double multiFactorX = targetLimitX / (friction * (targetLimitX + (boundedAdditionX * addFactor)));
        xFactor *= multiFactorX;

        double boundedAdditionZ = vivecraft$getBoundedAddition(this.vivecraft$additionZ);
        double targetLimitZ = (friction * boundedAdditionZ) / (1f - friction);
        double multiFactorZ = targetLimitZ / (friction * (targetLimitZ + (boundedAdditionZ * addFactor)));
        zFactor *= multiFactorZ;

        this.setDeltaMovement(this.getDeltaMovement().x * xFactor, this.getDeltaMovement().y, this.getDeltaMovement().z * zFactor);
    }

    @Unique
    private double vivecraft$getBoundedAddition(double orig) {
        return orig >= -1.0E-6D && orig <= 1.0E-6D ? 1.0E-6D : orig;
    }

    @Unique
    private boolean vivecraft$isLocalPlayer(Object player) {
        return player.getClass().equals(LocalPlayer.class) || Minecraft.getInstance().player == player;
    }

    @Override
    @Unique
    public boolean vivecraft$getInitFromServer() {
        return this.vivecraft$initFromServer;
    }

    @Override
    @Unique
    public float vivecraft$getMuhSpeedFactor() {
        // this shouldn't ever be null, but mixins doesn't always apply the default value
        return this.vivecraft$moveMulIn != null && this.vivecraft$moveMulIn.lengthSqr() > 0.0D ?
            this.getBlockSpeedFactor() * (float) (this.vivecraft$moveMulIn.x + this.vivecraft$moveMulIn.z) * 0.5F :
            this.getBlockSpeedFactor();
    }

    @Override
    @Unique
    public float vivecraft$getMuhJumpFactor() {
        // this shouldn't ever be null, but mixins doesn't always apply the default value
        return this.vivecraft$moveMulIn != null && this.vivecraft$moveMulIn.lengthSqr() > 0.0D ?
            this.getBlockJumpFactor() * (float) this.vivecraft$moveMulIn.y : this.getBlockJumpFactor();
    }

    @Override
    @Unique
    public void vivecraft$stepSound(BlockPos blockPos, Vec3 soundPos) {
        BlockState blockState = this.level().getBlockState(blockPos);
        Block block = blockState.getBlock();
        SoundType soundType = block.getSoundType(blockState);
        BlockState aboveBlockState = this.level().getBlockState(blockPos.above());

        if (aboveBlockState.getBlock() == Blocks.SNOW) {
            soundType = Blocks.SNOW.getSoundType(aboveBlockState);
        }

        // TODO: liquid is deprecated
        if (!this.isSilent() && !block.defaultBlockState().liquid()) {
            float volume = soundType.getVolume();
            float pitch = soundType.getPitch();
            SoundEvent soundevent = soundType.getStepSound();

            this.level()
                .playSound(null, soundPos.x, soundPos.y, soundPos.z, soundevent, this.getSoundSource(), volume, pitch);
        }
    }

    @Override
    @Unique
    public void vivecraft$setItemInUseClient(ItemStack itemStack, InteractionHand interactionHand) {
        this.useItem = itemStack;
        this.usingItemHand = interactionHand;
        this.startedUsingItem = itemStack != ItemStack.EMPTY;
    }

    @Override
    @Unique
    public void vivecraft$setTeleported(boolean teleported) {
        this.vivecraft$teleported = teleported;
    }

    @Override
    @Unique
    public void vivecraft$setItemInUseRemainingClient(int count) {
        this.useItemRemaining = count;
    }

    @Override
    @Unique
    public double vivecraft$getRoomYOffsetFromPose() {
        // Adjust room origin to account for pose, only when not standing on something.
        if (this.getPose() == Pose.FALL_FLYING ||
            this.getPose() == Pose.SPIN_ATTACK ||
            (this.getPose() == Pose.SWIMMING && !ClientDataHolderVR.getInstance().crawlTracker.crawlsteresis))
        {
            return -1.2D;
        } else {
            return 0.0D;
        }
    }
}
