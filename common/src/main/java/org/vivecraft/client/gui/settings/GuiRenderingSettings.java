package org.vivecraft.client.gui.settings;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.vivecraft.client.gui.framework.GuiVROption;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client.gui.framework.VROptionEntry;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.settings.VRSettings;

public class GuiRenderingSettings extends GuiVROptionsBase {
    private final VROptionEntry[] hudOptions = new VROptionEntry[]{
        new VROptionEntry(VRSettings.VrOptions.DOUBLE_GUI_RESOLUTION),
        new VROptionEntry(VRSettings.VrOptions.GUI_MIPMAPS),
        new VROptionEntry(VRSettings.VrOptions.GUI_SCALE),
        new VROptionEntry(VRSettings.VrOptions.HUD_MAX_GUI_SCALE)
    };

    public GuiRenderingSettings(Screen lastScreen) {
        super(lastScreen);
    }

    @Override
    public void init() {
        this.vrTitle = "vivecraft.options.screen.guirendering";
        super.init(this.hudOptions, true);
        super.addDefaultButtons();
    }

    @Override
    protected void loadDefaults() {
        super.loadDefaults();
        this.minecraft.options.hideGui = false;
    }

    @Override
    protected void actionPerformed(AbstractWidget widget) {
        if (widget instanceof GuiVROption button) {
            if (VRState.VR_INITIALIZED && (
                button.getId() == VRSettings.VrOptions.DOUBLE_GUI_RESOLUTION.ordinal() ||
                button.getId() == VRSettings.VrOptions.GUI_SCALE.ordinal()
            ))
            {
                this.dataHolder.vrRenderer.resizeFrameBuffers("GUI Setting Changed");
                this.reinit = true;
            } else if (VRState.VR_INITIALIZED && button.getId() == VRSettings.VrOptions.GUI_MIPMAPS.ordinal()) {
                this.dataHolder.vrRenderer.reinitFrameBuffers("GUI Mipmpams Changed");
                this.reinit = true;
            }
        }
    }
}
