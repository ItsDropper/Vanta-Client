package name.vantaclient.client.gui.menu;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;

/** Services the menu shell offers to its pages. */
public interface MenuHost {
    enum Page { MODS, INTEGRATIONS, INTEGRATION, PROFILES, KEYBINDS, THEMES, PREFERENCES, MOD }

    /** A navigable location; {@code modId} identifies a mod or integration detail page. */
    record Route(Page page, String modId) {
        public static Route of(Page page) { return new Route(page, null); }
    }

    Font font();
    Minecraft minecraft();
    Screen screen();
    ModCatalog catalog();

    /** Rebuilds the retained tree (keeps the search field focused if it was). */
    void rebuild();
    void navigate(Route route);

    /** Focuses {@code node} once the tree currently being built is installed. */
    void focusAfterBuild(name.vantaclient.client.gui.framework.VantaUiNode node);

    /** Approximate width in layout units of a page body. */
    int contentWidth();
    String search();

    ModsPage.State modsState();
    ProfilesPage.State profilesState();

    KeyMapping pendingKey();
    void beginKeyCapture(KeyMapping mapping);
    void cancelKeyCapture();
    void resetKey(KeyMapping mapping);
    boolean hasConflict(KeyMapping mapping);
    int selectedKeyCode();
    void selectKeyCode(int glfwKeyCode);
}
