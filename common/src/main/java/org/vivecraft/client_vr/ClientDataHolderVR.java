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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

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

    // list of all registered trackers
    private final List<Tracker> trackers = new ArrayList<>();

    // our trackers
    public final BackpackTracker backpackTracker = createTracker(BackpackTracker::new);
    public final BowTracker bowTracker = createTracker(BowTracker::new);
    public final CameraTracker cameraTracker = createTracker(CameraTracker::new);
    public final ClimbTracker climbTracker = createTracker(ClimbTracker::new);
    public final CrawlTracker crawlTracker = createTracker(CrawlTracker::new);
    public final EatingTracker eatingTracker = createTracker(EatingTracker::new);
    public final HorseTracker horseTracker = createTracker(HorseTracker::new);
    public final InteractTracker interactTracker = createTracker(InteractTracker::new);
    public final JumpTracker jumpTracker = createTracker(JumpTracker::new);
    public final RowTracker rowTracker = createTracker(RowTracker::new);
    public final RunTracker runTracker = createTracker(RunTracker::new);
    public final SneakTracker sneakTracker = createTracker(SneakTracker::new);
    public final SwimTracker swimTracker = createTracker(SwimTracker::new);
    public final SwingTracker swingTracker = createTracker(SwingTracker::new);
    public final TeleportTracker teleportTracker = createTracker(TeleportTracker::new);
    public final TelescopeTracker telescopeTracker = createTracker(TelescopeTracker::new);
    public final VehicleTracker vehicleTracker = createTracker(VehicleTracker::new);


    public VRSettings vrSettings;
    public boolean grabScreenShot = false;
    public String incorrectGarbageCollector = "";
    public long frameIndex = 0L;

    public RenderPass currentPass;
    public boolean isFirstPass;

    // if the main/offhand should be rendered as menu hands
    public boolean menuHandOff;
    public boolean menuHandMain;

    public boolean completelyDisabled;

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

    /**
     * Creates a tracker instance, adds it to the registered list and returns it
     * @param constructor Constructor to use to create the tracker instance
     * @return created tracker instance
     * @param <T> Class of the tracker
     */
    public <T extends Tracker> T createTracker(BiFunction<Minecraft, ClientDataHolderVR, T> constructor) {
        T tracker = constructor.apply(Minecraft.getInstance(), this);
        registerTracker(tracker);
        return tracker;
    }

    /**
     * registers a tracker
     * @param tracker tracker to register
     * @throws IllegalArgumentException if the tracker is already registered
     */
    public void registerTracker(Tracker tracker) throws IllegalArgumentException{
        if (this.trackers.contains(tracker)) {
            throw new IllegalArgumentException("Tracker is already added and should not be added again!");
        }
        this.trackers.add(tracker);
    }

    /**
     * @return Unmodifiable list of the registered trackers
     */
    public List<Tracker> getTrackers() {
        return Collections.unmodifiableList(this.trackers);
    }
}
