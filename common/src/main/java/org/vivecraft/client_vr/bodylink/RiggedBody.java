package org.vivecraft.client_vr.bodylink;


import com.bhaptics.haptic.models.PositionType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.settings.AutoCalibration;
import org.vivecraft.common.utils.math.Axis;
import org.vivecraft.common.utils.math.Quaternion;

import javax.annotation.Nullable;
import java.util.*;

public class RiggedBody{

    HashMap<PositionType,List<HapticPoint>> hapticPoints = new HashMap<>();
    HashMap<AnchorType, BodyAnchor> anchors = new HashMap<>();

    /**
     * Root bone with coordinates relative to players feet
     * */
    Bone rootBone;
    Bone headBone;

    public RiggedBody(){
        // Create Skeleton

        double hmdHeight = AutoCalibration.getPlayerHeight();

        rootBone = new Bone(null, Vec3.ZERO);
        headBone = new Bone(rootBone,new Vec3(0, hmdHeight, 0));
        // TODO: Make complete Rig

        // Add tracked devices
         // TODO need recalibration event
        BodyAnchor hmdAnchor = new BodyAnchor(new Vec3(0,hmdHeight,0), new Quaternion(), AnchorType.HMD);
        rootBone.addPoint( hmdAnchor );
        anchors.put(AnchorType.HMD, hmdAnchor);
        // TODO Complete Anchor setup

        // Add untracked devices

        // FIXME detect Suit or other devices and register bodypoints
    }

    public void updatePose(VRData vrData){
        // TODO: update full rig using IK
        rootBone.currentRotRel = new Quaternion(Axis.YAW, vrData.getBodyYaw());
        headBone.currentPosRel = vrData.hmd.getPosition().subtract(headBone.basePosition);
        headBone.currentRotRel = new Quaternion(vrData.hmd.getMatrix());

        rootBone.updatePoints();
    }


    public ArrayList<HapticPoint> getHapticPoints(com.bhaptics.haptic.models.PositionType... type) {
        ArrayList<HapticPoint> matches = new ArrayList<>();

        for (PositionType t: type) {
            if(t == PositionType.All){
                matches.clear();
                for(Map.Entry<PositionType, List<HapticPoint>> entry : hapticPoints.entrySet()){
                    matches.addAll(entry.getValue());
                }
                break;
            }

            List<HapticPoint> points = hapticPoints.get(t);
            if(points != null) {
                matches.addAll(points);
            }
        }

        return matches;
    }

    public void clearHapticPoints() {
        hapticPoints.clear();
    }

    double userThiccness = 0.3;

    public void addHapticPoints(Haptics.DeviceType deviceType) {
        if(deviceType == Haptics.DeviceType.X40){
            double dimX = 0.23;
            double dimY = 0.30;
            double dimZ = userThiccness;

            double chestCenterOffset = 1.2;

            for (int side = 0; side < 2; side++) {
                HapticPoint[] points = new HapticPoint[20];
                boolean front = side == 0;
                PositionType type = front ? PositionType.VestFront : PositionType.VestBack;

                for (int x = 0; x < 4; x++) {
                    for (int y = 0; y < 5; y++) {
                        double posX = ((double) x / 3) * dimX - dimX * 0.5;
                        double posY = ((double) x / 4 ) * dimY - dimY * 0.5 + chestCenterOffset;
                        double posZ = (front? -1 : 1) * dimZ * 0.5;

                        int index = x + y * 4;
                        Vec3 posVec = new Vec3(posX, posY, posZ);
                        Quaternion q = new Quaternion();

                        int rotationalIndex = front? x : (7 - x);
                        q=q.rotate(Axis.YAW, (float) (-90 + (rotationalIndex + 0.5) / 8 * 360),true);

                        q=q.rotate(Axis.PITCH, (float)( 90 - ((y+0.5) / 5) * 180), true );

                        HapticPoint point = new HapticPoint(posVec,q,new Haptics.HapticMotor(type,index));
                        point.setAnchor(rootBone, 1.0);

                        points[index] = point;
                    }
                }

                this.hapticPoints.put(type, new ArrayList(Arrays.asList(points)));

            }


        }

    }

