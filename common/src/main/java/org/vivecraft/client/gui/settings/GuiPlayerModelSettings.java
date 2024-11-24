package org.vivecraft.client.gui.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client.gui.framework.VROptionEntry;
import org.vivecraft.client.gui.screens.FBTCalibrationScreen;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.settings.AutoCalibration;
import org.vivecraft.client_vr.settings.VRSettings;

public class GuiPlayerModelSettings extends GuiVROptionsBase {
    private static final VROptionEntry[] modelOptions = new VROptionEntry[]{
        new VROptionEntry(VRSettings.VrOptions.SHOW_PLAYER_MODEL),
        new VROptionEntry(VRSettings.VrOptions.PLAYER_MODEL_TYPE),
        new VROptionEntry(VRSettings.VrOptions.SHOW_PLAYER_MODEL_ARMS),
        new VROptionEntry(VRSettings.VrOptions.PLAYER_MODEL_ARMS_SCALE),
        new VROptionEntry(VRSettings.VrOptions.PLAYER_LIMBS_CONNECTED),
        new VROptionEntry(VRSettings.VrOptions.PLAYER_MODEL_BODY_SCALE),
        new VROptionEntry(VRSettings.VrOptions.PLAYER_LIMBS_LIMIT),
        new VROptionEntry(VRSettings.VrOptions.PLAYER_MODEL_LEGS_SCALE),
        new VROptionEntry(VRSettings.VrOptions.PLAYER_WALK_ANIM),
        new VROptionEntry(VRSettings.VrOptions.PLAYER_ARM_ANIM)
    };

    private final VROptionEntry[] fbtCalibration = new VROptionEntry[]{new VROptionEntry(
        "vivecraft.options.screen.fbtcalibration.button", (button, mousePos) -> {
        Minecraft.getInstance().setScreen(new FBTCalibrationScreen(this));
        return true;
    })};

    private final VROptionEntry[] heightCalibration = new VROptionEntry[]{new VROptionEntry(
        "vivecraft.gui.calibrateheight", (button, mousePos) -> {
        AutoCalibration.calibrateManual();
        ClientDataHolderVR.getInstance().vrSettings.saveOptions();
        return true;
    })};

    public GuiPlayerModelSettings(Screen lastScreen) {
        super(lastScreen);
    }

    @Override
    public void init() {
        this.vrTitle = "vivecraft.options.screen.playermodel";
        super.init(modelOptions, true);
        if (VRState.VR_INITIALIZED && !this.vrSettings.seated && (this.dataHolder.vr.hasFBT() ||
            ClientDataHolderVR.getInstance().vr.getTrackers().size() >= 3)) {
            super.init(this.fbtCalibration, false);
        } else {
            super.init(this.heightCalibration, false);
        }

        super.addDefaultButtons();
    }
}
