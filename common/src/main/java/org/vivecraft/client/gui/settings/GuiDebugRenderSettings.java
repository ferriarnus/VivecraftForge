package org.vivecraft.client.gui.settings;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.vivecraft.client.gui.widgets.SettingsList;
import org.vivecraft.client_vr.settings.VRSettings;

import java.util.LinkedList;
import java.util.List;

public class GuiDebugRenderSettings extends GuiListScreen {
    public GuiDebugRenderSettings(Screen lastScreen) {
        super(Component.translatable("vivecraft.options.screen.debug"), lastScreen);
    }

    @Override
    protected List<SettingsList.BaseEntry> getEntries() {
        List<SettingsList.BaseEntry> entries = new LinkedList<>();
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.RENDER_DEBUG_HEAD_HITBOX));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.RENDER_DEBUG_DEVICE_AXES));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.RENDER_DEBUG_PLAYER_AXES));

        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.SHOW_PLAYER_MODEL));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.SHOW_PLAYER_MODEL_ARMS));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.PLAYER_MODEL_ARMS_SCALE));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.PLAYER_MODEL_BODY_SCALE));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.PLAYER_MODEL_LEGS_SCALE));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.PLAYER_MODEL_TYPE));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.PLAYER_LIMBS_CONNECTED));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.PLAYER_LIMBS_LIMIT));

        return entries;
    }
}
