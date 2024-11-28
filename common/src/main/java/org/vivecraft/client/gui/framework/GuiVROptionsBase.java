package org.vivecraft.client.gui.framework;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.phys.Vec2;
import org.lwjgl.glfw.GLFW;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.VRSettings;

import java.util.ArrayList;
import java.util.List;

public abstract class GuiVROptionsBase extends Screen {
    protected ClientDataHolderVR dataHolder = ClientDataHolderVR.getInstance();
    protected final Screen lastScreen;
    protected final VRSettings vrSettings;
    protected boolean reinit;
    protected boolean drawDefaultButtons = true;
    protected ObjectSelectionList visibleList = null;
    private int nextButtonIndex = 0;
    public String vrTitle = "Title";
    private Button btnDone;
    private Button btnDefaults;

    public GuiVROptionsBase(Screen lastScreen) {
        super(Component.literal(""));
        this.lastScreen = lastScreen;
        this.vrSettings = ClientDataHolderVR.getInstance().vrSettings;
    }

    protected void addDefaultButtons() {
        this.addRenderableWidget(this.btnDone = new Button.Builder(Component.translatable("gui.back"), (p) ->
        {
            if (!this.onDoneClicked()) {
                this.dataHolder.vrSettings.saveOptions();
                this.minecraft.setScreen(this.lastScreen);
            }
        })
            .pos(this.width / 2 + 5, this.height - 30)
            .size(150, 20)
            .build());
        this.addRenderableWidget(this.btnDefaults = new Button.Builder(Component.translatable("vivecraft.gui.loaddefaults"), (p) ->
        {
            this.loadDefaults();
            this.dataHolder.vrSettings.saveOptions();
            this.reinit = true;
        })
            .pos(this.width / 2 - 155, this.height - 30)
            .size(150, 20)
            .build());
    }

    protected boolean onDoneClicked() {
        return false;
    }

    /**
     * Adds the buttons (and other controls) to the screen in question. Called when the GUI is displayed and when the
     * window resizes, the buttonList is cleared beforehand.
     */
    protected void init(VROptionLayout[] settings, boolean clear) {
        // init with a complete layout
        if (clear) {
            this.clearWidgets();
        }

        for (final VROptionLayout layout : settings) {
            if (layout.getOption() != null && layout.getOption().getEnumFloat()) {
                // Option Slider
                this.addRenderableWidget(new GuiVROptionSlider(layout.getOrdinal(), layout.getX(this.width), layout.getY(this.height), layout.getOption()) {
                    @Override
                    public void onClick(double mouseX, double mouseY) {
                        if (layout.getCustomHandler() == null || !layout.getCustomHandler().apply(this, new Vec2((float) mouseX, (float) mouseY))) {
                            super.onClick(mouseX, mouseY);
                        }
                    }
                });
            } else if (layout.getOption() != null) {
                // Option Button
                this.addRenderableWidget(new GuiVROptionButton(layout.getOrdinal(), layout.getX(this.width), layout.getY(this.height), layout.getOption(), layout.getButtonText(), (p) ->
                {
                    if (layout.getCustomHandler() == null || !layout.getCustomHandler().apply((GuiVROptionButton) p, new Vec2(0.0F, 0.0F))) {
                        this.vrSettings.setOptionValue(((GuiVROptionButton) p).getOption());
                        p.setMessage(Component.literal(layout.getButtonText()));
                    }
                }));
            } else if (layout.getScreen() != null) {
                // Screen button
                this.addRenderableWidget(new GuiVROptionButton(layout.getOrdinal(), layout.getX(this.width), layout.getY(this.height), layout.getButtonText(), (p) ->
                {
                    try {
                        if (layout.getCustomHandler() != null && layout.getCustomHandler().apply((GuiVROptionButton) p, new Vec2(0.0F, 0.0F))) {
                            return;
                        }

                        this.vrSettings.saveOptions();
                        this.minecraft.setScreen(layout.getScreen().getConstructor(Screen.class).newInstance(this));
                    } catch (ReflectiveOperationException e) {
                        VRSettings.LOGGER.error("Vivecraft: error setting screen: ", e);
                    }
                }));
            } else if (layout.getCustomHandler() != null) {
                // Custom click handler button
                this.addRenderableWidget(new GuiVROptionButton(layout.getOrdinal(), layout.getX(this.width), layout.getY(this.height), layout.getButtonText(), (p) ->
                {
                    layout.getCustomHandler().apply((GuiVROptionButton) p, new Vec2(0.0F, 0.0F));
                }));
            } else {
                // just a button, do something with it on your own time.
                this.addRenderableWidget(new GuiVROptionButton(layout.getOrdinal(), layout.getX(this.width), layout.getY(this.height), layout.getButtonText(), (p) -> {}));
            }
        }
    }

