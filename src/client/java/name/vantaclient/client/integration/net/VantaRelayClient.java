package name.vantaclient.client.integration.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Client for Vanta's self-hostable HTTPS/WSS room relay.
 *
 * Tokens are deliberately memory-only. The relay endpoint is user-configured;
 * there is no default third-party service or public endpoint baked into the client.
 */
public final class VantaRelayClient {
    private static final Gson GSON = new Gson();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(12);

    public record Room(String code, String hostToken, Instant expiresAt) {}
    public record JoinTicket(String roomCode, String requestId, String guestToken,
                             Instant expiresAt) {}
    public record RelayEvent(String type, String requestId, String playerName,
                             String reason, Instant expiresAt) {}

    private record CreateRoomResponse(String roomCode, String hostToken, String expiresAt) {}
    private record JoinResponse(String requestId, String guestToken, String expiresAt) {}
    private record ControlMessage(String type, String requestId, String reason) {}

    private final URI baseUri;
    private final HttpClient httpClient;

    public VantaRelayClient(String endpoint) {
        this.baseUri = parseBaseUri(endpoint);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public String endpoint() {
        return baseUri.toString();
    }

    /** Validates and normalizes an endpoint, allowing plain HTTP only for local development. */
    public static String normalizeBaseUrl(String endpoint) {
        return parseBaseUri(endpoint).toString();
    }

    public Room createRoom() throws IOException {
        JsonObject response = post("/v1/rooms", "{}");
        String code = requiredString(response, "roomCode");
        String token = requiredString(response, "hostToken");
        if (!code.matches("[A-Z2-7]{20,32}") || token.length() < 40) {
            throw new IOException("Relay returned invalid room credentials.");
        }
        return new Room(code, token, parseInstant(response.get("expiresAt").getAsString()));
    }

    public JoinTicket requestJoin(String roomCode, String playerName) throws IOException {
        String normalizedCode = validateRoomCode(roomCode);
        if (playerName == null || !playerName.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IOException("Enter a valid Minecraft player name.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("playerName", playerName);
        JsonObject response = post("/v1/rooms/" + normalizedCode + "/requests", GSON.toJson(body));
        String requestId = requiredString(response, "requestId");
        String token = requiredString(response, "guestToken");
        if (!requestId.matches("[A-Za-z0-9_-]{12,32}") || token.length() < 40) {
            throw new IOException("Relay returned invalid guest credentials.");
        }
        return new JoinTicket(normalizedCode, requestId, token,
                parseInstant(response.get("expiresAt").getAsString()));
    }

    public HostControlConnection connectHostControl(Room room,
                                                     Consumer<RelayEvent> events,
                                                     Consumer<Throwable> errors) throws IOException {
        requireRoom(room);
        String path = "/v1/host/control/" + room.code();
        WebSocketJsonListener listener = new WebSocketJsonListener(events, errors);
        java.net.http.WebSocket socket = connectWebSocket(path, room.hostToken(), listener);
        return new HostControlConnection(socket);
    }

    public GuestDecisionConnection connectGuestControl(JoinTicket ticket) throws IOException {
        requireTicket(ticket);
        GuestDecisionListener listener = new GuestDecisionListener();
        String path = "/v1/guest/control/" + ticket.roomCode() + "/" + ticket.requestId();
        java.net.http.WebSocket socket = connectWebSocket(path, ticket.guestToken(), listener);
        return new GuestDecisionConnection(socket, listener);
    }

    public WebSocketDuplexConnection connectHostData(Room room, String requestId) throws IOException {
        requireRoom(room);
        String path = "/v1/host/data/" + room.code() + "/" + validateRequestId(requestId);
        return WebSocketDuplexConnection.connect(httpClient, webSocketUri(path), room.hostToken());
    }

    public WebSocketDuplexConnection connectGuestData(JoinTicket ticket) throws IOException {
        requireTicket(ticket);
        String path = "/v1/guest/data/" + ticket.roomCode() + "/" + ticket.requestId();
        return WebSocketDuplexConnection.connect(httpClient, webSocketUri(path), ticket.guestToken());
    }

    private JsonObject post(String path, String body) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Relay request was interrupted.", exception);
        } catch (RuntimeException exception) {
            throw new IOException("Could not reach the configured Vanta relay.", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String code = "HTTP " + response.statusCode();
            try {
                JsonObject error = GSON.fromJson(response.body(), JsonObject.class);
                if (error != null && error.has("error")) code = error.get("error").getAsString();
            } catch (RuntimeException ignored) {
                // Do not echo arbitrary server response bodies into the UI.
            }
            throw new IOException("Relay rejected the request: " + code);
        }
        try {
            JsonObject object = GSON.fromJson(response.body(), JsonObject.class);
            if (object == null) throw new IOException("Relay returned an empty response.");
            return object;
        } catch (RuntimeException exception) {
            throw new IOException("Relay returned malformed JSON.", exception);
        }
    }

    private java.net.http.WebSocket connectWebSocket(String path, String token,
                                                      java.net.http.WebSocket.Listener listener)
            throws IOException {
        try {
            return httpClient.newWebSocketBuilder()
                    .connectTimeout(SOCKET_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .buildAsync(webSocketUri(path), listener)
                    .get(SOCKET_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Relay connection was interrupted.", exception);
        } catch (ExecutionException | TimeoutException | CompletionException exception) {
            throw new IOException("Could not establish a secure relay connection.", exception);
        }
    }

    private URI webSocketUri(String path) {
        String scheme = baseUri.getScheme().equals("https") ? "wss" : "ws";
        return URI.create(scheme + "://" + baseUri.getRawAuthority() + path);
    }

    private static URI parseBaseUri(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > 256) {
            throw new IllegalArgumentException("Enter your relay's HTTPS address.");
        }
        final URI parsed;
        try {
            parsed = URI.create(endpoint.trim()).normalize();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Relay address is not a valid URL.", exception);
        }
        String scheme = parsed.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || parsed.getHost() == null || parsed.getUserInfo() != null
                || parsed.getQuery() != null || parsed.getFragment() != null
                || !(parsed.getPath().isEmpty() || parsed.getPath().equals("/"))) {
            throw new IllegalArgumentException("Relay address must be an HTTPS origin without a path.");
        }
        if ("http".equalsIgnoreCase(scheme) && !isLoopbackHost(parsed.getHost())) {
            throw new IllegalArgumentException("Remote relays must use HTTPS. Plain HTTP is allowed only on this device.");
        }
        try {
            return new URI(scheme.toLowerCase(), null, parsed.getHost(), parsed.getPort(),
                    "/", null, null);
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("Relay address is invalid.", exception);
        }
    }

    private static boolean isLoopbackHost(String host) {
        return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
                || host.equals("::1") || host.equals("[::1]");
    }

    private static String validateRoomCode(String value) throws IOException {
        String code = value == null ? "" : value.trim().toUpperCase();
        if (!code.matches("[A-Z2-7]{20,32}")) throw new IOException("Invite code is invalid.");
        return code;
    }

    private static String validateRequestId(String value) throws IOException {
        if (value == null || !value.matches("[A-Za-z0-9_-]{12,32}")) {
            throw new IOException("Relay request is invalid.");
        }
        return value;
    }

    private static void requireRoom(Room room) throws IOException {
        if (room == null || room.hostToken() == null || room.hostToken().length() < 40) {
            throw new IOException("Host session is not initialized.");
        }
        validateRoomCode(room.code());
    }

    private static void requireTicket(JoinTicket ticket) throws IOException {
        if (ticket == null || ticket.guestToken() == null || ticket.guestToken().length() < 40) {
            throw new IOException("Guest session is not initialized.");
        }
        validateRoomCode(ticket.roomCode());
        validateRequestId(ticket.requestId());
    }

    private static String requiredString(JsonObject object, String name) throws IOException {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            throw new IOException("Relay response is missing " + name + ".");
        }
        return object.get(name).getAsString();
    }

    private static Instant parseInstant(String value) throws IOException {
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new IOException("Relay returned an invalid expiry time.", exception);
        }
    }

