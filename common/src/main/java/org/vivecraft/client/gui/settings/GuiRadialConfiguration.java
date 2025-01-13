package org.vivecraft.client.gui.settings;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.commons.lang3.ArrayUtils;
import org.vivecraft.client.gui.framework.GuiVROptionButton;
import org.vivecraft.client.gui.framework.GuiVROptionsBase;
import org.vivecraft.client.gui.framework.VROptionLayout;
import org.vivecraft.client_vr.gui.GuiRadial;
import org.vivecraft.client_vr.settings.VRSettings;

import java.util.Arrays;
import java.util.Optional;

public class GuiRadialConfiguration extends GuiVROptionsBase {
    private static final VROptionLayout[] OPTIONS = new VROptionLayout[]{
        new VROptionLayout(VRSettings.VrOptions.RADIAL_MODE_HOLD, VROptionLayout.Position.POS_LEFT, 0.0F, true, "")
    };
    private String[] arr;
    private boolean isShift = false;
    private int selectedIndex = -1;
    private GuiRadialItemsList list;
    private boolean isselectmode = false;

    public GuiRadialConfiguration(Screen lastScreen) {
        super(lastScreen);
    }

    public void setKey(KeyMapping key) {
        if (key != null) {
            this.arr[this.selectedIndex] = key.getName();
        } else {
            this.arr[this.selectedIndex] = "";
        }

        this.selectedIndex = -1;
        this.isselectmode = false;
        this.reinit = true;
        this.visibleList = null;

        if (!this.isShift) {
            this.dataHolder.vrSettings.vrRadialItems = ArrayUtils.clone(this.arr);
        } else {
            this.dataHolder.vrSettings.vrRadialItemsAlt = ArrayUtils.clone(this.arr);
        }

        this.dataHolder.vrSettings.saveOptions();
    }

    @Override
    public void init() {
        this.vrTitle = "vivecraft.options.screen.radialmenu";
        this.list = new GuiRadialItemsList(this, this.minecraft);
        if (this.visibleList != null) {
            this.visibleList = this.list;
        }
        this.clearWidgets();

        if (this.isselectmode) {
            this.addRenderableWidget(new Button(this.width / 2 - 155, this.height - 25, 150, 20,
                new TranslatableComponent("gui.cancel"),
                (p) -> {
                    this.isselectmode = false;
                    this.reinit = true;
                    this.visibleList = null;
                }));

            this.addRenderableWidget(new Button(this.width / 2 - 155, 26, 150, 20,
                new TranslatableComponent("vivecraft.gui.clear"),
                (p) -> this.setKey(null)));
        } else {
            this.addRenderableWidget(new Button(this.width / 2 + 2, this.height / 6 - 10, 150, 20,
                this.isShift ?
                    new TranslatableComponent("vivecraft.gui.radialmenu.mainset") :
                    new TranslatableComponent("vivecraft.gui.radialmenu.alternateset"),
                (p) -> {
                    this.isShift = !this.isShift;
                    this.reinit = true;
                }));

            super.init(OPTIONS, false);

            int centerX = this.width / 2;
            int centerY = this.height / 2 + 10;
            this.arr = ArrayUtils.clone(this.dataHolder.vrSettings.vrRadialItems);
            String[] altSet = ArrayUtils.clone(this.dataHolder.vrSettings.vrRadialItemsAlt);

            if (this.isShift) {
                this.arr = altSet;
            }

            for (int i = 0; i < this.dataHolder.vrSettings.vrRadialButtons; i++) {
                // not all buttons need to be set
                if (i >= this.arr.length) break;

                String current = this.arr[i];
                int index = i;
                Optional<KeyMapping> keyMapping = Arrays.stream(this.minecraft.options.keyMappings)
                    .filter(keymapping -> keymapping.getName().equalsIgnoreCase(current))
                    .findFirst();

                String label = keyMapping.map(mapping -> I18n.get(mapping.getName())).orElse("");
                this.addRenderableWidget(GuiRadial.createButton(label, (p) -> {
                    this.selectedIndex = index;
                    this.isselectmode = true;
                    this.reinit = true;
                    this.visibleList = this.list;
                }, index, centerX, centerY));
            }

            // add button count button
            this.addRenderableWidget(
                new GuiVROptionButton(VRSettings.VrOptions.RADIAL_NUMBER.ordinal(),
                    centerX - 10, centerY - 10, 20, 20,
                    VRSettings.VrOptions.RADIAL_NUMBER, "" + this.dataHolder.vrSettings.vrRadialButtons,
                    (p) -> {
                        this.dataHolder.vrSettings.vrRadialButtons += 2;
                        if (this.dataHolder.vrSettings.vrRadialButtons >
                            VRSettings.VrOptions.RADIAL_NUMBER.getValueMax())
                        {
                            this.dataHolder.vrSettings.vrRadialButtons = (int) VRSettings.VrOptions.RADIAL_NUMBER.getValueMin();
                        }
                        this.reinit = true;
                    }));
            super.addDefaultButtons();
        }
    }

    @Override
    protected void loadDefaults() {
        super.loadDefaults();
        this.vrSettings.vrRadialItems = this.vrSettings.getRadialItemsDefault();
        this.vrSettings.vrRadialItemsAlt = this.vrSettings.getRadialItemsAltDefault();
    }

    @Override
    protected boolean onDoneClicked() {
        if (this.isselectmode) {
            this.isselectmode = false;
            this.reinit = true;
            this.visibleList = null;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        super.render(poseStack, mouseX, mouseY, partialTick);

        if (this.visibleList == null) {
            drawCenteredString(poseStack, this.minecraft.font,
                new TranslatableComponent("vivecraft.messages.radialmenubind.1"), this.width / 2, this.height - 50,
                0x55FF55);

            if (this.isShift) {
                drawCenteredString(poseStack, this.minecraft.font,
                    new TranslatableComponent("vivecraft.messages.radialmenubind.2"), this.width / 2, this.height - 36,
                    0xD23877);
                drawCenteredString(poseStack, this.minecraft.font,
                    new TranslatableComponent("vivecraft.messages.radialmenubind.3"), this.width / 2, this.height - 22,
                    0xD23877);
            }
        }
    }
}