    protected void loadDefaults() {
        for (GuiEventListener child : this.children()) {
            if (child instanceof GuiVROption optionButton) {
                this.vrSettings.loadDefault(optionButton.getOption());
            }
        }
    }

    protected void init(VROptionEntry[] settings, boolean clear) {
        // init with a generated layout from entries
        if (clear) {
            this.clearWidgets();
            this.nextButtonIndex = 0;
        }

        ArrayList<VROptionLayout> layouts = new ArrayList<>();

        if (this.nextButtonIndex < this.children().size()) {
            this.nextButtonIndex = this.children().size();
        }

        int nextIndex = this.nextButtonIndex;

        for (VROptionEntry setting : settings) {
            VROptionLayout.Position pos = setting.center ? VROptionLayout.Position.POS_CENTER :
                (nextIndex % 2 == 0 ? VROptionLayout.Position.POS_LEFT : VROptionLayout.Position.POS_RIGHT);

            if (setting.center && nextIndex % 2 != 0) {
                nextIndex++;
            }

            if (setting.option != null) {
                if (setting.option != VRSettings.VrOptions.DUMMY) {
                    layouts.add(new VROptionLayout(setting.option, setting.customHandler, pos,
                        (float) Math.floor(nextIndex / 2.0F), true, setting.title));
                }
            } else if (setting.customHandler != null) {
                layouts.add(new VROptionLayout(setting.customHandler, pos, (float) Math.floor(nextIndex / 2.0F), true,
                    setting.title));
            }

            if (setting.center) {
                nextIndex++;
            }

            nextIndex++;
        }

        this.nextButtonIndex = nextIndex;
        this.init(layouts.toArray(new VROptionLayout[0]), false);
    }

