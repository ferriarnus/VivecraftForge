package org.vivecraft.client.gui.settings;

import net.minecraft.client.gui.screens.Screen;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client.gui.framework.VROptionLayout;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.settings.VRSettings;

public class GuiMainVRSettings extends GuiVROptionsBase {
    private final VROptionLayout[] vrAlwaysOptions = new VROptionLayout[]{
        new VROptionLayout(GuiHUDSettings.class, VROptionLayout.Position.POS_LEFT, 2.0F, true,
            "vivecraft.options.screen.gui.button"),
        new VROptionLayout(GuiRenderOpticsSettings.class, VROptionLayout.Position.POS_LEFT, 1.0F, true,
            "vivecraft.options.screen.stereorendering.button"),
        new VROptionLayout(GuiQuickCommandEditor.class, VROptionLayout.Position.POS_RIGHT, 1.0F, true,
            "vivecraft.options.screen.quickcommands.button"),
        new VROptionLayout(GuiOtherHUDSettings.class, VROptionLayout.Position.POS_RIGHT, 2.0F, true,
            "vivecraft.options.screen.guiother.button"),
        new VROptionLayout(VRSettings.VrOptions.WORLD_SCALE, VROptionLayout.Position.POS_LEFT, 6.0F, true, null),
        new VROptionLayout(VRSettings.VrOptions.WORLD_ROTATION, VROptionLayout.Position.POS_RIGHT, 6.0F, true, null),
        new VROptionLayout(VRSettings.VrOptions.PLAY_MODE_SEATED, (button, mousePos) -> {
            this.reinit = true;

            if (!this.dataHolder.vrSettings.seated) {
                this.isConfirm = true;
                return true;
            } else {
                return false;
            }
        }, VROptionLayout.Position.POS_LEFT, 0.0F, true, null),
        new VROptionLayout(VRSettings.VrOptions.VR_HOTSWITCH, VROptionLayout.Position.POS_RIGHT, 0.0F, true, null)
    };
    private final VROptionLayout[] vrStandingOptions = new VROptionLayout[]{
        new VROptionLayout(GuiStandingSettings.class, VROptionLayout.Position.POS_LEFT, 4.0F, true,
            "vivecraft.options.screen.standing.button"),
        new VROptionLayout(GuiRoomscaleSettings.class, VROptionLayout.Position.POS_RIGHT, 4.0F, true,
            "vivecraft.options.screen.roomscale.button"),
        new VROptionLayout(GuiVRControls.class, VROptionLayout.Position.POS_LEFT, 5.0F, true,
            "vivecraft.options.screen.controls.button"),
        new VROptionLayout(GuiRadialConfiguration.class, VROptionLayout.Position.POS_RIGHT, 5.0F, true,
            "vivecraft.options.screen.radialmenu.button")
    };
    private final VROptionLayout[] vrSeatedOptions = new VROptionLayout[]{
        new VROptionLayout(GuiSeatedOptions.class, VROptionLayout.Position.POS_LEFT, 4.0F, true,
            "vivecraft.options.screen.seated.button"),
        new VROptionLayout(VRSettings.VrOptions.RESET_ORIGIN, (button, mousePos) -> {
            this.resetOrigin();
            return true;
        }, VROptionLayout.Position.POS_RIGHT, 4.0F, true, null)
    };
    private final VROptionLayout[] vrConfirm = new VROptionLayout[]{
        new VROptionLayout((button, mousePos) -> {
            this.reinit = true;
            this.isConfirm = false;
            return false;
        }, VROptionLayout.Position.POS_RIGHT, 2.0F, true, "gui.cancel"),
        new VROptionLayout((button, mousePos) -> {
            this.dataHolder.vrSettings.seated = true;
            this.vrSettings.saveOptions();
            this.reinit = true;
            this.isConfirm = false;
            return false;
        }, VROptionLayout.Position.POS_LEFT, 2.0F, true, "vivecraft.gui.ok")
    };
    private boolean isConfirm = false;

    public GuiMainVRSettings(Screen lastScreen) {
        super(lastScreen);
    }

    @Override
    protected void init() {
        if (!this.isConfirm) {
            this.vrTitle = "vivecraft.options.screen.main";

            if (this.dataHolder.vrSettings.seated) {
                super.init(this.vrSeatedOptions, true);
            } else {
                super.init(this.vrStandingOptions, true);

                if (this.dataHolder.vrSettings.allowStandingOriginOffset) {
                    super.init(new VROptionLayout[]{
                        new VROptionLayout(VRSettings.VrOptions.RESET_ORIGIN, (button, mousePos) -> {
                            this.resetOrigin();
                            return true;
                        }, VROptionLayout.Position.POS_LEFT, 7.0F, true, null)
                    }, false);
                }
            }

            super.init(this.vrAlwaysOptions, false);
            super.addDefaultButtons();
        } else {
            this.vrTitle = "vivecraft.messages.seatedmode";
            super.init(this.vrConfirm, true);
        }
    }

    @Override
    protected void loadDefaults() {
        super.loadDefaults();
        if (VRState.VR_INITIALIZED) {
            MCVR.get().seatedRot = 0.0F;
            MCVR.get().clearOffset();
        }
    }

    @Override
    protected boolean onDoneClicked() {
        if (this.isConfirm) {
            this.isConfirm = false;
            this.reinit = true;
            return true;
        }
        return super.onDoneClicked();
    }

    protected void resetOrigin() {
        if (VRState.VR_RUNNING) {
            MCVR.get().resetPosition();
            this.vrSettings.saveOptions();
            this.minecraft.setScreen(null);
        }
    }
}
