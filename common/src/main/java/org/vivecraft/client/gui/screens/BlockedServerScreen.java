package org.vivecraft.client.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BlockedServerScreen extends Screen {

    private final Screen lastScreen;
    private final String server;
    private final Runnable onContinue;
    private MultiLineLabel message;

    public BlockedServerScreen(Screen lastScreen, String server, Runnable onContinue) {
        super(Component.translatable("vivecraft.messages.blocklist.title"));
        this.lastScreen = lastScreen;
        this.server = server;
        this.onContinue = onContinue;
    }

    protected void init() {

        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.back"), (p) ->
            Minecraft.getInstance().setScreen(this.lastScreen))
            .pos(this.width / 2 + 5, this.height - 32)
            .size(150, 20)
            .build());
        this.addRenderableWidget(
            new Button.Builder(Component.translatable("vivecraft.gui.continueWithout"), p -> this.onContinue.run())
                .pos(this.width / 2 - 155, this.height - 32)
                .size(150, 20)
                .build());
        this.message = MultiLineLabel.create(this.font,
            Component.translatable("vivecraft.messages.blocklist", this.server), 360);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        this.message.renderCentered(guiGraphics, this.width / 2, 120);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
