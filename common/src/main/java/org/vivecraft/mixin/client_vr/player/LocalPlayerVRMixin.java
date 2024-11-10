package org.vivecraft.mixin.client_vr.player;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
import org.vivecraft.client_vr.extensions.ItemInHandRendererExtension;
import org.vivecraft.client_vr.extensions.PlayerExtension;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.render.VRFirstPersonArmSwing;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_vr.utils.external.jinfinadeck;
import org.vivecraft.client_vr.utils.external.jkatvr;
import org.vivecraft.common.network.CommonNetworkHelper;

import java.util.UUID;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerVRMixin extends AbstractClientPlayer implements PlayerExtension {

    @Unique
    private Vec3 vivecraft$moveMulIn = Vec3.ZERO;
    @Unique
    private boolean vivecraft$initFromServer;
    @Unique
    private int vivecraft$movementTeleportTimer;
    @Unique
    private boolean vivecraft$teleported;
    @Unique
    private double vivecraft$additionX;
    @Unique
    private double vivecraft$additionZ;
    @Final
    @Shadow
    protected Minecraft minecraft;
    @Shadow
    private boolean startedUsingItem;
    @Shadow
    @Final
    public ClientPacketListener connection;
    @Unique
    private final ClientDataHolderVR vivecraft$dataholder = ClientDataHolderVR.getInstance();
    @Shadow
    private InteractionHand usingItemHand;

    public LocalPlayerVRMixin(ClientLevel clientLevel, GameProfile gameProfile) {
        super(clientLevel, gameProfile);
    }

    @Shadow
    protected abstract void updateAutoJump(float f, float g);

    @Shadow
    public abstract void swing(InteractionHand interactionHand);

    @Inject(at = @At("TAIL"), method = "startRiding")
    public void vivecraft$startRidingTracker(Entity entity, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if (VRState.vrInitialized && vivecraft$isLocalPlayer(this)) {
            ClientDataHolderVR.getInstance().vehicleTracker.onStartRiding(entity, (LocalPlayer) (Object) this);
        }
    }

    @Inject(at = @At("TAIL"), method = "removeVehicle")
    public void vivecraft$stopRidingTracker(CallbackInfo ci) {
        if (VRState.vrInitialized && vivecraft$isLocalPlayer(this)) {
            ClientDataHolderVR.getInstance().vehicleTracker.onStopRiding((LocalPlayer) (Object) this);
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = At.Shift.BEFORE), method = "tick")
    public void vivecraft$overrideLookPre(CallbackInfo ci) {
        if (VRState.vrRunning && vivecraft$isLocalPlayer(this)) {
            ClientDataHolderVR.getInstance().vrPlayer.doPermanantLookOverride((LocalPlayer) (Object) this, ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_pre);
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = At.Shift.AFTER), method = "tick")
    public void vivecraft$overridePose(CallbackInfo ci) {
        if (VRState.vrRunning && vivecraft$isLocalPlayer(this)) {
            ClientNetworking.overridePose((LocalPlayer) (Object) this);
            ClientDataHolderVR.getInstance().vrPlayer.doPermanantLookOverride((LocalPlayer) (Object) this, ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_pre);
        }
    }

    @ModifyVariable(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isPassenger()Z"), ordinal = 2, method = "sendPosition")
    private boolean vivecraft$directTeleport(boolean updateRotation) {
        if (this.vivecraft$teleported) {
            updateRotation = true;
            ByteBuf bytebuf = Unpooled.buffer();
            bytebuf.writeFloat((float) this.getX());
            bytebuf.writeFloat((float) this.getY());
            bytebuf.writeFloat((float) this.getZ());
            byte[] abyte = new byte[bytebuf.readableBytes()];
            bytebuf.readBytes(abyte);
            ServerboundCustomPayloadPacket serverboundcustompayloadpacket = ClientNetworking.getVivecraftClientPacket(CommonNetworkHelper.PacketDiscriminators.TELEPORT, abyte);
            this.connection.send(serverboundcustompayloadpacket);
        }
        return updateRotation;
    }

    // needed, or the server will spam 'moved too quickly'/'moved wrongly'
    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"), method = "sendPosition", slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isPassenger()Z")))
    public void vivecraft$noMovePacketsOnTeleport(ClientPacketListener instance, Packet<?> packet) {
        if (!this.vivecraft$teleported) {
            instance.send(packet);
        }
    }

    @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;lastOnGround:Z", shift = At.Shift.AFTER, ordinal = 1), method = "sendPosition")
    public void vivecraft$walkUp(CallbackInfo ci) {
        // clear teleport here, after all the packets would be sent
        this.vivecraft$teleported = false;
        if (VRState.vrRunning && ClientDataHolderVR.getInstance().vrSettings.walkUpBlocks) {
            this.minecraft.options.autoJump().set(false);
        }
    }

    @Override
    @Unique
    public void vivecraft$swingArm(InteractionHand interactionhand, VRFirstPersonArmSwing interact) {
        ((ItemInHandRendererExtension) this.minecraft.getEntityRenderDispatcher().getItemInHandRenderer()).vivecraft$setSwingType(interact);
        this.swing(interactionhand);
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;aiStep()V"), method = "aiStep")
    public void vivecraft$ai(CallbackInfo ci) {
        if (VRState.vrRunning && vivecraft$isLocalPlayer(this)) {
            this.vivecraft$dataholder.vrPlayer.tick((LocalPlayer) (Object) this, this.minecraft, this.random);
        }
    }

    @Unique
    private static final ResourceLocation WALK_UP_BLOCKS_MODIFIER_ID = ResourceLocation.parse("vivecraft:walk_up_blocks");

    @Inject(at = @At("HEAD"), method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", cancellable = true)
    public void vivecraft$overwriteMove(MoverType pType, Vec3 pPos, CallbackInfo info) {
        if (!VRState.vrRunning || !vivecraft$isLocalPlayer(this)) {
            return;
        }

        this.vivecraft$moveMulIn = this.stuckSpeedMultiplier;

        if (pPos.length() != 0.0D && !this.isPassenger()) {
            boolean flag = VRPlayer.get().getFreeMove();
            boolean flag1 = flag || ClientDataHolderVR.getInstance().vrSettings.simulateFalling && !this.onClimbable()
                && !this.isShiftKeyDown();

            if (ClientDataHolderVR.getInstance().climbTracker.isActive((LocalPlayer) (Object) this)
                && (flag || ClientDataHolderVR.getInstance().climbTracker.isGrabbingLadder())) {
                flag1 = true;
            }

            Vec3 vec3 = VRPlayer.get().roomOrigin;

            if ((ClientDataHolderVR.getInstance().climbTracker.isGrabbingLadder() || flag
                || ClientDataHolderVR.getInstance().swimTracker.isActive((LocalPlayer) (Object) this))
                && (this.zza != 0.0F || this.isFallFlying() || Math.abs(this.getDeltaMovement().x) > 0.01D
                || Math.abs(this.getDeltaMovement().z) > 0.01D)) {
                double d0 = vec3.x - this.getX();
                double d1 = vec3.z - this.getZ();
                double d2 = this.getX();
                double d3 = this.getZ();
                super.move(pType, pPos);

                // TODO 1.20.5
                AttributeInstance attributeInstance = this.getAttribute(Attributes.STEP_HEIGHT);
                if (attributeInstance != null) {
                    if (ClientDataHolderVR.getInstance().vrSettings.walkUpBlocks) {
                        if (this.getBlockJumpFactor() == 1.0F) {
                            if (attributeInstance.getModifier(WALK_UP_BLOCKS_MODIFIER_ID) == null) {
                                attributeInstance.addTransientModifier(new AttributeModifier(WALK_UP_BLOCKS_MODIFIER_ID, 0.4F, AttributeModifier.Operation.ADD_VALUE));
                            }
                        } else {
                            if (attributeInstance.getModifier(WALK_UP_BLOCKS_MODIFIER_ID) != null) {
                                attributeInstance.removeModifier(WALK_UP_BLOCKS_MODIFIER_ID);
                            }
                        }
                    } else {
                        if (attributeInstance.getModifier(WALK_UP_BLOCKS_MODIFIER_ID) != null) {
                            attributeInstance.removeModifier(WALK_UP_BLOCKS_MODIFIER_ID);
                        }
                        this.updateAutoJump((float) (this.getX() - d2), (float) (this.getZ() - d3));
                    }
                }

                double d4 = this.getY() + this.vivecraft$getRoomYOffsetFromPose();
                VRPlayer.get().setRoomOrigin(this.getX() + d0, d4, this.getZ() + d1, false);
            } else if (flag1) {
                super.move(pType, new Vec3(0.0D, pPos.y, 0.0D));
                VRPlayer.get().setRoomOrigin(VRPlayer.get().roomOrigin.x, this.getY() + this.vivecraft$getRoomYOffsetFromPose(),
                    VRPlayer.get().roomOrigin.z, false);
            } else {
                this.setOnGround(true);
            }
        } else {
            super.move(pType, pPos);
        }
        info.cancel();
    }

    @Override
    @Unique
    public double vivecraft$getRoomYOffsetFromPose() {
        double d0 = 0.0D;

        if (this.getPose() == Pose.FALL_FLYING || this.getPose() == Pose.SPIN_ATTACK
            || this.getPose() == Pose.SWIMMING && !ClientDataHolderVR.getInstance().crawlTracker.crawlsteresis) {
            d0 = -1.2D;
        }

        return d0;
    }

    @ModifyArg(at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(F)F"), method = "updateAutoJump")
    private float vivecraft$modifyAutoJumpSin(float original) {
        return VRState.vrRunning ? ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_pre.getBodyYaw() * ((float) Math.PI / 180) : original;
    }

    @ModifyArg(at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;cos(F)F"), method = "updateAutoJump")
    private float vivecraft$modifyAutoJumpCos(float original) {
        return VRState.vrRunning ? ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_pre.getBodyYaw() * ((float) Math.PI / 180) : original;
    }

    @Override
    public ItemStack eat(Level level, ItemStack itemStack) {
        if (VRState.vrRunning && itemStack.get(DataComponents.FOOD) != null && (Object) this == Minecraft.getInstance().player && itemStack.getHoverName().getString().equals("EAT ME")) {
            ClientDataHolderVR.getInstance().vrPlayer.wfMode = 0.5D;
            ClientDataHolderVR.getInstance().vrPlayer.wfCount = 400;
        }
        ClientDataHolderVR.getInstance().hapticTracker.handleEat(itemStack);
        return super.eat(level, itemStack);
    }

    @Override
    public void moveTo(double pX, double p_20109_, double pY, float p_20111_, float pZ) {
        super.moveTo(pX, p_20109_, pY, p_20111_, pZ);
        if (!VRState.vrRunning || !vivecraft$isLocalPlayer(this)) {
            return;
        }
        if (this.vivecraft$initFromServer) {
            ClientDataHolderVR.getInstance().vrPlayer.snapRoomOriginToPlayerEntity((LocalPlayer) (Object) this, false, false);
        }
    }

    @Override
    public void absMoveTo(double pX, double p_19892_, double pY, float p_19894_, float pZ) {
        super.absMoveTo(pX, p_19892_, pY, p_19894_, pZ);
        if (!VRState.vrRunning || !vivecraft$isLocalPlayer(this)) {
            return;
        }
        ClientDataHolderVR.getInstance().vrPlayer.snapRoomOriginToPlayerEntity((LocalPlayer) (Object) this, false, false);
    }

    @Override
    public void setPos(double pX, double p_20211_, double pY) {
        this.vivecraft$initFromServer = true;
        if (!VRState.vrRunning || !vivecraft$isLocalPlayer(this)) {
            super.setPos(pX, p_20211_, pY);
            return;
        }
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();
        super.setPos(pX, p_20211_, pY);
        double d3 = this.getX();
        double d4 = this.getY();
        double d5 = this.getZ();
        Entity entity = this.getVehicle();

        if (this.isPassenger()) {
            Vec3 vec3 = ClientDataHolderVR.getInstance().vehicleTracker.Premount_Pos_Room;
            vec3 = vec3.yRot(ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_pre.rotation_radians);
            pX = pX - vec3.x;
            p_20211_ = ClientDataHolderVR.getInstance().vehicleTracker.getVehicleFloor(entity, p_20211_);
            pY = pY - vec3.z;
            ClientDataHolderVR.getInstance().vrPlayer.setRoomOrigin(pX, p_20211_, pY, pX + p_20211_ + pY == 0.0D);
        } else {
            Vec3 vec31 = ClientDataHolderVR.getInstance().vrPlayer.roomOrigin;
            VRPlayer.get().setRoomOrigin(vec31.x + (d3 - d0), vec31.y + (d4 - d1), vec31.z + (d5 - d2),
                pX + p_20211_ + pY == 0.0D);
        }
    }

    @Unique
    public void vivecraft$doDrag() {
        float friction = 0.91F;

        if (this.onGround()) {
            friction = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.91F;
        }
        double xFactor = friction;
        double zFactor = friction;
        // account for stock drag code we can't change in LivingEntity#travel
        this.setDeltaMovement(this.getDeltaMovement().x / xFactor, this.getDeltaMovement().y, this.getDeltaMovement().z / zFactor);

        double addFactor = vivecraft$dataholder.vrSettings.inertiaFactor.getFactor();

        double boundedAdditionX = vivecraft$getBoundedAddition(vivecraft$additionX);
        double targetLimitX = (friction * boundedAdditionX) / (1f - friction);
        double multiFactorX = targetLimitX / (friction * (targetLimitX + (boundedAdditionX * addFactor)));
        xFactor *= multiFactorX;

        double boundedAdditionZ = vivecraft$getBoundedAddition(vivecraft$additionZ);
        double targetLimitZ = (friction * boundedAdditionZ) / (1f - friction);
        double multiFactorZ = targetLimitZ / (friction * (targetLimitZ + (boundedAdditionZ * addFactor)));
        zFactor *= multiFactorZ;

        this.setDeltaMovement(this.getDeltaMovement().x * xFactor, this.getDeltaMovement().y, this.getDeltaMovement().z * zFactor);
    }

    @Unique
    public double vivecraft$getBoundedAddition(double orig) {
        return orig >= -1.0E-6D && orig <= 1.0E-6D ? 1.0E-6D : orig;
    }

    @Override
    public void moveRelative(float pAmount, Vec3 pRelative) {
        if (!VRState.vrRunning || !vivecraft$isLocalPlayer(this)) {
            super.moveRelative(pAmount, pRelative);
            return;
        }

        double up = pRelative.y;
        double strafe = pRelative.x;
        double forward = pRelative.z;
        VRPlayer vrplayer = this.vivecraft$dataholder.vrPlayer;

        if (vrplayer.getFreeMove()) {
            double speed = strafe * strafe + forward * forward;
            double mX = 0.0D;
            double mZ = 0.0D;
            double mY = 0.0D;
            double addFactor = 1.0D;

            if (speed >= (double) 1.0E-4F || ClientDataHolderVR.katvr) {
                speed = Mth.sqrt((float) speed);

                if (speed < 1.0D && !ClientDataHolderVR.katvr) {
                    speed = 1.0D;
                }

                speed = (double) pAmount / speed;
                strafe = strafe * speed;
                forward = forward * speed;
                Vec3 direction = new Vec3(strafe, 0.0D, forward);
                boolean isFlyingOrSwimming = !this.isPassenger() && (this.getAbilities().flying || this.isSwimming());

                if (ClientDataHolderVR.katvr) {
                    jkatvr.query();
                    speed = jkatvr.getSpeed() * jkatvr.walkDirection() * this.vivecraft$dataholder.vrSettings.movementSpeedMultiplier;
                    direction = new Vec3(0.0D, 0.0D, speed);

                    if (isFlyingOrSwimming) {
                        direction = direction.xRot(vrplayer.vrdata_world_pre.hmd.getPitch() * ((float) Math.PI / 180F));
                    }

                    direction = direction.yRot(-jkatvr.getYaw() * ((float) Math.PI / 180F) + vrplayer.vrdata_world_pre.rotation_radians);
                } else if (ClientDataHolderVR.infinadeck) {
                    jinfinadeck.query();
                    speed = jinfinadeck.getSpeed() * jinfinadeck.walkDirection() * this.vivecraft$dataholder.vrSettings.movementSpeedMultiplier;
                    direction = new Vec3(0.0D, 0.0D, speed);

                    if (isFlyingOrSwimming) {
                        direction = direction.xRot(vrplayer.vrdata_world_pre.hmd.getPitch() * ((float) Math.PI / 180F));
                    }

                    direction = direction.yRot(-jinfinadeck.getYaw() * ((float) Math.PI / 180F) + vrplayer.vrdata_world_pre.rotation_radians);
                } else if (this.vivecraft$dataholder.vrSettings.seated) {
                    int c = 0;
                    if (this.vivecraft$dataholder.vrSettings.seatedUseHMD) {
                        c = 1;
                    }

                    if (isFlyingOrSwimming) {
                        direction = direction.xRot(vrplayer.vrdata_world_pre.getController(c).getPitch() * ((float) Math.PI / 180F));
                    }

                    direction = direction.yRot(-vrplayer.vrdata_world_pre.getController(c).getYaw() * ((float) Math.PI / 180F));
                } else {

                    VRSettings.FreeMove freeMoveType = !this.isPassenger() && this.getAbilities().flying && this.vivecraft$dataholder.vrSettings.vrFreeMoveFlyMode != VRSettings.FreeMove.AUTO ? this.vivecraft$dataholder.vrSettings.vrFreeMoveFlyMode : this.vivecraft$dataholder.vrSettings.vrFreeMoveMode;

                    if (isFlyingOrSwimming) {
                        switch (freeMoveType) {
                            case CONTROLLER:
                                direction = direction.xRot(vrplayer.vrdata_world_pre.getController(1).getPitch() * ((float) Math.PI / 180F));
                                break;
                            case HMD:
                            case RUN_IN_PLACE:
                            case ROOM:
                                direction = direction.xRot(vrplayer.vrdata_world_pre.hmd.getPitch() * ((float) Math.PI / 180F));
                                break;
                        }
                    }
                    if (this.vivecraft$dataholder.jumpTracker.isjumping()) {
                        direction = direction.yRot(-vrplayer.vrdata_world_pre.hmd.getYaw() * ((float) Math.PI / 180F));
                    } else {
                        switch (freeMoveType) {
                            case CONTROLLER:
                                direction = direction.yRot(-vrplayer.vrdata_world_pre.getController(1).getYaw() * ((float) Math.PI / 180F));
                                break;

                            case HMD:
                                direction = direction.yRot(-vrplayer.vrdata_world_pre.hmd.getYaw() * ((float) Math.PI / 180F));
                                break;

                            case RUN_IN_PLACE:
                                direction = direction.yRot((float) (-this.vivecraft$dataholder.runTracker.getYaw() * (double) ((float) Math.PI / 180F)));
                                direction = direction.scale(this.vivecraft$dataholder.runTracker.getSpeed());
                                break;

                            case ROOM:
                                direction = direction.yRot((180.0F + this.vivecraft$dataholder.vrSettings.worldRotation) * ((float) Math.PI / 180F));
                                break;
                        }
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

                this.setDeltaMovement(this.getDeltaMovement().x + mX * addFactor, this.getDeltaMovement().y + mY * (double) yAdd, this.getDeltaMovement().z + mZ * addFactor);
                this.vivecraft$additionX = mX;
                this.vivecraft$additionZ = mZ;
            }

            if (!this.getAbilities().flying && !this.wasTouchingWater) {
                this.vivecraft$doDrag();
            }
        }
    }

    @Override
    public void die(DamageSource pCause) {
        super.die(pCause);
        if (!VRState.vrRunning || !vivecraft$isLocalPlayer(this)) {
            return;
        }
        ClientDataHolderVR.getInstance().vr.triggerHapticPulse(0, 2000);
        ClientDataHolderVR.getInstance().vr.triggerHapticPulse(1, 2000);
    }

    @Override
    @Unique
    public boolean vivecraft$getInitFromServer() {
        return this.vivecraft$initFromServer;
    }

    @Override
    @Unique
    public void vivecraft$setMovementTeleportTimer(int value) {
        this.vivecraft$movementTeleportTimer = value;
    }

    @Override
    @Unique
    public int vivecraft$getMovementTeleportTimer() {
        return vivecraft$movementTeleportTimer;
    }

    @Override
    @Unique
    public float vivecraft$getMuhSpeedFactor() {
        return this.vivecraft$moveMulIn.lengthSqr() > 0.0D ? (float) ((double) this.getBlockSpeedFactor() * (this.vivecraft$moveMulIn.x + this.vivecraft$moveMulIn.z) / 2.0D) : this.getBlockSpeedFactor();
    }

    @Override
    @Unique
    public float vivecraft$getMuhJumpFactor() {
        return this.vivecraft$moveMulIn.lengthSqr() > 0.0D ? (float) ((double) this.getBlockJumpFactor() * this.vivecraft$moveMulIn.y) : this.getBlockJumpFactor();
    }

    @Override
    @Unique
    public void vivecraft$stepSound(BlockPos blockforNoise, Vec3 soundPos) {
        BlockState blockstate = this.level().getBlockState(blockforNoise);
        SoundType soundtype = blockstate.getSoundType();
        BlockState blockstate1 = this.level().getBlockState(blockforNoise.above());

        if (blockstate1.getBlock() == Blocks.SNOW) {
            soundtype = Blocks.SNOW.defaultBlockState().getSoundType();
        }

        float f = soundtype.getVolume();
        float f1 = soundtype.getPitch();
        SoundEvent soundevent = soundtype.getStepSound();

        // TODO: liquid is deprecated
        if (!this.isSilent() && !blockstate.liquid()) {
            this.level().playSound(null, soundPos.x, soundPos.y, soundPos.z, soundevent, this.getSoundSource(), f, f1);
        }
    }

    @Override
    @Unique
    public boolean vivecraft$isClimbeyJumpEquipped() {
        return this.getItemBySlot(EquipmentSlot.FEET) != null && ClientDataHolderVR.getInstance().jumpTracker.isBoots(this.getItemBySlot(EquipmentSlot.FEET));
    }

    @Override
    @Unique
    public boolean vivecraft$isClimbeyClimbEquipped() {
        if (this.getMainHandItem() != null && ClientDataHolderVR.getInstance().climbTracker.isClaws(this.getMainHandItem())) {
            return true;
        } else {
            return this.getOffhandItem() != null && ClientDataHolderVR.getInstance().climbTracker.isClaws(this.getOffhandItem());
        }
    }

    @Override
    public void releaseUsingItem() {
        if (vivecraft$isLocalPlayer(this)) {
            ClientNetworking.sendActiveHand((byte) this.getUsedItemHand().ordinal());
        }
        super.releaseUsingItem();
    }

    @Override
    @Unique
    public void vivecraft$setItemInUseClient(ItemStack item, InteractionHand hand) {
        this.useItem = item;

        if (item != ItemStack.EMPTY) {
            this.startedUsingItem = true;
            this.usingItemHand = hand;
        } else {
            this.startedUsingItem = false;
            this.usingItemHand = hand;
        }
    }

    @Override
    @Unique
    public void vivecraft$setTeleported(boolean teleported) {
        this.vivecraft$teleported = teleported;
    }

    @Override
    @Unique
    public void vivecraft$setItemInUseCountClient(int count) {
        this.useItemRemaining = count;
    }

    @Unique
    private boolean vivecraft$isLocalPlayer(Object player) {
        return player.getClass().equals(LocalPlayer.class) || Minecraft.getInstance().player == player;
    }
}
