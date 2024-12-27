package org.vivecraft.client.gui.settings;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TranslatableComponent;
import org.vivecraft.client.gui.widgets.SettingsList;
import org.vivecraft.client_vr.settings.VRSettings;

import java.util.LinkedList;
import java.util.List;

public class GuiDebugRenderSettings extends GuiListScreen {
    public GuiDebugRenderSettings(Screen lastScreen) {
        super(new TranslatableComponent("vivecraft.options.screen.debug"), lastScreen);
    }

    @Override
    protected List<SettingsList.BaseEntry> getEntries() {
        List<SettingsList.BaseEntry> entries = new LinkedList<>();
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.RENDER_DEBUG_HEAD_HITBOX));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.RENDER_DEBUG_DEVICE_AXES));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.RENDER_DEBUG_PLAYER_AXES));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.RENDER_DEBUG_TRACKERS));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.MAIN_PLAYER_DATA));

        return entries;
    }
}