package org.vivecraft.client_vr.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TorchBlock;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vivecraft.client.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.ItemTags;
import org.vivecraft.client_vr.gameplay.trackers.ClimbTracker;
import org.vivecraft.client_vr.gameplay.trackers.SwingTracker;
import org.vivecraft.client_vr.gameplay.trackers.TelescopeTracker;

public class VivecraftItemRendering {
    private static final ClientDataHolderVR DH = ClientDataHolderVR.getInstance();

    /**
     * determines how the given ItemStack should be rendered
     * @param itemStack ItemStack to identify
     * @param player Player holding the ItemStack
     * @param itemRenderer ItemRenderer to query the item model from
     * @return ItemTransformType that specifies how the item should be rendered
     */
    public static VivecraftItemTransformType getTransformType(ItemStack itemStack, AbstractClientPlayer player, ItemRenderer itemRenderer) {
        VivecraftItemTransformType itemTransformType = VivecraftItemTransformType.Item;
        Item item = itemStack.getItem();

        if (itemStack.getUseAnimation() == UseAnim.EAT || itemStack.getUseAnimation() == UseAnim.DRINK) {
            itemTransformType = VivecraftItemTransformType.Noms;
        } else if (item instanceof BlockItem) {
            Block block = ((BlockItem) item).getBlock();

            if (block instanceof TorchBlock) {
                itemTransformType = VivecraftItemTransformType.Block_Stick;
            } else {
                BakedModel bakedmodel = itemRenderer.getModel(itemStack, player.level(), player, 0);

                if (bakedmodel.isGui3d()) {
                    itemTransformType = VivecraftItemTransformType.Block_3D;
                } else {
                    itemTransformType = VivecraftItemTransformType.Block_Item;
                }
            }
        } else if (item instanceof MapItem || itemStack.is(ItemTags.VIVECRAFT_MAPS)) {
            itemTransformType = VivecraftItemTransformType.Map;
        } else if (itemStack.getUseAnimation() == UseAnim.BOW && !itemStack.is(ItemTags.VIVECRAFT_BOW_EXCLUSION)) {
            itemTransformType = VivecraftItemTransformType.Bow_Seated;

            if (DH.bowTracker.isActive((LocalPlayer) player)) {
                if (DH.bowTracker.isDrawing) {
                    itemTransformType = VivecraftItemTransformType.Bow_Roomscale_Drawing;
                } else {
                    itemTransformType = VivecraftItemTransformType.Bow_Roomscale;
                }
            }
        } else if (itemStack.getUseAnimation() == UseAnim.TOOT_HORN) {
            itemTransformType = VivecraftItemTransformType.Horn;
        } else if (item instanceof SwordItem || itemStack.is(ItemTags.VIVECRAFT_SWORDS)) {
            itemTransformType = VivecraftItemTransformType.Sword;
        } else if (item instanceof ShieldItem || itemStack.is(ItemTags.VIVECRAFT_SHIELDS)) {
            itemTransformType = VivecraftItemTransformType.Shield;
        } else if (item instanceof TridentItem || itemStack.is(ItemTags.VIVECRAFT_SPEARS)) {
            itemTransformType = VivecraftItemTransformType.Spear;
        } else if (item instanceof CrossbowItem || itemStack.is(ItemTags.VIVECRAFT_CROSSBOWS)) {
            itemTransformType = VivecraftItemTransformType.Crossbow;
        } else if (item instanceof CompassItem || item == Items.CLOCK || itemStack.is(ItemTags.VIVECRAFT_COMPASSES)) {
            itemTransformType = VivecraftItemTransformType.Compass;
        } else if (SwingTracker.isTool(item)) {
            itemTransformType = VivecraftItemTransformType.Tool;

            if (item instanceof FoodOnAStickItem || item instanceof FishingRodItem || itemStack.is(ItemTags.VIVECRAFT_FISHING_RODS)) {
                itemTransformType = VivecraftItemTransformType.Tool_Rod;
            }
        } else if (TelescopeTracker.isTelescope(itemStack)) {
            itemTransformType = VivecraftItemTransformType.Telescope;
        }
        return itemTransformType;
    }

