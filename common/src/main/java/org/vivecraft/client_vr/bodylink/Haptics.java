package org.vivecraft.client_vr.bodylink;

import com.bhaptics.haptic.HapticPlayerImpl;
import com.bhaptics.haptic.models.*;
import com.bhaptics.haptic.utils.HapticPlayerCallback;
import com.bhaptics.haptic.utils.LogUtils;
import com.bhaptics.haptic.utils.StringUtils;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.CallbackI;
import org.vivecraft.client.utils.Utils;
import org.vivecraft.common.utils.math.Quaternion;
import org.vivecraft.common.utils.math.Vector3;


import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

public class Haptics {
    static final String appId = "org.vivecraft";
    static final String appName = "Vivecraft bHaptics Integration";


    public enum Animations {

        explosion(1),
        fire(2,2500),
        potion_positive(0),
        potion_negative(0),
        low_health(1,1500),
        hunger(1,2000),
        critical_health(1,1000),
        generic_hit(3),
        zombie_hit(3),
        rain(1,1500),
        consume(1),
        consume_effect(1);

        public int variants;
        public long durationMillis;

        Animations(int variants) {
            this.variants = variants;
        }
        Animations(int variants, long durationMillis){
            this.variants=variants;
            this.durationMillis=durationMillis;
        }
    }

    public static class HapticMotor{
        PositionType positionType;
        int index;
        int intensity;
        int duration;

        public HapticMotor(PositionType positionType, int index) {
            this.positionType = positionType;
            this.index = index;
        }

        public void dot(int intensity, int duration) {
            this.intensity=intensity;
            this.duration=duration;

            if(!connected)
                return;
            String key = "pos"+positionType.ordinal()+"_index"+index;
            //TODO Combine requests
            DotPoint d = new DotPoint(index,intensity);
            ArrayList<DotPoint> list = new ArrayList<>();
            list.add(d);
            bHapticsPlayer.submitDot(key,positionType,list,duration);
        }

        public static void flushBuffered(){

        }
    }


    static HashMap<String, HapticAnimation> regAnimations = new HashMap<>();

    public static class HapticAnimation {
        String baseId;
        int variations;
        long durationMillis = -1;
        boolean looping;
        public boolean isLoop;
        long startTimeStamp = -1;

        Vec3 loopingVec;

        public String getRandomVariant() {
            return baseId + "_" + (int) (Math.random() * variations);
        }

        public void playSingle(boolean layered, Vec3 vec, double scale) {

            if(bHapticsPlayer == null){
                return;
            }
            if (!layered && isPlaying())
                return;
            RotationOption rotationOption;
            Quaternion rot = new Quaternion();
            if (vec != null && !vec.equals(Vec3.ZERO)) {
                rot = Quaternion.createFromToVector(new Vector3(0, 0, -1), new Vector3(vec.normalize()));
            }

            rotationOption = new RotationOption(rot.toEuler().getYaw(), rot.toEuler().getPitch());
            ScaleOption scaleOption = new ScaleOption(scale,1);

            String id = getRandomVariant();
            startTimeStamp=System.currentTimeMillis();

            bHapticsPlayer.submitRegistered(id, baseId, rotationOption,scaleOption);
            //bHapticsPlayer.submitRegistered(id);
        }

        public void playSingle(boolean layered, Vec3 vec) {
            playSingle(layered, vec, 1.0);
        }

        public void setLooping(boolean looping) {
            isLoop = true;
            this.looping = looping;

        }

        public void setLoopingVec(Vec3 loopingVec) {
            this.loopingVec = loopingVec;
        }


        public boolean isPlaying() {
            if(this.startTimeStamp == -1 || this.durationMillis == -1)
                return false;
            else
                return System.currentTimeMillis() < this.startTimeStamp + this.durationMillis;
        }

        public void stopPlaying(){
            startTimeStamp = -1;
            bHapticsPlayer.turnOff(baseId);
        }

        public void tick(){
            if(!isLoop)
                return;
            if (looping && !isPlaying()) {
                playSingle(false, loopingVec);
            }else if (!looping && isPlaying()){
                stopPlaying();
            }
        }


    }


    public static void tick() {
        if(!isConnected())
            return;
        for (HapticAnimation h : regAnimations.values()) {
            h.tick();
        }
    }



    static void loadAnimationFiles() {

        for (Animations animation : Animations.values()) {
            for (int i = 0; i < animation.variants; i++) {
                String fullId = animation.name() + "_" + i;
                String content = Utils.loadAssetAsString("tact/" + fullId + ".tact", false);
                if(content == null){
                    LogManager.getLogger().warn("Missing .tact file " + fullId + ".tact");
                }else{
                    bHapticsPlayer.register(fullId, content);
                }

            }

            HapticAnimation hp = new HapticAnimation();
            hp.baseId = animation.name();
            hp.variations = animation.variants;
            hp.durationMillis = animation.durationMillis;
            regAnimations.put(animation.name(), hp);
        }
    }

    public static HapticAnimation getAnimation(Animations animation){
        return regAnimations.get(animation.name());
    }

    private static HapticPlayerImpl bHapticsPlayer = null;
    private static boolean connected;

    public static boolean isConnected() {
        if (bHapticsPlayer == null)
            return false;
        return connected;
    }

    public static void test(){
        //getAnimation(Animations.explosion).playSingle(true,null);

        //bHapticsPlayer.submitRegistered("explosion_0");
        bHapticsPlayer.submitDot("test", PositionType.VestBack, Arrays.asList(new DotPoint(3, 100)), 200);
    }

    public static void connect() {
        Logger logger = LogManager.getLogger();
        try {
            bHapticsPlayer = new HapticPlayerImpl(appId, appName, true, connected -> {
                Haptics.connected = connected;
                RiggedBody.getInstance().clearHapticPoints();
                if (connected){
                    loadAnimationFiles();
                    // FIXME detect and add haptic devices

                    RiggedBody.getInstance().addHapticPoints(DeviceType.X40);
                }
            });

            logger.info("BHaptics library loaded");
        } catch (Throwable e) {
            logger.error("BHaptics library not found", e);
        }
    }


    public static boolean setLoopState(Animations animation, boolean loop){
        HapticAnimation hapticAnimation = getAnimation(animation);
        if(hapticAnimation == null || hapticAnimation.looping == loop)
            return false;

        hapticAnimation.setLooping(loop);
        return true;
    }
    public static boolean isPlaying(String id) {
        if (!isConnected()) {
            return false;
        }
        return regAnimations.get(id).isPlaying();
    }

    enum DeviceType{
        None, X40
    }

}
