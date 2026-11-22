package name.vantaclient.client.gui;

import name.vantaclient.client.gui.compose.VantaCompose;
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
public final class VantaWorldShareScreen extends Screen implements VantaCompose.Screen {
    private static final List<String> GAME_MODES =
            List.of("Survival", "Creative", "Adventure", "Spectator");

    private final Screen parent;
    private VantaUiRuntime runtime;
    private VantaClientPreferences preferences;
    private VantaLanHostingService.Status lanStatus =
            new VantaLanHostingService.Status(VantaLanHostingService.State.NO_WORLD, -1);
    private VantaRemoteSessionManager.Snapshot session = VantaRemoteSessionManager.snapshot();

    private String relayEndpoint = VantaClientPreferences.DEFAULT_REMOTE_RELAY_ENDPOINT;
    private String inviteCode = "";
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
        boolean compact = VantaUiRuntime.designWidth(width, height) < 680.0f;
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        lanStatus = VantaLanHostingService.status();

        VantaUiNode root = VantaUiNode.column(compact ? 6 : 10, compact ? 8 : 14)
                ;
        VantaUiNode header = VantaUiNode.row(compact ? 8 : 12, compact ? 10 : 16)
                .height(compact ? 52 : 54)
                .background(VantaUiTheme.PANEL)
                .align(VantaUiNode.Alignment.CENTER);
        VantaUiNode heading = VantaUiNode.column(2, 0);
        heading.add(VantaUiNode.eyebrow("Vanta / multiplayer"));
        heading.add(VantaUiNode.title("World sharing"));
        header.add(heading);
        header.add(VantaUiNode.spacer(1).flex(1));
        header.add(VantaUiNode.button("Back", this::onClose).height(30)
                .tooltip("Return to the previous screen"));
        root.add(header);

