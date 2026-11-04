package name.vantaclient.client.gui;

import name.vantaclient.client.gui.framework.VantaUiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bounded in-game notification queue for meaningful asynchronous outcomes.
 * Routine navigation and feature toggles should not create notifications.
 */
public final class VantaNotificationManager {
    public enum Type { INFO, SUCCESS, WARNING, ERROR }

    private static final Object LOCK = new Object();
    private static final ArrayDeque<Entry> PENDING = new ArrayDeque<>();
    private static final ArrayList<Entry> ACTIVE = new ArrayList<>();
    private static final LinkedHashMap<String, Long> RECENT = new LinkedHashMap<>();
    private static final int MAX_ACTIVE = 3;
    private static final int MAX_PENDING = 5;
    private static final long DEDUPE_WINDOW = 3_000_000_000L;
    private static final long RECENT_WINDOW = 6_000_000_000L;
    private static final long GLOBAL_INTERVAL = 450_000_000L;
    private static long lastNonErrorQueuedAt;

    private VantaNotificationManager() {}

    public static void info(String title, String message) {
        enqueue(Type.INFO, title, message);
    }

    public static void success(String title, String message) {
        enqueue(Type.SUCCESS, title, message);
    }

    public static void warning(String title, String message) {
        enqueue(Type.WARNING, title, message);
    }

    public static void error(String title, String message) {
        enqueue(Type.ERROR, title, message);
    }

