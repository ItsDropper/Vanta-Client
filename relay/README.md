# Vanta self-hosted multiplayer relay

This service is an opt-in, self-hosted WebSocket relay for Vanta's remote world-sharing feature. It does not require players to expose a public home IP or forward a Minecraft port. Put it behind an HTTPS reverse proxy and configure Vanta clients with its HTTPS origin.

## What it does

- Creates short-lived rooms with 128-bit invite codes and 256-bit host tokens.
- Requires the host to approve every guest request.
- Issues a separate 256-bit, short-lived guest token for each request.
- Opens one host data socket and one guest data socket per approved player.
- Relays only bounded binary WebSocket frames. `VANTA_EOF` is a directional half-close marker.
- Expires rooms and requests, caps pending/active guests, and rate-limits room creation/join attempts by source address.
- Does not inspect, modify, log or persist Minecraft payloads. The relay does see transport metadata and the source network addresses required by the underlying connection.

The relay is a forwarding service, not a direct P2P path. A future WebRTC transport can use the same stream interface and fall back to this relay when direct connectivity fails.

## Run locally

Requires Go 1.23 or newer.

```sh
cd relay
go mod tidy
go test ./...
go run ./cmd/vanta-relay
```

The HTTP listener defaults to port 8080. Set `PORT` to change it. The listener itself is plain HTTP so it can run behind a trusted TLS reverse proxy. Do not expose it directly to the Internet without HTTPS/WSS.

## Docker

```sh
docker build -t vanta-relay ./relay
docker run --rm -p 8080:8080 vanta-relay
```

Terminate TLS at a reverse proxy such as Caddy or nginx. Set the client relay URL to the public `https://` origin; the client converts API paths to `wss://` for WebSocket connections.

## Endpoints

- `GET /healthz`
- `POST /v1/rooms` — create a room; returns `roomCode`, `hostToken`, `expiresAt`.
- `GET /v1/host/control/{roomCode}` — authenticated host signalling socket.
- `POST /v1/rooms/{roomCode}/requests` — guest asks to join; body: `{"playerName":"MinecraftName"}`.
- `GET /v1/guest/control/{roomCode}/{requestId}` — authenticated guest approval result.
- `GET /v1/host/data/{roomCode}/{requestId}` — host side of an approved stream.
- `GET /v1/guest/data/{roomCode}/{requestId}` — guest side of an approved stream.

All authenticated routes require `Authorization: Bearer <token>`. The host token is room-scoped; each guest token is request-scoped and single-use for its control and data sockets.

## Reverse proxy example

Caddy can terminate TLS and proxy WebSockets automatically:

```caddy
relay.example.net {
    reverse_proxy 127.0.0.1:8080
}
```

Only use an origin whose operator you trust. The room invite is a bearer credential: anyone who obtains the code can request access, but cannot join without host approval.

## Operational limits

The initial relay keeps rooms in memory. A process restart intentionally destroys all invites and active sessions. Run one instance behind a reverse proxy; do not load-balance multiple replicas without adding shared room coordination. Use firewall rules, connection limits and provider bandwidth controls appropriate to your deployment. The default implementation is intended for friends/small groups, not public server hosting.
