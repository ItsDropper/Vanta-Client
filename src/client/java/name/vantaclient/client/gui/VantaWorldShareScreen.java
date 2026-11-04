package name.vantaclient.client.gui;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import name.vantaclient.client.integration.VantaLanHostingService;
import name.vantaclient.client.integration.net.VantaRemoteSessionManager;
import name.vantaclient.client.integration.net.VantaRelayClient;
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

/** Retained-mode host and guest entry point for Vanta's self-hosted remote relay. */
public final class VantaWorldShareScreen extends Screen {
    private static final List<String> GAME_MODES =
            List.of("Survival", "Creative", "Adventure", "Spectator");

    private final Screen parent;
    private VantaUiRuntime runtime;
    private VantaClientPreferences preferences;
    private VantaLanHostingService.Status lanStatus =
            new VantaLanHostingService.Status(VantaLanHostingService.State.NO_WORLD, -1);
    private VantaRemoteSessionManager.Snapshot session = VantaRemoteSessionManager.snapshot();

    private String relayEndpoint = "";
    private String inviteCode = "";
    private String playerName = "";
    private String localStatus = "";
    private GameType selectedGameType = GameType.SURVIVAL;
    private boolean allowCommands;
    private long lastSessionRevision = -1L;

    public VantaWorldShareScreen(Screen parent) {
        super(Component.literal("Vanta World Sharing"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        preferences = VantaClientPreferences.get();
        relayEndpoint = preferences.remoteRelayEndpoint;
        playerName = Minecraft.getInstance().getUser().getName();
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null && server.getDefaultGameType() != null) {
            selectedGameType = server.getDefaultGameType();
        }
        lanStatus = VantaLanHostingService.status();
        session = VantaRemoteSessionManager.snapshot();
        lastSessionRevision = VantaRemoteSessionManager.revision();
        rebuildUi();
    }

    private void rebuildUi() {
        if (runtime != null) runtime.dispose();
        runtime = new VantaUiRuntime(buildRoot());
        runtime.setScale((float) preferences.uiScale);
        runtime.layout(font, width, height);
    }

    private VantaUiNode buildRoot() {
        boolean compact = width < 680;
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        lanStatus = VantaLanHostingService.status();

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
                .tooltip("Return to the previous screen"));
        root.add(header);

        VantaUiNode content = VantaUiNode.column(12, 16)
                .flex(1)
                .padding(compact ? 12 : 18)
                .background(VantaUiTheme.PANEL)
                .scrollable();

        addRelaySettings(content, compact);
        content.add(VantaUiNode.spacer(1).height(2));
        addHostSection(content, server, compact);
        content.add(VantaUiNode.spacer(1).height(2));
        addGuestSection(content, compact);

        root.add(content);
        return root;
    }

    private void addRelaySettings(VantaUiNode content, boolean compact) {
        content.add(VantaUiNode.eyebrow("YOUR RELAY"));
        content.add(VantaUiNode.text(
                "Use a relay you operate or trust. Remote relays must use HTTPS/WSS; no Vanta-operated public relay is configured.",
                VantaUiTheme.TEXT_SECONDARY));
        VantaUiNode endpointRow = VantaUiNode.row(8, 0)
                .height(34)
                .align(VantaUiNode.Alignment.CENTER);
        endpointRow.add(VantaUiNode.textField("https://your-relay.example", 
                () -> relayEndpoint, value -> relayEndpoint = value, 256)
                .flex(1).minSize(80, 30));
        endpointRow.add(VantaUiNode.button("SAVE", this::saveRelayEndpoint)
                .size(62, 30));
        content.add(endpointRow);
        content.add(VantaUiNode.toggle("Maximum IP privacy (TURN only)",
                () -> preferences.remoteMaximumIpPrivacy,
                value -> {
                    preferences.remoteMaximumIpPrivacy = value;
                    VantaClientPreferences.save();
                }).height(34));
        content.add(VantaUiNode.text(
                "When enabled, WebRTC uses TURN-only candidates so guests do not learn your home IP. This requires TURN infrastructure; the WebSocket relay remains the fallback.",
                VantaUiTheme.TEXT_MUTED));
        content.add(VantaUiNode.text(
                "The relay forwards Minecraft TCP traffic without interpreting packets. Its operator can still see connection metadata and source network addresses.",
                VantaUiTheme.TEXT_MUTED));
        content.add(VantaUiNode.paragraph(
                "Remote hosting also publishes the world to your local network. Nearby devices may discover and join it through normal Minecraft LAN discovery.",
                VantaUiTheme.TEXT_MUTED, compact ? 3 : 2));
        if (!localStatus.isBlank()) {
            content.add(VantaUiNode.paragraph(localStatus,
                    VantaUiTheme.TEXT_MUTED, compact ? 3 : 2));
        }
    }

