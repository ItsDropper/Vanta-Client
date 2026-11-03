package name.vantaclient.client.hudeditor;

import name.vantaclient.client.hud.ArmorHudElement;
import name.vantaclient.client.hud.DebugHudElement;
import name.vantaclient.client.hud.CpsHudElement;
import name.vantaclient.client.hud.FpsHudElement;
import name.vantaclient.client.hud.KeystrokesHudElement;
import name.vantaclient.client.hud.PingHudElement;
import name.vantaclient.client.hud.PotionEffectsHudElement;
import name.vantaclient.client.hud.PerformanceHudElement;
import name.vantaclient.client.hud.TotemCounterHudElement;
import name.vantaclient.client.hud.VanillaHudElement;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class HudManager {
    private static final List<HudElement> ELEMENTS = new ArrayList<>();
    private static final List<HudElement> ACTIVE_ELEMENTS = new ArrayList<>();
    private static boolean initialized = false;
    private static boolean visibilityInitialized = false;

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
        add(new CpsHudElement());
        add(new PerformanceHudElement());
        for (VanillaHudElement.Part part : VanillaHudElement.Part.values()) {
            add(new VanillaHudElement(part));
        }
    }

    public static void add(HudElement element) {
        ELEMENTS.add(element);
        visibilityInitialized = false;
    }

    /**
     * Resolve feature visibility once per client tick. Disabled HUDs are excluded
     * from the render loop, avoiding matrix operations and feature checks per frame.
     */
    public static void refreshVisibleElements() {
        init();
        ACTIVE_ELEMENTS.clear();
        for (HudElement element : ELEMENTS) {
            if (element.shouldRender()) {
                ACTIVE_ELEMENTS.add(element);
            }
        }
        visibilityInitialized = true;
    }

    public static void render(GuiGraphics graphics) {
        init();
        if (!visibilityInitialized) {
            refreshVisibleElements();
        }
        for (HudElement element : ACTIVE_ELEMENTS) {
            element.renderScaled(graphics);
        }
    }

    public static List<HudElement> getElements() {
        init();
        return ELEMENTS;
    }
}
