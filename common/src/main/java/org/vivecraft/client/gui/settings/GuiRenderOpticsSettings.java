package org.vivecraft.client.gui.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.vivecraft.client.gui.framework.GuiVROption;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client.gui.framework.VROptionEntry;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.settings.VRHotkeys;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.mod_compat_vr.shaders.ShadersHelper;

public class GuiRenderOpticsSettings extends GuiVROptionsBase {
    private static final VRSettings.VrOptions[] VR_DISPLAY_OPTIONS = new VRSettings.VrOptions[]{
        VRSettings.VrOptions.RENDER_SCALEFACTOR,
        VRSettings.VrOptions.MIRROR_DISPLAY,
        VRSettings.VrOptions.FSAA,
        VRSettings.VrOptions.STENCIL_ON,
        VRSettings.VrOptions.HANDHELD_CAMERA_RENDER_SCALE,
        VRSettings.VrOptions.HANDHELD_CAMERA_FOV,
        VRSettings.VrOptions.MIRROR_SCREENSHOT_CAMERA,
        VRSettings.VrOptions.RELOAD_EXTERNAL_CAMERA
    };
    private static final VRSettings.VrOptions[] UNDISTORTED_OPTIONS = new VRSettings.VrOptions[]{
        VRSettings.VrOptions.MONO_FOV,
        VRSettings.VrOptions.MIRROR_CENTER_SMOOTH
    };
    private static final VRSettings.VrOptions[] THIRD_OPTIONS = new VRSettings.VrOptions[]{
        VRSettings.VrOptions.MIXED_REALITY_FOV,
        VRSettings.VrOptions.MIXED_REALITY_RENDER_CAMERA_MODEL
    };
    private static final VRSettings.VrOptions[] CROP_OPTIONS = new VRSettings.VrOptions[]{
        VRSettings.VrOptions.MIRROR_EYE,
        VRSettings.VrOptions.MIRROR_CROP
    };
    private static final VRSettings.VrOptions[] SINGLE_OPTIONS = new VRSettings.VrOptions[]{
        VRSettings.VrOptions.MIRROR_EYE
    };
    private final VROptionEntry[] MROptions = new VROptionEntry[]{new VROptionEntry(
        "vivecraft.options.screen.mixedreality.button", (button, mousePos) -> {
        Minecraft.getInstance().setScreen(new GuiMixedRealitySettings(this));
        return true;
    })};
    private final VROptionEntry[] postAndShader = new VROptionEntry[]{
        new VROptionEntry(
        "vivecraft.options.screen.posteffects.button", (button, mousePos) -> {
        Minecraft.getInstance().setScreen(new GuiPostEffectsSettings(this));
        return true;
    }),
        new VROptionEntry(
            "vivecraft.options.screen.shadercompat.button", (button, mousePos) -> {
            Minecraft.getInstance().setScreen(new GuiShaderCompatSettings(this));
            return true;
        })};

    private float prevRenderScaleFactor = this.vrSettings.renderScaleFactor;
    private float prevHandCameraResScale = this.vrSettings.handCameraResScale;

    public GuiRenderOpticsSettings(Screen lastScreen) {
        super(lastScreen);
    }

    @Override
    public void init() {
        this.vrTitle = "vivecraft.options.screen.stereorendering";
        VRSettings.VrOptions[] buttons = new VRSettings.VrOptions[VR_DISPLAY_OPTIONS.length];
        System.arraycopy(VR_DISPLAY_OPTIONS, 0, buttons, 0, VR_DISPLAY_OPTIONS.length);

        for (int i = 0; i < buttons.length; i++) {
            VRSettings.VrOptions option = buttons[i];

            if (option == VRSettings.VrOptions.RELOAD_EXTERNAL_CAMERA && (!VRHotkeys.hasExternalCameraConfig() ||
                (this.dataHolder.vrSettings.displayMirrorMode != VRSettings.MirrorMode.MIXED_REALITY &&
                    this.dataHolder.vrSettings.displayMirrorMode != VRSettings.MirrorMode.THIRD_PERSON
                )
            ))
            {
                buttons[i] = VRSettings.VrOptions.DUMMY;
            }
        }

        super.init(buttons, true);

        super.init(this.postAndShader, false);

        switch(this.dataHolder.vrSettings.displayMirrorMode) {
            case MIXED_REALITY -> super.init(this.MROptions, false);
            case FIRST_PERSON -> super.init(UNDISTORTED_OPTIONS, false);
            case THIRD_PERSON -> super.init(THIRD_OPTIONS, false);
            case CROPPED -> super.init(CROP_OPTIONS, false);
            case SINGLE -> super.init(SINGLE_OPTIONS, false);
        }

        super.addDefaultButtons();

        // disable the screenshot render scale, since that is not active, when shaders needs a fixed resolution for each pass
        this.children().stream().filter((w) -> w instanceof GuiVROption && w instanceof AbstractWidget).forEach((w) -> {
            if (((GuiVROption) w).getOption() == VRSettings.VrOptions.HANDHELD_CAMERA_RENDER_SCALE &&
                ShadersHelper.needsSameSizeBuffers())
            {
                ((AbstractWidget) w).active = false;
            }
        });
    }

    @Override
    protected void loadDefaults() {
        super.loadDefaults();
        this.minecraft.options.fov().set(70);
        if (VRState.VR_INITIALIZED) {
            this.dataHolder.vrRenderer.reinitFrameBuffers("Defaults Loaded");
        }
    }

    @Override
    protected void actionPerformed(AbstractWidget widget) {
        if (widget instanceof GuiVROption guivroption) {
            if (guivroption.getId() == VRSettings.VrOptions.MIRROR_DISPLAY.ordinal() || guivroption.getId() == VRSettings.VrOptions.FSAA.ordinal() || guivroption.getId() == VRSettings.VrOptions.STENCIL_ON.ordinal()) {
                if (VRState.VR_INITIALIZED) {
                    if (guivroption.getId() == VRSettings.VrOptions.MIRROR_DISPLAY.ordinal() && ShadersHelper.isShaderActive()) {
                        this.dataHolder.vrRenderer.resizeFrameBuffers("Render Setting Changed");
                    } else {
                        this.dataHolder.vrRenderer.reinitFrameBuffers("Render Setting Changed");
                    }
                }
                this.reinit = true;
            }
            if (guivroption.getId() == VRSettings.VrOptions.RELOAD_EXTERNAL_CAMERA.ordinal()) {
                VRHotkeys.loadExternalCameraConfig(this.vrSettings);
            }
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Hacky way of making the render scale slider only reinit on mouse release
        if (this.vrSettings.renderScaleFactor != this.prevRenderScaleFactor || this.vrSettings.handCameraResScale != this.prevHandCameraResScale) {
            this.prevRenderScaleFactor = this.vrSettings.renderScaleFactor;
            this.prevHandCameraResScale = this.vrSettings.handCameraResScale;
            if (VRState.VR_INITIALIZED) {
                this.dataHolder.vrRenderer.resizeFrameBuffers("Render Scale Changed: VR scale: %.1fx, Camera scale: %.1fx".formatted(this.vrSettings.renderScaleFactor, this.vrSettings.handCameraResScale));
            }
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }
}
