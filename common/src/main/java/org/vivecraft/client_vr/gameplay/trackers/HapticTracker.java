package org.vivecraft.client_vr.gameplay.trackers;

import com.bhaptics.haptic.models.PositionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client_vr.bodylink.Haptics;
import org.vivecraft.client_vr.bodylink.RiggedBody;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client.utils.Debug;
import org.vivecraft.common.utils.math.Axis;
import org.vivecraft.common.utils.math.Quaternion;

import java.awt.*;
import java.util.ArrayList;
import java.util.Random;


public class HapticTracker extends Tracker{
    ArrayList<HapticsModule> modules = new ArrayList<>();

    float lastHealth;
    Random random = new Random();
    int hungerThreshold = 15;

    public HapticTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
        modules.add(new RainModule());
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        return Haptics.isConnected();
    }

    @Override
    public void doProcess(LocalPlayer player) {
        //TODO Find better place for this
        RiggedBody.getInstance().updatePose(ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld());

        float thresholdLowHealth = 5;
        float thresholdCriticalHealth = 2;

        Haptics.setLoopState(Haptics.Animations.fire, player.isOnFire());
        Haptics.setLoopState(Haptics.Animations.potion_positive, hasPotionPositive(player));
        Haptics.setLoopState(Haptics.Animations.potion_negative, hasPotionNegative(player));
        Haptics.setLoopState(Haptics.Animations.low_health, player.getHealth() < thresholdLowHealth && !(player.getHealth() < thresholdCriticalHealth) );
        Haptics.setLoopState(Haptics.Animations.critical_health, player.getHealth() < thresholdCriticalHealth );
        Haptics.setLoopState(Haptics.Animations.rain, isInRain(player));

        for (HapticsModule h: modules) {
            if(h.enabled){
                h.tick(player);
            }
        }

        if(player.getHealth() != lastHealth){
            float damage = lastHealth - player.getHealth();
            if(damage > 0){
                handleHit(null,damage);
            }
            lastHealth = player.getHealth();
        }

        doHunger(player);

        Haptics.tick();
    }


    void doHunger(LocalPlayer player){
        int food=player.getFoodData().getFoodLevel();
        if(food < hungerThreshold){
            float foodPerc = (float) food / 20;
            if(random.nextInt(20 * 3 + (int)(foodPerc * 30 * 20) ) == 0){
                Haptics.getAnimation(Haptics.Animations.hunger).playSingle(false,null);
            }
        }
    }


    boolean hasPotionPositive(LocalPlayer player){
        for( MobEffectInstance effect : player.getActiveEffects()){
            if( effect.getEffect().isBeneficial()
                && !effect.isAmbient() ){
                return true;
            }
        }
        return false;
    }
    boolean hasPotionNegative(LocalPlayer player){
        for( MobEffectInstance effect : player.getActiveEffects()){
            if( effect.getEffect().getCategory() == MobEffectCategory.HARMFUL
                    && !effect.isAmbient() ){
                return true;
            }
        }
        return false;
    }

    boolean isInRain(LocalPlayer player){
        BlockPos blockpos = player.blockPosition();
        return player.clientLevel.isRainingAt(blockpos) || player.clientLevel.isRainingAt(new BlockPos(blockpos.getX(), (int)player.getBoundingBox().maxY, blockpos.getZ()));
    }

    public void handleExplode(ClientboundExplodePacket packetIn) {
        double maxExplosionDist = 5;
        Vec3 exposionPos = new Vec3(packetIn.getX(),packetIn.getY(),packetIn.getZ());
        double explosionDist = exposionPos.subtract(mc.player.position()).length();
        if(explosionDist < maxExplosionDist){
            double distFactor = 1.0 - (explosionDist / maxExplosionDist);
            Haptics.getAnimation(Haptics.Animations.explosion).playSingle(true,null, distFactor);
        }
    }

    public void handleHit(DamageSource damageSrc, float damageAmount){
        //TODO Always generic. Need custom server packet.
        Vec3 dmgVec = new Vec3(1,0,1).multiply(mc.player.getDeltaMovement().scale(-1.0)).normalize();
        dmgVec=new Quaternion(Axis.YAW,mc.player.yHeadRot +180).multiply(dmgVec);

        Haptics.getAnimation(Haptics.Animations.generic_hit).playSingle(true,dmgVec);
    }

    public void handleEat(ItemStack itemStack){
        if(itemStack.isEdible() && itemStack.getItem().getFoodProperties() != null){
                if(itemStack.getItem().getFoodProperties().getEffects().isEmpty()) {
                    Haptics.getAnimation(Haptics.Animations.consume).playSingle(true,null);
                }else{
                    Haptics.getAnimation(Haptics.Animations.consume_effect).playSingle(true,null);
                }
        }
    }



    abstract static class HapticsModule{
        boolean enabled = false;
        abstract void tick(LocalPlayer player);

    }

    class RainModule extends HapticsModule{
        // Range: 0 to 1
        double minAngle = 0;
        double dropChanceThreshold = 0.2;


        Random random = new Random();

        public RainModule() {
            super();
            enabled = false;
        }

        @Override
        void tick(LocalPlayer player) {

            if (!player.clientLevel.isRaining())
                return;

            boolean isSnow = player.clientLevel.getBiome(player.blockPosition()).value().coldEnoughToSnow(player.blockPosition());

            // Terminal Velocity of rain in m/s
            Vec3 rainFall = new Vec3(0,-9,0);

            // Add inverse player motion for relative motion
            rainFall = rainFall.subtract(player.getDeltaMovement());

            Vec3 rainDir = rainFall.normalize();

            ArrayList<RiggedBody.HapticPoint> points = RiggedBody.getInstance().getHapticPoints(PositionType.All);

            //Debug d = Debug .get("hapticsrain");

            for(RiggedBody.HapticPoint p : points){
                // Check Occlusion
                if(!player.clientLevel.isRainingAt(player.blockPosition())){
                    continue;
                }

                Vec3 normal = p.getNormal(true);

                //d.drawVector("vec:"+p.hashCode(),p.getPosWorld(player), normal, Color.red);

                double exposure = normal.dot(rainDir.reverse());

                if( exposure < minAngle ){
                    // cull backface
                    exposure = 0;
                }

                double snowFactor = isSnow? 2.0 : 1.0;

                if(Math.abs(random.nextGaussian()) * exposure > dropChanceThreshold * snowFactor) {
                    int intensity = 10; // TODO Randomize
                    int duration = 10;
                    p.motor.dot(intensity, duration);
                }
            }
        }


    }
}
