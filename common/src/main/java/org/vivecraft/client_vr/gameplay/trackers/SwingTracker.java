package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.Xplat;
import org.vivecraft.client.utils.MathUtils;
import org.vivecraft.client_vr.BlockTags;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.ItemTags;
import org.vivecraft.client_vr.Vector3fHistory;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.mod_compat_vr.bettercombat.BetterCombatHelper;
import org.vivecraft.mod_compat_vr.epicfight.EpicFightHelper;

import java.util.List;

public class SwingTracker extends Tracker {
    private final Vec3[] lastWeaponEndAir = new Vec3[]{new Vec3(0.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 0.0D)};
    private final boolean[] lastWeaponSolid = new boolean[2];
    public final Vec3[] miningPoint = new Vec3[2];
    public final Vec3[] attackingPoint = new Vec3[2];
    public final Vector3fHistory[] tipHistory = new Vector3fHistory[]{new Vector3fHistory(), new Vector3fHistory()};
    public boolean[] canAct = new boolean[2];
    public int disableSwing = 3;
    private double speedThresh = 3.0D;

    public SwingTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        if (this.disableSwing > 0) {
            this.disableSwing--;
            return false;
        } else if (this.mc.gameMode == null) {
            return false;
        } else if (player == null) {
            return false;
        } else if (!player.isAlive()) {
            return false;
        } else if (player.isSleeping()) {
            return false;
        } else if (this.mc.screen != null) {
            return false;
        } else if (this.dh.vrSettings.weaponCollision == VRSettings.WeaponCollision.OFF) {
            return false;
        } else if (this.dh.vrSettings.weaponCollision == VRSettings.WeaponCollision.AUTO) {
            return !player.isCreative();
        } else if (this.dh.vrSettings.seated) {
            return false;
        } else if (this.dh.vrSettings.vrFreeMoveMode == VRSettings.FreeMove.RUN_IN_PLACE && player.zza > 0.0F) {
            return false; // don't hit things while RIPing.
        } else if (player.isBlocking()) {
            return false; //don't hit things while blocking.
        } else {
            return !this.dh.jumpTracker.isjumping();
        }
    }

    /**
     * @param item Item to check
     * @return if the given {@code item} is a Tool
     */
    public static boolean isTool(Item item) {
        return item instanceof DiggerItem ||
            item instanceof ArrowItem ||
            item instanceof FishingRodItem ||
            item instanceof FoodOnAStickItem ||
            item instanceof ShearsItem ||
            item == Items.BONE ||
            item == Items.BLAZE_ROD ||
            item == Items.BAMBOO ||
            item == Items.TORCH ||
            item == Items.REDSTONE_TORCH ||
            item == Items.STICK ||
            item == Items.DEBUG_STICK ||
            item instanceof FlintAndSteelItem ||
            item instanceof BrushItem ||
            item.getDefaultInstance().is(ItemTags.VIVECRAFT_TOOLS);
    }

    @Override
    public void doProcess(LocalPlayer player) {
        this.speedThresh = 3.0D;

        if (player.isCreative()) {
            this.speedThresh *= 1.5D;
        }

        this.mc.getProfiler().push("updateSwingAttack");

        for (int c = 0; c < 2; c++) {
            if (!this.dh.climbTracker.isGrabbingLadder(c)) {
                Vec3 handPos = this.dh.vrPlayer.vrdata_world_pre.getController(c).getPosition();
                Vector3f handDirection = this.dh.vrPlayer.vrdata_world_pre.getHand(c).getCustomVector(MathUtils.BACK);
                ItemStack itemstack = player.getItemInHand(c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
                Item item = itemstack.getItem();
                boolean isTool = false;
                boolean isSword = false;

                if (!(item instanceof SwordItem || itemstack.is(ItemTags.VIVECRAFT_SWORDS)) && !(item instanceof TridentItem || itemstack.is(ItemTags.VIVECRAFT_SPEARS))) {
                    if (isTool(item)) {
                        isTool = true;
                    }
                } else {
                    isSword = true;
                    isTool = true;
                }

                float weaponLength;
                float entityReachAdd;

                double playerEntityReach = Xplat.getItemEntityReach(3.0, itemstack, c == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                if (BetterCombatHelper.isLoaded()) {
                    // better combat overrides the player reach
                    playerEntityReach = BetterCombatHelper.getItemRange(playerEntityReach, itemstack);
                }

                // subtract arm length and clamp it to 6 meters
                playerEntityReach = Math.min(playerEntityReach, 6.0) - 0.5;

                if (isSword) {
                    weaponLength = 0.6F;
                    // in default situations a total reach of 2.5
                    entityReachAdd = (float) playerEntityReach - weaponLength;
                } else if (isTool) {
                    weaponLength = 0.35F;
                    // in default situations a total reach of 1.55
                    entityReachAdd = (float) playerEntityReach * 0.62F - weaponLength;
                } else if (!itemstack.isEmpty()) {
                    weaponLength = 0.1F;
                    // in default situations a total reach of 0.4
                    entityReachAdd = (float) playerEntityReach * 0.16F - weaponLength;
                } else {
                    weaponLength = 0.0F;
                    entityReachAdd = 0.3F;
                }

                weaponLength *= this.dh.vrPlayer.vrdata_world_pre.worldScale;

                Vector3f weaponEnd = handDirection.mul(weaponLength, new Vector3f());
                this.miningPoint[c] = handPos.add(weaponEnd.x, weaponEnd.y, weaponEnd.z);

                // do speed calc in actual room coords
                Vector3f tip = this.dh.vrPlayer.vrdata_room_pre.getController(c).getPositionF()
                    .add(this.dh.vrPlayer.vrdata_room_pre.getHand(c).getCustomVector(MathUtils.BACK).mul(0.3F));
                this.tipHistory[c].add(tip);

                // at a 0.3m offset on index controllers a speed of 3m/s is an intended smack, 7 m/s is about as high as your arm can go.
                float speed = this.tipHistory[c].averageSpeed(0.33D);
                boolean inAnEntity = false;
                this.canAct[c] = (double) speed > this.speedThresh && !this.lastWeaponSolid[c];

                // Check EntityCollisions first
                boolean entityAct = this.canAct[c];

                // no hitting around corners, to not trigger anticheat
                if (entityAct) {
                    BlockHitResult blockhitresult = this.mc.level.clip(new ClipContext(this.dh.vrPlayer.vrdata_world_pre.hmd.getPosition(), handPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.mc.player));

                    if (blockhitresult.getType() != HitResult.Type.MISS) {
                        entityAct = false;
                    }
                }

                this.attackingPoint[c] = this.constrain(handPos, this.miningPoint[c]);

                Vector3f weaponEntityEnd = handDirection.mul(weaponLength + entityReachAdd, new Vector3f());
                Vec3 weaponTip = handPos.add(weaponEntityEnd.x, weaponEntityEnd.y, weaponEntityEnd.z);
                // no hitting through blocks
                weaponTip = this.constrain(handPos, weaponTip);

                AABB weaponBB = new AABB(handPos, this.attackingPoint[c]);
                AABB weaponTipBB = new AABB(handPos, weaponTip);

                List<Entity> mobs = this.mc.level.getEntities(this.mc.player, weaponTipBB);
                mobs.removeIf((e) -> e instanceof Player);

                // shorter range for players to try to prevent accidental hits
                List<Entity> players = this.mc.level.getEntities(this.mc.player, weaponBB);
                players.removeIf((e) -> !(e instanceof Player));
                mobs.addAll(players);

                for (Entity entity : mobs) {
                    if (entity.isPickable() && entity != this.mc.getCameraEntity().getVehicle()) {
                        if (entityAct) {
                            // Minecraft.getInstance().physicalGuiManager.preClickAction();

                            if (!EpicFightHelper.isLoaded() || !EpicFightHelper.attack()) {
                                // only attack if epic fight didn't trigger
                                this.mc.gameMode.attack(player, entity);
                            } else {
                                // only attack once with epic fight
                                entityAct = false;
                            }
                            this.dh.vr.triggerHapticPulse(c, 1000);
                            this.lastWeaponSolid[c] = true;
                        }
                        inAnEntity = true;
                    }
                }
                // no hitting while climbey climbing
                if (this.dh.climbTracker.isClimbeyClimb() && (!isTool ||
                    (c == 0 && VivecraftVRMod.INSTANCE.keyClimbeyGrab.isDown(ControllerType.RIGHT)) ||
                    (c == 1 && VivecraftVRMod.INSTANCE.keyClimbeyGrab.isDown(ControllerType.LEFT)))) {
                    continue;
                }

                BlockPos blockpos = BlockPos.containing(this.miningPoint[c]);
                BlockState blockstate = this.mc.level.getBlockState(blockpos);

                boolean mineableByItem = this.dh.vrSettings.swordBlockCollision && (itemstack.isCorrectToolForDrops(blockstate) || blockstate.getDestroyProgress(player, player.level(), blockpos) == 1F);

                // block check
                // don't hit blocks with swords or same time as hitting entity
                this.canAct[c] = this.canAct[c] && (!isSword || mineableByItem) && !inAnEntity;

                // every time end of weapon enters a solid for the first time, trace from our previous air position
                // and damage the block it collides with...
                BlockHitResult blockHit = this.mc.level.clip(new ClipContext(this.lastWeaponEndAir[c], this.miningPoint[c], ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.mc.player));

                if (!blockstate.isAir() && blockHit.getType() == HitResult.Type.BLOCK && this.lastWeaponEndAir[c].length() != 0.0D) {

                    this.lastWeaponSolid[c] = true;

                    boolean sameBlock = blockHit.getBlockPos().equals(blockpos); //fix ladders?
                    // don't break climbable blocks
                    boolean protectedBlock = this.dh.vrSettings.realisticClimbEnabled &&
                        (blockstate.getBlock() instanceof LadderBlock ||
                            blockstate.getBlock() instanceof VineBlock ||
                            blockstate.is(BlockTags.VIVECRAFT_CLIMBABLE));

                    if (blockHit.getType() == HitResult.Type.BLOCK && sameBlock && this.canAct[c] && !protectedBlock) {
                        int totalHits = 3;

                        // roomscale hoe interaction
                        if ((item instanceof HoeItem || itemstack.is(ItemTags.VIVECRAFT_HOES) || itemstack.is(ItemTags.VIVECRAFT_SCYTHES)) &&
                            (blockstate.getBlock() instanceof CropBlock ||
                                blockstate.getBlock() instanceof StemBlock ||
                                blockstate.getBlock() instanceof AttachedStemBlock ||
                                blockstate.is(BlockTags.VIVECRAFT_CROPS) ||
                                // check if the item can use the block
                                item.useOn(new UseOnContext(player,
                                    c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                                    blockHit)).shouldSwing()
                            ))
                        {
                            // don't try to break crops with hoes
                            // actually use the item on the block
                            boolean useSuccessful = this.mc.gameMode.useItemOn(player, c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, blockHit).shouldSwing();
                            if (itemstack.is(ItemTags.VIVECRAFT_SCYTHES) && !useSuccessful) {
                                // some scythes just need to be used
                                this.mc.gameMode.useItem(player, c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
                            }
                        }
                        // roomscale brushes
                        else if ((item instanceof BrushItem /*|| itemstack.is(ItemTags.VIVECRAFT_BRUSHES*/)) {
                            ((BrushItem) item).spawnDustParticles(player.level(), blockHit, blockstate, player.getViewVector(0.0F), c == 0 ? player.getMainArm() : player.getMainArm().getOpposite());
                            player.level().playSound(player, blockHit.getBlockPos(), blockstate.getBlock() instanceof BrushableBlock ? ((BrushableBlock) blockstate.getBlock()).getBrushSound() : SoundEvents.BRUSH_GENERIC, SoundSource.BLOCKS);
                            this.mc.gameMode.useItemOn(player, c == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, blockHit);
                        }
                        // roomscale noteblocks
                        else if (blockstate.getBlock() instanceof NoteBlock || blockstate.is(BlockTags.VIVECRAFT_MUSIC_BLOCKS)) {
                            this.mc.gameMode.continueDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());
                        }
                        // roomscale mining
                        else {
                            // faster swings do more damage
                            totalHits = (int) ((double) totalHits + Math.min((double) speed - this.speedThresh, 4.0D));
                            //this.mc.physicalGuiManager.preClickAction();

                            // this will either destroy the block if in creative or set it as the current block.
                            // does nothing in survival if you are already hitting this block.
                            this.mc.gameMode.startDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection());

                            // seems to be the only way to tell it didn't instabreak.
                            if (this.getIsHittingBlock()) {
                                for (int hit = 0; hit < totalHits; hit++) {
                                    // send multiple ticks worth of 'holding left click' to it.
                                    if (this.mc.gameMode.continueDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection())) {
                                        this.mc.particleEngine.crack(blockHit.getBlockPos(), blockHit.getDirection());
                                    }

                                    this.clearBlockHitDelay();

                                    if (!this.getIsHittingBlock()) {
                                        // seems to be the only way to tell if it broke.
                                        break;
                                    }
                                }

                                this.mc.gameMode.destroyDelay = 0;
                            }

                            this.dh.vrPlayer.blockDust(blockHit.getLocation().x, blockHit.getLocation().y, blockHit.getLocation().z, 3 * totalHits, blockpos, blockstate, 0.6F, 1.0F);
                        }

                        this.dh.vr.triggerHapticPulse(c, 250 * totalHits);
                    }
                } else {
                    // reset
                    this.lastWeaponEndAir[c] = this.miningPoint[c];
                    this.lastWeaponSolid[c] = false;
                }
            }
        }

        this.mc.getProfiler().pop();
    }

    private boolean getIsHittingBlock() {
        return this.mc.gameMode.isDestroying();
    }

    private void clearBlockHitDelay() {
        // TODO set destroyTicks to 1 to cancel multiple sound events per hit
        //MCReflection.PlayerController_blockHitDelay.set(Minecraft.getInstance().gameMode, 0);
        // Minecraft.getInstance().gameMode.blockBreakingCooldown = 1;
    }

    /**
     * does a raytrace to find the closest block
     * @param start start of raytrace
     * @param end end of raytrace
     * @return hit position, if a Block was hit, or {@code end} otherwise
     */
    private Vec3 constrain(Vec3 start, Vec3 end) {
        BlockHitResult blockhitresult = this.mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.mc.player));
        return blockhitresult.getType() == HitResult.Type.BLOCK ? blockhitresult.getLocation() : end;
    }

    /**
     *
     * @param player Player that is holding the item
     * @param itemStack held item
     * @return the transparency for held items to indicate attack power or sneaking.
     */
    public static float getItemFade(LocalPlayer player, ItemStack itemStack) {
        float fade = player.getAttackStrengthScale(0.0F) * 0.75F + 0.25F;

        if (player.isShiftKeyDown()) {
            fade = 0.75F;
        }

        if (ClientDataHolderVR.getInstance().swingTracker.lastWeaponSolid[ClientDataHolderVR.IS_MAIN_HAND ? 0 : 1]) {
            fade -= 0.25F;
        }

        if (itemStack != ItemStack.EMPTY) {
            if (player.isBlocking() && player.getUseItem() != itemStack) {
                fade -= 0.25F;
            }

            if (itemStack.getItem() == Items.SHIELD && !player.isBlocking()) {
                fade -= 0.25F;
            }
        }

        if ((double) fade < 0.1D) {
            fade = 0.1F;
        }

        if (fade > 1.0F) {
            fade = 1.0F;
        }

        return fade;
    }
}
