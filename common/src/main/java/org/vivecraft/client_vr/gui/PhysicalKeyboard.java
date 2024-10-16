package org.vivecraft.client_vr.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.vivecraft.client.utils.Utils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.InputSimulator;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.settings.OptionEnum;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_vr.utils.RGBAColor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class PhysicalKeyboard {
    private static final int ROWS = 4;
    private static final int COLUMNS = 13;
    private static final float SPACING = 0.0064F;
    private static final float KEY_WIDTH = 0.04F;
    private static final float KEY_HEIGHT = 0.04F;
    private static final float KEY_WIDTH_SPECIAL = KEY_WIDTH * 2 + SPACING;

    private final Minecraft mc = Minecraft.getInstance();
    private final ClientDataHolderVR dh = ClientDataHolderVR.getInstance();
    private boolean reinit;
    private boolean shift;
    private boolean shiftSticky;
    private final List<KeyButton> keys;
    private int rows;
    private int columns;
    private float spacing;
    private float keyWidth;
    private float keyHeight;
    private float keyWidthSpecial;
    private float scale = 1.0F;
    private final KeyButton[] pressedKey = new KeyButton[2];
    private final long[] pressTime = new long[2];
    private final long[] pressRepeatTime = new long[2];
    private long shiftPressTime;
    private boolean lastPressedShift;
    private Supplier<String> easterEggText = () -> {
        int[] data = {0xbc, 0xa1, 0xb7, 0xaf, 0xa2, 0xee, 0xbc, 0xaf, 0xa7, 0xa0, 0xac, 0xa1, 0xb9};
        byte[] bytes = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            bytes[i] = (byte) (data[i] ^ 0xce);
        }
        String str = new String(bytes, StandardCharsets.UTF_8);
        this.easterEggText = () -> str; // poor man's lazy init
        return str;
    };
    private int easterEggIndex = 0;
    private boolean easterEggActive;
    private final Map<Integer, RGBAColor> customTheme = new HashMap<>();

    public PhysicalKeyboard() {
        this.keys = new ArrayList<>();
    }

    public void init() {
        this.unpressAllKeys();
        this.keys.clear();

        this.rows = ROWS;
        this.columns = COLUMNS;
        this.spacing = SPACING * this.scale;
        this.keyWidth = KEY_WIDTH * this.scale;
        this.keyHeight = KEY_HEIGHT * this.scale;
        this.keyWidthSpecial = KEY_WIDTH_SPECIAL * this.scale;

        String chars = this.dh.vrSettings.keyboardKeys;
        if (this.shift) {
            chars = this.dh.vrSettings.keyboardKeysShift;
        }

        float calcRows = (float) chars.length() / (float) this.columns;
        if (Math.abs(this.rows - calcRows) > 0.01F) {
            this.rows = Mth.ceil(calcRows);
        }

        for (int row = 0; row < this.rows; row++) {
            for (int column = 0; column < this.columns; column++) {
                int index = row * this.columns + column;
                char ch = ' ';

                if (index < chars.length()) {
                    ch = chars.charAt(index);
                }

                final char buttonChar = ch;
                final int code = index < this.dh.vrSettings.keyboardCodes.length ?
                    this.dh.vrSettings.keyboardCodes[index] : GLFW.GLFW_KEY_UNKNOWN;
                this.addKey(new KeyButton(index, String.valueOf(ch),
                    this.keyWidthSpecial + this.spacing + column * (this.keyWidth + this.spacing),
                    row * (this.keyHeight + this.spacing), this.keyWidth, this.keyHeight)
                {
                    @Override
                    public void onPressed() {
                        InputSimulator.pressKeyForBind(code);
                        InputSimulator.typeChar(buttonChar);

                        if (!PhysicalKeyboard.this.shiftSticky) {
                            setShift(false, false);
                        }

                        if (buttonChar == '/' && PhysicalKeyboard.this.mc.screen == null) {
                            // this is dumb but whatever
                            InputSimulator.pressKey(GLFW.GLFW_KEY_SLASH);
                            InputSimulator.releaseKey(GLFW.GLFW_KEY_SLASH);
                        }
                    }

                    @Override
                    public void onReleased() {
                        InputSimulator.releaseKeyForBind(code);
                    }
                });
            }
        }

        // shift keys
        for (int i = 0; i < 2; i++) {
            this.addKey(new KeyButton(1000 + i, "Shift",
                i == 1 ? this.keyWidthSpecial + this.spacing + this.columns * (this.keyWidth + this.spacing) : 0.0F,
                3.0F * (this.keyHeight + this.spacing), this.keyWidthSpecial, this.keyHeight)
            {
                @Override
                public void onPressed() {
                    if (PhysicalKeyboard.this.shift && !PhysicalKeyboard.this.shiftSticky &&
                        Utils.milliTime() - PhysicalKeyboard.this.shiftPressTime < 400L)
                    {
                        setShift(true, true);
                    } else {
                        setShift(!PhysicalKeyboard.this.shift, false);
                    }

                    PhysicalKeyboard.this.shiftPressTime = Utils.milliTime();
                }

                @Override
                public RGBAColor getRenderColor() {
                    if (PhysicalKeyboard.this.shift) {
                        RGBAColor color = new RGBAColor(this.pressed ? 1.0F : 0.5F, this.pressed ? 1.0F : 0.5F, 0.0F,
                            0.5F);

                        if (!PhysicalKeyboard.this.shiftSticky) {
                            color.r = 0.0F;
                        }

                        return color;
                    }

                    return super.getRenderColor();
                }
            });
        }

        this.addKey(new KeyButton(1002, " ",
            this.keyWidthSpecial + this.spacing + (this.columns - 5) / 2.0F * (this.keyWidth + this.spacing),
            this.rows * (this.keyHeight + this.spacing), 5.0F * (this.keyWidth + this.spacing) - this.spacing,
            this.keyHeight)
        {
            @Override
            public void onPressed() {
                InputSimulator.pressKeyForBind(GLFW.GLFW_KEY_SPACE);
                InputSimulator.typeChar(' ');
            }

            @Override
            public void onReleased() {
                InputSimulator.releaseKeyForBind(GLFW.GLFW_KEY_SPACE);
            }
        });

        this.addKey(new KeyPressButton(1003, "Tab",
            0.0F,
            this.keyHeight + this.spacing, this.keyWidthSpecial, this.keyHeight, GLFW.GLFW_KEY_TAB));

        this.addKey(new KeyPressButton(1004, "Esc",
            0.0F,
            0.0F, this.keyWidthSpecial, this.keyHeight, GLFW.GLFW_KEY_ESCAPE));

        this.addKey(new KeyPressButton(1005, "Bksp",
            this.keyWidthSpecial + this.spacing + this.columns * (this.keyWidth + this.spacing),
            0.0F, this.keyWidthSpecial, this.keyHeight, GLFW.GLFW_KEY_BACKSPACE));

        this.addKey(new KeyPressButton(1006, "Enter",
            this.keyWidthSpecial + this.spacing + this.columns * (this.keyWidth + this.spacing),
            2.0F * (this.keyHeight + this.spacing), this.keyWidthSpecial, this.keyHeight, GLFW.GLFW_KEY_ENTER));

        this.addKey(new KeyPressButton(1007, "\u2191",
            this.keyWidthSpecial + this.spacing + (this.columns + 1) * (this.keyWidth + this.spacing),
            4.0F * (this.keyHeight + this.spacing), this.keyWidth, this.keyHeight, GLFW.GLFW_KEY_UP));

        this.addKey(new KeyPressButton(1008, "\u2193",
            this.keyWidthSpecial + this.spacing + (this.columns + 1) * (this.keyWidth + this.spacing),
            5.0F * (this.keyHeight + this.spacing), this.keyWidth, this.keyHeight, GLFW.GLFW_KEY_DOWN));

        this.addKey(new KeyPressButton(1009, "\u2190",
            this.keyWidthSpecial + this.spacing + this.columns * (this.keyWidth + this.spacing),
            5.0F * (this.keyHeight + this.spacing), this.keyWidth, this.keyHeight, GLFW.GLFW_KEY_LEFT));

        this.addKey(new KeyPressButton(1010, "\u2192",
            this.keyWidthSpecial + this.spacing + (this.columns + 2) * (this.keyWidth + this.spacing),
            5.0F * (this.keyHeight + this.spacing), this.keyWidth, this.keyHeight, GLFW.GLFW_KEY_RIGHT));

        this.addKey(new KeyButton(1011, "Cut",
            (this.keyWidthSpecial + this.spacing),
            -1.0F * (this.keyHeight + this.spacing), this.keyWidthSpecial, this.keyHeight)
        {
            @Override
            public void onPressed() {
                InputSimulator.pressKey(GLFW.GLFW_KEY_LEFT_CONTROL);
                InputSimulator.pressKey(GLFW.GLFW_KEY_X);
                InputSimulator.releaseKey(GLFW.GLFW_KEY_X);
                InputSimulator.releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL);
            }
        });

        this.addKey(new KeyButton(1012, "Copy",
            2.0F * (this.keyWidthSpecial + this.spacing),
            -1.0F * (this.keyHeight + this.spacing), this.keyWidthSpecial, this.keyHeight)
        {
            @Override
            public void onPressed() {
                InputSimulator.pressKey(GLFW.GLFW_KEY_LEFT_CONTROL);
                InputSimulator.pressKey(GLFW.GLFW_KEY_C);
                InputSimulator.releaseKey(GLFW.GLFW_KEY_C);
                InputSimulator.releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL);
            }
        });

        this.addKey(new KeyButton(1013, "Paste",
            3.0F * (this.keyWidthSpecial + this.spacing),
            -1.0F * (this.keyHeight + this.spacing), this.keyWidthSpecial, this.keyHeight)
        {
            @Override
            public void onPressed() {
                InputSimulator.pressKey(GLFW.GLFW_KEY_LEFT_CONTROL);
                InputSimulator.pressKey(GLFW.GLFW_KEY_V);
                InputSimulator.releaseKey(GLFW.GLFW_KEY_V);
                InputSimulator.releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL);
            }
        });

        // Set pressed keys to the new objects
        for (int c = 0; c < 2; c++) {
            if (this.pressedKey[c] != null) {
                for (KeyButton key : this.keys) {
                    if (key.id == this.pressedKey[c].id) {
                        this.pressedKey[c] = key;
                        key.pressed = true;
                        break;
                    }
                }
            }
        }

        if (this.dh.vrSettings.physicalKeyboardTheme == KeyboardTheme.CUSTOM) {
            this.customTheme.clear();
            File themeFile = new File(this.mc.gameDirectory, "keyboardtheme.txt");
            if (!themeFile.exists()) {
                // Write template theme file
                try (PrintWriter pw = new PrintWriter(new FileWriter(themeFile, StandardCharsets.UTF_8))) {
                    char[] normalChars = this.dh.vrSettings.keyboardKeys.toCharArray();
                    for (int i = 0; i < normalChars.length; i++) {
                        pw.println("# " + normalChars[i] + " (Normal)");
                        pw.println(i + "=255,255,255");
                    }
                    char[] shiftChars = this.dh.vrSettings.keyboardKeysShift.toCharArray();
                    for (int i = 0; i < shiftChars.length; i++) {
                        pw.println("# " + shiftChars[i] + " (Shifted)");
                        pw.println((i + 500) + "=255,255,255");
                    }
                    this.keys.forEach(button -> {
                        if (button.id >= 1000) {
                            pw.println("# " + button.label);
                            pw.println(button.id + "=255,255,255");
                        }
                    });
                } catch (IOException ex) {
                    VRSettings.LOGGER.error("Vivecraft: error creating keyboard template: ", ex);
                }
            } else {
                // Load theme file
                try (Stream<String> lines = Files.lines(Paths.get(themeFile.toURI()), StandardCharsets.UTF_8)) {
                    lines.forEach(line -> {
                        if (line.isEmpty() || line.charAt(0) == '#') {
                            return;
                        }
                        try {
                            String[] split = line.split("=", 2);
                            int id = Integer.parseInt(split[0]);
                            String[] colorSplit = split[1].split(",");
                            RGBAColor color = new RGBAColor(Integer.parseInt(colorSplit[0]),
                                Integer.parseInt(colorSplit[1]), Integer.parseInt(colorSplit[2]), 255);
                            this.customTheme.put(id, color);
                        } catch (Exception ex) {
                            VRSettings.LOGGER.error("Vivecraft: error reading keyboard theme line: {}:", line, ex);
                        }
                    });
                } catch (IOException ex) {
                    VRSettings.LOGGER.error("Vivecraft: error reading keyboard theme:", ex);
                }
            }
        }

        this.reinit = false;
    }

    public void process() {
        if (this.reinit) {
            this.init();
        }

        for (int c = 0; c < 2; ++c) {
            ControllerType controller = ControllerType.values()[c];
            KeyButton key = this.findTouchedKey(controller);

            long milliTime = Utils.milliTime();
            if (key != null) {
                if (key != this.pressedKey[c] && milliTime - this.pressTime[c] >= 150L) {
                    if (this.pressedKey[c] != null) {
                        this.pressedKey[c].unpress();
                        this.pressedKey[c] = null;
                    }

                    key.press(controller, false);
                    this.pressedKey[c] = key;
                    this.pressTime[c] = milliTime;
                    this.pressRepeatTime[c] = milliTime;
                } else if (key == this.pressedKey[c] && milliTime - this.pressTime[c] >= 500L &&
                    milliTime - this.pressRepeatTime[c] >= 100L)
                {
                    key.press(controller, true);
                    this.pressRepeatTime[c] = milliTime;
                }
            } else if (this.pressedKey[c] != null) {
                this.pressedKey[c].unpress();
                this.pressedKey[c] = null;
                this.pressTime[c] = milliTime;
            }
        }
    }

    public void processBindings() {
        if (GuiHandler.KEY_KEYBOARD_SHIFT.consumeClick()) {
            this.setShift(true, true);
            this.lastPressedShift = true;
        }

        if (!GuiHandler.KEY_KEYBOARD_SHIFT.isDown() && this.lastPressedShift) {
            this.setShift(false, false);
            this.lastPressedShift = false;
        }
    }

    private Vector3f getCenterPos() {
        return new Vector3f(
            ((this.keyWidth + this.spacing) * (this.columns + this.columns % 2.0F / 2.0F) +
                (this.keyWidthSpecial + this.spacing) * 2.0F
            ) / 2.0F,
            (this.keyHeight + this.spacing) * (this.rows + 1),
            0.0F);
    }

    private KeyButton findTouchedKey(ControllerType controller) {
        // Transform the controller into keyboard space
        Matrix4f matrix = new Matrix4f();
        matrix.translate(this.getCenterPos());
        matrix.mul(new Matrix4f(KeyboardHandler.ROTATION_ROOM).invert());
        matrix.translate(-KeyboardHandler.POS_ROOM.x, -KeyboardHandler.POS_ROOM.y, -KeyboardHandler.POS_ROOM.z);

        Vector3f pos = matrix.transformPosition(this.dh.vrPlayer.vrdata_room_pre.getController(controller.ordinal()).getPositionF());

        // Do intersection checks
        for (KeyButton key : this.keys) {
            if (key.getCollisionBoundingBox().contains(pos.x, pos.y, pos.z)) {
                return key;
            }
        }

        return null;
    }

    private void updateEasterEgg(String label) {
        String text = this.easterEggText.get();
        if (this.easterEggIndex < text.length()) {
            if (label.toLowerCase().equals(String.valueOf(text.charAt(this.easterEggIndex)))) {
                this.easterEggIndex++;
            } else {
                this.easterEggIndex = 0;
            }
        } else if (label.equals("Enter")) {
            this.easterEggActive = !this.easterEggActive;
        } else {
            this.easterEggIndex = 0;
        }
    }

    private void drawBox(BufferBuilder buf, AABB box, RGBAColor color, PoseStack poseStack) {
        // Alright let's draw a box
        org.joml.Matrix4f matrix = poseStack.last().pose();
        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;
        buf.vertex(matrix, minX, minY, minZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, 0.0F, -1.0F).endVertex();
        buf.vertex(matrix, minX, maxY, minZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, 0.0F, -1.0F).endVertex();
        buf.vertex(matrix, maxX, maxY, minZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, 0.0F, -1.0F).endVertex();
        buf.vertex(matrix, maxX, minY, minZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, 0.0F, -1.0F).endVertex();

        buf.vertex(matrix, minX, minY, minZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, -1.0F, 0.0F).endVertex();
        buf.vertex(matrix, maxX, minY, minZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, -1.0F, 0.0F).endVertex();
        buf.vertex(matrix, maxX, minY, maxZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, -1.0F, 0.0F).endVertex();
        buf.vertex(matrix, minX, minY, maxZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, -1.0F, 0.0F).endVertex();

        buf.vertex(matrix, minX, minY, minZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(-1.0F, 0.0F, 0.0F).endVertex();
        buf.vertex(matrix, minX, minY, maxZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(-1.0F, 0.0F, 0.0F).endVertex();
        buf.vertex(matrix, minX, maxY, maxZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(-1.0F, 0.0F, 0.0F).endVertex();
        buf.vertex(matrix, minX, maxY, minZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(-1.0F, 0.0F, 0.0F).endVertex();

        buf.vertex(matrix, maxX, maxY, maxZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, 0.0F, 1.0F).endVertex();
        buf.vertex(matrix, minX, maxY, maxZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, 0.0F, 1.0F).endVertex();
        buf.vertex(matrix, minX, minY, maxZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, 0.0F, 1.0F).endVertex();
        buf.vertex(matrix, maxX, minY, maxZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, 0.0F, 1.0F).endVertex();

        buf.vertex(matrix, maxX, maxY, maxZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, 1.0F, 0.0F).endVertex();
        buf.vertex(matrix, maxX, maxY, minZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, 1.0F, 0.0F).endVertex();
        buf.vertex(matrix, minX, maxY, minZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, 1.0F, 0.0F).endVertex();
        buf.vertex(matrix, minX, maxY, maxZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(0.0F, 1.0F, 0.0F).endVertex();

        buf.vertex(matrix, maxX, maxY, maxZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(1.0F, 0.0F, 0.0F).endVertex();
        buf.vertex(matrix, maxX, minY, maxZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(1.0F, 0.0F, 0.0F).endVertex();
        buf.vertex(matrix, maxX, minY, minZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(1.0F, 0.0F, 0.0F).endVertex();
        buf.vertex(matrix, maxX, maxY, minZ).uv(0, 0)
            .color(color.r, color.g, color.b, color.a)
            .normal(1.0F, 0.0F, 0.0F).endVertex();
        // Woo that was fun
    }

    public void render(PoseStack poseStack) {
        poseStack.pushPose();
        Vector3f center = this.getCenterPos();
        poseStack.translate(-center.x, -center.y, -center.z);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();

        if (this.easterEggActive) {
            // https://qimg.techjargaming.com/i/UkG1cWAh.png
            for (KeyButton key : this.keys) {
                RGBAColor color = RGBAColor.fromHSB((this.dh.tickCounter + this.mc.getFrameTime()) / 100.0F +
                        (float) (key.boundingBox.minX + (key.boundingBox.maxX - key.boundingBox.minX) / 2.0D) / 2.0F, 1.0F,
                    1.0F);
                key.color.r = color.r;
                key.color.g = color.g;
                key.color.b = color.b;
            }
        } else {
            this.keys.forEach(button -> {
                if (this.dh.vrSettings.physicalKeyboardTheme == KeyboardTheme.CUSTOM) {
                    RGBAColor color = this.customTheme.get(this.shift && button.id < 1000 ? button.id + 500 : button.id);
                    if (color != null) {
                        button.color.r = color.r;
                        button.color.g = color.g;
                        button.color.b = color.b;
                    }
                } else {
                    this.dh.vrSettings.physicalKeyboardTheme.assignColor(button);
                }
            });
        }

        RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);

        // TODO: does this still do the right thing for shaders?
        this.mc.getTextureManager().bindForSetup(new ResourceLocation("vivecraft:textures/white.png"));
        RenderSystem.setShaderTexture(0, new ResourceLocation("vivecraft:textures/white.png"));

        // We need to ignore depth so we can see the back faces and text
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        // Stuff for drawing labels
        Font font = this.mc.font;
        ArrayList<Tuple<String, Vector3f>> labels = new ArrayList<>();
        float textScale = 0.002F * this.scale;

        // Start building vertices for key boxes
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        buf.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);

        for (KeyButton key : this.keys) {
            AABB box = key.getRenderBoundingBox();
            RGBAColor color = key.getRenderColor();

            // Draw the key itself
            this.drawBox(buf, box, color, poseStack);

            // Calculate text position
            float stringWidth = (float) font.width(key.label) * textScale;
            float stringHeight = font.lineHeight * textScale;
            float textX = (float) box.minX + ((float) box.maxX - (float) box.minX) / 2.0F - stringWidth / 2.0F;
            float textY = (float) box.minY + ((float) box.maxY - (float) box.minY) / 2.0F - stringHeight / 2.0F;
            float textZ = (float) box.minZ + ((float) box.maxZ - (float) box.minZ) / 2.0F;

            // Put label in the list
            labels.add(new Tuple<>(key.label, new Vector3f(textX, textY, textZ)));
        }

        // Draw all the key boxes
        tesselator.end();

        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        //GlStateManager._disableLighting();

        // Start building vertices for text
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(tesselator.getBuilder());

        // Build all the text
        for (Tuple<String, Vector3f> label : labels) {
            poseStack.pushPose();
            poseStack.translate(label.getB().x, label.getB().y, label.getB().z);
            poseStack.scale(textScale, textScale, 1.0F);
            font.drawInBatch(label.getA(), 0.0F, 0.0F, 0xFFFFFFFF, false, poseStack.last().pose(), bufferSource,
                Font.DisplayMode.NORMAL, 0, 15728880, font.isBidirectional());
            poseStack.popPose();
        }

        // Draw all the labels
        bufferSource.endBatch();
        //GlStateManager._enableLighting();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        poseStack.popPose();
    }

    public void show() {
        if (!this.shiftSticky) {
            this.shift = false;
        }

        this.scale = this.dh.vrSettings.physicalKeyboardScale;
        this.reinit = true;
    }

    public void unpressAllKeys() {
        for (KeyButton key : this.keys) {
            if (key.pressed) {
                key.unpress();
            }
        }
    }

    private KeyButton addKey(KeyButton key) {
        this.keys.add(key);
        return key;
    }

    public boolean isShift() {
        return this.shift;
    }

    public boolean isShiftSticky() {
        return this.shiftSticky;
    }

    public void setShift(boolean shift, boolean sticky) {
        if (shift != this.shift || sticky != this.shiftSticky) {
            this.shift = shift;
            this.shiftSticky = shift && sticky;
            this.reinit = true;
        }
    }

    public float getScale() {
        return this.scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
        this.reinit = true;
    }

    private class KeyPressButton extends KeyButton {

        private final int keyCode;

        public KeyPressButton(int id, String label, float x, float y, float width, float height, int keyCode) {
            super(id, label, x, y, width, height);
            this.keyCode = keyCode;
        }

        @Override
        public void onPressed() {
            InputSimulator.pressKey(this.keyCode);
        }

        @Override
        public void onReleased() {
            InputSimulator.releaseKey(this.keyCode);
        }
    }

    private abstract class KeyButton {
        public final int id;
        public final String label;
        public final AABB boundingBox;
        public RGBAColor color = new RGBAColor(1.0F, 1.0F, 1.0F, 0.5F);
        public boolean pressed;

        public KeyButton(int id, String label, float x, float y, float width, float height) {
            this.id = id;
            this.label = label;
            this.boundingBox = new AABB(x, y, 0.0D, x + width, y + height, 0.028D * PhysicalKeyboard.this.scale);
        }

        public AABB getRenderBoundingBox() {
            return this.pressed ? this.boundingBox.move(0.0D, 0.0D, 0.012D * PhysicalKeyboard.this.scale) : this.boundingBox;
        }

        public AABB getCollisionBoundingBox() {
            return this.pressed ? this.boundingBox.expandTowards(0.0D, 0.0D, 0.08D) : this.boundingBox;
        }

        public RGBAColor getRenderColor() {
            RGBAColor color = this.color.copy();

            if (!this.pressed) {
                color.r *= 0.5F;
                color.g *= 0.5F;
                color.b *= 0.5F;
            }

            return color;
        }

        public final void press(ControllerType controller, boolean isRepeat) {
            if (!isRepeat) {
                PhysicalKeyboard.this.mc.getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }

            MCVR.get().triggerHapticPulse(controller, isRepeat ? 300 : 600);
            this.pressed = true;
            this.onPressed();
            updateEasterEgg(this.label);
        }

        public final void unpress() {
            this.pressed = false;
            this.onReleased();
        }

        public abstract void onPressed();

        public void onReleased() {
        }
    }

    public enum KeyboardTheme implements OptionEnum<KeyboardTheme> {
        DEFAULT {
            @Override
            public void assignColor(KeyButton button) {
                button.color.r = 1.0F;
                button.color.g = 1.0F;
                button.color.b = 1.0F;
            }
        },
        RED {
            @Override
            public void assignColor(KeyButton button) {
                button.color.r = 1.0F;
                button.color.g = 0.0F;
                button.color.b = 0.0F;
            }
        },
        GREEN {
            @Override
            public void assignColor(KeyButton button) {
                button.color.r = 0.0F;
                button.color.g = 1.0F;
                button.color.b = 0.0F;
            }
        },
        BLUE {
            @Override
            public void assignColor(KeyButton button) {
                button.color.r = 0.0F;
                button.color.g = 0.0F;
                button.color.b = 1.0F;
            }
        },
        BLACK {
            @Override
            public void assignColor(KeyButton button) {
                button.color.r = 0.0F;
                button.color.g = 0.0F;
                button.color.b = 0.0F;
            }
        },
        GRASS {
            @Override
            public void assignColor(KeyButton button) {
                if (button.boundingBox.maxY < 0.07D) {
                    button.color.r = 0.321F;
                    button.color.g = 0.584F;
                    button.color.b = 0.184F;
                } else {
                    button.color.r = 0.607F;
                    button.color.g = 0.462F;
                    button.color.b = 0.325F;
                }
            }
        },
        BEES {
            @Override
            public void assignColor(KeyButton button) {
                float val = button.boundingBox.maxX % 0.2D < 0.1D ? 1.0F : 0.0F;
                button.color.r = val;
                button.color.g = val;
                button.color.b = 0.0F;
            }
        },
        AESTHETIC {
            @Override
            public void assignColor(KeyButton button) {
                if (button.id >= 1000) {
                    button.color.r = 0.0F;
                    button.color.g = 1.0F;
                    button.color.b = 1.0F;
                } else {
                    button.color.r = 1.0F;
                    button.color.g = 0.0F;
                    button.color.b = 1.0F;
                }
            }
        },
        DOSE {
            @Override
            public void assignColor(KeyButton button) {
                button.color.r = button.id % 2 == 0 ? 0.5F : 0.0F;
                button.color.g = button.id % 2 == 0 ? 0.0F : 1.0F;
                button.color.b = button.id % 2 == 0 ? 1.0F : 0.0F;
            }
        },
        CUSTOM {
            @Override
            public void assignColor(KeyButton button) {
                // Handled elsewhere
            }
        };

        public abstract void assignColor(KeyButton button);
    }
}
