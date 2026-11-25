package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public class DebugHudFeature extends Feature {

    public static final DebugHudFeature INSTANCE = new DebugHudFeature();

    private DebugHudFeature() {
        super(
                "Debug HUD",
                "Displays useful client and gameplay information.",
                Category.MISC
        );
    }


    @Override
    public void onEnable() {
        DropperUtilsConfig.get().debugHud = true;
        DropperUtilsConfig.save();
    }


    @Override
    public void onDisable() {
        DropperUtilsConfig.get().debugHud = false;
        DropperUtilsConfig.save();
    }


    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().debugHud);
    }
}