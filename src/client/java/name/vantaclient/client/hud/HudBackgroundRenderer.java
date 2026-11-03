package name.vantaclient.client.hud;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.feature.VanillaHudFeature;
import name.vantaclient.client.hudeditor.HudElement;
import name.vantaclient.client.hudeditor.HudManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws opt-in HUD backgrounds as one layer below both vanilla and custom HUD content.
 * Connected rectangles are unioned before drawing so overlapping panels never compound alpha.
 */
public final class HudBackgroundRenderer {
    private static final int BACKGROUND_COLOR = 0xCC292D35;

    private HudBackgroundRenderer() {}

    public record Bounds(int left, int top, int right, int bottom) {
        public Bounds {
            if (right <= left || bottom <= top) {
                throw new IllegalArgumentException("HUD background bounds must have positive dimensions.");
            }
        }

        private Bounds union(Bounds other) {
            return new Bounds(
                    Math.min(left, other.left),
                    Math.min(top, other.top),
                    Math.max(right, other.right),
                    Math.max(bottom, other.bottom)
            );
        }
    }

    /** Collects enabled HUD hitboxes and paints their backgrounds beneath the HUD render pass. */
    public static void render(GuiGraphics graphics) {
        VantaClientPreferences preferences = VantaClientPreferences.get();
        List<Bounds> backgrounds = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = Math.max(1, mc.getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(1, mc.getWindow().getGuiScaledHeight());
        boolean editing = HudEditorFeature.INSTANCE.isEnabled();
        boolean vanillaPositionsEnabled = VanillaHudFeature.INSTANCE.isEnabled();

        for (HudElement element : HudManager.getElements()) {
            if (!preferences.isHudBackgroundEnabled(element.getBackgroundId())) continue;

            if (element instanceof VanillaHudElement vanilla) {
                VanillaHudElement.Part part = vanilla.getPart();
                if (editing) {
                    add(backgrounds, vanilla.getX(), vanilla.getY(),
                            vanilla.getScaledWidth(), vanilla.getScaledHeight());
                } else if (vanillaPositionsEnabled) {
                    VantaClientPreferences.HudPosition position =
                            VanillaHudElement.getPosition(part);
                    int x = Math.round(position.x * screenWidth);
                    int y = Math.round(position.y * screenHeight);
                    add(backgrounds, x, y,
                            Math.round(part.width() * position.scale),
                            Math.round(part.height() * position.scale));
                } else {
                    add(backgrounds, part.defaultX(screenWidth), part.defaultY(screenHeight),
                            part.width(), part.height());
                }
                continue;
            }

            if (element.shouldRender()) {
                add(backgrounds, element.getX(), element.getY(),
                        element.getScaledWidth(), element.getScaledHeight());
            }
        }

        if (preferences.connectHudBackgrounds) {
            backgrounds = mergeConnectedBounds(
                    backgrounds, preferences.hudBackgroundConnectionGap);
        }

        for (Bounds bounds : backgrounds) {
            graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(),
                    BACKGROUND_COLOR);
        }
    }

    private static void add(List<Bounds> bounds, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        bounds.add(new Bounds(x, y, x + width, y + height));
    }

    /**
     * Unions hitboxes that overlap or touch along an edge (including a small gap).
     * A restart after every union handles transitive groups regardless of input order.
     */
    public static List<Bounds> mergeConnectedBounds(List<Bounds> input, int connectionGap) {
        int allowedGap = Math.max(0, connectionGap);
        List<Bounds> merged = new ArrayList<>();
        for (Bounds rectangle : input) {
            if (rectangle == null) continue;
            Bounds candidate = rectangle;
            int index = 0;
            while (index < merged.size()) {
                Bounds existing = merged.get(index);
                if (areConnected(candidate, existing, allowedGap)) {
                    candidate = candidate.union(existing);
                    merged.remove(index);
                    index = 0;
                } else {
                    index++;
                }
            }
            merged.add(candidate);
        }
        return List.copyOf(merged);
    }

    private static boolean areConnected(Bounds first, Bounds second, int maximumGap) {
        boolean horizontalOverlap = first.left() < second.right()
                && second.left() < first.right();
        boolean verticalOverlap = first.top() < second.bottom()
                && second.top() < first.bottom();

        int horizontalGap = Math.max(0,
                Math.max(first.left() - second.right(), second.left() - first.right()));
        int verticalGap = Math.max(0,
                Math.max(first.top() - second.bottom(), second.top() - first.bottom()));

        return (verticalOverlap && horizontalGap <= maximumGap)
                || (horizontalOverlap && verticalGap <= maximumGap);
    }
}
