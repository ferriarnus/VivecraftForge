package org.vivecraft.client.gui.screens;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.jetbrains.annotations.NotNull;
import org.vivecraft.client.gui.widgets.TextScrollWidget;
import org.vivecraft.client_vr.ClientDataHolderVR;


public class GarbageCollectorScreen extends Screen {

    private static final String GUIDE_URL = "https://github.com/Vivecraft/VivecraftMod/wiki/Memory-and-GC-Setup";

    private final Screen lastScreen;
    private final String currentGarbageCollector;

    public GarbageCollectorScreen(String currentGarbageCollector) {
        super(Component.translatable("vivecraft.messages.gctitle"));
        this.lastScreen = Minecraft.getInstance().screen;
        this.currentGarbageCollector = currentGarbageCollector;
    }

    @Override
    protected void init() {
        Component message = Component.translatable("vivecraft.messages.gcinfo",
            Component.literal(this.currentGarbageCollector).withStyle(s -> s.withColor(ChatFormatting.RED)),
            Component.literal("ZGC"),
            Component.literal(Integer.toString(6)),
            Component.literal("-XX:+UseZGC").withStyle(s -> s
                .withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "-XX:+UseZGC"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.copy.click")))),
            Component.translatable("vivecraft.gui.openguide").withStyle(style -> style
                .withUnderlined(true)
                .withColor(ChatFormatting.GREEN)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, CommonComponents.GUI_OPEN_IN_BROWSER))
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, GUIDE_URL))));
        this.addRenderableWidget(new TextScrollWidget(this.width / 2 - 155, 30, 310, this.height - 30 - 60, message));

        this.addRenderableWidget(new Button.Builder(Component.translatable("vivecraft.gui.dontshowagain"), (p) -> {
            ClientDataHolderVR.getInstance().vrSettings.disableGarbageCollectorMessage = true;
            ClientDataHolderVR.getInstance().vrSettings.saveOptions();
            Minecraft.getInstance().setScreen(this.lastScreen);
        })
            .pos(this.width / 2 - 155, this.height - 56)
            .size(150, 20)
            .build());

        this.addRenderableWidget(new Button.Builder(Component.translatable("vivecraft.gui.ok"), (p) ->
            onClose())
            .pos(this.width / 2 + 5, this.height - 56)
            .size(150, 20)
            .build());

        this.addRenderableWidget(new Button.Builder(Component.translatable("vivecraft.gui.openguide"),
            ConfirmLinkScreen.confirmLink(this, GUIDE_URL))
            .pos(this.width / 2 - 75, this.height - 32)
            .size(150, 20)
            .build());
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        ClientDataHolderVR.getInstance().incorrectGarbageCollector = "";
        this.minecraft.setScreen(this.lastScreen);
    }
}
