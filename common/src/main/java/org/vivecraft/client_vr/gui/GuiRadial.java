package org.vivecraft.client_vr.gui;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.vivecraft.client.gui.framework.TwoHandedScreen;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.control.VRInputAction;

public class GuiRadial extends TwoHandedScreen {
    private boolean isShift = false;
    String[] arr;

    @Override
    public void init() {
        this.arr = this.dh.vrSettings.vrRadialItems;
        String[] altSet = this.dh.vrSettings.vrRadialItemsAlt;
        this.clearWidgets();

        int numButtons = this.dh.vrSettings.vrRadialButtons;
        int buttonWidthMin = 120;
        // distance from the center, with 14 buttons, move them closer together
        float dist = numButtons * (numButtons >= 14 ? 5F : 5.5F);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (this.isShift) {
            this.arr = altSet;
        }

        for (int i = 0; i < numButtons; i++) {
            KeyMapping keymapping = null;

            for (KeyMapping keymapping1 : this.minecraft.options.keyMappings) {
                if (i < this.arr.length && keymapping1.getName().equalsIgnoreCase(this.arr[i])) {
                    keymapping = keymapping1;
                }
            }

            String label = "?";

            if (keymapping != null) {
                label = I18n.get(keymapping.getName());
            }

            int buttonWidth = Math.max(buttonWidthMin, this.font.width(label));
            // coords of the button, button 0 is at the top with x = 0, y = -dist
            float distX = numButtons * 4 + buttonWidth * 0.5F;

            // position buttons on equal y spacing
            float btnIndex = (i < numButtons / 2 ? i : numButtons - i) / (float) (numButtons / 2);
            int y = (int) (2.0F * dist * btnIndex - dist);

            // position x so the buttons produce an ellipse
            int x = (int) (distX * (Math.sqrt(1.0F - (y * y) / (dist * dist))));

            // move in between buttons closer to the middle
            if (Math.abs(y) > 20) {
                x = (int) (x * 0.87F);
            }

            // second half of buttons should be on the left side
            x *= i > numButtons / 2 ? -1 : 1;

            int index = i;

            if (!"?".equals(label)) {
                this.addRenderableWidget(new Button.Builder(Component.translatable(label),
                    (p) -> {
                        VRInputAction vrinputaction = MCVR.get().getInputAction(this.arr[index]);

                        if (vrinputaction != null) {
                            vrinputaction.pressBinding();
                            vrinputaction.unpressBinding(2);
                        }
                    })
                    .size(buttonWidth, 20)
                    .pos(centerX + x - buttonWidth / 2, centerY + y - 10)
                    .build());
            }
        }
    }

    public void setShift(boolean shift) {
        if (shift != this.isShift) {
            this.isShift = shift;
            this.init();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, 0, 0, partialTick);
    }
}
