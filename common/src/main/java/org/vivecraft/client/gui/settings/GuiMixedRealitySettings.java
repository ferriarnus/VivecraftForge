package org.vivecraft.client.gui.settings;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.vivecraft.client.gui.framework.GuiVROption;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.settings.VRSettings;

public class GuiMixedRealitySettings extends GuiVROptionsBase {
    static VRSettings.VrOptions[] MROptions = new VRSettings.VrOptions[]{
        VRSettings.VrOptions.MIXED_REALITY_UNITY_LIKE,
        VRSettings.VrOptions.MIXED_REALITY_RENDER_HANDS,
        VRSettings.VrOptions.MIXED_REALITY_ALPHA_MASK,
        VRSettings.VrOptions.MIXED_REALITY_KEY_COLOR,
        VRSettings.VrOptions.MIXED_REALITY_FOV,
        VRSettings.VrOptions.MIXED_REALITY_RENDER_CAMERA_MODEL,
        VRSettings.VrOptions.MIXED_REALITY_UNDISTORTED,
        VRSettings.VrOptions.MIRROR_EYE,
        VRSettings.VrOptions.MONO_FOV,
        VRSettings.VrOptions.MIRROR_CENTER_SMOOTH
    };

    public GuiMixedRealitySettings(Screen lastScreen) {
        super(lastScreen);
    }

    @Override
    public void init() {
        this.vrTitle = "vivecraft.options.screen.mixedreality";
        VRSettings.VrOptions[] newOptions = new VRSettings.VrOptions[MROptions.length];
        System.arraycopy(MROptions, 0, newOptions, 0, MROptions.length);

        for (int i = 0; i < newOptions.length; ++i) {
            VRSettings.VrOptions optionToCheck = newOptions[i];

            if (optionToCheck == VRSettings.VrOptions.MONO_FOV && (!this.dataHolder.vrSettings.mixedRealityUndistorted || !this.dataHolder.vrSettings.mixedRealityUnityLike)) {
                newOptions[i] = VRSettings.VrOptions.DUMMY;
            }

            if (optionToCheck == VRSettings.VrOptions.MIXED_REALITY_ALPHA_MASK && !this.dataHolder.vrSettings.mixedRealityUnityLike) {
                newOptions[i] = VRSettings.VrOptions.DUMMY;
            }

            if (optionToCheck == VRSettings.VrOptions.MIXED_REALITY_UNDISTORTED && !this.dataHolder.vrSettings.mixedRealityUnityLike) {
                newOptions[i] = VRSettings.VrOptions.DUMMY;
            }

            if (optionToCheck == VRSettings.VrOptions.MIXED_REALITY_KEY_COLOR && this.dataHolder.vrSettings.mixedRealityAlphaMask && this.dataHolder.vrSettings.mixedRealityUnityLike) {
                newOptions[i] = VRSettings.VrOptions.DUMMY;
            }

            if (optionToCheck == VRSettings.VrOptions.MIRROR_CENTER_SMOOTH && (!this.dataHolder.vrSettings.mixedRealityUndistorted || !this.dataHolder.vrSettings.mixedRealityUnityLike)) {
                newOptions[i] = VRSettings.VrOptions.DUMMY;
            }

            if (optionToCheck == VRSettings.VrOptions.MIRROR_EYE && (this.dataHolder.vrSettings.mixedRealityUndistorted || !this.dataHolder.vrSettings.mixedRealityUnityLike)) {
                newOptions[i] = VRSettings.VrOptions.DUMMY;
            }
        }

        super.init(newOptions, true);
        super.addDefaultButtons();
    }

    @Override
    protected void loadDefaults() {
        super.loadDefaults();
        if (VRState.VR_INITIALIZED) {
            this.dataHolder.vrRenderer.reinitWithoutShaders("Defaults Loaded");
        }
    }

    @Override
    protected void actionPerformed(AbstractWidget widget) {
        if (widget instanceof GuiVROption guivroption) {
            if (guivroption.getId() == VRSettings.VrOptions.MIXED_REALITY_ALPHA_MASK.ordinal() ||
                guivroption.getId() == VRSettings.VrOptions.MIXED_REALITY_UNITY_LIKE.ordinal() ||
                guivroption.getId() == VRSettings.VrOptions.MIXED_REALITY_UNDISTORTED.ordinal())
            {
                this.reinit = true;
            }
        }
    }
}
