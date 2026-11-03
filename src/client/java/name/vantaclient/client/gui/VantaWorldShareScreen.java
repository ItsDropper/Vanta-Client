package name.vantaclient.client.gui;

import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import name.vantaclient.client.integration.VantaLanHostingService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;

import java.util.List;

/** Vanta's retained-mode front end for the first, LAN-only world-sharing milestone. */
public final class VantaWorldShareScreen extends Screen {
    private static final List<String> GAME_MODES =
            List.of("Survival", "Creative", "Adventure", "Spectator");

    private final Screen parent;
    private VantaUiRuntime runtime;
    private GameType selectedGameType = GameType.SURVIVAL;
    private boolean allowCommands;
    private VantaLanHostingService.Status status =
            new VantaLanHostingService.Status(VantaLanHostingService.State.NO_WORLD, -1);

    public VantaWorldShareScreen(Screen parent) {
        super(Component.literal("Vanta World Sharing"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null && server.getDefaultGameType() != null) {
            selectedGameType = server.getDefaultGameType();
        }
        status = VantaLanHostingService.status();
        rebuildUi();
    }

    private void rebuildUi() {
        if (runtime != null) runtime.dispose();
        runtime = new VantaUiRuntime(buildRoot());
        runtime.setScale((float) name.vantaclient.client.config.VantaClientPreferences.get().uiScale);
        runtime.layout(font, width, height);
    }

    private VantaUiNode buildRoot() {
        boolean compact = width < 620;
        VantaUiNode root = VantaUiNode.column(10, 12).background(VantaUiTheme.CANVAS);

        VantaUiNode header = VantaUiNode.row(10, 12)
                .height(compact ? 42 : 48)
                .background(VantaUiTheme.PANEL)
                .align(VantaUiNode.Alignment.CENTER);
        VantaUiNode heading = VantaUiNode.column(2, 0);
        heading.add(VantaUiNode.eyebrow("VANTA / MULTIPLAYER"));
        heading.add(VantaUiNode.title("WORLD SHARING"));
        header.add(heading);
        header.add(VantaUiNode.spacer(1).flex(1));
        header.add(VantaUiNode.button("BACK", this::onClose).height(30)
                .tooltip("Return to the pause menu"));
        root.add(header);

        VantaUiNode content = VantaUiNode.column(12, 18)
                .flex(1).padding(compact ? 12 : 20)
                .background(VantaUiTheme.PANEL).scrollable();
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();

        switch (status.state()) {
            case NO_WORLD -> {
                content.add(VantaUiNode.eyebrow("NO ACTIVE WORLD"));
                content.add(VantaUiNode.title("Open a singleplayer world first"));
                content.add(VantaUiNode.text(
                        "Vanta can share an integrated singleplayer world while you are in-game.",
                        VantaUiTheme.TEXT_SECONDARY));
            }
            case HOSTED -> {
                content.add(VantaUiNode.eyebrow("LOCAL HOSTING ACTIVE"));
                content.add(VantaUiNode.title("Your world is open to LAN"));
                content.add(VantaUiNode.text("Port  " + status.port(),
                        VantaUiTheme.TEXT_PRIMARY));
                content.add(VantaUiNode.text(
                        "Players on the same local network can find this world from Minecraft's Multiplayer screen.",
                        VantaUiTheme.TEXT_SECONDARY));
                content.add(VantaUiNode.text(
                        "To stop hosting, close the world and return to the title screen.",
                        VantaUiTheme.TEXT_MUTED));
            }
            case NOT_HOSTED, FAILED -> {
                content.add(VantaUiNode.eyebrow("LOCAL NETWORK"));
                content.add(VantaUiNode.title("Share your current world"));
                content.add(VantaUiNode.text(
                        "Open the integrated server to devices on your local network. Minecraft chooses an available port.",
                        VantaUiTheme.TEXT_SECONDARY));

                if (server != null) {
                    content.add(VantaUiNode.dropdown("Guest game mode", GAME_MODES,
                            () -> gameTypeLabel(selectedGameType),
                            value -> selectedGameType = gameTypeFromLabel(value)).height(34));
                    content.add(VantaUiNode.toggle("Allow commands",
                            () -> allowCommands, value -> allowCommands = value)
                            .height(36)
                            .tooltip("Allow commands for players who join this LAN session"));
                    content.add(VantaUiNode.button("START LOCAL HOSTING", this::startHosting)
                            .height(36));
                } else {
                    content.add(VantaUiNode.text(
                            "The singleplayer server is not ready. Return to the world and try again.",
                            VantaUiTheme.TEXT_MUTED));
                }

                if (status.state() == VantaLanHostingService.State.FAILED) {
                    content.add(VantaUiNode.text(
                            "Minecraft could not open a LAN port. Check whether another service is using the network port, then retry.",
                            VantaUiTheme.TEXT_MUTED));
                }
            }
        }

        content.add(VantaUiNode.spacer(1).height(4));
        content.add(VantaUiNode.eyebrow("REMOTE PLAY"));
        content.add(VantaUiNode.text(
                "Internet peer invitations are not implemented in this milestone. They need an authenticated signalling and relay or NAT-traversal service; this screen does not request or display public addresses.",
                VantaUiTheme.TEXT_MUTED));
        root.add(content);
        return root;
    }

    private void startHosting() {
        status = VantaLanHostingService.start(selectedGameType, allowCommands);
        rebuildUi();
    }

    private static String gameTypeLabel(GameType gameType) {
        if (gameType == null) return "Survival";
        return switch (gameType) {
            case SURVIVAL -> "Survival";
            case CREATIVE -> "Creative";
            case ADVENTURE -> "Adventure";
            case SPECTATOR -> "Spectator";
        };
    }

    private static GameType gameTypeFromLabel(String label) {
        return switch (label) {
            case "Creative" -> GameType.CREATIVE;
            case "Adventure" -> GameType.ADVENTURE;
            case "Spectator" -> GameType.SPECTATOR;
            default -> GameType.SURVIVAL;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, VantaUiTheme.CANVAS);
        if (runtime != null) {
            runtime.setScale((float) name.vantaclient.client.config.VantaClientPreferences.get().uiScale);
            runtime.render(graphics, font, width, height, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (runtime != null && runtime.mouseClicked(event.x(), event.y(), event.button(), font)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = runtime != null && runtime.mouseReleased(event.button());
        return handled || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (runtime != null && runtime.mouseDragged(event.x(), event.y(), event.button())) return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (runtime != null && runtime.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean shiftDown = (event.modifiers() & 1) != 0;
        if (runtime != null && runtime.keyPressed(event.key(), shiftDown)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (runtime != null && runtime.charTyped(event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        if (runtime != null) runtime.dispose();
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
        else super.onClose();
    }

    /**
     * Keep the integrated server ticking while this screen is visible so LAN guests
     * can connect without the host having to close the world-sharing screen.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
