package org.vivecraft.client.utils;

import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.client.ComponentCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class TextUtils {
    private static final char[] ILLEGAL_CHARS = new char[]{'"', '<', '>', '|', '\u0000', '\u0001', '\u0002', '\u0003', '\u0004', '\u0005', '\u0006', '\u0007', '\b', '\t', '\n', '\u000b', '\f', '\r', '\u000e', '\u000f', '\u0010', '\u0011', '\u0012', '\u0013', '\u0014', '\u0015', '\u0016', '\u0017', '\u0018', '\u0019', '\u001a', '\u001b', '\u001c', '\u001d', '\u001e', '\u001f', ':', '*', '?', '\\', '/'};

    static {
        // Needs to be sorted for binary search
        Arrays.sort(ILLEGAL_CHARS);
    }

    /**
     * removes invalid path characters from a filename
     * @param fileName name to sanitize
     * @return sanitized filename
     */
    public static String sanitizeFileName(String fileName) {
        StringBuilder sanitized = new StringBuilder();

        for (int i = 0; i < fileName.length(); i++) {
            char ch = fileName.charAt(i);

            if (Arrays.binarySearch(ILLEGAL_CHARS, ch) < 0) {
                sanitized.append(ch);
            } else {
                sanitized.append('_');
            }
        }
        return sanitized.toString();
    }

    /**
     * splits the given {@code in} String into multiple lines, splits so that a line doesn't exceed {@code length} characters, and at new lines. The output is added to the provided list {@code wrapped}
     * With thanks to http://ramblingsrobert.wordpress.com/2011/04/13/java-word-wrap-algorithm/
     * @param in String to wrap
     * @param length max line length
     * @param wrapped List to add split lines to
     */
    public static void wordWrap(String in, int length, ArrayList<String> wrapped) {
        // can't wrap with length 0, so return the original string
        if (length == 0) {
            wrapped.add(in);
            return;
        }
        String newLine = "\n";
        boolean quickExit = false;

        // Remove carriage return
        in = in.replace("\r", "");

        if (in.length() < length) {
            quickExit = true;
            length = in.length();
        }

        // Split on a newline if present
        if (in.substring(0, length).contains(newLine)) {
            String wrappedLine = in.substring(0, in.indexOf(newLine)).trim();
            wrapped.add(wrappedLine);
            wordWrap(in.substring(in.indexOf(newLine) + 1), length, wrapped);
        } else if (quickExit) {
            wrapped.add(in);
        } else {
            // Otherwise, split along the nearest previous space / tab / dash
            int spaceIndex = Math.max(Math.max(in.lastIndexOf(" ", length),
                    in.lastIndexOf("\t", length)),
                in.lastIndexOf("-", length));

            // If no nearest space, split at length
            if (spaceIndex == -1) {
                spaceIndex = length;
            }

            // Split!
            String wrappedLine = in.substring(0, spaceIndex).trim();
            wrapped.add(wrappedLine);
            wordWrap(in.substring(spaceIndex), length, wrapped);
        }
    }

    /**
     * same as {@link ComponentRenderUtils#wrapComponents}, but with a custom line Prefix
     * @param text text to wrap
     * @param width max width of the text
     * @param font fon to use for splitting/sizing
     * @param linePrefix prefix on wrapped lines
     * @return list of wrapped lines
     */
    public static List<FormattedText> wrapText(
        FormattedText text, int width, Font font, @Nullable FormattedText linePrefix)
    {
        ComponentCollector componentcollector = new ComponentCollector();
        text.visit((style, str) -> {
            componentcollector.append(FormattedText.of(str, style));
            return Optional.empty();
        }, Style.EMPTY);

        List<FormattedText> list = Lists.newArrayList();
        font.getSplitter()
            .splitLines(componentcollector.getResultOrEmpty(), width, Style.EMPTY, (lineText, sameLine) ->
                list.add(sameLine && linePrefix != null ? FormattedText.composite(linePrefix, lineText) : lineText));
        return list.isEmpty() ? Lists.newArrayList(FormattedText.EMPTY) : list;
    }

    /**
     * extracts all ChatFormatting of the given Style
     * @param style Style to parse
     * @return list of all ChatFormatting in the provided Style
     */
    public static List<ChatFormatting> styleToFormats(Style style) {
        if (style.isEmpty()) {
            return new ArrayList<>();
        } else {
            ArrayList<ChatFormatting> arraylist = new ArrayList<>();

            if (style.getColor() != null) {
                arraylist.add(ChatFormatting.getByName(style.getColor().serialize()));
            }

            if (style.isBold()) {
                arraylist.add(ChatFormatting.BOLD);
            }

            if (style.isItalic()) {
                arraylist.add(ChatFormatting.ITALIC);
            }

            if (style.isStrikethrough()) {
                arraylist.add(ChatFormatting.STRIKETHROUGH);
            }

            if (style.isUnderlined()) {
                arraylist.add(ChatFormatting.UNDERLINE);
            }

            if (style.isObfuscated()) {
                arraylist.add(ChatFormatting.OBFUSCATED);
            }

            return arraylist;
        }
    }

    /**
     * builds a String of all formatting codes provided in {@code formats}
     * @param formats list of ChatFormatting elements
     * @return format string of all ChatFormatting in the provided list
     */
    public static String formatsToString(List<ChatFormatting> formats) {
        if (formats.isEmpty()) {
            return "";
        } else {
            StringBuilder builder = new StringBuilder();
            formats.forEach(builder::append);
            return builder.toString();
        }
    }

    /**
     * converts a Style to a format string
     * @param style style to get the formatting from
     * @return format string of the Style
     */
    public static String styleToFormatString(Style style) {
        return formatsToString(styleToFormats(style));
    }

    /**
     * creates a chained component of the given Throwable. each stack element is a new line
     * @param throwable Throwable to convert
     * @return Component of the Throwable message and stack trace
     */
    public static Component throwableToComponent(Throwable throwable) {
        MutableComponent result = Component.literal(throwable.getClass().getName() +
            (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()));

        for (StackTraceElement element : throwable.getStackTrace()) {
            result.append(Component.literal("\n" + element.toString()));
        }
        return result;
    }
}
