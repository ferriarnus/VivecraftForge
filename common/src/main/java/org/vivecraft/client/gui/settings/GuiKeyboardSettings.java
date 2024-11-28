package org.vivecraft.client.gui.settings;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.vivecraft.client.gui.framework.GuiVROption;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client.gui.framework.VROptionEntry;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.settings.VRSettings;

public class GuiKeyboardSettings extends GuiVROptionsBase {
    private static final VROptionEntry[] keyboardOptions = new VROptionEntry[]{
        new VROptionEntry(VRSettings.VrOptions.PHYSICAL_KEYBOARD, (button, mousePos) -> {
            KeyboardHandler.setOverlayShowing(false);
            return false;
        }),
        new VROptionEntry(VRSettings.VrOptions.KEYBOARD_PRESS_BINDS),
        new VROptionEntry(VRSettings.VrOptions.AUTO_OPEN_KEYBOARD),
        new VROptionEntry(VRSettings.VrOptions.AUTO_CLOSE_KEYBOARD),
        new VROptionEntry(VRSettings.VrOptions.PHYSICAL_KEYBOARD_SCALE),
        new VROptionEntry(VRSettings.VrOptions.PHYSICAL_KEYBOARD_THEME)
    };

    public GuiKeyboardSettings(Screen lastScreen) {
        super(lastScreen);
    }

    @Override
    public void init() {
        this.vrTitle = "vivecraft.options.screen.keyboard";
        super.init(keyboardOptions, true);
        super.addDefaultButtons();
    }

    @Override
    protected void loadDefaults() {
        super.loadDefaults();
    }

    @Override
    protected void actionPerformed(AbstractWidget widget) {
        if (widget instanceof GuiVROption button) {
            if (button.getId() == VRSettings.VrOptions.PHYSICAL_KEYBOARD_THEME.ordinal()) {
                KeyboardHandler.PHYSICAL_KEYBOARD.init();
            }
        }
    }
}
