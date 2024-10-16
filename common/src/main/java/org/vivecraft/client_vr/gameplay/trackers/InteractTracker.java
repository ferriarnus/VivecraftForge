package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.Xplat;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.VRFirstPersonArmSwing;
import org.vivecraft.client_vr.settings.VRHotkeys;
import org.vivecraft.client_vr.settings.VRSettings;

import java.util.HashSet;

public class InteractTracker extends Tracker {
    // indicates when a hand has a bucket and is in a liquid
    public boolean[] bukkit = new boolean[2];

    // indicates the pointed at hotbar slot
    public int hotbar = -1;

    // indicates if the bow can be drawn
    private final boolean[] inBow = new boolean[2];

    // indicates what block or entity the hand is in
    public BlockHitResult[] inBlockHit = new BlockHitResult[2];
    private final BlockPos[] inBlockPos = new BlockPos[2];
    private final Entity[] inEntity = new Entity[2];
    private final EntityHitResult[] inEntityHit = new EntityHitResult[2];

    // indicates if the hand is inside a camera to move
    private final boolean[] inCamera = new boolean[2];
    private final boolean[] inHandheldCamera = new boolean[2];

    // indicates if any interact is active
    private final boolean[] active = new boolean[2];
    private final boolean[] wasactive = new boolean[2];

    // a set of blocks that can be interacted with
    private HashSet<Class<?>> rightClickable = null;

