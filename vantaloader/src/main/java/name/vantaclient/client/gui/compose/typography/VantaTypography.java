package name.vantaclient.client.gui.compose.typography;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared typography and text-layout primitives for the retained Vanta UI. */
public final class VantaTypography {
    private static final Identifier DEFAULT_FONT = Identifier.fromNamespaceAndPath("vanta", "ui");

    private static final Identifier DISPLAY_FONT = Identifier.fromNamespaceAndPath("vanta", "brand");
    private static final Identifier HEADING_FONT = Identifier.fromNamespaceAndPath("vanta", "heading");
    private static final Identifier TITLE_FONT = Identifier.fromNamespaceAndPath("vanta", "title");
    private static final Identifier LABEL_FONT = Identifier.fromNamespaceAndPath("vanta", "medium");
    private static final Identifier CAPTION_FONT = Identifier.fromNamespaceAndPath("vanta", "caption");

    private static volatile float deviceScale = 1.0f;

    /** Device pixels per layout unit of the surface currently being drawn (selects the font raster). */
    public static void setDeviceScale(float value) {
        if (Float.isFinite(value) && value > 0.0f) deviceScale = value;
    }

    public enum Role { DISPLAY, HEADING, TITLE, BODY, LABEL, CAPTION }

    /** Resolves the shared type scale from the same cached snapshot used by rendering. */
    public static int size(Role role) {
        var scale = name.vantaclient.client.gui.compose.theme.VantaThemeFactory.create().typography();
        return switch (role) {
            case DISPLAY -> scale.displaySize();
            case HEADING -> scale.headingSize();
            case TITLE -> scale.titleSize();
            case BODY -> scale.bodySize();
            case LABEL -> scale.labelSize();
            case CAPTION -> scale.captionSize();
        };
    }

    public static int lineHeight(Role role) {
        var scale = name.vantaclient.client.gui.compose.theme.VantaThemeFactory.create().typography();
        return switch (role) {
            case DISPLAY -> scale.displayLineHeight();
            case HEADING -> scale.headingLineHeight();
            case TITLE -> scale.titleLineHeight();
            case BODY, LABEL -> scale.bodyLineHeight();
            case CAPTION -> scale.captionLineHeight();
        };
    }

    public static Identifier font(Role role) {
        return switch (role) {
            case DISPLAY -> DISPLAY_FONT;
            case HEADING -> HEADING_FONT;
            case TITLE -> TITLE_FONT;
            case BODY -> DEFAULT_FONT;
            case LABEL -> LABEL_FONT;
            case CAPTION -> CAPTION_FONT;
        };
    }
    private static final int CACHE_LIMIT = 4096;

    private record StyledKey(String value, Identifier font, boolean bold) {}
    private record WidthKey(Font renderer, String value, Identifier font) {}
    private record FitKey(Font renderer, String value, int width, Identifier font) {}
    private record WrapKey(Font renderer, String value, int width, int lines, Identifier font) {}

    private static final Map<StyledKey, Component> STYLED = lruCache();
    private static final Map<WidthKey, Integer> WIDTHS = lruCache();
    private static final Map<FitKey, String> FITTED = lruCache();
    private static final Map<WrapKey, List<String>> WRAPPED = lruCache();

    private VantaTypography() {}

