package org.vivecraft.client.gui.settings;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;

public class GuiRadialItemsList extends ObjectSelectionList<GuiRadialItemsList.BaseEntry> {

    private static final int MAX_LIST_LABEL_WIDTH = 300;

    private final GuiRadialConfiguration parent;

    public GuiRadialItemsList(GuiRadialConfiguration parent, Minecraft mc) {
        super(mc, parent.width, parent.height - 77, 49, 20);
        this.parent = parent;
        this.buildList();
    }

    public void buildList() {
        KeyMapping[] mappings = ArrayUtils.clone(this.minecraft.options.keyMappings);
        Arrays.sort(mappings);
        String currentCategory = null;

        for (KeyMapping keymapping : mappings) {
            String category = keymapping != null ? keymapping.getCategory() : null;

            if (category != null) {
                if (!category.equals(currentCategory)) {
                    currentCategory = category;
                    this.addEntry(new CategoryEntry(category));
                }

                this.addEntry(new MappingEntry(keymapping, this.parent));
            }
        }
    }

    public static class CategoryEntry extends BaseEntry {
        private final String labelText;
        private final int labelWidth;

        public CategoryEntry(String name) {
            this.labelText = I18n.get(name);
            this.labelWidth = Minecraft.getInstance().font.width(this.labelText);
        }

        @Override
        public void render(
            GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY,
            boolean hovering, float partialTick)
        {
            guiGraphics.drawString(Minecraft.getInstance().font, this.labelText, (Minecraft.getInstance().screen.width / 2 - this.labelWidth / 2),
                (top + height - 9 - 1), 0x67697F);
        }
    }

    public static class MappingEntry extends BaseEntry {
        private final KeyMapping myKey;
        private final GuiRadialConfiguration parentScreen;

        private MappingEntry(KeyMapping key, GuiRadialConfiguration parent) {
            this.myKey = key;
            this.parentScreen = parent;
        }

        @Override
        public void render(
            GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY,
            boolean hovering, float partialTick)
        {
            ChatFormatting chatformatting = ChatFormatting.WHITE;

            if (hovering) {
                chatformatting = ChatFormatting.GREEN;
            }

            guiGraphics.drawString(Minecraft.getInstance().font, chatformatting + I18n.get(this.myKey.getName()),
                (Minecraft.getInstance().screen.width / 2 - MAX_LIST_LABEL_WIDTH / 2), (top + height / 2 - 9 / 2), 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double pMouseX, double p_94738_, int pMouseY) {
            this.parentScreen.setKey(this.myKey);
            return true;
        }
    }

    public static abstract class BaseEntry extends Entry<BaseEntry> {

        public BaseEntry() {}

        @Override
        public Component getNarration() {
            return null;
        }
    }
}
