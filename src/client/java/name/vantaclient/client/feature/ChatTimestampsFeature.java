package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public final class ChatTimestampsFeature extends Feature {
    public static final ChatTimestampsFeature INSTANCE = new ChatTimestampsFeature();

    private ChatTimestampsFeature() {
        super("Chat Timestamps", "Adds local time to incoming chat messages.", Category.MISC);
    }

    @Override public void saveConfig() {
        DropperUtilsConfig.get().chatTimestamps = isEnabled();
        DropperUtilsConfig.save();
    }

    @Override public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().chatTimestamps);
    }
}
