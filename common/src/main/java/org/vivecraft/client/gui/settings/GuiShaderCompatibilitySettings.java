package org.vivecraft.client.gui.settings;

import net.minecraft.client.gui.screens.Screen;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client.gui.framework.VROptionEntry;
import org.vivecraft.client_vr.settings.VRSettings;

public class GuiShaderCompatibilitySettings extends GuiVROptionsBase {
    private static final VROptionEntry[] shaderOptions = new VROptionEntry[]{
        new VROptionEntry(VRSettings.VrOptions.SHADER_GUI_RENDER),
        new VROptionEntry(VRSettings.VrOptions.SHADER_SHADOW_MODEL_LIMB_SCALE)
    };

    public GuiShaderCompatibilitySettings(Screen lastScreen) {
        super(lastScreen);
    }

    @Override
    public void init() {
        this.vrTitle = "vivecraft.options.screen.shadercompat";
        super.init(shaderOptions, true);

        super.addDefaultButtons();
    }
}