    private void addHostSection(VantaUiNode content, MinecraftServer server, boolean compact) {
        content.add(VantaUiNode.eyebrow("HOST A WORLD"));
        if (server == null) {
            content.add(VantaUiNode.title("Open a singleplayer world to host"));
            content.add(VantaUiNode.text(
                    "Hosting is available while a singleplayer integrated server is running. You can still join a friend's invite from this screen.",
                    VantaUiTheme.TEXT_SECONDARY));
            return;
        }

        if (lanStatus.state() == VantaLanHostingService.State.HOSTED) {
            content.add(VantaUiNode.text("Your integrated server is ready for guests.",
                    VantaUiTheme.TEXT_SECONDARY));
        } else {
            content.add(VantaUiNode.text(
                    "Vanta publishes the active singleplayer world through Minecraft's integrated server.",
                    VantaUiTheme.TEXT_SECONDARY));
            content.add(VantaUiNode.dropdown("Guest game mode", GAME_MODES,
                    () -> gameTypeLabel(selectedGameType),
                    value -> selectedGameType = gameTypeFromLabel(value)).height(34));
            content.add(VantaUiNode.toggle("Allow commands for guests",
                    () -> allowCommands, value -> allowCommands = value).height(34));
        }

        if (session.hostActive()) {
            content.add(VantaUiNode.spacer(1).height(1).background(VantaUiTheme.BORDER_SUBTLE));
            content.add(VantaUiNode.row(8, 0).height(22)
                    .add(VantaUiNode.eyebrow("REMOTE INVITE ACTIVE"))
                    .add(VantaUiNode.spacer(1).flex(1))
                    .add(VantaUiNode.text(session.connectedGuests() + " connected",
                            VantaUiTheme.TEXT_SECONDARY)));
            content.add(VantaUiNode.row(8, 0)
                    .height(36)
                    .align(VantaUiNode.Alignment.CENTER)
                    .add(VantaUiNode.title(session.roomCode()).flex(1))
                    .add(VantaUiNode.button("COPY INVITE",
                            () -> Minecraft.getInstance().keyboardHandler.setClipboard(session.roomCode()))
                            .size(104, 30)));
            if (session.roomExpiresAt() != null) {
                content.add(VantaUiNode.text("Invite expires "
                        + session.roomExpiresAt().toString().replace('T', ' ').replace("Z", " UTC"),
                        VantaUiTheme.TEXT_MUTED));
            }
            content.add(VantaUiNode.paragraph(session.hostStatus(),
                    VantaUiTheme.TEXT_SECONDARY, compact ? 3 : 2));
            for (VantaRemoteSessionManager.PendingGuest guest : session.pendingGuests()) {
                VantaUiNode request = VantaUiNode.row(8, 4)
                        .height(compact ? 66 : 38)
                        .align(VantaUiNode.Alignment.CENTER);
                VantaUiNode guestInfo = VantaUiNode.column(2, 0).flex(1);
                guestInfo.add(VantaUiNode.text("Claims name: " + guest.playerName(),
                        VantaUiTheme.TEXT_PRIMARY));
                guestInfo.add(VantaUiNode.text(guest.approving()
                                ? "Approval sent; opening stream…"
                                : "Waiting for your approval",
                        VantaUiTheme.TEXT_MUTED));
                request.add(guestInfo);
                request.add(VantaUiNode.button("APPROVE",
                        () -> VantaRemoteSessionManager.approveGuest(guest.requestId()))
                        .size(76, 30).enabled(!guest.approving()));
                request.add(VantaUiNode.button("REJECT",
                        () -> VantaRemoteSessionManager.rejectGuest(guest.requestId()))
                        .size(64, 30).enabled(!guest.approving()));
                content.add(request);
            }
            content.add(VantaUiNode.button("STOP REMOTE HOSTING",
                    VantaRemoteSessionManager::stopHosting).height(34));
        } else if (session.hostBusy()) {
            content.add(VantaUiNode.progressIndicator("Creating your invite room…").height(30));
            content.add(VantaUiNode.text(session.hostStatus(), VantaUiTheme.TEXT_MUTED));
        } else {
            content.add(VantaUiNode.button(
                    lanStatus.state() == VantaLanHostingService.State.HOSTED
                            ? "CREATE REMOTE INVITE" : "HOST WORLD",
                    this::startRemoteHosting).height(36));
            content.add(VantaUiNode.text(session.hostStatus(), VantaUiTheme.TEXT_MUTED));
        }

        if (lanStatus.state() == VantaLanHostingService.State.FAILED) {
            content.add(VantaUiNode.paragraph(
                    "Minecraft could not open a local listener. Remote hosting cannot continue until the integrated server is published.",
                    VantaUiTheme.TEXT_MUTED, 3));
        }
    }