        VantaUiNode content = VantaUiNode.column(compact ? 8 : 16, compact ? 10 : 20)
                .flex(1)
                .padding(compact ? 8 : 18)
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
        content.add(VantaUiNode.paragraph(
                "Vanta Cloudflare relay is preconfigured. You can replace it with any HTTPS relay you operate or trust.",
                VantaUiTheme.TEXT_SECONDARY, compact ? 3 : 2));
        VantaUiNode endpointRow = VantaUiNode.row(8, 0)
                .height(34)
                .align(VantaUiNode.Alignment.CENTER);
        endpointRow.add(VantaUiNode.textField("https://vanta-relay.hugohakonen.workers.dev", 
                () -> relayEndpoint, value -> relayEndpoint = value, 256)
                .flex(1).minSize(80, 30));
        endpointRow.add(VantaUiNode.button("Save", this::saveRelayEndpoint)
                .size(62, 30));
        content.add(endpointRow);
        content.add(VantaUiNode.toggle("Maximum IP privacy (TURN only)",
                () -> preferences.remoteMaximumIpPrivacy,
                value -> {
                    preferences.remoteMaximumIpPrivacy = value;
                    VantaClientPreferences.save();
                }).height(34));
        content.add(VantaUiNode.paragraph(
                "When enabled, WebRTC uses TURN-only candidates so guests do not learn your home IP. This requires TURN infrastructure; the WebSocket relay remains the fallback.",
                VantaUiTheme.TEXT_MUTED, compact ? 4 : 2));
        content.add(VantaUiNode.paragraph(
                "The relay forwards Minecraft TCP traffic without interpreting packets. Its operator can still see connection metadata and source network addresses.",
                VantaUiTheme.TEXT_MUTED, compact ? 3 : 2));
        content.add(VantaUiNode.paragraph(
                "Remote hosting also publishes the world to your local network. Nearby devices may discover and join it through normal Minecraft LAN discovery.",
                VantaUiTheme.TEXT_MUTED, compact ? 3 : 2));
        if (!localStatus.isBlank()) {
            content.add(VantaUiNode.paragraph(localStatus,
                    VantaUiTheme.TEXT_MUTED, compact ? 3 : 2));
        }
    }

    private void addHostSection(VantaUiNode content, MinecraftServer server, boolean compact) {
        content.add(VantaUiNode.eyebrow("Host a world"));
        if (server == null) {
            content.add(VantaUiNode.title("Open a singleplayer world to host"));
            content.add(VantaUiNode.paragraph(
                    "Hosting is available while a singleplayer integrated server is running. You can still join a friend's invite from this screen.",
                    VantaUiTheme.TEXT_SECONDARY, compact ? 3 : 2));
            return;
        }

        if (lanStatus.state() == VantaLanHostingService.State.HOSTED) {
            content.add(VantaUiNode.text("Your integrated server is ready for guests.",
                    VantaUiTheme.TEXT_SECONDARY));
        } else {
            content.add(VantaUiNode.paragraph(
                    "Vanta publishes the active singleplayer world through Minecraft's integrated server.",
                    VantaUiTheme.TEXT_SECONDARY, compact ? 2 : 1));
            content.add(VantaUiNode.dropdown("Guest game mode", GAME_MODES,
                    () -> gameTypeLabel(selectedGameType),
                    value -> selectedGameType = gameTypeFromLabel(value)).height(34));
            content.add(VantaUiNode.toggle("Allow commands for guests",
                    () -> allowCommands, value -> allowCommands = value).height(34));
        }

        if (session.hostActive()) {
            content.add(VantaUiNode.spacer(1).height(1).background(VantaUiTheme.BORDER_SUBTLE));
            content.add(VantaUiNode.row(8, 0).height(22)
                    .add(VantaUiNode.eyebrow("Remote invite active"))
                    .add(VantaUiNode.spacer(1).flex(1))
                    .add(VantaUiNode.text(session.connectedGuests() + " connected",
                            VantaUiTheme.TEXT_SECONDARY)));
            content.add(VantaUiNode.row(8, 0)
                    .height(36)
                    .align(VantaUiNode.Alignment.CENTER)
                    .add(VantaUiNode.title(session.roomCode()).flex(1))
                    .add(VantaUiNode.button("Copy invite",
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
                String claimedProfile = guest.playerUUID() == null
                        ? "Profile ID unavailable"
                        : "Profile claim " + guest.playerUUID().substring(0, 8);
                guestInfo.add(VantaUiNode.text("Claims name: " + guest.playerName(),
                        VantaUiTheme.TEXT_PRIMARY));
                guestInfo.add(VantaUiNode.text(guest.previouslyWhitelisted()
                                ? "Saved profile claim · key check required"
                                : claimedProfile + " · unverified; approval required",
                        VantaUiTheme.TEXT_MUTED));
                request.add(guestInfo);
                request.add(VantaUiNode.button("Approve",
                        () -> VantaRemoteSessionManager.approveGuest(guest.requestId()))
                        .size(76, 30).enabled(!guest.approving()));
                request.add(VantaUiNode.button("Reject",
                        () -> VantaRemoteSessionManager.rejectGuest(guest.requestId()))
                        .size(64, 30).enabled(!guest.approving()));
                content.add(request);
            }
            for (VantaRemoteSessionManager.ConnectedGuest guest
                    : VantaRemoteSessionManager.connectedGuests()) {
                VantaUiNode active = VantaUiNode.row(8, 4)
                        .height(38)
                        .align(VantaUiNode.Alignment.CENTER);
                active.add(VantaUiNode.text(guest.playerName(),
                        VantaUiTheme.TEXT_PRIMARY).flex(1));
                active.add(VantaUiNode.text(guest.identityVerified()
                                ? "Account key verified" : "Awaiting account key",
                        VantaUiTheme.TEXT_MUTED));
                active.add(VantaUiNode.button("Kick",
                        () -> VantaRemoteSessionManager.kickGuest(guest.requestId()))
                        .size(62, 30));
                content.add(active);
            }
            content.add(VantaUiNode.paragraph(
                    "Only matching Mojang-verified account keys are saved. Offline UUID mismatches do not block approved guests, but they are not saved as trusted profiles.",
                    VantaUiTheme.TEXT_MUTED, compact ? 4 : 2));
            List<String> savedProfiles = VantaRemoteSessionManager.whitelistedPlayerUUIDs();
            if (!savedProfiles.isEmpty()) {
                content.add(VantaUiNode.eyebrow("Saved players for this world"));
                for (String profileUUID : savedProfiles) {
                    VantaUiNode saved = VantaUiNode.row(8, 4)
                            .height(34)
                            .align(VantaUiNode.Alignment.CENTER);
                    saved.add(VantaUiNode.text(profileUUID.substring(0, 8) + "…",
                            VantaUiTheme.TEXT_SECONDARY).flex(1));
                    saved.add(VantaUiNode.button("Remove",
                            () -> VantaRemoteSessionManager.revokeWhitelistedPlayer(profileUUID))
                            .size(72, 28));
                    content.add(saved);
                }
            }
            content.add(VantaUiNode.button("Stop remote hosting",
                    VantaRemoteSessionManager::stopHosting).height(34));
        } else if (session.hostBusy()) {
            content.add(VantaUiNode.progressIndicator("Creating your invite room…").height(30));
            content.add(VantaUiNode.paragraph(session.hostStatus(), VantaUiTheme.TEXT_MUTED, compact ? 3 : 2));
        } else {
            content.add(VantaUiNode.button(
                    lanStatus.state() == VantaLanHostingService.State.HOSTED
                            ? "Create remote invite" : "Host world",
                    this::startRemoteHosting).height(36));
            content.add(VantaUiNode.paragraph(session.hostStatus(), VantaUiTheme.TEXT_MUTED, compact ? 3 : 2));
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
        content.add(VantaUiNode.text("Minecraft account: "
                + Minecraft.getInstance().getUser().getName(), VantaUiTheme.TEXT_SECONDARY));

        if (session.guestBusy()) {
            content.add(VantaUiNode.progressIndicator("Waiting for the host…").height(30));
            content.add(VantaUiNode.paragraph(session.guestStatus(),
                    VantaUiTheme.TEXT_SECONDARY, compact ? 3 : 2));
            content.add(VantaUiNode.button("Cancel join",
                    VantaRemoteSessionManager::stopJoining).height(32));
        } else if (session.guestActive()) {
            content.add(VantaUiNode.paragraph(session.guestStatus(),
                    VantaUiTheme.TEXT_SECONDARY, compact ? 3 : 2));
            content.add(VantaUiNode.button("Disconnect remote world",
                    VantaRemoteSessionManager::stopJoining).height(32));
        } else {
            content.add(VantaUiNode.button("Join remote world", this::joinRemoteWorld)
                    .height(36));
            content.add(VantaUiNode.paragraph(session.guestStatus(), VantaUiTheme.TEXT_MUTED, compact ? 3 : 2));
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
        VantaRemoteSessionManager.join(relayEndpoint, inviteCode, this);
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
        VantaCompose.renderScrim(graphics, width, height);
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
