# Vanta Remote World Sharing

**Status:** design only. The implemented Vanta Share screen currently publishes a singleplayer integrated server to the local network. It does not create Internet invitations or route remote gameplay traffic.

## Target experience

A host opens a singleplayer world, creates a short-lived invite, and approves incoming players. Guests join from a Vanta invite flow without needing to discover or manually enter the host's IP address. The Minecraft login/authentication path remains enabled.

The target transport is peer-to-peer when the two networks can connect directly, with a relay fallback for restrictive NATs and networks. Signalling and relay services must be deployable by the project owner; the client must not silently use an unrelated third-party endpoint.

## Proposed data path

1. The host uses the existing integrated-server LAN publishing API and retains the actual bound port.
2. A host-side loopback connector accepts an established peer data channel and forwards its byte stream to the integrated server at `127.0.0.1:<published-port>`.
3. A guest-side loopback listener presents a local TCP endpoint to Minecraft. The guest joins that local endpoint through the normal Minecraft connection flow; Vanta forwards the resulting byte stream through the peer transport.
4. Each remote player receives an independent tunnel. The relay/signalling plane handles session coordination only unless direct connectivity fails and relay fallback is selected.

A loopback TCP bridge is the preferred first prototype because it avoids replacing Minecraft's internal Netty channel implementation. It must bind only to loopback, use a fixed host-side destination, close both sides on failure, and enforce bounded buffers and write backpressure.

## Transport and signalling

- Preferred direct transport candidate: a reliable, ordered WebRTC DataChannel using DTLS/SCTP.
- Signalling: HTTPS/WSS exchange of SDP and ICE candidates. Signalling messages are not gameplay payloads.
- Connectivity: use ICE to establish a direct path where possible; use TURN or an explicitly configured project-owned relay when direct connectivity fails.
- The relay is a transport fallback, not proof of P2P. The UI must distinguish direct and relayed sessions accurately.
- Candidate Java binding for a prototype: [libdatachannel-java](https://github.com/pschichtel/libdatachannel-java). It wraps native libdatachannel and requires architecture-specific native components; no dependency has been selected or added. Validate Windows, Linux and macOS packaging, native loading, license obligations, thread ownership and shutdown before adoption.

## Session security and privacy

- Generate cryptographically random, short-lived, single-use invite secrets. Do not use sequential room IDs as authorization.
- Require the host to approve each incoming player before opening the gameplay tunnel.
- Keep Minecraft's normal online-mode authentication enabled. A transport invite is not a substitute for player authentication.
- Use TLS for signalling and DTLS for WebRTC data. Reject plaintext remote endpoints.
- Do not display, copy, log or persist the host's public IP in the client UI.
- Do not persist SDP, ICE candidates, invite secrets or player presence beyond the minimum session lifetime.
- Apply per-session connection limits, message-size limits, bounded queues, rate limits and abuse controls to any relay service.
- Never expose the loopback bridge to the LAN or public interfaces.

## Implementation gates

1. **Local tunnel prototype:** implement and test the loopback TCP bridge using an in-memory duplex transport. Cover half-close, EOF, concurrent clients, disconnect cleanup, bounded buffering, maximum packet sizes and cancellation.
2. **WebRTC prototype:** connect two local peers by exchanging SDP in-process. Confirm ordered binary delivery, channel close, backpressure and native resource cleanup.
3. **Self-hostable signalling/relay:** create a separate deployable service with ephemeral room state, authenticated host/guest roles, invite expiry, host approval and TURN/relay configuration. No public default endpoint until it is intentionally operated.
4. **Client integration:** add host invite and guest join flows, lifecycle cancellation, reconnect handling and clear direct-vs-relayed connection status.
5. **Gameplay acceptance:** verify one and multiple guests, online-mode authentication, modded payload sizes, chunk/bulk transfer, latency, packet loss, NAT restrictions, relay fallback, host world closure and crash recovery.

## Current implementation boundary

The current LAN milestone is useful and retained: it opens the integrated server using Minecraft's own API, displays the bound LAN port, keeps guest commands off by default and clearly labels Internet invitations as unimplemented. Do not replace it with a fake invite code or a hard-coded third-party relay URL.