    private void addGuestSection(VantaUiNode content, boolean compact) {
        content.add(VantaUiNode.spacer(1).height(1).background(VantaUiTheme.BORDER_SUBTLE));
        content.add(VantaUiNode.eyebrow("JOIN A FRIEND"));
        content.add(VantaUiNode.textField("Invite code",
                () -> inviteCode, value -> inviteCode = value.toUpperCase().replaceAll("[^A-Z2-7]", ""),
                32).height(34));
        content.add(VantaUiNode.textField("Minecraft player name",
                () -> playerName, value -> playerName = value, 16).height(34));

        if (session.guestBusy()) {
            content.add(VantaUiNode.progressIndicator("Waiting for the host…").height(30));
            content.add(VantaUiNode.paragraph(session.guestStatus(),
                    VantaUiTheme.TEXT_SECONDARY, compact ? 3 : 2));
            content.add(VantaUiNode.button("CANCEL JOIN",
                    VantaRemoteSessionManager::stopJoining).height(32));
        } else if (session.guestActive()) {
            content.add(VantaUiNode.paragraph(session.guestStatus(),
                    VantaUiTheme.TEXT_SECONDARY, compact ? 3 : 2));
            content.add(VantaUiNode.button("DISCONNECT REMOTE WORLD",
                    VantaRemoteSessionManager::stopJoining).height(32));
        } else {
            content.add(VantaUiNode.button("JOIN REMOTE WORLD", this::joinRemoteWorld)
                    .height(36));
            content.add(VantaUiNode.text(session.guestStatus(), VantaUiTheme.TEXT_MUTED));
        }
    }

    private void saveRelayEndpoint() {
        try {
            relayEndpoint = VantaRelayClient.normalizeBaseUrl(relayEndpoint);
            preferences.remoteRelayEndpoint = relayEndpoint;
            VantaClientPreferences.save();
            localStatus = "Relay address saved on this device.";
        } catch (IllegalArgumentException exception) {
            localStatus = exception.getMessage();
        }
        rebuildUi();
    }

    private void openLanHosting() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            localStatus = "Open a singleplayer world first.";
        } else {
            lanStatus = VantaLanHostingService.start(selectedGameType, allowCommands);
            localStatus = lanStatus.state() == VantaLanHostingService.State.HOSTED
                    ? "Local hosting is active. You can now create a remote invite."
                    : "Could not open the local integrated server.";
        }
        rebuildUi();
    }

    private void startRemoteHosting() {
        if (!saveEndpointWithoutRebuild()) {
            rebuildUi();
            return;
        }
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            localStatus = "Open a singleplayer world before creating a remote invite.";
            rebuildUi();
            return;
        }
        lanStatus = VantaLanHostingService.status();
        if (lanStatus.state() != VantaLanHostingService.State.HOSTED) {
            lanStatus = VantaLanHostingService.start(selectedGameType, allowCommands);
        }
        if (lanStatus.state() != VantaLanHostingService.State.HOSTED) {
            localStatus = "Minecraft could not open the integrated server to LAN.";
            rebuildUi();
            return;
        }
        localStatus = "Starting your private relay invite…";
        VantaRemoteSessionManager.startHosting(relayEndpoint, server, lanStatus.port());
        rebuildUi();
    }

    private void joinRemoteWorld() {
        if (!saveEndpointWithoutRebuild()) {
            rebuildUi();
            return;
        }
        VantaRemoteSessionManager.join(relayEndpoint, inviteCode, playerName, this);
        rebuildUi();
    }

    private boolean saveEndpointWithoutRebuild() {
        try {
            relayEndpoint = VantaRelayClient.normalizeBaseUrl(relayEndpoint);
            preferences.remoteRelayEndpoint = relayEndpoint;
            VantaClientPreferences.save();
            localStatus = "";
            return true;
        } catch (IllegalArgumentException exception) {
            localStatus = exception.getMessage();
            return false;
        }
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
    public void tick() {
        super.tick();
        if (runtime != null) runtime.tick();
        long revision = VantaRemoteSessionManager.revision();
        if (revision != lastSessionRevision) {
            lastSessionRevision = revision;
            session = VantaRemoteSessionManager.snapshot();
            rebuildUi();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, VantaUiTheme.CANVAS);
        if (runtime != null) {
            runtime.setScale((float) preferences.uiScale);
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
        if (VantaRemoteSessionManager.shouldCancelPendingGuestJoin()) {
            VantaRemoteSessionManager.stopJoining();
        }
        if (minecraft != null) minecraft.setScreen(parent);
        else super.onClose();
    }

    /**
     * ConnectScreen replaces this screen after host approval. Screen replacement
     * does not invoke onClose; only an explicit Back action cancels a pending join.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