    private static RelayEvent parseEvent(String value) {
        return GSON.fromJson(value, RelayEvent.class);
    }

    public static final class HostControlConnection implements AutoCloseable {
        private final java.net.http.WebSocket socket;

        private HostControlConnection(java.net.http.WebSocket socket) {
            this.socket = socket;
        }

        public void approve(String requestId) throws IOException {
            send(new ControlMessage("approve", validateRequestId(requestId), null));
        }

        public void reject(String requestId, String reason) throws IOException {
            send(new ControlMessage("reject", validateRequestId(requestId),
                    reason == null ? "" : reason.substring(0, Math.min(80, reason.length()))));
        }

        private void send(ControlMessage message) throws IOException {
            try {
                socket.sendText(GSON.toJson(message), true)
                        .get(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Relay control message was interrupted.", exception);
            } catch (ExecutionException | TimeoutException exception) {
                throw new IOException("Could not send the host approval.", exception);
            }
        }

        @Override
        public void close() {
            try {
                socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "host closed")
                        .get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                socket.abort();
            }
        }
    }

    public static final class GuestDecisionConnection implements AutoCloseable {
        private final java.net.http.WebSocket socket;
        private final GuestDecisionListener listener;

        private GuestDecisionConnection(java.net.http.WebSocket socket,
                                         GuestDecisionListener listener) {
            this.socket = socket;
            this.listener = listener;
        }

