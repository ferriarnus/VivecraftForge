package org.vivecraft.client.gui.settings;

import org.vivecraft.client.gui.framework.GuiVROption;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.settings.VRSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

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

    public GuiMixedRealitySettings(Screen par1Screen) {
        super(par1Screen);
    }

    public void init() {
        this.vrTitle = "vivecraft.options.screen.mixedreality";
        VRSettings.VrOptions[] avrsettings$vroptions = new VRSettings.VrOptions[MROptions.length];
        System.arraycopy(MROptions, 0, avrsettings$vroptions, 0, MROptions.length);

        for (int j = 0; j < avrsettings$vroptions.length; ++j) {
            VRSettings.VrOptions vrsettings$vroptions1 = avrsettings$vroptions[j];

            if (vrsettings$vroptions1 == VRSettings.VrOptions.MONO_FOV && (!this.dataholder.vrSettings.mixedRealityUndistorted || !this.dataholder.vrSettings.mixedRealityUnityLike)) {
                avrsettings$vroptions[j] = VRSettings.VrOptions.DUMMY;
            }

            if (vrsettings$vroptions1 == VRSettings.VrOptions.MIXED_REALITY_ALPHA_MASK && !this.dataholder.vrSettings.mixedRealityUnityLike) {
                avrsettings$vroptions[j] = VRSettings.VrOptions.DUMMY;
            }

            if (vrsettings$vroptions1 == VRSettings.VrOptions.MIXED_REALITY_UNDISTORTED && !this.dataholder.vrSettings.mixedRealityUnityLike) {
                avrsettings$vroptions[j] = VRSettings.VrOptions.DUMMY;
            }

            if (vrsettings$vroptions1 == VRSettings.VrOptions.MIXED_REALITY_KEY_COLOR && this.dataholder.vrSettings.mixedRealityAlphaMask && this.dataholder.vrSettings.mixedRealityUnityLike) {
                avrsettings$vroptions[j] = VRSettings.VrOptions.DUMMY;
            }

            if (vrsettings$vroptions1 == VRSettings.VrOptions.MIRROR_CENTER_SMOOTH && (!this.dataholder.vrSettings.mixedRealityUndistorted || !this.dataholder.vrSettings.mixedRealityUnityLike)) {
                avrsettings$vroptions[j] = VRSettings.VrOptions.DUMMY;
            }

            if (vrsettings$vroptions1 == VRSettings.VrOptions.MIRROR_EYE && (this.dataholder.vrSettings.mixedRealityUndistorted || !this.dataholder.vrSettings.mixedRealityUnityLike)) {
                avrsettings$vroptions[j] = VRSettings.VrOptions.DUMMY;
            }
        }

        super.init(avrsettings$vroptions, true);
        super.addDefaultButtons();
    }

    public void render(GuiGraphics guiGraphics, int pMouseX, int pMouseY, float pPartialTicks) {
        super.render(guiGraphics, pMouseX, pMouseY, pPartialTicks);
    }

    protected void loadDefaults() {
        super.loadDefaults();
        if (VRState.vrInitialized) {
            this.dataholder.vrRenderer.reinitWithoutShaders("Defaults Loaded");
        }
    }

    protected void actionPerformed(AbstractWidget widget) {
        if (widget instanceof GuiVROption guivroption) {
            if (guivroption.getId() == VRSettings.VrOptions.MIXED_REALITY_ALPHA_MASK.ordinal()
                || guivroption.getId() == VRSettings.VrOptions.MIXED_REALITY_UNITY_LIKE.ordinal()
                || guivroption.getId() == VRSettings.VrOptions.MIXED_REALITY_UNDISTORTED.ordinal()) {
                this.reinit = true;
            }
        }
    }
}