    /**
     * modifies the given poseStack, so that a third person item transform is positioned in the hand
     * @param poseStack PoseStack to modify
     * @param itemTransformType itemTransformType of the item
     * @param mainHand if the item is in the main hand or not
     * @param player Player holding the item
     * @param equippedProgress equip progress of the item
     * @param partialTick current partial tick
     * @param itemStack ItemStack the player is holding
     * @param hand which hand the item is held in
     */
    public static void applyThirdPersonItemTransforms(PoseStack poseStack, VivecraftItemTransformType itemTransformType, boolean mainHand, AbstractClientPlayer player, float equippedProgress, float partialTick, ItemStack itemStack, InteractionHand hand) {
        // TODO make this work with stuff by default
        int k = mainHand ? 1 : -1;
        // scale so the item is slightly bigger than the hand
        float scale = 0.525F;
        float translateX = 0.0F;
        float translateY = 0.05F;
        float translateZ = 0.0F;
        boolean useLeftHandModelinLeftHand = false;

        // claws need the actual hand size
        if (ClimbTracker.isClaws(itemStack)) {
            scale = 0.4F;
        }

        // poseStack.mulPose(preRotation);
        poseStack.translate(translateX, translateY, translateZ);
        // poseStack.mulPose(rotation);
        poseStack.scale(scale, scale, scale);
    }