    public class Bone{
        Bone parent;
        ArrayList<Bone> children = new ArrayList<>();
        ArrayList<BodyPoint> attachedPoints = new ArrayList<>();

        /**
         * hinge origin relative to parent bone position
         * in the default Pose
         * */
        Vec3 basePosition;
        /**
         * Current Position relative to parent
         * Coordinate System: OpenGL
         * */
        Vec3 currentPosRel;
        Quaternion currentRotRel;

        public Bone(@Nullable Bone parent, Vec3 origin) {
            this.parent = parent;
            this.basePosition = origin;
            this.currentPosRel = origin;
            this.currentRotRel = new Quaternion();

            if(parent != null) {
                parent.children.add(this);
            }
        }

        public void addPoint(BodyPoint point){
            attachedPoints.add(point);
        }

        public Quaternion getAbsRot(){
            Quaternion totalRot = new Quaternion();
            if(parent != null){
                totalRot = parent.getAbsRot();
            }

            return totalRot.multiply(currentRotRel);
        }

        public Vec3 getAbsPos(){
            Vec3 parentPos = Vec3.ZERO;
            Quaternion parentRot = new Quaternion();
            if(parent != null){
                parentPos = parent.getAbsPos();
                parentRot = parent.getAbsRot();
            }
            return parentPos.add( parentRot.multiply(currentPosRel) );
        }


        public void updatePoints(){
            for(BodyPoint b: attachedPoints) {
                b.currentPos = getAbsPos().add( getAbsRot().multiply( b.basePos) );
                b.currentRot = getAbsRot().multiply(b.baseRot);
            }

            for(Bone child : children){
                child.updatePoints();
            }
        }


    }


    abstract class BodyPoint {

        /**
         * Absolute position in player space
         * Coordinate System: OpenGL
         * */
        public Vec3 currentPos;

        /**
         * Current absolute rotation
         * */
        public Quaternion currentRot;



        /*** Position relative to parent bone position and rotation in Calibration Pose
         *  Coordinate System: OpenGL
         * */
        Vec3 basePos;

        /*** Position relative to parent  rotation in Calibration Pose
         *  Coordinate System: OpenGL
         * */
        Quaternion baseRot;

        public BodyPoint(Vec3 basePos, Quaternion baseRot){
            this.basePos = basePos;
            this.baseRot = baseRot;
            this.currentPos = basePos;
            this.currentRot = baseRot;
        }

        abstract boolean isTracked();

        Bone parent;

        /**
         * Returns this points forward vector
         * Coordinate System: OpenGL if mc == false
         * Minecraft if mc == true
         * */
        public Vec3 getNormal( boolean mc ){
            Vec3 n= currentRot.multiply(new Vec3(0,0,-1));
            if(mc){
                return n.yRot(180f);
            }else{
                return n;
            }
        }

        /**
         * Get absolute position in Minecraft Worldspace
         * Coordinate System: Minecraft
         */
        public Vec3 getPosWorld(LocalPlayer player) {
            Vec3 playerPos = player.position();
            Vec3 currentPosMC = new Quaternion(Axis.YAW, 180).multiply(currentPos);
            return playerPos.add(currentPosMC);
        }

        public void setAnchor(Bone parent, double attachPos){
            this.parent = parent;
            // FIXME: Attachment at endpoint of bone
            // Need variable length bones
        }
    }



    public class HapticPoint extends BodyPoint {
        public Haptics.HapticMotor motor;

        public HapticPoint(Vec3 relPos, Quaternion relRot, Haptics.HapticMotor motor) {
            super(relPos, relRot);
            this.motor = motor;
        }

        @Override
        boolean isTracked() {
            return false;
        }


    }

    public class BodyAnchor extends BodyPoint{
        AnchorType type;

        public BodyAnchor(Vec3 relPos, Quaternion relRot, AnchorType type) {
            super(relPos, relRot);
            this.type = type;
        }

        @Override
        boolean isTracked() {
            return true;
        }
    }

    public enum AnchorType {
        HMD, HAND_L, HAND_R, FOOT_L, FOOT_R, BELT, GENERATED, OTHER_SENSOR
    }


    public static RiggedBody instance;
    public static RiggedBody getInstance(){
        return instance;
    }
    static {
        instance = new RiggedBody();

    }

}
