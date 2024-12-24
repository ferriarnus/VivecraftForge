package org.vivecraft.client.gui.settings;

import net.minecraft.client.gui.screens.Screen;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client.gui.framework.VROptionEntry;
import org.vivecraft.client_vr.settings.VRSettings;

public class GuiShaderCompatSettings extends GuiVROptionsBase {
    private static final VROptionEntry[] MODEL_OPTIONS = new VROptionEntry[]{
        new VROptionEntry(VRSettings.VrOptions.SHADER_SLOW),
        new VROptionEntry(VRSettings.VrOptions.SHADER_PATCHING),
        new VROptionEntry(VRSettings.VrOptions.SHADER_GUI_RENDER)
    };

    public GuiShaderCompatSettings(Screen lastScreen) {
        super(lastScreen);
    }

    @Override
    public void init() {
        this.vrTitle = "vivecraft.options.screen.posteffects";
        super.init(MODEL_OPTIONS, true);

        super.addDefaultButtons();
    }
}