    public InteractTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        if (this.mc.gameMode == null) {
            return false;
        } else if (player == null) {
            return false;
        } else if (!player.isAlive()) {
            return false;
        } else if (player.isSleeping()) {
            return false;
        } else if (this.dh.vrSettings.seated) {
            return false;
        } else {
            return !(player.isBlocking() && this.hotbar < 0);
        }
    }

    @Override
    public void reset(LocalPlayer player) {
        for (int c = 0; c < 2; c++) {
            this.reset(player, c);
        }
    }

    private void reset(LocalPlayer player, int c) {
        // stop moving cameras
        if (this.inCamera[c] &&
            VRHotkeys.isMovingThirdPersonCam() &&
            VRHotkeys.getMovingThirdPersonCamTriggerer() == VRHotkeys.Triggerer.INTERACTION &&
            VRHotkeys.getMovingThirdPersonCamController() == c)
        {
            VRHotkeys.stopMovingThirdPersonCam();
        }

        if (this.inHandheldCamera[c] &&
            this.dh.cameraTracker.isMoving() &&
            this.dh.cameraTracker.getMovingController() == c &&
            !this.dh.cameraTracker.isQuickMode())
        {
            this.dh.cameraTracker.stopMoving();
        }

        this.inBow[c] = false;
        this.inBlockPos[c] = null;
        this.inBlockHit[c] = null;
        this.inEntity[c] = null;
        this.inEntityHit[c] = null;
        this.inCamera[c] = false;
        this.inHandheldCamera[c] = false;
        this.active[c] = false;
        this.dh.vr.getInputAction(VivecraftVRMod.INSTANCE.keyVRInteract).setEnabled(ControllerType.values()[c], false);
    }

    @Override
    public void doProcess(LocalPlayer player) {
        if (this.rightClickable == null) {
            // compile a list of blocks that explicitly declare OnBlockActivated (right click)
            this.rightClickable = new HashSet<>();

            String name = Xplat.getUseMethodName();
            for (Object object : BuiltInRegistries.BLOCK) {
                Class<?> oclass = object.getClass();

                addIfClassHasMethod(name, oclass);
                addIfClassHasMethod(name, oclass.getSuperclass());
            }

            // remove base classes, since that would trigger on all blocks
            this.rightClickable.remove(Block.class);
            this.rightClickable.remove(BlockBehaviour.class);
            this.rightClickable.remove(BlockBehaviour.BlockStateBase.class);
        }

        for (int c = 0; c < 2; c++) {
            if ((this.inCamera[c] || this.inHandheldCamera[c] || this.inBow[c]) &&
                VivecraftVRMod.INSTANCE.keyVRInteract.isDown(ControllerType.values()[c])) {
                // don't reevaluate, if the interact is still active
                continue;
            }

            this.reset(player, c);

            // interactive hotbar is priority 1
            if (c == 0 && this.hotbar >= 0) {
                this.active[c] = true;
            }

            // roomscale Bow shooting, only activate for the hand with the arrow
            if (!this.active[c] && this.dh.bowTracker.isNotched() &&
                c == (this.dh.vrSettings.reverseShootingEye ? 1 : 0))
            {
                this.inBow[c] = true;
                this.active[c] = true;
            }

            Vec3 hmdPos = this.dh.vrPlayer.vrdata_world_pre.getHeadPivot();
            Vec3 handPos = this.dh.vrPlayer.vrdata_world_pre.getController(c).getPosition();
            Vector3f handDirection = this.dh.vrPlayer.vrdata_world_pre.getHand(c).getCustomVector(MathUtils.BACK);
            ItemStack handItem = player.getItemInHand(c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);

            // third person camera movement
            if (!this.active[c] &&
                (this.dh.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY || this.dh.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON) &&
                this.dh.vrSettings.mixedRealityRenderCameraModel) {

                VRData.VRDevicePose camData = this.dh.vrPlayer.vrdata_world_pre.getEye(RenderPass.THIRD);
                Vec3 camPos = camData.getPosition();

                Vector3f offset = camData.getCustomVector(MathUtils.BACK)
                    .mul(0.15F * this.dh.vrPlayer.vrdata_world_pre.worldScale);
                offset.add(camData.getCustomVector(MathUtils.DOWN)
                    .mul(0.05F * this.dh.vrPlayer.vrdata_world_pre.worldScale));

                camPos = camPos.subtract(offset.x, offset.y, offset.z);

                if (handPos.distanceTo(camPos) < (double) 0.15F * this.dh.vrPlayer.vrdata_world_pre.worldScale) {
                    this.inCamera[c] = true;
                    this.active[c] = true;
                }
            }

            // screenshot camera movement
            if (!this.active[c] && this.dh.cameraTracker.isVisible() && !this.dh.cameraTracker.isQuickMode()) {
                VRData.VRDevicePose camData = this.dh.vrPlayer.vrdata_world_pre.getEye(RenderPass.CAMERA);
                Vec3 camPos = camData.getPosition();

                Vector3f offset = camData.getCustomVector(MathUtils.BACK).mul(0.08F * this.dh.vrPlayer.vrdata_world_pre.worldScale);

                camPos = camPos.subtract(offset.x, offset.y, offset.z);

                if (handPos.distanceTo(camPos) < (double) 0.11F * this.dh.vrPlayer.vrdata_world_pre.worldScale) {
                    this.inHandheldCamera[c] = true;
                    this.active[c] = true;
                }
            }

            // entity interaction
            if (this.dh.vrSettings.realisticEntityInteractEnabled && !this.active[c]) {
                Vec3 extWeapon = new Vec3(
                    handPos.x + handDirection.x * -0.1F,
                    handPos.y + handDirection.y * -0.1F,
                    handPos.z + handDirection.z * -0.1F);

                AABB weaponBB = new AABB(handPos, extWeapon);
                this.inEntityHit[c] = ProjectileUtil.getEntityHitResult(this.mc.getCameraEntity(), hmdPos, handPos, weaponBB, (e) -> {
                    return !e.isSpectator() && e.isPickable() && e != this.mc.getCameraEntity().getVehicle();
                }, 0.0D);

                if (this.inEntityHit[c] != null) {
                    Entity entity = this.inEntityHit[c].getEntity();
                    this.inEntity[c] = entity;
                    this.active[c] = true;
                }
            }

            // block interaction
            if (this.dh.vrSettings.realisticBlockInteractEnabled && !this.active[c]) {
                BlockPos blockpos = BlockPos.containing(handPos);
                BlockState blockstate = this.mc.level.getBlockState(blockpos);

                BlockHitResult hit = blockstate.getShape(this.mc.level, blockpos).clip(hmdPos, handPos, blockpos);
                this.inBlockPos[c] = blockpos;
                this.inBlockHit[c] = hit;

                this.active[c] = hit != null && (this.rightClickable.contains(blockstate.getBlock().getClass()) || this.rightClickable.contains(blockstate.getBlock().getClass().getSuperclass()));
                this.bukkit[c] = false;

                // bucket liquid pickup
                // TODO: liquid is deprecated
                if (!this.active[c] && handItem.getItem() == Items.BUCKET && blockstate.liquid()) {
                    this.active[c] = true;
                    this.bukkit[c] = true;
                }
            }

            // haptic if something activated
            if (!this.wasactive[c] && this.active[c]) {
                this.dh.vr.triggerHapticPulse(c, 250);
            }

            this.dh.vr.getInputAction(VivecraftVRMod.INSTANCE.keyVRInteract).setEnabled(ControllerType.values()[c], this.active[c]);
            this.wasactive[c] = this.active[c];
        }
    }

    private void addIfClassHasMethod(String name, Class<?> oclass) {
        try {
            if (oclass.getMethod(name,
                BlockState.class,
                net.minecraft.world.level.Level.class,
                BlockPos.class,
                net.minecraft.world.entity.player.Player.class,
                InteractionHand.class,
                BlockHitResult.class).getDeclaringClass() == oclass) {
                this.rightClickable.add(oclass);
            }
        } catch (NoSuchMethodException ignored) {
        }
    }

    public boolean isInteractActive(int controller) {
        return this.active[controller];
    }

    public boolean isInCamera() {
        return this.inCamera[0] || this.inCamera[1];
    }

    public boolean isInHandheldCamera() {
        return this.inHandheldCamera[0] || this.inHandheldCamera[1];
    }

    public void processBindings() {
        for (int c = 0; c < 2; c++) {
            if (VivecraftVRMod.INSTANCE.keyVRInteract.consumeClick(ControllerType.values()[c]) && this.active[c]) {
                InteractionHand hand = InteractionHand.values()[c];
                boolean success = false;

                if (this.hotbar >= 0 && this.hotbar < 9 && this.mc.player.getInventory().selected != this.hotbar && hand == InteractionHand.MAIN_HAND) {
                    this.mc.player.getInventory().selected = this.hotbar;
                    success = true;
                } else if (this.hotbar == 9 && hand == InteractionHand.MAIN_HAND) {
                    this.mc.player.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                    success = true;
                } else if (this.inCamera[c]) {
                    VRHotkeys.startMovingThirdPersonCam(c, VRHotkeys.Triggerer.INTERACTION);
                    success = true;
                } else if (this.inHandheldCamera[c]) {
                    this.dh.cameraTracker.startMoving(c);
                    success = true;
                } else if (this.inEntityHit[c] != null) {
                    success = this.mc.gameMode.interactAt(this.mc.player, this.inEntity[c], this.inEntityHit[c], hand).consumesAction() ||
                        this.mc.gameMode.interact(this.mc.player, this.inEntity[c], hand).consumesAction();
                } else if (this.inBlockHit[c] != null) {
                    success = this.mc.gameMode.useItemOn(this.mc.player, hand, this.inBlockHit[c]).consumesAction();
                } else if (this.bukkit[c]) {
                    success = this.mc.gameMode.useItem(this.mc.player, hand).consumesAction();
                }

                if (success) {
                    // swing arm on success
                    this.dh.swingType = VRFirstPersonArmSwing.Interact;
                    this.mc.player.swing(hand);
                    this.dh.vr.triggerHapticPulse(c, 750);
                }
            }
        }
    }
}