    /**
     * modifies the given poseStack, so that a first person item transform is positioned in the hand
     * @param poseStack PoseStack to modify
     * @param itemTransformType itemTransformType of the item
     * @param mainHand if the item is in the main hand or not
     * @param player Player holding the item
     * @param equippedProgress equip progress of the item
     * @param partialTick current partial tick
     * @param itemStack ItemStack the player is holding
     * @param hand which hand the item is held in
     */
    public static void applyFirstPersonItemTransforms(PoseStack poseStack, VivecraftItemTransformType itemTransformType, boolean mainHand, AbstractClientPlayer player, float equippedProgress, float partialTick, ItemStack itemStack, InteractionHand hand) {

        float gunAngle = (float) DH.vr.getGunAngle();

        // defaults
        float scale = 0.7F;
        float translateX = -0.05F;
        float translateY = 0.005F;
        float translateZ = 0.0F;

        Quaternionf rotation = new Quaternionf();
        Quaternionf preRotation = new Quaternionf();
        rotation.mul(Axis.XP.rotationDegrees(-110.0F + gunAngle));

        switch (itemTransformType) {
            case Bow_Seated -> {
                rotation.mul(Axis.XP.rotationDegrees(90.0F - gunAngle));
                translateY += -0.1F;
                translateZ += 0.1F;
            }
            case Bow_Roomscale -> {
                preRotation.set(rotation);
                rotation.identity();
                translateX -= 0.0225F;
                translateY -= 0.25F;
                translateZ += 0.025F + 0.03F * gunAngle / 40.0F;
                scale = 1.0F;
            }
            case Bow_Roomscale_Drawing -> {
                // here there be dragons
                // reset
                rotation.identity();
                scale = 1.0F;

                int bowHand = 1;

                // bow in main hand
                if (DH.vrSettings.reverseShootingEye) {
                    bowHand = 0;
                }

                Vector3fc aim = DH.bowTracker.getAimVector();

                Vector3f localBack = DH.vrPlayer.vrdata_world_render.getHand(bowHand).getCustomVector(MathUtils.BACK);

                float aimPitch = (float) Math.toDegrees(Math.asin(aim.y() / aim.length()));
                float yaw = (float) Math.toDegrees(Math.atan2(aim.x(), aim.z()));

                // we want the normal to aim aiming plane, but vertical.
                Vector3f aimHorizontal = new Vector3f(aim.x(), 0.0F, aim.z());

                Vector3f pAim2 = new Vector3f();
                // angle between controller up and aim, just for ortho check
                float aimProj = localBack.dot(aimHorizontal);

                // check to make sure we aren't holding the bow perfectly straight up.
                if (aimProj != 0.0F) {
                    // projection of l_controller_up onto aim vector ... why is there no multiply?
                    aimHorizontal.mul(aimProj, pAim2);
                }

                Vector3f proj = localBack.sub(pAim2, new Vector3f()).normalize();
                // angle between our projection and straight up (the default bow render pos.)
                float dot = proj.dot(MathUtils.UP);

                // angle sign test, negative is left roll
                float dot2 = aimHorizontal.dot(proj.cross(MathUtils.UP, new Vector3f()));

                float angle;
                if (dot2 < 0.0F) {
                    angle = (float) -Math.acos(dot);
                } else {
                    angle = (float) Math.acos(dot);
                }

                // calculate bow model roll.
                float roll = 180F / Mth.PI * angle;

                if (DH.bowTracker.isCharged()) {
                    // bow jitter
                    long j = Util.getMillis() - DH.bowTracker.startDrawTime;
                    translateX += 0.003F * (float) Math.sin((double) j);
                }

                poseStack.translate(0.0F, 0.0F, 0.1F);
                // un-do controller tracking
                poseStack.last().pose()
                    .mul(DH.vrPlayer.vrdata_world_render.getController(bowHand).getMatrix().transpose());

                // rotate in world coords
                rotation.mul(Axis.YP.rotationDegrees(yaw));
                rotation.mul(Axis.XP.rotationDegrees(-aimPitch));
                rotation.mul(Axis.ZP.rotationDegrees(-roll));
                rotation.mul(Axis.ZP.rotationDegrees(180.0F));

                poseStack.last().pose().rotate(rotation);

                rotation = Axis.YP.rotationDegrees(180.0F);
                rotation.mul(Axis.XP.rotationDegrees(160.0F));

                translateX += 0.125F;
                translateY += 0.1225F;
                translateZ += 0.16F;
            }
            case Crossbow -> {
                rotation = Axis.YP.rotationDegrees(10.0F);
                translateX += 0.01F;
                translateZ -= 0.02F;
                translateY -= 0.02F;
                scale = 0.5F;
            }
            case Map -> {
                rotation = Axis.XP.rotationDegrees(-45.0F);
                translateX = 0.0F;
                translateY = 0.16F;
                translateZ = -0.075f;
                scale = 0.75F;
            }
            case Noms -> {
                rotation = Axis.ZP.rotationDegrees(180.0F);
                rotation.mul(Axis.XP.rotationDegrees(-135.0F));
                translateX += 0.08F;
                // eating jitter
                translateZ += 0.02F + 0.006F * (float) Math.sin(player.getUseItemRemainingTicks());
                scale = 0.4F;
            }
            case Item, Block_Item -> {
                rotation = Axis.ZP.rotationDegrees(180.0F);
                rotation.mul(Axis.XP.rotationDegrees(-135.0F));
                translateX += 0.08F;
                translateZ += -0.08F;
                scale = 0.4F;
            }
            case Compass -> {
                rotation = Axis.YP.rotationDegrees(90.0F);
                rotation.mul(Axis.XP.rotationDegrees(25.0F));
                scale = 0.4F;
            }
            case Block_3D -> {
                translateX += 0.05F;
                translateZ -= 0.1F;
                scale = 0.3F;
            }
            case Block_Stick -> {
                rotation = Axis.XP.rotationDegrees(-45.0F + gunAngle);
                translateY += -0.105F + 0.06F * gunAngle / 40.0F;
                translateZ -= 0.1F;
            }
            case Horn -> {
                rotation = Axis.XP.rotationDegrees(-45.0F + gunAngle);
                translateY += -0.105F + 0.06F * gunAngle / 40.0F;
                translateZ -= 0.1F;
                scale = 0.3F;
            }
            case Shield -> {
                int side = mainHand ? 1 : -1;
                if (DH.vrSettings.reverseHands) {
                    side *= -1;
                }

                scale = 0.4F;

                // move so that the gui is not obstructed
                translateY += 0.18F;

                if (side == 1) {
                    rotation.mul(Axis.XP.rotationDegrees(105.0F - gunAngle));
                    translateX += 0.11F;
                } else {
                    rotation.mul(Axis.XP.rotationDegrees(115.0F - gunAngle));
                    translateX -= 0.015F;
                }

                translateZ += 0.1F;

                if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == hand) {
                    rotation.mul(Axis.XP.rotationDegrees(side * 5F));
                    rotation.mul(Axis.ZP.rotationDegrees(-5F));

                    if (side == 1) {
                        translateX += 0.04F;
                        translateY -= 0.12F;
                        translateZ -= 0.1F;
                    } else {
                        translateX += 0.19F;
                        translateY -= 0.12F;
                        translateZ -= 0.11F;
                    }

                    // use shield animation
                    if (player.isBlocking()) {
                        rotation.mul(Axis.YP.rotationDegrees(side * 90.0F));
                    } else {
                        rotation.mul(Axis.YP.rotationDegrees((1.0F - equippedProgress) * side * 90.0F));
                    }
                }
                rotation.mul(Axis.YP.rotationDegrees(side * -90.0F));
            }
            case Spear -> {
                rotation.identity();
                translateX -= 0.135F;
                translateZ += 0.575F;
                scale = 0.6F;

                float progress = 0.0F;
                boolean charging = false;
                int riptideLevel = 0;

                if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == hand) {
                    charging = true;
                    riptideLevel = EnchantmentHelper.getRiptide(itemStack);

                    if (riptideLevel <= 0 || player.isInWaterOrRain()) {
                        progress = itemStack.getUseDuration() - (player.getUseItemRemainingTicks() - partialTick + 1.0F);

                        if (progress > TridentItem.THROW_THRESHOLD_TIME) {
                            float rotationProgress = progress - TridentItem.THROW_THRESHOLD_TIME;
                            progress = TridentItem.THROW_THRESHOLD_TIME;

                            if (riptideLevel > 0 && player.isInWaterOrRain()) {
                                // rotation when charged, use item use, to start at 0
                                poseStack.mulPose(Axis.ZP.rotationDegrees(-rotationProgress * 10.0F * riptideLevel));
                            }

                            // every 4 frames at 90fps equals every 44ms
                            // TODO: this should not be FPS bound. do it in tick maybe? then it would be every 50ms
                            if (DH.frameIndex % 4L == 0L) {
                                // haptics when charged
                                DH.vr.triggerHapticPulse(mainHand ? 0 : 1, 200);
                            }

                            // charge jitter
                            translateX += 0.003F * (float) Math.sin(Util.getMillis());
                        }
                    }
                }

                if (player.isAutoSpinAttack()) {
                    riptideLevel = 5;
                    translateZ -= 0.15F;
                    poseStack.mulPose(Axis.ZP.rotationDegrees(
                        (-DH.tickCounter * 10 * riptideLevel) % 360 - partialTick * 10.0F * riptideLevel));
                    charging = true;
                }

                if (!charging) {
                    translateY += 0.2F * gunAngle / 40.0F;
                    rotation.mul(Axis.XP.rotationDegrees(gunAngle));
                }

                rotation.mul(Axis.XP.rotationDegrees(-65.0F));
                translateZ += -0.75F + progress / 10.0F * 0.25F;
            }
            case Tool_Rod -> {
                rotation.mul(Axis.XP.rotationDegrees(40.0F));
                translateY += -0.02F + gunAngle / 40.0F * 0.1F;
                translateX += 0.05F;
                translateZ -= 0.15F;
                scale = 0.8F;
            }
            case Tool -> {
                if (itemStack.getItem() instanceof ArrowItem || itemStack.is(ItemTags.VIVECRAFT_ARROWS)) {
                    preRotation = Axis.ZP.rotationDegrees(-180.0F);
                    rotation.mul(Axis.XP.rotationDegrees(-gunAngle));
                }
            }
            case Telescope -> {
                preRotation.identity();
                rotation.identity();
                scale *= 0.625F;
                translateX = (mainHand ? -0.03F : 0.03F) * scale;
                translateY = 0.0F;
                translateZ = -0.1F * scale;
            }
            // case Sword -> {}
            default -> {}
        }

        poseStack.mulPose(preRotation);
        poseStack.translate(translateX, translateY, translateZ);
        poseStack.mulPose(rotation);
        poseStack.scale(scale, scale, scale);
    }

    public enum VivecraftItemTransformType {
        Item,
        Block_3D,
        Block_Stick,
        Block_Item,
        Shield,
        Sword,
        Tool,
        Tool_Rod,
        Bow_Seated,
        Bow_Roomscale,
        Bow_Roomscale_Drawing,
        Spear,
        Map,
        Noms,
        Crossbow,
        Telescope,
        Compass,
        Horn
    }
}
