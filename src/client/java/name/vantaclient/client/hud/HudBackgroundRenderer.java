package name.vantaclient.client.hud;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.feature.VanillaHudFeature;
import name.vantaclient.client.hudeditor.HudElement;
import name.vantaclient.client.hudeditor.HudManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
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
                    add(backgrounds, vanilla.getHitboxX(), vanilla.getHitboxY(),
                            vanilla.getHitboxWidth(), vanilla.getHitboxHeight());
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
                add(backgrounds, element.getHitboxX(), element.getHitboxY(),
                        element.getHitboxWidth(), element.getHitboxHeight());
            }
        }

        int connectionGap = preferences.connectHudBackgrounds
                ? preferences.hudBackgroundConnectionGap : 0;
        // Always union overlaps so the shared area is never rendered at compounded opacity.
        backgrounds = mergeConnectedBounds(backgrounds, connectionGap);

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
     * Builds connected components from the original hitboxes, then unions each component.
     * Adjacency is never tested against an expanded group bound: doing so could falsely
     * connect a third HUD through empty space introduced by an earlier rectangular union.
     */
    public static List<Bounds> mergeConnectedBounds(List<Bounds> input, int connectionGap) {
        if (input == null || input.isEmpty()) return List.of();
        int allowedGap = Math.max(0, connectionGap);
        List<Bounds> rectangles = input.stream().filter(java.util.Objects::nonNull).toList();
        boolean[] visited = new boolean[rectangles.size()];
        List<Bounds> groups = new ArrayList<>();
        ArrayDeque<Integer> pending = new ArrayDeque<>();

        for (int start = 0; start < rectangles.size(); start++) {
            if (visited[start]) continue;
            visited[start] = true;
            pending.addLast(start);
            Bounds group = rectangles.get(start);

            while (!pending.isEmpty()) {
                Bounds current = rectangles.get(pending.removeFirst());
                for (int candidate = 0; candidate < rectangles.size(); candidate++) {
                    if (visited[candidate]) continue;
                    Bounds other = rectangles.get(candidate);
                    if (!areConnected(current, other, allowedGap)) continue;
                    visited[candidate] = true;
                    pending.addLast(candidate);
                    group = group.union(other);
                }
            }
            groups.add(group);
        }
        return List.copyOf(groups);
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
