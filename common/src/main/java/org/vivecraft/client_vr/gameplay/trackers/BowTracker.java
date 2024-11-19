package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client.utils.ScaleHelper;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.extensions.PlayerExtension;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.network.packet.c2s.DrawPayloadC2S;

public class BowTracker extends Tracker {
    private double currentDraw;
    public boolean isDrawing;
    private boolean pressed;
    private boolean canDraw;

    // when the arrow was started drawing, to handle charged shots
    public long startDrawTime;
    private double maxDraw;
    private static final long maxDrawMillis = 1100L;
    private static final double notchDotThreshold = 20.0D;
    private Vec3 aim;

    // when the arrow was nocked,
    private float tsNotch = 0.0F;
    private int hapCounter = 0;
    private int lastHapStep = 0;

    public BowTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    public Vec3 getAimVector() {
        return this.aim;
    }

    public float getDrawPercent() {
        return (float) (this.currentDraw / this.maxDraw);
    }

    public boolean isNotched() {
        return this.canDraw || this.isDrawing;
    }

    public boolean isCharged() {
        return Util.getMillis() - this.startDrawTime >= maxDrawMillis;
    }

    public static boolean isBow(ItemStack itemStack) {
        if (itemStack == ItemStack.EMPTY) {
            return false;
        } else if (ClientDataHolderVR.getInstance().vrSettings.bowMode == VRSettings.BowMode.OFF) {
            return false;
        } else if (ClientDataHolderVR.getInstance().vrSettings.bowMode == VRSettings.BowMode.VANILLA) {
            return itemStack.getItem() == Items.BOW;
        } else {
            return itemStack.getItem().getUseAnimation(itemStack) == UseAnim.BOW && !itemStack.is(org.vivecraft.client_vr.ItemTags.VIVECRAFT_BOW_EXCLUSION);
        }
    }

    public static boolean isHoldingBow(LivingEntity entity, InteractionHand hand) {
        return !ClientDataHolderVR.getInstance().vrSettings.seated && isBow(entity.getItemInHand(hand));
    }

    public static boolean isHoldingBowEither(LivingEntity entity) {
        return isHoldingBow(entity, InteractionHand.MAIN_HAND) || isHoldingBow(entity, InteractionHand.OFF_HAND);
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        if (player == null) {
            return false;
        } else if (this.mc.gameMode == null) {
            return false;
        } else if (!player.isAlive()) {
            return false;
        } else if (player.isSleeping()) {
            return false;
        } else {
            return isHoldingBow(player, InteractionHand.MAIN_HAND) || isHoldingBow(player, InteractionHand.OFF_HAND);
        }
    }

    @Override
    public void reset(LocalPlayer player) {
        this.isDrawing = false;
        this.canDraw = false;
    }

    @Override
    public EntryPoint getEntryPoint() {
        return EntryPoint.SPECIAL_ITEMS;
    }

