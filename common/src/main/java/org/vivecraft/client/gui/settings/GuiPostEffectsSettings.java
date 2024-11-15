package org.vivecraft.client.gui.settings;

import net.minecraft.client.gui.screens.Screen;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client.gui.framework.VROptionEntry;
import org.vivecraft.client_vr.settings.VRSettings;

public class GuiPostEffectsSettings extends GuiVROptionsBase {
    private static final VROptionEntry[] modelOptions = new VROptionEntry[]{
        new VROptionEntry(VRSettings.VrOptions.LOW_HEALTH_INDICATOR),
        new VROptionEntry(VRSettings.VrOptions.HIT_INDICATOR),
        new VROptionEntry(VRSettings.VrOptions.WATER_EFFECT),
        new VROptionEntry(VRSettings.VrOptions.PORTAL_EFFECT),
        new VROptionEntry(VRSettings.VrOptions.FREEZE_EFFECT),
        new VROptionEntry(VRSettings.VrOptions.PUMPKIN_EFFECT)
    };

    public GuiPostEffectsSettings(Screen lastScreen) {
        super(lastScreen);
    }

    @Override
    public void init() {
        this.vrTitle = "vivecraft.options.screen.posteffects";
        super.init(modelOptions, true);

        super.addDefaultButtons();
    }
}
