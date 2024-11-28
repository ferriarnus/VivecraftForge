package org.vivecraft.client_vr.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.vivecraft.client_vr.ClientDataHolderVR;

public class AutoCalibration {
    public static final float DEFAULT_HEIGHT = 1.52F;

    /**
     * sets the player height for how they are standing right now
     */
    public static void calibrateManual() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();

        dataHolder.vrSettings.manualCalibration = (float) dataHolder.vr.hmdPivotHistory.averagePosition(0.5D).y;
        // round to nearest %
        int percentHeight = Math.round(100.0F * getPlayerHeight() / DEFAULT_HEIGHT);

        minecraft.gui.getChat().addMessage(Component.translatable("vivecraft.messages.heightset", percentHeight));
        dataHolder.vrSettings.saveOptions();
    }

    public static float getPlayerHeight() {
        ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();
        float height = DEFAULT_HEIGHT;

        // you cant do roomscale crap or calibrate your height in seated, anyway.
        if (!dataHolder.vrSettings.seated) {
            if (dataHolder.vrSettings.manualCalibration != -1.0F) {
                height = dataHolder.vrSettings.manualCalibration;
            }
        }
        return height;
    }
}
