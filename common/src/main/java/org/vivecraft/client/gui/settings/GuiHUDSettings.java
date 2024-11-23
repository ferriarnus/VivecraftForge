package org.vivecraft.client.gui.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.vivecraft.client.gui.framework.GuiVROption;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client.gui.framework.VROptionEntry;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.settings.VRSettings;

public class GuiHUDSettings extends GuiVROptionsBase {
    private final VROptionEntry[] hudOptions = new VROptionEntry[]{
        new VROptionEntry(VRSettings.VrOptions.HUD_HIDE),
        new VROptionEntry(VRSettings.VrOptions.HUD_LOCK_TO),
        new VROptionEntry(VRSettings.VrOptions.HUD_SCALE),
        new VROptionEntry(VRSettings.VrOptions.HUD_DISTANCE),
        new VROptionEntry(VRSettings.VrOptions.HUD_OCCLUSION),
        new VROptionEntry(VRSettings.VrOptions.HUD_OPACITY),
        new VROptionEntry(VRSettings.VrOptions.RENDER_MENU_BACKGROUND),
        new VROptionEntry(VRSettings.VrOptions.TOUCH_HOTBAR),
        new VROptionEntry(VRSettings.VrOptions.MENU_ALWAYS_FOLLOW_FACE),
        new VROptionEntry(VRSettings.VrOptions.GUI_APPEAR_OVER_BLOCK),
        new VROptionEntry(VRSettings.VrOptions.DOUBLE_GUI_RESOLUTION),
        new VROptionEntry(VRSettings.VrOptions.GUI_SCALE),
        new VROptionEntry(VRSettings.VrOptions.HUD_MAX_GUI_SCALE),
        new VROptionEntry(VRSettings.VrOptions.SHADER_GUI_RENDER),
        new VROptionEntry("vivecraft.options.screen.keyboard.button", (button, mousePos) -> {
            Minecraft.getInstance().setScreen(new GuiKeyboardSettings(this));
            return true;
        }),
        new VROptionEntry("vivecraft.options.screen.menuworld.button", (button, mousePos) -> {
            Minecraft.getInstance().setScreen(new GuiMenuWorldSettings(this));
            return true;
        }),
    };

    public GuiHUDSettings(Screen guiScreen) {
        super(guiScreen);
    }

    public void init() {
        this.vrTitle = "vivecraft.options.screen.gui";
        super.init(this.hudOptions, true);
        super.addDefaultButtons();
    }

    protected void loadDefaults() {
        super.loadDefaults();
        this.minecraft.options.hideGui = false;
    }

    protected void actionPerformed(AbstractWidget widget) {
        if (widget instanceof GuiVROption button) {
            if (button.getId() == VRSettings.VrOptions.MENU_ALWAYS_FOLLOW_FACE.ordinal()) {
                GuiHandler.onScreenChanged(Minecraft.getInstance().screen, Minecraft.getInstance().screen, false);
            }
            if ((button.getId() == VRSettings.VrOptions.DOUBLE_GUI_RESOLUTION.ordinal()
                || button.getId() == VRSettings.VrOptions.GUI_SCALE.ordinal())
                && VRState.vrInitialized) {
                this.dataholder.vrRenderer.resizeFrameBuffers("GUI Setting Changed");
                this.reinit = true;
            }
        }
    }
}
