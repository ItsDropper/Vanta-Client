package name.vantaclient.client.gui.compose.interaction;

import name.vantaclient.client.gui.framework.VantaUiNode;

/**
 * Owns pointer capture for controls that continue receiving drag updates after
 * the cursor leaves their bounds. Coordinate conversion stays centralized so
 * scaled screens cannot diverge between sliders and scrollbars.
 */
public final class VantaPointerCapture {
    public enum Type { NONE, SLIDER, SCROLLBAR }

    private Type type = Type.NONE;
    private VantaUiNode target;

    public Type type() {
        return type;
    }

    public VantaUiNode target() {
        return target;
    }

    public boolean isCaptured() {
        return target != null;
    }

    public boolean captureSlider(VantaUiNode slider) {
        if (slider == null || slider.kind() != VantaUiNode.Kind.SLIDER
                || !slider.isInteractive() || !slider.isVisible()) {
            cancel();
            return false;
        }
        cancel();
        target = slider;
        type = Type.SLIDER;
        return true;
    }

    /**
     * The runtime only passes scrollbar nodes returned by the tree's thumb-hit
     * test. That node may be a non-interactive container by design.
     */
    public boolean captureScrollbar(VantaUiNode scrollbar) {
        if (scrollbar == null || !scrollbar.isVisible()) {
            cancel();
            return false;
        }
        cancel();
        target = scrollbar;
        type = Type.SCROLLBAR;
        return true;
    }

    public boolean drag(double mouseX, double mouseY, double offsetX, double offsetY, float scale) {
        if (target == null) return false;
        if (!Double.isFinite(scale) || scale <= 0.0f
                || !Double.isFinite(mouseX) || !Double.isFinite(mouseY)
                || !Double.isFinite(offsetX) || !Double.isFinite(offsetY)) {
            cancel();
            return false;
        }
        double logicalX = (mouseX - offsetX) / scale;
        double logicalY = (mouseY - offsetY) / scale;
        if (type == Type.SLIDER) target.drag(logicalX);
        else if (type == Type.SCROLLBAR) target.dragScrollbar(logicalY);
        else return false;
        return true;
    }

    /** Releases capture for the primary pointer only; other buttons are ignored. */
    public boolean release(int button) {
        if (button != 0 || target == null) return false;
        cancel();
        return true;
    }

    public void cancel() {
        if (target != null && type == Type.SCROLLBAR) target.endScrollbarDrag();
        target = null;
        type = Type.NONE;
    }
}
