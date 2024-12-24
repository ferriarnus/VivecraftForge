package org.vivecraft.client.gui.settings;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.vivecraft.client.gui.widgets.SettingsList;
import org.vivecraft.client_vr.ClientDataHolderVR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class GuiBlacklistEditor extends GuiListScreen {

    private List<String> elements;

    public GuiBlacklistEditor(Screen lastScreen) {
        super(Component.translatable("vivecraft.options.screen.blocklist"), lastScreen);
    }

    @Override
    protected void init() {
        clearWidgets();
        double scrollAmount = this.list != null ? this.list.getScrollAmount() : 0.0D;

        this.list = new SettingsList(this, this.minecraft, getEntries());
        this.list.setScrollAmount(scrollAmount);
        this.addWidget(this.list);

        this.addRenderableWidget(
            Button.builder(Component.translatable("vivecraft.gui.loaddefaults"), button -> {
                    ClientDataHolderVR.getInstance().vrSettings.vrServerBlacklist =
                        ClientDataHolderVR.getInstance().vrSettings.getServerBlacklistDefault();
                    ClientDataHolderVR.getInstance().vrSettings.saveOptions();
                    this.elements = null;
                    this.reinit = true;
                })
                .bounds(this.width / 2 - 155, this.height - 27, 150, 20)
                .build());

        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.back"), button -> this.onClose())
                .bounds(this.width / 2 + 5, this.height - 27, 150, 20)
                .build());
    }

    @Override
    public void onClose() {
        ClientDataHolderVR.getInstance().vrSettings.vrServerBlacklist = this.elements.toArray(new String[0]);
        ClientDataHolderVR.getInstance().vrSettings.saveOptions();
        super.onClose();
    }

    private List<String> getCurrentValues() {
        return this.list.children().stream().map(entry -> {
            if (entry instanceof ListValueEntry listValueEntry) {
                return listValueEntry.getString();
            } else {
                return "";
            }
        }).filter(string -> !string.isEmpty()).collect(Collectors.toList());
    }

    @Override
    protected List<SettingsList.BaseEntry> getEntries() {
        List<SettingsList.BaseEntry> entries = new LinkedList<>();
        if (this.elements == null) {
            this.elements = new ArrayList<>(Arrays.asList(ClientDataHolderVR.getInstance().vrSettings.vrServerBlacklist));
        }
        int i = 0;
        for (String item : this.elements) {
            EditBox box = new EditBox(Minecraft.getInstance().font, 0, 0, ListValueEntry.valueButtonWidth - 1, 20, Component.literal(item));
            box.setMaxLength(1000);
            box.setValue(item);
            int index = i++;
            box.setResponder(s -> this.elements.set(index, s));
            entries.add(new ListValueEntry(Component.empty(), box, button -> {
                this.elements.remove(index);
                this.reinit = true;
            }));
        }
        entries.add(new SettingsList.WidgetEntry(Component.translatable("vivecraft.options.addnew"), Button.builder(Component.literal("+"), button -> {
            this.elements = getCurrentValues();
            this.elements.add("");
            this.reinit = true;
        }).size(20, 20).build()));
        return entries;
    }

    private static class ListValueEntry extends SettingsList.WidgetEntry {
        public static final int valueButtonWidth = 280;

        private final Button deleteButton;

        public ListValueEntry(Component name, EditBox valueWidget, Button.OnPress deleteAction) {
            super(name, valueWidget);

            this.deleteButton = Button
                .builder(Component.literal("-"), deleteAction)
                .tooltip(Tooltip.create(Component.translatable("selectWorld.delete")))
                .bounds(0, 0, 20, 20).build();
        }

        @Override
        public void render(
            GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY,
            boolean hovering, float partialTick)
        {
            this.valueWidget.setX(left + -50);
            this.valueWidget.setY(top);
            this.valueWidget.render(guiGraphics, mouseX, mouseY, partialTick);
            this.deleteButton.setX(left + 230);
            this.deleteButton.setY(top);
            this.deleteButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.valueWidget, this.deleteButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(this.valueWidget, this.deleteButton);
        }

        public String getString() {
            return ((EditBox) this.valueWidget).getValue();
        }
    }
}