        public RelayEvent awaitDecision(Duration timeout) throws IOException {
            try {
                RelayEvent event = listener.decision.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (event == null || !("approved".equals(event.type()) || "rejected".equals(event.type()))) {
                    throw new IOException("Relay returned an unexpected approval response.");
                }
                return event;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Waiting for host approval was interrupted.", exception);
            } catch (ExecutionException | TimeoutException exception) {
                throw new IOException("The host did not approve the invite in time.", exception);
            }
        }

        @Override
        public void close() {
            try {
                socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "guest decision received")
                        .get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                socket.abort();
            }
        }
    }

    private static final class WebSocketJsonListener implements java.net.http.WebSocket.Listener {
        private final Consumer<RelayEvent> events;
        private final Consumer<Throwable> errors;
        private final StringBuilder text = new StringBuilder();

        private WebSocketJsonListener(Consumer<RelayEvent> events, Consumer<Throwable> errors) {
            this.events = events == null ? event -> {} : events;
            this.errors = errors == null ? error -> {} : errors;
        }

        @Override
        public void onOpen(java.net.http.WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public synchronized java.util.concurrent.CompletionStage<?> onText(
                java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
            if (text.length() + data.length() > 4 * 1024) {
                webSocket.abort();
                errors.accept(new IOException("Relay control message exceeded its size limit."));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            text.append(data);
            if (last) {
                try {
                    RelayEvent event = parseEvent(text.toString());
                    if (event == null || event.type() == null) throw new IOException("Invalid relay event.");
                    events.accept(event);
                } catch (RuntimeException | IOException exception) {
                    errors.accept(exception);
                    webSocket.abort();
                } finally {
                    text.setLength(0);
                }
            }
            webSocket.request(1);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(java.net.http.WebSocket webSocket, Throwable error) {
            errors.accept(error);
        }
    }

    private static final class GuestDecisionListener implements java.net.http.WebSocket.Listener {
        private final java.util.concurrent.CompletableFuture<RelayEvent> decision =
                new java.util.concurrent.CompletableFuture<>();
        private final StringBuilder text = new StringBuilder();

        @Override
        public void onOpen(java.net.http.WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public synchronized java.util.concurrent.CompletionStage<?> onText(
                java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
            if (text.length() + data.length() > 4 * 1024) {
                decision.completeExceptionally(new IOException("Relay response exceeded its size limit."));
                webSocket.abort();
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            text.append(data);
            if (last) {
                try {
                    RelayEvent event = parseEvent(text.toString());
                    if (event == null || event.type() == null) throw new IOException("Invalid relay response.");
                    if ("approved".equals(event.type()) || "rejected".equals(event.type())) {
                        decision.complete(event);
                    } else {
                        decision.completeExceptionally(new IOException("Unexpected relay response."));
                    }
                } catch (RuntimeException exception) {
                    decision.completeExceptionally(exception);
                    webSocket.abort();
                } finally {
                    text.setLength(0);
                }
            }
            webSocket.request(1);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(java.net.http.WebSocket webSocket, Throwable error) {
            decision.completeExceptionally(error);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(
                java.net.http.WebSocket webSocket, int statusCode, String reason) {
            if (!decision.isDone()) {
                decision.completeExceptionally(new IOException("Relay closed before host approval."));
            }
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }
}
