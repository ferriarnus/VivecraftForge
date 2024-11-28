package org.vivecraft.client.gui.settings;

import net.minecraft.client.gui.screens.Screen;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client.gui.framework.VROptionEntry;
import org.vivecraft.client_vr.settings.VRSettings;

public class GuiMenuWorldSettings extends GuiVROptionsBase {
    private final VROptionEntry[] miscSettings = new VROptionEntry[]{
        new VROptionEntry(VRSettings.VrOptions.MENU_WORLD_SELECTION),
        new VROptionEntry("vivecraft.gui.menuworld.refresh", (button, mousePos) -> {
            if (this.dataHolder.menuWorldRenderer != null && this.dataHolder.menuWorldRenderer.getLevel() != null) {
                try {
                    this.dataHolder.menuWorldRenderer.destroy();
                    this.dataHolder.menuWorldRenderer.prepare();
                } catch (Exception exception) {
                    VRSettings.LOGGER.error("Vivecraft: error refreshing menuworld: ", exception);
                }
            }
            return true;
        }),
        new VROptionEntry(VRSettings.VrOptions.MENU_WORLD_FALLBACK),
        new VROptionEntry("vivecraft.gui.menuworld.loadnew", (button, mousePos) -> {
            if (this.dataHolder.menuWorldRenderer != null) {
                try {
                    this.dataHolder.menuWorldRenderer.destroy();
                    this.dataHolder.menuWorldRenderer.init();
                } catch (Exception exception) {
                    VRSettings.LOGGER.error("Vivecraft: error loading new menuworld: ", exception);
                }
            }
            return true;
        })
    };

    public GuiMenuWorldSettings(Screen lastScreen) {
        super(lastScreen);
    }

    @Override
    public void init() {
        this.vrTitle = "vivecraft.options.screen.menuworld";
        super.init(this.miscSettings, true);
        super.addDefaultButtons();
    }
}