    private static void enqueue(Type type, String title, String message) {
        String safeTitle = clean(title, 42);
        String safeMessage = clean(message, 112);
        if (safeTitle.isEmpty() || safeMessage.isEmpty()) return;

        long now = System.nanoTime();
        String key = type.name() + "|" + safeTitle.toLowerCase(Locale.ROOT)
                + "|" + safeMessage.toLowerCase(Locale.ROOT);
        synchronized (LOCK) {
            for (Entry entry : ACTIVE) {
                if (entry.key.equals(key) && now - entry.lastOccurrence <= DEDUPE_WINDOW) {
                    entry.occurrences++;
                    entry.lastOccurrence = now;
                    entry.expiresAt = Math.max(entry.expiresAt, now + 1_250_000_000L);
                    return;
                }
            }
            for (Entry entry : PENDING) {
                if (entry.key.equals(key) && now - entry.lastOccurrence <= DEDUPE_WINDOW) {
                    entry.occurrences++;
                    entry.lastOccurrence = now;
                    return;
                }
            }

            Long last = RECENT.get(key);
            if (last != null && now - last < RECENT_WINDOW) return;
            if (type != Type.ERROR && now - lastNonErrorQueuedAt < GLOBAL_INTERVAL) return;
            RECENT.put(key, now);
            if (type != Type.ERROR) lastNonErrorQueuedAt = now;

            if (PENDING.size() >= MAX_PENDING) {
                Entry removable = null;
                for (Entry entry : PENDING) {
                    if (entry.type != Type.ERROR) {
                        removable = entry;
                        break;
                    }
                }
                if (removable != null) PENDING.remove(removable);
                else if (type != Type.ERROR) return;
                else PENDING.removeFirst();
            }
            PENDING.addLast(new Entry(type, safeTitle, safeMessage, key, now, lifetime(type)));
            activatePending(now);

            while (RECENT.size() > 64) {
                Iterator<Map.Entry<String, Long>> iterator = RECENT.entrySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
        }
    }

    /** Called on the client tick to expire notifications and advance the queue. */
    public static void tick() {
        long now = System.nanoTime();
        synchronized (LOCK) {
            ACTIVE.removeIf(entry -> now >= entry.expiresAt);
            activatePending(now);
            RECENT.entrySet().removeIf(entry -> now - entry.getValue() > 30_000_000_000L);
        }
    }

    private static void activatePending(long now) {
        while (ACTIVE.size() < MAX_ACTIVE && !PENDING.isEmpty()) {
            Entry entry = PENDING.removeFirst();
            entry.startedAt = now;
            entry.expiresAt = now + entry.duration;
            ACTIVE.add(entry);
        }
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (screenWidth < 120 || screenHeight < 80) return;
        List<Entry> snapshot;
        synchronized (LOCK) {
            snapshot = List.copyOf(ACTIVE);
        }
        if (snapshot.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int cardWidth = Math.max(120, Math.min(286, screenWidth - 20));
        int cardHeight = 44;
        int baseX = screenWidth - cardWidth - 10;
        long now = System.nanoTime();

        for (int index = 0; index < snapshot.size(); index++) {
            Entry entry = snapshot.get(index);
            long age = Math.max(0L, now - entry.startedAt);
            long remaining = Math.max(0L, entry.expiresAt - now);
            float enter = Math.min(1.0f, age / 190_000_000.0f);
            enter = 1.0f - (1.0f - enter) * (1.0f - enter);
            float fade = remaining < 420_000_000L ? remaining / 420_000_000.0f : 1.0f;
            float opacity = Math.max(0.0f, Math.min(1.0f, enter * fade));
            int slide = Math.round((1.0f - enter) * (cardWidth + 14));
            int x = baseX + slide;
            int y = 12 + index * (cardHeight + 7);
            int background = VantaUiTheme.withAlpha(0xFF111A28, Math.round(226.0f * opacity));
            int foreground = VantaUiTheme.withAlpha(VantaUiTheme.TEXT_PRIMARY,
                    Math.round(255.0f * opacity));
            int secondary = VantaUiTheme.withAlpha(VantaUiTheme.TEXT_SECONDARY,
                    Math.round(235.0f * opacity));
            int marker = VantaUiTheme.withAlpha(typeColor(entry.type),
                    Math.round(255.0f * opacity));

            VantaUiTheme.fillRounded(graphics, x, y, cardWidth, cardHeight, 10, background);
            VantaUiTheme.fillRounded(graphics, x + 10, y + 11, 6, 6, 3, marker);

            int textX = x + 24;
            int textWidth = Math.max(0, cardWidth - 34);
            String visibleTitle = font.plainSubstrByWidth(entry.title, textWidth);
            String visibleMessage = font.plainSubstrByWidth(entry.message, textWidth);
            graphics.drawString(font, visibleTitle, textX, y + 8, foreground, false);
            graphics.drawString(font, visibleMessage, textX, y + 23, secondary, false);
            if (entry.occurrences > 1) {
                String count = "×" + entry.occurrences;
                graphics.drawString(font, count, x + cardWidth - font.width(count) - 9,
                        y + 8, secondary, false);
            }
        }
    }

    private static int typeColor(Type type) {
        return switch (type) {
            case INFO -> VantaUiTheme.STATUS_INFO;
            case SUCCESS -> VantaUiTheme.STATUS_SUCCESS;
            case WARNING -> VantaUiTheme.STATUS_WARNING;
            case ERROR -> VantaUiTheme.STATUS_DANGER;
        };
    }

    private static long lifetime(Type type) {
        return switch (type) {
            case INFO -> 3_400_000_000L;
            case SUCCESS -> 4_000_000_000L;
            case WARNING -> 4_600_000_000L;
            case ERROR -> 5_200_000_000L;
        };
    }

    private static String clean(String value, int maximum) {
        if (value == null) return "";
        String normalized = value.replace('\n', ' ').replace('\r', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\\t]]", " ")
                .replaceAll("\\s+", " ").strip();
        if (normalized.length() > maximum) {
            normalized = normalized.substring(0, Math.max(0, maximum - 1)).stripTrailing() + "…";
        }
        return normalized;
    }

    private static final class Entry {
        private final Type type;
        private final String title;
        private final String message;
        private final String key;
        private final long duration;
        private long startedAt;
        private long expiresAt;
        private long lastOccurrence;
        private int occurrences = 1;

        private Entry(Type type, String title, String message, String key, long now, long duration) {
            this.type = type;
            this.title = title;
            this.message = message;
            this.key = key;
            this.lastOccurrence = now;
            this.duration = duration;
        }
    }
}