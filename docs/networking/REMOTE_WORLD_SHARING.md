# Vanta Remote World Sharing

**Status:** The client now has a WebRTC peer transport integrated with the remote-session manager. Approved guests negotiate one reliable, ordered DataChannel over authenticated SDP/ICE signalling. The WebSocket data relay remains an availability fallback. This is not yet zero-setup public hosting: the signalling relay and its STUN/TURN environment still need an operator, and no official Vanta-operated service is deployed by this repository.

## Current connection model

Vanta uses an explicitly configured, self-hosted WebSocket relay. Remote gameplay bytes travel through that relay; this implementation is **relayed multiplayer, not a direct peer-to-peer connection**. There is no Vanta-operated public relay URL and the client does not silently contact a third party.

### Host flow

1. The host opens a singleplayer world and chooses **Create Remote Invite**.
2. Vanta opens Minecraft's existing integrated-server LAN listener and keeps its actual bound port.
3. The client creates an ephemeral room at the configured relay and opens an authenticated host-control WebSocket.
4. Vanta displays the invite code. A guest request shows the player name claimed by the guest; it is not verified identity.
5. The host explicitly approves or rejects each request.
6. For each approved request, Vanta obtains request-scoped ICE configuration, negotiates a WebRTC peer, and forwards the ordered DataChannel byte stream to the fixed loopback destination `127.0.0.1:<published-port>`. If ICE/WebRTC fails, both clients can use the existing authenticated WebSocket data relay as a fallback.

### Guest flow

1. The guest enters the same configured relay origin, invite code and Minecraft player name.
2. The relay issues a request-scoped guest token and notifies the host.
3. The guest waits for host approval. Rejection, expiry, cancellation and relay errors are handled by the session manager.
4. After approval, Vanta fetches ICE configuration using the guest's request-scoped bearer token and negotiates a WebRTC DataChannel (or the WebSocket fallback). It then opens a TCP listener bound only to `127.0.0.1`.
5. Vanilla Minecraft's `ConnectScreen` connects to that local port. The loopback bridge forwards the byte stream to the host's relay tunnel.
6. Minecraft's normal login and online-mode authentication remain in the connection path. The relay does not authenticate Minecraft accounts.

The loopback listener is not exposed to the LAN or the public Internet. The host tunnel always targets the local integrated-server port; it does not accept a caller-supplied destination.

## Relay service

The Go service lives under `relay/`. It is intentionally small-group/self-hosted infrastructure with in-memory session state.

Default limits:
- Room lifetime: 45 minutes.
- Join request lifetime: 2 minutes.
- Maximum pending/approved guests per room: 8.
- Maximum outstanding requests per room: 16.
- Binary WebSocket frame size: 16 KiB.
- HTTP room creation and join request rate: 20 per source address per minute.

It uses random room codes, room-scoped host bearer tokens, request-scoped guest bearer tokens, authenticated control/data sockets, explicit host approval, bounded message sizes, rate limiting and a reaper for expired rooms/requests. Room state is lost when the relay process restarts.

Deploy the relay behind a trusted HTTPS/WSS reverse proxy. The listener itself is plain HTTP for reverse-proxy deployment. The client accepts HTTPS for remote services and permits plain HTTP only for loopback development addresses. The relay URL is a user preference; invite codes and authentication tokens are memory-only.

See [relay/README.md](../../relay/README.md) for local and Docker deployment instructions.

## Privacy and trust boundaries

- The configured relay operator can observe connection metadata and source network addresses. Do not use a relay you do not trust.
- The relay forwards Minecraft TCP traffic without interpreting packet contents. The pre-login handshake is not equivalent to an end-to-end encrypted session; Minecraft's normal authentication and encryption flow remains owned by Minecraft.
- The LAN publishing step also makes the integrated server discoverable to devices on the host's local network. The Vanta UI discloses this before remote hosting starts.
- The host sees a guest-provided player name for approval. That name is not authenticated by the relay; Minecraft's own login later verifies the connecting account.
- The client does not display or persist the host's public IP, relay bearer tokens, guest tokens, SDP or ICE candidates.
- Closing the world, stopping remote hosting, cancelling a guest join, disconnecting Minecraft or exiting the client tears down the corresponding tunnels and control sockets.

## Direct P2P status

The relay exposes request-scoped host/guest signalling sockets for SDP offers/answers and trickled ICE candidates. The host is the only offerer; the guest is the only answerer. Both endpoints must be connected before either side receives `ready`. The server rejects unapproved requests, duplicate offers/answers, wrong-role messages, malformed JSON and oversized SDP/candidate payloads. Signalling sockets are single-use and are closed when either peer leaves or the host control connection disappears.

The client now uses the signalling endpoints to exchange an SDP offer/answer and trickled ICE candidates. Each approved request gets a reliable, ordered DataChannel; its messages are framed into an ordered byte stream with bounded receive queues and directional EOF. ICE automatically considers configured STUN/TURN candidates. The relay issues short-lived coturn REST credentials from `VANTA_TURN_SHARED_SECRET`; if ICE cannot establish a peer connection, the clients use the authenticated WebSocket data relay instead. The WebSocket fallback is not TURN.

No default Vanta-operated signalling or TURN service is configured in this repository. A normal player still needs a relay origin until official Vanta infrastructure is deployed and its contract is added. Do not treat a successful compile or local test as proof of Internet P2P connectivity. Do not label a relayed session as direct P2P or add a hard-coded third-party signalling or TURN endpoint.

## Acceptance still required

1. Deploy the Go signalling relay at an HTTPS origin and configure `VANTA_STUN_URLS`, `VANTA_TURN_URLS`, and `VANTA_TURN_SHARED_SECRET` against an operational STUN/TURN deployment.
2. Run two separate Minecraft clients with the same relay origin configured.
3. Create an invite, request access, approve and reject guests, and verify the claimed name is clearly presented as unverified.
4. Verify normal online-mode authentication, multiple guests, large chunk/resource transfers, connection loss, relay restart, world closure and cancellation.
5. Verify firewall behaviour and that the guest bridge binds only to loopback on Windows and Linux.
6. Verify the selected ICE candidate pair (direct vs TURN), confirm fallback behaviour, and review client and relay logs for accidental credential disclosure.

The repository's build and UI screenshot jobs validate compilation and screen rendering; they do not prove a live cross-network multiplayer session. No external relay has been deployed from this repository session.
