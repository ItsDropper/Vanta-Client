package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Local chat timestamp utility with persistent clock-format selection. */
public final class ChatTimestampsFeature extends Feature {
    public static final ChatTimestampsFeature INSTANCE = new ChatTimestampsFeature();

    private enum Format {
        TWENTY_FOUR_HOUR("24-hour", "HH:mm"),
        TWENTY_FOUR_HOUR_SECONDS("24-hour with seconds", "HH:mm:ss"),
        TWELVE_HOUR("12-hour", "h:mm a");

        private final String label;
        private final String pattern;
        private final DateTimeFormatter formatter;

        Format(String label, String pattern) {
            this.label = label;
            this.pattern = pattern;
            this.formatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
        }

        private String pattern() { return pattern; }
    }

    private static final List<String> FORMATS = List.of(
            "24-hour", "24-hour with seconds", "12-hour");
    private static Format format = Format.TWENTY_FOUR_HOUR;

    private ChatTimestampsFeature() {
        super("Chat Timestamps", "Adds local time to incoming chat messages.", Category.MISC);
    }

    public static List<String> getFormats() {
        return FORMATS;
    }

    public static String getFormatLabel() {
        return format.label;
    }

    public static void setFormat(String value) {
        if (value == null) return;
        for (Format candidate : Format.values()) {
            if (candidate.label.equalsIgnoreCase(value)
                    || candidate.name().equalsIgnoreCase(value)) {
                format = candidate;
                DropperUtilsConfig.get().chatTimestampFormat = candidate.pattern();
                DropperUtilsConfig.save();
                return;
            }
        }
    }

    public static String formatTimestamp() {
        return LocalTime.now().format(format.formatter);
    }

    @Override
    public void saveConfig() {
        DropperUtilsConfig.get().chatTimestamps = isEnabled();
        DropperUtilsConfig.get().chatTimestampFormat = format.pattern();
        DropperUtilsConfig.save();
    }

    @Override
    public void loadConfig() {
        DropperUtilsConfig config = DropperUtilsConfig.get();
        format = Format.TWENTY_FOUR_HOUR;
        for (Format candidate : Format.values()) {
            if (candidate.pattern().equals(config.chatTimestampFormat)) {
                format = candidate;
                break;
            }
        }
        setEnabled(config.chatTimestamps);
    }
}
