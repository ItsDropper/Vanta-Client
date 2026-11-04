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

    // Reused on the client render thread. Typical HUD layouts stay within the initial
    // capacity, so collecting and merging hitboxes does not allocate every frame.
    private static int[] lefts = new int[32];
    private static int[] tops = new int[32];
    private static int[] rights = new int[32];
    private static int[] bottoms = new int[32];
    private static int[] pending = new int[32];
    private static boolean[] visited = new boolean[32];
    private static int backgroundCount;

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
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = Math.max(1, mc.getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(1, mc.getWindow().getGuiScaledHeight());
        boolean editing = HudEditorFeature.INSTANCE.isEnabled();
        boolean vanillaPositionsEnabled = VanillaHudFeature.INSTANCE.isEnabled();
        backgroundCount = 0;

        // Vanilla HUD backgrounds are independent of custom HUD visibility:
        // their persisted hitboxes must remain available while the editor is closed.
        for (VanillaHudElement vanilla : HudManager.getVanillaElements()) {
            if (!preferences.isHudBackgroundEnabled(vanilla.getBackgroundId())) continue;

            VanillaHudElement.Part part = vanilla.getPart();
            if (editing) {
                add(vanilla.getHitboxX(), vanilla.getHitboxY(),
                        vanilla.getHitboxWidth(), vanilla.getHitboxHeight());
            } else if (vanillaPositionsEnabled) {
                VantaClientPreferences.HudPosition position = VanillaHudElement.getPosition(part);
                int x = Math.round(position.x * screenWidth);
                int y = Math.round(position.y * screenHeight);
                add(x, y, Math.round(part.width() * position.scale),
                        Math.round(part.height() * position.scale));
            } else {
                add(part.defaultX(screenWidth), part.defaultY(screenHeight),
                        part.width(), part.height());
            }
        }

        // HudManager already resolves custom element visibility once per tick.
        // Reuse that snapshot instead of calling shouldRender() again every frame.
        for (HudElement element : HudManager.getActiveElements()) {
            if (element instanceof VanillaHudElement
                    || !preferences.isHudBackgroundEnabled(element.getBackgroundId())) continue;
            add(element.getHitboxX(), element.getHitboxY(),
                    element.getHitboxWidth(), element.getHitboxHeight());
        }

        int connectionGap = preferences.connectHudBackgrounds
                ? preferences.hudBackgroundConnectionGap : 0;
        drawConnectedBackgrounds(graphics, Math.max(0, connectionGap));
    }

    private static void add(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        ensureCapacity(backgroundCount + 1);
        lefts[backgroundCount] = x;
        tops[backgroundCount] = y;
        rights[backgroundCount] = x + width;
        bottoms[backgroundCount] = y + height;
        backgroundCount++;
    }

    private static void ensureCapacity(int required) {
        if (required <= lefts.length) return;
        int capacity = Math.max(required, lefts.length * 2);
        lefts = java.util.Arrays.copyOf(lefts, capacity);
        tops = java.util.Arrays.copyOf(tops, capacity);
        rights = java.util.Arrays.copyOf(rights, capacity);
        bottoms = java.util.Arrays.copyOf(bottoms, capacity);
        pending = java.util.Arrays.copyOf(pending, capacity);
        visited = java.util.Arrays.copyOf(visited, capacity);
    }

    /** Union connected hitboxes without allocating collections or rectangle objects per frame. */
    private static void drawConnectedBackgrounds(GuiGraphics graphics, int maximumGap) {
        if (backgroundCount == 0) return;
        java.util.Arrays.fill(visited, 0, backgroundCount, false);

        for (int start = 0; start < backgroundCount; start++) {
            if (visited[start]) continue;
            int head = 0;
            int tail = 0;
            pending[tail++] = start;
            visited[start] = true;

            int groupLeft = lefts[start];
            int groupTop = tops[start];
            int groupRight = rights[start];
            int groupBottom = bottoms[start];

            while (head < tail) {
                int current = pending[head++];
                for (int candidate = 0; candidate < backgroundCount; candidate++) {
                    if (visited[candidate] || !areConnected(
                            lefts[current], tops[current], rights[current], bottoms[current],
                            lefts[candidate], tops[candidate], rights[candidate], bottoms[candidate],
                            maximumGap)) continue;

                    visited[candidate] = true;
                    pending[tail++] = candidate;
                    groupLeft = Math.min(groupLeft, lefts[candidate]);
                    groupTop = Math.min(groupTop, tops[candidate]);
                    groupRight = Math.max(groupRight, rights[candidate]);
                    groupBottom = Math.max(groupBottom, bottoms[candidate]);
                }
            }

            graphics.fill(groupLeft, groupTop, groupRight, groupBottom, BACKGROUND_COLOR);
        }
    }

    private static boolean areConnected(int firstLeft, int firstTop, int firstRight, int firstBottom,
                                        int secondLeft, int secondTop, int secondRight, int secondBottom,
                                        int maximumGap) {
        boolean horizontalOverlap = firstLeft < secondRight && secondLeft < firstRight;
        boolean verticalOverlap = firstTop < secondBottom && secondTop < firstBottom;
        int horizontalGap = Math.max(0,
                Math.max(firstLeft - secondRight, secondLeft - firstRight));
        int verticalGap = Math.max(0,
                Math.max(firstTop - secondBottom, secondTop - firstBottom));
        return (verticalOverlap && horizontalGap <= maximumGap)
                || (horizontalOverlap && verticalGap <= maximumGap);
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
