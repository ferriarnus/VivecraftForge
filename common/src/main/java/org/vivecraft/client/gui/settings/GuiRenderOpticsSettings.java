package org.vivecraft.client.gui.settings;

import java.util.stream.IntStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.vivecraft.client.gui.framework.GuiVROption;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client.gui.framework.VROptionEntry;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.settings.VRHotkeys;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.mod_compat_vr.ShadersHelper;

public class GuiRenderOpticsSettings extends GuiVROptionsBase {
    static VRSettings.VrOptions[] monoDisplayOptions = new VRSettings.VrOptions[]{
        VRSettings.VrOptions.MONO_FOV,
        VRSettings.VrOptions.DUMMY,
        VRSettings.VrOptions.FSAA
    };
    static VRSettings.VrOptions[] openVRDisplayOptions = new VRSettings.VrOptions[]{
        VRSettings.VrOptions.RENDER_SCALEFACTOR,
        VRSettings.VrOptions.MIRROR_DISPLAY,
        VRSettings.VrOptions.FSAA,
        VRSettings.VrOptions.STENCIL_ON,
        VRSettings.VrOptions.HANDHELD_CAMERA_RENDER_SCALE,
        VRSettings.VrOptions.HANDHELD_CAMERA_FOV,
        VRSettings.VrOptions.MIRROR_SCREENSHOT_CAMERA,
        VRSettings.VrOptions.RELOAD_EXTERNAL_CAMERA
    };
    static VRSettings.VrOptions[] UDOptions = new VRSettings.VrOptions[]{
        VRSettings.VrOptions.MONO_FOV,
        VRSettings.VrOptions.MIRROR_CENTER_SMOOTH
    };
    static VRSettings.VrOptions[] TUDOptions = new VRSettings.VrOptions[]{
        VRSettings.VrOptions.MIXED_REALITY_FOV,
        VRSettings.VrOptions.MIXED_REALITY_RENDER_CAMERA_MODEL
    };
    static VRSettings.VrOptions[] CROPOptions = new VRSettings.VrOptions[]{
        VRSettings.VrOptions.MIRROR_EYE,
        VRSettings.VrOptions.MIRROR_CROP
    };
    static VRSettings.VrOptions[] SOptions = new VRSettings.VrOptions[]{
        VRSettings.VrOptions.MIRROR_EYE
    };
    final VROptionEntry[] MROptions = new VROptionEntry[]{
        new VROptionEntry("vivecraft.options.screen.mixedreality.button", (button, mousePos) -> {
            Minecraft.getInstance().setScreen(new GuiMixedRealitySettings(this));
            return true;
        })
    };

    private float prevRenderScaleFactor = this.settings.renderScaleFactor;
    private float prevHandCameraResScale = this.settings.handCameraResScale;

    public GuiRenderOpticsSettings(Screen par1Screen) {
        super(par1Screen);
    }

    public void init() {
        this.vrTitle = "vivecraft.options.screen.stereorendering";
        VRSettings.VrOptions[] avrsettings$vroptions = new VRSettings.VrOptions[openVRDisplayOptions.length];
        System.arraycopy(openVRDisplayOptions, 0, avrsettings$vroptions, 0, openVRDisplayOptions.length);

        for (int i = 0; i < avrsettings$vroptions.length; ++i) {
            VRSettings.VrOptions vrsettings$vroptions = avrsettings$vroptions[i];

            if (vrsettings$vroptions == VRSettings.VrOptions.RELOAD_EXTERNAL_CAMERA && (!VRHotkeys.hasExternalCameraConfig() || this.dataholder.vrSettings.displayMirrorMode != VRSettings.MirrorMode.MIXED_REALITY && this.dataholder.vrSettings.displayMirrorMode != VRSettings.MirrorMode.THIRD_PERSON)) {
                avrsettings$vroptions[i] = VRSettings.VrOptions.DUMMY;
            }
        }

        super.init(avrsettings$vroptions, true);

        if (this.dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY) {
            super.init(MROptions, false);
        } else if (this.dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.FIRST_PERSON) {
            super.init(UDOptions, false);
        } else if (this.dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON) {
            super.init(TUDOptions, false);
        } else if (this.dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.CROPPED) {
            super.init(CROPOptions, false);
        } else if (this.dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.SINGLE) {
            super.init(SOptions, false);
        }

        super.addDefaultButtons();
        this.children().stream().filter((w) ->
        {
            return w instanceof GuiVROption;
        }).forEach((w) ->
        {
            GuiVROption guivroption = (GuiVROption) w;

//            if (guivroption.getOption() == VRSettings.VrOptions.HANDHELD_CAMERA_RENDER_SCALE && Config.isShaders())  //Optifine
//            {
//                guivroption.active = false;
//            }
        });
    }

    public void render(GuiGraphics guiGraphics, int pMouseX, int pMouseY, float pPartialTicks) {
        super.render(guiGraphics, pMouseX, pMouseY, pPartialTicks);
    }

    protected void loadDefaults() {
        super.loadDefaults();
        this.minecraft.options.fov().set(70);
        if (VRState.vrInitialized) {
            this.dataholder.vrRenderer.reinitFrameBuffers("Defaults Loaded");
        }
    }

    protected void actionPerformed(AbstractWidget widget) {
        if (widget instanceof GuiVROption guivroption) {

            if (guivroption.getId() == VRSettings.VrOptions.MIRROR_DISPLAY.ordinal() || guivroption.getId() == VRSettings.VrOptions.FSAA.ordinal() || guivroption.getId() == VRSettings.VrOptions.STENCIL_ON.ordinal()) {
                if (VRState.vrInitialized) {
                    if (guivroption.getId() == VRSettings.VrOptions.MIRROR_DISPLAY.ordinal() && ShadersHelper.isShaderActive()) {
                        this.dataholder.vrRenderer.resizeFrameBuffers("Render Setting Changed");
                    } else {
                        this.dataholder.vrRenderer.reinitFrameBuffers("Render Setting Changed");
                    }
                }
                this.reinit = true;
            }
            if (guivroption.getId() == VRSettings.VrOptions.RELOAD_EXTERNAL_CAMERA.ordinal()) {
                VRHotkeys.loadExternalCameraConfig();
            }
        }
    }

    public boolean mouseReleased(double pMouseX, double p_94754_, int pMouseY) {
        if (this.settings.renderScaleFactor != this.prevRenderScaleFactor || this.settings.handCameraResScale != this.prevHandCameraResScale) {
            this.prevRenderScaleFactor = this.settings.renderScaleFactor;
            this.prevHandCameraResScale = this.settings.handCameraResScale;
            if (VRState.vrInitialized) {
                this.dataholder.vrRenderer.resizeFrameBuffers("Render Setting Changed");
            }
        }

        return super.mouseReleased(pMouseX, p_94754_, pMouseY);
    }
}