    @Override
    public void doProcess(LocalPlayer player) {
        VRData vrData = this.dh.vrPlayer.vrdata_world_render;

        if (vrData == null) {
            vrData = this.dh.vrPlayer.vrdata_world_pre;
        }

        if (this.dh.vrSettings.seated) {
            this.aim = vrData.getController(0).getCustomVector(new Vec3(0.0D, 0.0D, 1.0D));
        } else {
            boolean lastPressed = this.pressed;
            boolean lastCanDraw = this.canDraw;

            this.maxDraw = (double) this.mc.player.getBbHeight() * 0.22D;

            // this is meant to be relative to the base Bb height, not the scaled one
            this.maxDraw /= ScaleHelper.getEntityBbScale(player, this.mc.getFrameTime());

            int bowHand = 1;
            int arrowHand = 0;

            // reverse bow hands
            if (this.dh.vrSettings.reverseShootingEye) {
                bowHand = 0;
                arrowHand = 1;
            }

            // these are wrong since this is called every frame but should be fine so long as they're only compared to each other.
            Vec3 arrowPos = vrData.getController(arrowHand).getPosition();
            Vec3 bowPos = vrData.getController(bowHand).getPosition();
            //

            double controllersDist = bowPos.distanceTo(arrowPos);
            Vec3 up = new Vec3(0.0D, 1.0D * vrData.worldScale, 0.0D);

            Vec3 stringPos = vrData.getHand(bowHand).getCustomVector(up).scale(this.maxDraw * 0.5D).add(bowPos);

            double notchDist = arrowPos.distanceTo(stringPos);

            this.aim = arrowPos.subtract(bowPos).normalize();

            Vec3 arrowAim = vrData.getController(arrowHand).getCustomVector(new Vec3(0.0D, 0.0D, -1.0D));

            Vec3 bowAim = vrData.getHand(bowHand).getCustomVector(new Vec3(0.0D, -1.0D, 0.0D));

            double controllersDot = Math.toDegrees(Math.acos(bowAim.dot(arrowAim)));

            this.pressed = VivecraftVRMod.INSTANCE.keyVRInteract.isDown(ControllerType.values()[arrowHand]);

            float notchDistThreshold = 0.15F * vrData.worldScale;
            boolean main = isHoldingBow(player, InteractionHand.MAIN_HAND);

            InteractionHand hand = main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

            ItemStack ammo = ItemStack.EMPTY;
            ItemStack bow = ItemStack.EMPTY;

            if (main) {
                // autofind ammo, this only works for items that extend ProjectileWeaponItem
                bow = player.getMainHandItem();
                ammo = player.getProjectile(bow);
            } else {
                // brig your own ammo
                if (player.getMainHandItem().is(ItemTags.ARROWS)) {
                    ammo = player.getMainHandItem();
                }
                bow = player.getOffhandItem();
            }

            int stage0 = bow.getUseDuration();
            int stage1 = bow.getUseDuration() - 15;
            int stage2 = 0;

            if (ammo != ItemStack.EMPTY &&
                notchDist <= notchDistThreshold &&
                controllersDot <= notchDotThreshold) {
                // can draw
                if (!this.canDraw) {
                    this.startDrawTime = Util.getMillis();
                }

                this.canDraw = true;
                this.tsNotch = (float) Util.getMillis();

                if (!this.isDrawing) {
                    ((PlayerExtension) player).vivecraft$setItemInUseClient(bow, hand);
                    ((PlayerExtension) player).vivecraft$setItemInUseRemainingClient(stage0);
                    //Minecraft.getInstance().physicalGuiManager.preClickAction();
                }
            } else if (!this.isDrawing && (float) Util.getMillis() - this.tsNotch > 500.0F) {
                this.canDraw = false;

                ((PlayerExtension) player).vivecraft$setItemInUseClient(ItemStack.EMPTY, hand); // client draw only
            }

            if (!this.isDrawing && this.canDraw && this.pressed && !lastPressed) {
                // draw
                this.isDrawing = true;
                //Minecraft.getInstance().physicalGuiManager.preClickAction();
                this.mc.gameMode.useItem(player, hand); // server
            }

            if (this.isDrawing && !this.pressed && lastPressed && (double) this.getDrawPercent() > 0.0D) {
                // fire!
                this.dh.vr.triggerHapticPulse(arrowHand, 500);
                this.dh.vr.triggerHapticPulse(bowHand, 3000);
                this.mc.getConnection()
                    .send(ClientNetworking.createServerPacket(new DrawPayloadC2S(this.getDrawPercent())));

                // TODO: for REVERSE_BOW
                // ClientNetworking.sendActiveHand((byte) arrowHand);

                this.mc.gameMode.releaseUsingItem(player);

                // reset to 0, in case user switches modes.
                this.mc.getConnection()
                    .send(ClientNetworking.createServerPacket(new DrawPayloadC2S(0.0F)));
                this.isDrawing = false;
            }

            if (!this.pressed) {
                this.isDrawing = false;
            }

            if (!this.isDrawing && this.canDraw && !lastCanDraw) {
                //notch
                this.dh.vr.triggerHapticPulse(arrowHand, 800);
                this.dh.vr.triggerHapticPulse(bowHand, 800);
            }

            if (this.isDrawing) {
                this.currentDraw = (controllersDist - (double) notchDistThreshold) / vrData.worldScale;

                if (this.currentDraw > this.maxDraw) {
                    this.currentDraw = this.maxDraw;
                }

                int hapStrength = 0;

                if (this.getDrawPercent() > 0.0F) {
                    hapStrength = (int) (this.getDrawPercent() * 500.0F) + 700;
                }

                ((PlayerExtension) player).vivecraft$setItemInUseClient(bow, hand); // client draw only

                double drawPercent = this.getDrawPercent();

                if (drawPercent >= 1.0D) {
                    ((PlayerExtension) player).vivecraft$setItemInUseRemainingClient(stage2);
                } else if (drawPercent > 0.4D) {
                    ((PlayerExtension) player).vivecraft$setItemInUseRemainingClient(stage1);
                } else {
                    ((PlayerExtension) player).vivecraft$setItemInUseRemainingClient(stage0);
                }

                int hapStep = (int) (drawPercent * 4.0D * 4.0D * 3.0D);

                if (hapStep % 2 == 0 && this.lastHapStep != hapStep) {
                    this.dh.vr.triggerHapticPulse(arrowHand, hapStrength);

                    if (drawPercent == 1.0D) {
                        this.dh.vr.triggerHapticPulse(bowHand, hapStrength);
                    }
                }

                if (this.isCharged() && this.hapCounter % 4 == 0) {
                    this.dh.vr.triggerHapticPulse(bowHand, 200);
                }

                this.lastHapStep = hapStep;
                this.hapCounter++;
            } else {
                this.hapCounter = 0;
                this.lastHapStep = 0;
            }
        }
    }
}
