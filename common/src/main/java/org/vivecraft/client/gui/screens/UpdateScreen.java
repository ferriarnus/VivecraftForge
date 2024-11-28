package org.vivecraft.client.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.vivecraft.client.gui.widgets.TextScrollWidget;
import org.vivecraft.client.utils.UpdateChecker;


public class UpdateScreen extends Screen {

    private final Screen lastScreen;

    public UpdateScreen() {
        super(Component.translatable("vivecraft.messages.updateTitle"));
        this.lastScreen = Minecraft.getInstance().screen;
    }

    @Override
    protected void init() {

        this.addRenderableWidget(new TextScrollWidget(this.width / 2 - 155, 30, 310, this.height - 30 - 60, UpdateChecker.CHANGELOG));

        this.addRenderableWidget(new Button.Builder(Component.translatable("vivecraft.gui.downloadfrom", Component.literal("Modrinth")),
            ConfirmLinkScreen.confirmLink(this, "https://modrinth.com/mod/vivecraft"))
            .pos(this.width / 2 - 155, this.height - 56)
            .size(150, 20)
            .build());

        this.addRenderableWidget(new Button.Builder(Component.translatable("vivecraft.gui.downloadfrom", Component.literal("CurseForge")),
            ConfirmLinkScreen.confirmLink(this, "https://www.curseforge.com/minecraft/mc-mods/vivecraft"))
            .pos(this.width / 2 + 5, this.height - 56)
            .size(150, 20)
            .build());

        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.back"), (p) ->
            Minecraft.getInstance().setScreen(this.lastScreen))
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
        this.minecraft.setScreen(this.lastScreen);
    }
}
