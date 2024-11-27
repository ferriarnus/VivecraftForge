package org.vivecraft.client_vr;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.gameplay.trackers.*;
import org.vivecraft.client_vr.menuworlds.MenuWorldRenderer;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.VRRenderer;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.VRFirstPersonArmSwing;
import org.vivecraft.client_vr.settings.VRSettings;

public class ClientDataHolderVR {

    public static final ModelResourceLocation THIRD_PERSON_CAMERA_MODEL = new ModelResourceLocation("vivecraft", "camcorder", "");
    public static final ModelResourceLocation THIRD_PERSON_CAMERA_DISPLAY_MODEL = new ModelResourceLocation("vivecraft", "camcorder_display", "");

    public static boolean KAT_VR;
    public static boolean INFINADECK;

    public static boolean KIOSK;
    public static boolean VIEW_ONLY;

    public static boolean IS_MAIN_HAND;
    public static boolean IS_FP_HAND;

    private static ClientDataHolderVR INSTANCE;

    public VRPlayer vrPlayer;
    public MCVR vr;
    public VRRenderer vrRenderer;
    public MenuWorldRenderer menuWorldRenderer;
    public BackpackTracker backpackTracker = new BackpackTracker(Minecraft.getInstance(), this);
    public BowTracker bowTracker = new BowTracker(Minecraft.getInstance(), this);
    public SwimTracker swimTracker = new SwimTracker(Minecraft.getInstance(), this);
    public EatingTracker autoFood = new EatingTracker(Minecraft.getInstance(), this);
    public JumpTracker jumpTracker = new JumpTracker(Minecraft.getInstance(), this);
    public SneakTracker sneakTracker = new SneakTracker(Minecraft.getInstance(), this);
    public ClimbTracker climbTracker = new ClimbTracker(Minecraft.getInstance(), this);
    public RunTracker runTracker = new RunTracker(Minecraft.getInstance(), this);
    public RowTracker rowTracker = new RowTracker(Minecraft.getInstance(), this);
    public TeleportTracker teleportTracker = new TeleportTracker(Minecraft.getInstance(), this);
    public SwingTracker swingTracker = new SwingTracker(Minecraft.getInstance(), this);
    public HorseTracker horseTracker = new HorseTracker(Minecraft.getInstance(), this);
    public VehicleTracker vehicleTracker = new VehicleTracker(Minecraft.getInstance(), this);
    public InteractTracker interactTracker = new InteractTracker(Minecraft.getInstance(), this);
    public CrawlTracker crawlTracker = new CrawlTracker(Minecraft.getInstance(), this);
    public CameraTracker cameraTracker = new CameraTracker(Minecraft.getInstance(), this);

    public VRSettings vrSettings;
    public boolean integratedServerLaunchInProgress = false;
    public boolean grabScreenShot = false;
    public String incorrectGarbageCollector = "";
    public long frameIndex = 0L;

    public RenderPass currentPass;
    public boolean isFirstPass;

    // if the main/offhand should be rendered as menu hands
    public boolean menuHandOff;
    public boolean menuHandMain;

    public int tickCounter;

    public VRFirstPersonArmSwing swingType = VRFirstPersonArmSwing.Attack;

    // showed chat notifications
    public boolean showedUpdateNotification;
    public boolean showedStencilMessage;
    public boolean showedFbtCalibrationNotification;

    public static ClientDataHolderVR getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClientDataHolderVR();
        }
        return INSTANCE;
    }

    /**
     * checks if the given arm side is currently a menu hand
     * @param arm LEFT/RIGHT arm to check
     * @return if the arm is a menu hnd
     */
    public boolean isMenuHand(HumanoidArm arm) {
        if (arm == HumanoidArm.LEFT) {
            return this.vrSettings.reverseHands ? this.menuHandMain : this.menuHandOff;
        } else {
            return this.vrSettings.reverseHands ? this.menuHandOff : this.menuHandMain;
        }
    }
}
