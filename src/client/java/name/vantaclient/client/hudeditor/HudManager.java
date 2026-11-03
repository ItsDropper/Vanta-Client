package name.vantaclient.client.hudeditor;

import name.vantaclient.client.hud.ArmorHudElement;
import name.vantaclient.client.hud.DebugHudElement;
import name.vantaclient.client.hud.FpsHudElement;
import name.vantaclient.client.hud.KeystrokesHudElement;
import name.vantaclient.client.hud.PingHudElement;
import name.vantaclient.client.hud.PotionEffectsHudElement;
import name.vantaclient.client.hud.TotemCounterHudElement;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class HudManager {
    private static final List<HudElement> ELEMENTS = new ArrayList<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        add(new DebugHudElement());
        add(new ArmorHudElement());
        add(new TotemCounterHudElement());
        add(new PotionEffectsHudElement());
        add(new FpsHudElement());
        add(new PingHudElement());
        add(new KeystrokesHudElement());
    }

    public static void add(HudElement element) {
        ELEMENTS.add(element);
    }

    public static void render(GuiGraphics graphics) {
        init();
        for (HudElement element : ELEMENTS) element.renderScaled(graphics);
    }

    public static List<HudElement> getElements() {
        return ELEMENTS;
    }
}