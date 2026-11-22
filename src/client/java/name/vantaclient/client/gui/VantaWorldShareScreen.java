package name.vantaclient.client.gui;

import name.vantaclient.client.gui.compose.VantaCompose;
import name.vantaclient.client.gui.compose.components.VantaComponents;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import name.vantaclient.client.gui.menu.MenuComponents;
import name.vantaclient.client.gui.menu.MenuIcons;
import name.vantaclient.client.gui.menu.MenuTheme;
import name.vantaclient.client.gui.menu.ModSettingsPage;
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
        runtime.setZoom(zoom());
        runtime.setMaxViewportSize(MAX_WIDTH, MAX_HEIGHT);
        runtime.layout(font, width, height);
    }

    private static final int MAX_WIDTH = 1000;
    private static final int MAX_HEIGHT = 620;

    private float zoom() {
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        if (!Float.isFinite(guiScale) || guiScale <= 0) guiScale = 1.0f;
        float userScale = (float) preferences.uiScale;
        if (!Float.isFinite(userScale) || userScale <= 0) userScale = 1.0f;
        float fit = Math.min(width * guiScale * 0.94f / MAX_WIDTH, height * guiScale * 0.90f / MAX_HEIGHT) / userScale;
        float stepped = Math.round(fit * 8.0f) / 8.0f;
        return Math.max(0.5f, Math.min(3.0f, stepped));
    }

    private boolean compactLayout() {
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        if (!Float.isFinite(guiScale) || guiScale < 1.0f) guiScale = 1.0f;
        return width * guiScale / (float) Math.max(0.5, preferences.uiScale) < 820.0f;
    }

    private VantaUiNode buildRoot() {
        boolean compact = compactLayout();
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        lanStatus = VantaLanHostingService.status();

        VantaUiNode shell = VantaUiNode.stack(0);
        shell.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            MenuTheme.fill(g, x - 3, y + 4, w + 6, h + 4, 22, 0x1E000000);
            MenuTheme.gradient(g, x, y, w, h, 18, MenuTheme.panel(), MenuTheme.panelBottom());
            MenuTheme.outline(g, x, y, w, h, 18, 0x38FFFFFF);
        }));

        VantaUiNode page = VantaUiNode.column(0, 0);
        VantaUiNode bar = VantaUiNode.row(compact ? 6 : 10, compact ? 12 : 22)
                .height(compact ? 64 : 84).align(VantaUiNode.Alignment.CENTER);
        bar.add(MenuComponents.iconButton(MenuIcons.Icon.ARROW_LEFT, "Back", true, this::onClose));
        bar.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            MenuTheme.text(g, f, "World sharing", x + 6, y + h / 2 - 8, MenuTheme.TEXT, MenuTheme.FONT_HEADING);
            MenuTheme.text(g, f, MenuTheme.fit(f, "Play together through a private relay. No port forwarding.",
                    w - 12, MenuTheme.FONT_SMALL), x + 6, y + h / 2 + 16, MenuTheme.TEXT_DIM, MenuTheme.FONT_SMALL);
        }).flex(1).height(48));
        bar.add(MenuComponents.iconButton(MenuIcons.Icon.CLOSE, "Close", true, this::onClose));
        page.add(bar);

        VantaUiNode body = VantaUiNode.column(14, compact ? 14 : 22).flex(1).scrollable();
        if (!localStatus.isBlank()) {
            body.add(VantaUiNode.paragraph(localStatus, MenuTheme.TEXT_DIM, 2));
        }
        VantaUiNode host = card("Host a world", hostSubtitle(server), MenuIcons.Icon.GRID,
                hostStatusLabel(server), hostStatusColor(server));
        addHostSection(host, server);
        VantaUiNode guest = card("Join a friend", "Paste the invite code your host sent you.",
                MenuIcons.Icon.PROFILES, guestStatusLabel(), guestStatusColor());
        addGuestSection(guest);
        if (compact) {
            body.add(host);
            body.add(guest);
        } else {
            VantaUiNode row = VantaUiNode.row(16, 0).align(VantaUiNode.Alignment.START);
            row.add(host.flex(1));
            row.add(guest.flex(1));
            body.add(row);
        }
        page.add(body);
        shell.add(page);
        return shell;
    }

    private VantaUiNode card(String title, String subtitle, MenuIcons.Icon icon, String status, int statusColor) {
        VantaUiNode card = VantaUiNode.column(12, 22)
                .background(MenuTheme.CARD).border(0x26FFFFFF).radius(18);
        card.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            int accent = MenuTheme.accent();
            MenuTheme.fill(g, x, y + 2, 48, 48, 14, MenuTheme.alpha(accent, 40));
            MenuTheme.outline(g, x, y + 2, 48, 48, 14, MenuTheme.alpha(accent, 90));
            MenuIcons.draw(g, icon, x + 12, y + 14, 24, accent);
            int pillW = MenuTheme.width(f, status, MenuTheme.FONT_SMALL) + 30;
            int textW = Math.max(40, w - 64 - pillW - 8);
            MenuTheme.text(g, f, MenuTheme.fit(f, title, textW, MenuTheme.FONT_MEDIUM), x + 64, y + 16,
                    MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
            MenuTheme.text(g, f, MenuTheme.fit(f, subtitle, textW, MenuTheme.FONT_SMALL), x + 64, y + 38,
                    MenuTheme.TEXT_DIM, MenuTheme.FONT_SMALL);
            int px = x + w - pillW;
            MenuTheme.fill(g, px, y + 12, pillW, 26, 13, MenuTheme.alpha(statusColor, 36));
            MenuTheme.dot(g, px + 13, y + 25, 8, statusColor);
            MenuTheme.text(g, f, status, px + 24, y + 25, MenuTheme.mix(statusColor, 0xFFFFFFFF, 0.45f),
                    MenuTheme.FONT_SMALL);
        }).height(52));
        card.add(VantaUiNode.spacer(1).height(1).background(MenuTheme.LINE_SOFT).radius(0));
        return card;
    }

    private String hostSubtitle(MinecraftServer server) {
        if (server == null) return "Needs a singleplayer world to be open.";
        return session.hostActive() ? "Guests can join with your invite code."
                : "Publish this world and invite friends.";
    }

    private String hostStatusLabel(MinecraftServer server) {
        if (server == null) return "No world";
        if (lanStatus.state() == VantaLanHostingService.State.FAILED) return "Error";
        if (session.hostActive()) return "Live";
        if (session.hostBusy()) return "Starting";
        return "Ready";
    }

    private int hostStatusColor(MinecraftServer server) {
        if (server == null) return 0xFF7C8494;
        if (lanStatus.state() == VantaLanHostingService.State.FAILED) return 0xFFE5484D;
        if (session.hostActive()) return 0xFF3DD68C;
        if (session.hostBusy()) return 0xFFF2C14E;
        return MenuTheme.accent();
    }

    private String guestStatusLabel() {
        if (session.guestActive()) return "Connected";
        if (session.guestBusy()) return "Joining";
        return "Ready";
    }

    private int guestStatusColor() {
        if (session.guestActive()) return 0xFF3DD68C;
        if (session.guestBusy()) return 0xFFF2C14E;
        return MenuTheme.accent();
    }

    private VantaUiNode settingLine(String title, String detail, VantaUiNode control, int controlWidth) {
        VantaUiNode row = VantaUiNode.row(12, 14).height(detail == null ? 54 : 62)
                .background(MenuTheme.ROW).border(0x1FFFFFFF).radius(12)
                .align(VantaUiNode.Alignment.CENTER);
        VantaUiNode labels = VantaUiNode.column(2, 0).flex(1);
        labels.add(VantaUiNode.text(title, MenuTheme.TEXT).font(MenuTheme.FONT_MEDIUM).height(18));
        if (detail != null) labels.add(VantaUiNode.text(detail, MenuTheme.TEXT_MUTED).font(MenuTheme.FONT_SMALL).height(16));
        row.add(labels);
        row.add(control.width(controlWidth));
        return row;
    }

    private VantaUiNode primary(String label, Runnable action) {
        return MenuComponents.button(font, label, null, true, action).height(44);
    }

    private VantaUiNode secondary(String label, Runnable action) {
        return MenuComponents.button(font, label, null, false, action).height(40);
    }

    private void addHostSection(VantaUiNode content, MinecraftServer server) {
        if (server == null) {
            content.add(VantaUiNode.paragraph(
                    "Open a singleplayer world and come back here to invite friends. You can still join someone else's invite on the right.",
                    MenuTheme.TEXT_DIM, 4));
            return;
        }

        if (lanStatus.state() != VantaLanHostingService.State.HOSTED && !session.hostActive()) {
            content.add(settingLine("Guest game mode", "What friends start as.",
                    VantaUiNode.dropdown("", GAME_MODES, () -> gameTypeLabel(selectedGameType),
                            value -> selectedGameType = gameTypeFromLabel(value)).menuStyle().height(32), 150));
            content.add(settingLine("Allow commands", "Lets guests run cheats and ops commands.",
                    ModSettingsPage.toggleControl(() -> allowCommands, value -> allowCommands = value), 70));
        }

        if (session.hostActive()) {
            content.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
                int accent = MenuTheme.accent();
                MenuTheme.fill(g, x, y, w, h, 14, MenuTheme.alpha(accent, 26));
                MenuTheme.outline(g, x, y, w, h, 14, MenuTheme.alpha(accent, 110));
                MenuTheme.text(g, f, "Invite code", x + 16, y + 20, MenuTheme.TEXT_DIM, MenuTheme.FONT_SMALL);
                MenuTheme.text(g, f, MenuTheme.fit(f, session.roomCode(), w - 32, MenuTheme.FONT_HEADING),
                        x + 16, y + 50, MenuTheme.TEXT, MenuTheme.FONT_HEADING);
                MenuTheme.text(g, f, session.connectedGuests() + " connected", x + 16, y + h - 18,
                        0xFF3DD68C, MenuTheme.FONT_SMALL);
            }).height(110));
            content.add(secondary("Copy invite code",
                    () -> Minecraft.getInstance().keyboardHandler.setClipboard(session.roomCode())));
            if (session.roomExpiresAt() != null) {
                content.add(VantaUiNode.text("Expires "
                        + session.roomExpiresAt().toString().replace('T', ' ').replace("Z", " UTC"),
                        MenuTheme.TEXT_MUTED).font(MenuTheme.FONT_SMALL).height(16));
            }
            if (!session.hostStatus().isBlank()) {
                content.add(VantaUiNode.paragraph(session.hostStatus(), MenuTheme.TEXT_DIM, 2));
            }
            for (VantaRemoteSessionManager.PendingGuest guest : session.pendingGuests()) {
                VantaUiNode request = VantaUiNode.row(8, 12).height(64)
                        .background(MenuTheme.ROW).border(0x1FFFFFFF).radius(12)
                        .align(VantaUiNode.Alignment.CENTER);
                VantaUiNode info = VantaUiNode.column(2, 0).flex(1);
                String claimed = guest.playerUUID() == null ? "Profile ID unavailable"
                        : "Profile claim " + guest.playerUUID().substring(0, 8);
                info.add(VantaUiNode.text(guest.playerName() + " wants to join", MenuTheme.TEXT)
                        .font(MenuTheme.FONT_MEDIUM).height(18));
                info.add(VantaUiNode.text(guest.previouslyWhitelisted()
                                ? "Saved profile · key check required" : claimed + " · unverified",
                        MenuTheme.TEXT_MUTED).font(MenuTheme.FONT_SMALL).height(16));
                request.add(info);
                request.add(MenuComponents.button(font, "Approve", null, true,
                        () -> VantaRemoteSessionManager.approveGuest(guest.requestId()))
                        .size(92, 36).enabled(!guest.approving()));
                request.add(MenuComponents.button(font, "Reject", null, false,
                        () -> VantaRemoteSessionManager.rejectGuest(guest.requestId()))
                        .size(80, 36).enabled(!guest.approving()));
                content.add(request);
            }
            for (VantaRemoteSessionManager.ConnectedGuest guest : VantaRemoteSessionManager.connectedGuests()) {
                VantaUiNode active = VantaUiNode.row(8, 12).height(54)
                        .background(MenuTheme.ROW).border(0x1FFFFFFF).radius(12)
                        .align(VantaUiNode.Alignment.CENTER);
                VantaUiNode info = VantaUiNode.column(2, 0).flex(1);
                info.add(VantaUiNode.text(guest.playerName(), MenuTheme.TEXT).font(MenuTheme.FONT_MEDIUM).height(18));
                info.add(VantaUiNode.text(guest.identityVerified() ? "Account key verified" : "Awaiting account key",
                        MenuTheme.TEXT_MUTED).font(MenuTheme.FONT_SMALL).height(16));
                active.add(info);
                active.add(MenuComponents.button(font, "Kick", null, false,
                        () -> VantaRemoteSessionManager.kickGuest(guest.requestId())).size(76, 34));
                content.add(active);
            }
            List<String> savedProfiles = VantaRemoteSessionManager.whitelistedPlayerUUIDs();
            if (!savedProfiles.isEmpty()) {
                content.add(VantaUiNode.text("Saved players", MenuTheme.TEXT_MUTED).font(MenuTheme.FONT_SMALL).height(22));
                for (String profileUUID : savedProfiles) {
                    VantaUiNode saved = VantaUiNode.row(8, 12).height(46)
                            .background(MenuTheme.ROW).border(0x1FFFFFFF).radius(12)
                            .align(VantaUiNode.Alignment.CENTER);
                    saved.add(VantaUiNode.text(profileUUID.substring(0, 8) + "…", MenuTheme.TEXT_DIM).flex(1));
                    saved.add(MenuComponents.button(font, "Remove", null, false,
                            () -> VantaRemoteSessionManager.revokeWhitelistedPlayer(profileUUID)).size(88, 32));
                    content.add(saved);
                }
            }
            content.add(VantaUiNode.paragraph(
                    "Only matching Mojang-verified account keys are saved as trusted profiles.",
                    MenuTheme.TEXT_MUTED, 2));
            content.add(secondary("Stop hosting", VantaRemoteSessionManager::stopHosting));
        } else if (session.hostBusy()) {
            content.add(VantaUiNode.progressIndicator("Creating your invite…").height(30));
            if (!session.hostStatus().isBlank()) content.add(VantaUiNode.paragraph(session.hostStatus(), MenuTheme.TEXT_DIM, 2));
        } else {
            content.add(primary(lanStatus.state() == VantaLanHostingService.State.HOSTED
                    ? "Create invite code" : "Host this world", this::startRemoteHosting));
            if (!session.hostStatus().isBlank()) content.add(VantaUiNode.paragraph(session.hostStatus(), MenuTheme.TEXT_MUTED, 2));
        }

        if (lanStatus.state() == VantaLanHostingService.State.FAILED) {
            content.add(VantaUiNode.paragraph(lanStatus.detail().isBlank()
                            ? "Minecraft could not open a local listener. Remote hosting cannot continue until the integrated server is published."
                            : lanStatus.detail(), 0xFFE5484D, 3));
        }
    }

    private void addGuestSection(VantaUiNode content) {
        VantaUiNode field = VantaUiNode.row(0, 0).height(48)
                .background(MenuTheme.FIELD).radius(13).border(0x33FFFFFF);
        field.add(VantaUiNode.textField("Invite code",
                () -> inviteCode, value -> inviteCode = value.toUpperCase().replaceAll("[^A-Z2-7]", ""),
                32).menuStyle().height(48).flex(1));
        content.add(field);
        content.add(VantaUiNode.text("Joining as " + Minecraft.getInstance().getUser().getName(),
                MenuTheme.TEXT_MUTED).font(MenuTheme.FONT_SMALL).height(18));

        if (session.guestBusy()) {
            content.add(VantaUiNode.progressIndicator("Waiting for the host…").height(30));
            if (!session.guestStatus().isBlank()) content.add(VantaUiNode.paragraph(session.guestStatus(), MenuTheme.TEXT_DIM, 2));
            content.add(secondary("Cancel", VantaRemoteSessionManager::stopJoining));
        } else if (session.guestActive()) {
            if (!session.guestStatus().isBlank()) content.add(VantaUiNode.paragraph(session.guestStatus(), MenuTheme.TEXT_DIM, 2));
            content.add(secondary("Disconnect", VantaRemoteSessionManager::stopJoining));
        } else {
            content.add(primary("Join world", this::joinRemoteWorld));
            content.add(VantaUiNode.paragraph(session.guestStatus().isBlank()
                    ? "Ask your host to open World sharing and send you their invite code."
                    : session.guestStatus(), MenuTheme.TEXT_MUTED, 3));
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
                    : lanStatus.detail().isBlank()
                            ? "Could not open the local integrated server."
                            : lanStatus.detail();
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
            localStatus = lanStatus.detail().isBlank()
                    ? "Minecraft could not open the integrated server to LAN."
                    : lanStatus.detail();
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
