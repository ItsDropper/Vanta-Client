package name.vantaclient.client.gui.compose.typography;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Shared typography and text-layout primitives for the retained Vanta UI. */
public final class VantaTypography {
    private static final Identifier DEFAULT_FONT = Identifier.fromNamespaceAndPath("vanta", "ui");

    private VantaTypography() {}

    public static Component styledText(String value, Identifier fontId) {
        return styledText(value, fontId, false);
    }

    public static Component styledText(String value, Identifier fontId, boolean bold) {
        Style style = Style.EMPTY.withFont(new FontDescription.Resource(
                fontId == null ? DEFAULT_FONT : fontId));
        if (bold) style = style.withBold(true);
        return Component.literal(value == null ? "" : value).withStyle(style);
    }

    public static int textWidth(Font font, String value, Identifier fontId) {
        if (font == null) throw new IllegalArgumentException("Font cannot be null.");
        return font.width(styledText(value, fontId));
    }

    /** Returns the longest code-point-safe prefix that fits the given width. */
    public static String fitText(Font font, String value, int maximumWidth, Identifier fontId) {
        if (font == null || value == null || value.isEmpty() || maximumWidth <= 0) return "";
        if (textWidth(font, value, fontId) <= maximumWidth) return value;
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            if (textWidth(font, value.substring(0, end), fontId) <= maximumWidth) {
                return value.substring(0, end);
            }
        }
        return "";
    }

    /** Wraps text into bounded lines, truncating omitted content with an ellipsis. */
    public static List<String> wrapText(String value, Font font, int maximumWidth,
                                       int maximumLines, Identifier fontId) {
        if (font == null) throw new IllegalArgumentException("Font cannot be null.");
        if (value == null || value.isBlank()) return List.of("");
        if (maximumWidth <= 0 || maximumLines <= 0) return List.of("");

        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] words = value.trim().split("\\s+");
        boolean truncated = false;

        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (textWidth(font, candidate, fontId) > maximumWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
                if (lines.size() >= maximumLines) {
                    truncated = true;
                    break;
                }
            }

            String visibleWord = word;
            if (textWidth(font, visibleWord, fontId) > maximumWidth) {
                visibleWord = fitText(font, visibleWord, maximumWidth, fontId);
                truncated = true;
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(visibleWord);
            if (truncated) break;
        }

        if (!line.isEmpty() && lines.size() < maximumLines) lines.add(line.toString());
        if (truncated && !lines.isEmpty()) {
            int lastIndex = lines.size() - 1;
            String last = lines.get(lastIndex);
            String ellipsis = "…";
            while (!last.isEmpty() && textWidth(font, last + ellipsis, fontId) > maximumWidth) {
                last = last.substring(0, last.offsetByCodePoints(last.length(), -1));
            }
            if (textWidth(font, ellipsis, fontId) <= maximumWidth) last += ellipsis;
            lines.set(lastIndex, last);
        }
        if (lines.isEmpty()) lines.add(fitText(font, value, maximumWidth, fontId));
        return List.copyOf(lines);
    }
}
