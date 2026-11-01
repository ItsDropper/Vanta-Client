package name.vantaclient.client.hudeditor;

public class HudMouseHandler {

    private static HudElement dragging;

    private static int offsetX;
    private static int offsetY;

    private static double startX;
    private static double startY;

    private static boolean moved;
    private static HudElement hovered;

    public static void startDragging(HudElement element, double mouseX, double mouseY) {

        dragging = element;

        offsetX = (int) mouseX - element.getX();
        offsetY = (int) mouseY - element.getY();

        startX = mouseX;
        startY = mouseY;

        moved = false;
    }


    public static void drag(double mouseX, double mouseY) {

        if (dragging == null)
            return;

        if (Math.abs(mouseX - startX) > 3 ||
                Math.abs(mouseY - startY) > 3) {

            moved = true;
        }

        dragging.move(
                (int)mouseX - offsetX,
                (int)mouseY - offsetY
        );
    }


    public static void release() {

        if (dragging != null && moved) {
            dragging.savePosition();
        }

        dragging = null;
    }

    public static void updateHover(double mouseX, double mouseY) {

        hovered = null;

        for (HudElement element : HudManager.getElements()) {

            if (element.isHovered(mouseX, mouseY)) {
                hovered = element;
                return;
            }
        }
    }

    public static boolean hasMoved() {
        return moved;
    }


    public static HudElement getHovered() {
        return hovered;
    }
}