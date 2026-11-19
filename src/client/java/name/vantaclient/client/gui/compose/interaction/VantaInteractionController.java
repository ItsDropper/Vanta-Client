package name.vantaclient.client.gui.compose.interaction;

import name.vantaclient.client.gui.framework.VantaUiNode;

/**
 * Screen-level interaction coordinator. Owns keyboard focus and pointer capture
 * together so screen disposal and invalid-target cleanup have one lifecycle.
 */
public final class VantaInteractionController {
    private final VantaFocusController focus;
    private final VantaPointerCapture pointerCapture = new VantaPointerCapture();

    public VantaInteractionController(VantaUiNode root) {
        if (root == null) throw new IllegalArgumentException("Interaction root cannot be null.");
        focus = new VantaFocusController(root);
    }

    public VantaUiNode focused() {
        return focus.focused();
    }

    public VantaUiNode focus(VantaUiNode target) {
        focus.setFocused(target);
        return focus.focused();
    }

    public VantaUiNode cycleFocus(int direction) {
        return focus.cycle(direction);
    }

    public boolean validateFocus() {
        return focus.clearIfDetached();
    }

    public boolean captureSlider(VantaUiNode node) {
        return pointerCapture.captureSlider(node);
    }

    public boolean captureScrollbar(VantaUiNode node) {
        return pointerCapture.captureScrollbar(node);
    }

    public boolean dragPointer(double mouseX, double mouseY, double offsetX, double offsetY, float scale) {
        return pointerCapture.drag(mouseX, mouseY, offsetX, offsetY, scale);
    }

    public boolean releasePointer(int button) {
        return pointerCapture.release(button);
    }

    public boolean hasPointerCapture() {
        return pointerCapture.isCaptured();
    }

    public VantaPointerCapture.Type pointerCaptureType() {
        return pointerCapture.type();
    }

    public VantaUiNode pointerCaptureTarget() {
        return pointerCapture.target();
    }

    public void cancelPointerCapture() {
        pointerCapture.cancel();
    }

    public void clear() {
        pointerCapture.cancel();
        focus.clear();
    }
}
