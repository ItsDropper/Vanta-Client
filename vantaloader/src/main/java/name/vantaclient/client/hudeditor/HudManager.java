package name.vantaclient.client.hudeditor;

import name.vantaclient.api.RegisteredHud;
import name.vantaclient.api.VantaClientApi;
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
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HudManager {
    private static final List<HudElement> ELEMENTS = new ArrayList<>();
    private static final List<HudElement> READ_ONLY_ELEMENTS = Collections.unmodifiableList(ELEMENTS);
    private static final List<VanillaHudElement> VANILLA_ELEMENTS = new ArrayList<>();
    private static final List<VanillaHudElement> READ_ONLY_VANILLA_ELEMENTS =
            Collections.unmodifiableList(VANILLA_ELEMENTS);
    private static final List<HudElement> ACTIVE_ELEMENTS = new ArrayList<>();
    private static final Map<String, RegisteredHudElement> REGISTERED_ELEMENTS = new LinkedHashMap<>();
    private static final List<HudElement> READ_ONLY_ACTIVE_ELEMENTS =
            Collections.unmodifiableList(ACTIVE_ELEMENTS);
    private static boolean initialized = false;
    private static boolean visibilityInitialized = false;
    private static long layoutRevision;
    private static long registeredHudRevision = Long.MIN_VALUE;

    public static void init() {
        if (!initialized) {
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
        syncRegisteredHuds();
    }

    /** Reconcile the public API registry on the client thread. */
    private static void syncRegisteredHuds() {
        long revision = VantaClientApi.getHudRegistryRevision();
        if (revision == registeredHudRevision) return;
        List<RegisteredHud> registrations = VantaClientApi.getRegisteredHuds();
        registeredHudRevision = revision;
        Set<String> present = new HashSet<>();
        for (RegisteredHud registration : registrations) {
            String key = registration.modId() + ":" + registration.element().id();
            present.add(key);
            RegisteredHudElement current = REGISTERED_ELEMENTS.get(key);
            if (current != null && current.represents(registration)) continue;
            if (current != null) ELEMENTS.remove(current);
            RegisteredHudElement element = new RegisteredHudElement(registration);
            REGISTERED_ELEMENTS.put(key, element);
            ELEMENTS.add(element);
            visibilityInitialized = false;
        }
        var iterator = REGISTERED_ELEMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RegisteredHudElement> entry = iterator.next();
            if (present.contains(entry.getKey())) continue;
            ELEMENTS.remove(entry.getValue());
            iterator.remove();
            visibilityInitialized = false;
        }
    }

    public static void add(HudElement element) {
        ELEMENTS.add(element);
        if (element instanceof VanillaHudElement vanilla) {
            VANILLA_ELEMENTS.add(vanilla);
        }
        visibilityInitialized = false;
    }

    /**
     * Resolve feature visibility once per client tick. Disabled HUDs are excluded
     * from the render loop, avoiding matrix operations and feature checks per frame.
     */
    public static void refreshVisibleElements() {
        init();
        layoutRevision++;
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
        return READ_ONLY_ELEMENTS;
    }

    /** Revision advanced whenever the manager refreshes its active HUD/layout snapshot. */
    public static long getLayoutRevision() {
        return layoutRevision;
    }

    public static List<HudElement> getActiveElements() {
        init();
        if (!visibilityInitialized) {
            refreshVisibleElements();
        }
        return READ_ONLY_ACTIVE_ELEMENTS;
    }

    /** Vanilla layout entries remain available even when they are not editor-visible. */
    public static List<VanillaHudElement> getVanillaElements() {
        init();
        return READ_ONLY_VANILLA_ELEMENTS;
    }
}