    private static <K, V> Map<K, V> lruCache() {
        return new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > CACHE_LIMIT;
            }
        };
    }

    public static Component styledText(String value, Identifier fontId) {
        return styledText(value, fontId, false);
    }

    public static Component styledText(String value, Identifier fontId, boolean bold) {
        String normalized = value == null ? "" : value;
        Identifier resolvedFont = name.vantaclient.client.gui.VantaFonts.variant(
                fontId == null ? DEFAULT_FONT : fontId, deviceScale);
        StyledKey key = new StyledKey(normalized, resolvedFont, bold);
        synchronized (STYLED) {
            Component cached = STYLED.get(key);
            if (cached != null) return cached;
        }
        Style style = Style.EMPTY.withFont(new FontDescription.Resource(resolvedFont));
        if (bold) style = style.withBold(true);
        Component created = Component.literal(normalized).withStyle(style);
        synchronized (STYLED) {
            Component raced = STYLED.get(key);
            if (raced != null) return raced;
            STYLED.put(key, created);
        }
        return created;
    }

    public static int textWidth(Font font, String value, Identifier fontId) {
        if (font == null) throw new IllegalArgumentException("Font cannot be null.");
        String normalized = value == null ? "" : value;
        Identifier resolvedFont = fontId == null ? DEFAULT_FONT : fontId;
        WidthKey key = new WidthKey(font, normalized, resolvedFont);
        synchronized (WIDTHS) {
            Integer cached = WIDTHS.get(key);
            if (cached != null) return cached;
        }
        int width = font.width(styledText(normalized, resolvedFont));
        synchronized (WIDTHS) {
            Integer raced = WIDTHS.get(key);
            if (raced != null) return raced;
            WIDTHS.put(key, width);
        }
        return width;
    }

    /** Returns the longest code-point-safe prefix that fits the given width. */
    public static String fitText(Font font, String value, int maximumWidth, Identifier fontId) {
        if (font == null || value == null || value.isEmpty() || maximumWidth <= 0) return "";
        Identifier resolvedFont = fontId == null ? DEFAULT_FONT : fontId;
        FitKey key = new FitKey(font, value, maximumWidth, resolvedFont);
        synchronized (FITTED) {
            String cached = FITTED.get(key);
            if (cached != null) return cached;
        }
        String result;
        if (textWidth(font, value, resolvedFont) <= maximumWidth) {
            result = value;
        } else {
            int end = value.length();
            result = "";
            while (end > 0) {
                end = value.offsetByCodePoints(end, -1);
                if (textWidth(font, value.substring(0, end), resolvedFont) <= maximumWidth) {
                    result = value.substring(0, end);
                    break;
                }
            }
        }
        synchronized (FITTED) {
            String raced = FITTED.get(key);
            if (raced != null) return raced;
            FITTED.put(key, result);
        }
        return result;
    }

    /** Wraps text into bounded lines, truncating omitted content with an ellipsis. */
    public static List<String> wrapText(String value, Font font, int maximumWidth,
                                       int maximumLines, Identifier fontId) {
        if (font == null) throw new IllegalArgumentException("Font cannot be null.");
        if (value == null || value.isBlank()) return List.of("");
        if (maximumWidth <= 0 || maximumLines <= 0) return List.of("");
        Identifier resolvedFont = fontId == null ? DEFAULT_FONT : fontId;
        WrapKey key = new WrapKey(font, value, maximumWidth, maximumLines, resolvedFont);
        synchronized (WRAPPED) {
            List<String> cached = WRAPPED.get(key);
            if (cached != null) return cached;
        }

        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] words = value.trim().split("\\s+");
        boolean truncated = false;

        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (textWidth(font, candidate, resolvedFont) > maximumWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
                if (lines.size() >= maximumLines) {
                    truncated = true;
                    break;
                }
            }

            String visibleWord = word;
            if (textWidth(font, visibleWord, resolvedFont) > maximumWidth) {
                visibleWord = fitText(font, visibleWord, maximumWidth, resolvedFont);
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
            while (!last.isEmpty() && textWidth(font, last + ellipsis, resolvedFont) > maximumWidth) {
                last = last.substring(0, last.offsetByCodePoints(last.length(), -1));
            }
            if (textWidth(font, ellipsis, resolvedFont) <= maximumWidth) last += ellipsis;
            lines.set(lastIndex, last);
        }
        if (lines.isEmpty()) lines.add(fitText(font, value, maximumWidth, resolvedFont));
        List<String> result = List.copyOf(lines);
        synchronized (WRAPPED) {
            List<String> raced = WRAPPED.get(key);
            if (raced != null) return raced;
            WRAPPED.put(key, result);
        }
        return result;
    }
}