    protected void init(VRSettings.VrOptions[] settings, boolean clear) {
        // init with just a list and no special handlers
        VROptionEntry[] entries = new VROptionEntry[settings.length];

        for (int i = 0; i < settings.length; i++) {
            entries[i] = new VROptionEntry(settings[i]);
        }

        this.init(entries, clear);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.reinit) {
            this.reinit = false;
            // remember selected option
            VRSettings.VrOptions selected = this.getFocused() instanceof GuiVROption option ? option.getOption() : null;
            this.init();
            if (selected != null) {
                GuiEventListener newButton = this.children().stream().filter(listener -> listener instanceof GuiVROption option && option.getOption() == selected).findFirst().orElse(null);
                // refocus the new button, or clear it if it's not there
                this.setFocused(newButton);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (this.visibleList != null) {
            this.visibleList.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        guiGraphics.drawCenteredString(this.font, Component.translatable(this.vrTitle), this.width / 2, 15, 0xFFFFFF);

        if (this.btnDefaults != null) {
            this.btnDefaults.visible = this.drawDefaultButtons;
        }

        if (this.btnDone != null) {
            this.btnDone.visible = this.drawDefaultButtons;
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    protected void actionPerformed(AbstractWidget widget) {
    }

    protected void actionPerformedRightClick(AbstractWidget widget) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean success = super.mouseClicked(mouseX, mouseY, button);

        if (success && getFocused() instanceof AbstractWidget widget) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                this.actionPerformed(widget);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.actionPerformedRightClick(widget);
            }
        } else if (this.visibleList != null) {
            return this.visibleList.mouseClicked(mouseX, mouseY, button);
        }

        return success;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.visibleList != null ? this.visibleList.mouseReleased(mouseX, mouseY, button) : super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.visibleList != null ? this.visibleList.mouseDragged(mouseX, mouseY, button, dragX, dragY) : super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.visibleList != null) {
            this.visibleList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (!this.onDoneClicked()) {
                this.dataHolder.vrSettings.saveOptions();
                this.minecraft.setScreen(this.lastScreen);
            }

            return true;
        } else {
            if (super.keyPressed(keyCode, scanCode, modifiers)) {
                if (this.getFocused() instanceof AbstractWidget widget) {
                    this.actionPerformed(widget);
                }
                return true;
            } else {
                return this.visibleList != null && this.visibleList.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return this.visibleList != null && this.visibleList.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    private void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        GuiEventListener hover = null;

        if (this.minecraft.getLastInputType().isKeyboard()) {
            // only show focused tooltip when navigating with keyboard, so a click with the mouse removes it
            hover = this.getFocused();
        }
        // find active button
        if (hover == null) {
            for (GuiEventListener child : children()) {
                if (child instanceof AbstractWidget widget && this.isMouseOver(widget, mouseX, mouseY)) {
                    hover = child;
                }
            }
        }
        if (hover != null) {
            if (hover instanceof GuiVROption guiHover) {
                if (guiHover.getOption() != null) {
                    String tooltipString = "vivecraft.options." + guiHover.getOption().name() + ".tooltip";
                    String tooltip = "";
                    // check if it has a tooltip
                    if (I18n.exists(tooltipString)) {
                        tooltip = I18n.get(tooltipString, (Object) null);
                    }

                    if (this.dataHolder.vrSettings.overrides.hasSetting(guiHover.getOption())) {
                        VRSettings.ServerOverrides.Setting setting = this.dataHolder.vrSettings.overrides.getSetting(guiHover.getOption());
                        if (setting.isValueOverridden()) {
                            tooltip = I18n.get("vivecraft.message.overriddenbyserver") + tooltip;
                        } else if (setting.isFloat() && (setting.isValueMinOverridden() || setting.isValueMaxOverridden())) {
                            tooltip = I18n.get("vivecraft.message.limitedbyserver", setting.getValueMin(), setting.getValueMax()) + tooltip;
                        }
                    }
                    if (!tooltip.isEmpty()) {
                        // add format reset at line ends
                        tooltip = tooltip.replace("\n", "§r\n");

                        // make last line the roughly 308 wide
                        List<FormattedText> formattedText = this.font.getSplitter().splitLines(tooltip, 308, Style.EMPTY);
                        tooltip += " ".repeat((308 - (formattedText.isEmpty() ? 0 : this.font.width(formattedText.get(formattedText.size() - 1)))) / this.font.width(" "));

                        // if tooltip is not too low, draw below button, else above
                        if (guiHover.getY() + guiHover.getHeight() + formattedText.size() * (this.font.lineHeight + 1) + 14 < this.height) {
                            guiGraphics.renderTooltip(this.font, this.font.split(Component.literal(tooltip), 308), this.width / 2 - 166, guiHover.getY() + guiHover.getHeight() + 14);
                        } else {
                            guiGraphics.renderTooltip(this.font, this.font.split(Component.literal(tooltip), 308), this.width / 2 - 166, guiHover.getY() - formattedText.size() * (this.font.lineHeight + 1) + 9);
                        }
                    }
                }
            }
        }
    }

    private boolean isMouseOver(AbstractWidget widget, double mouseX, double mouseY) {
        return widget.visible && mouseX >= widget.getX() && mouseY >= widget.getY() && mouseX < (widget.getX() + widget.getWidth()) && mouseY < (widget.getY() + widget.getHeight());
    }
}
