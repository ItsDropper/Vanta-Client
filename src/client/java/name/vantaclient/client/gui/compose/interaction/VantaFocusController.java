package name.vantaclient.client.gui.compose.interaction;

import name.vantaclient.client.gui.framework.VantaUiNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns focus state and traversal for the retained UI tree.
 *
 * Focus traversal is deliberately independent of rendering and pointer routing:
 * callers may invalidate or lay out the tree without resetting focus.
 */
public final class VantaFocusController {
    private final VantaUiNode root;
    private VantaUiNode focused;

    public VantaFocusController(VantaUiNode root) {
        if (root == null) throw new IllegalArgumentException("Focus root cannot be null.");
        this.root = root;
    }

    public VantaUiNode focused() {
        return focused;
    }

    public void setFocused(VantaUiNode target) {
        if (focused == target) return;
        if (focused != null) focused.setFocused(false);
        focused = target != null && target.isInteractive() ? target : null;
        if (focused != null) focused.setFocused(true);
    }

    /**
     * Traverses enabled, visible controls in tree order and wraps at either end.
     * A stale focus target (removed or disabled since the last traversal) is
     * treated like no focus, avoiding index-dependent skips.
     */
    public VantaUiNode cycle(int direction) {
        if (direction == 0) return focused;
        List<VantaUiNode> candidates = new ArrayList<>();
        root.collectFocusable(candidates);
        if (candidates.isEmpty()) {
            setFocused(null);
            return null;
        }

        int index = focused == null ? -1 : candidates.indexOf(focused);
        int next;
        if (index < 0) {
            next = direction > 0 ? 0 : candidates.size() - 1;
        } else {
            next = Math.floorMod(index + (direction > 0 ? 1 : -1), candidates.size());
        }
        setFocused(candidates.get(next));
        return focused;
    }

    /** Clears focus when the current target no longer belongs to the focusable tree. */
    public boolean clearIfDetached() {
        if (focused == null) return false;
        List<VantaUiNode> candidates = new ArrayList<>();
        root.collectFocusable(candidates);
        if (candidates.contains(focused)) return false;
        setFocused(null);
        return true;
    }

    public void clear() {
        setFocused(null);
    }
}
