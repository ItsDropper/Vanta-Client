# Vanta relay deployment

This stack runs the Vanta room/signalling relay, Caddy HTTPS reverse proxy, and coturn STUN/TURN service on one Linux host. It is intended for the service operator, not for world hosts or guests. Players only need the resulting HTTPS relay address in their Vanta client.

## Requirements

- A Linux server with Docker Engine and the Docker Compose plugin.
- A DNS name whose A/AAAA records point to that server.
- Firewall access to TCP 80 and 443 for HTTPS certificate issuance and client traffic.
- Firewall access to TCP/UDP 3478 and UDP 49160–49200 for STUN/TURN.
- A stable public IP and the corresponding private interface address for coturn.

This is a single-instance deployment. The room store is in memory, so do not put multiple relay replicas behind a load balancer.

## Configure

From this directory:

```sh
cp .env.example .env
```

Edit `.env`:

- Set `VANTA_DOMAIN` to the DNS name clients will use.
- Set `TURN_PRIVATE_IP` to the server interface address coturn must bind to.
- Set `TURN_PUBLIC_IP` to the address guests can reach. When the server is behind NAT, coturn uses the public/private mapping; when the public address is directly assigned to the interface, the values may be identical.
- Replace `VANTA_TURN_SHARED_SECRET` with a strong random value. Generate one with `openssl rand -hex 32`. Keep it on the relay host only.

The DNS name must resolve publicly and ports 80/443 must reach Caddy before it can provision a trusted HTTPS certificate. Caddy terminates HTTPS/WSS and forwards requests to the private relay container.

## Start

```sh
docker compose up -d --build
docker compose ps
docker compose logs -f vanta-relay coturn caddy
```

Check the public health endpoint:

```sh
curl --fail https://${VANTA_DOMAIN}/healthz
```

A healthy response is HTTP 204. Configure both Vanta clients to use `https://<your-domain>`. The client converts the API's WebSocket routes to WSS. No Minecraft port needs to be exposed on the host's public interface.

## Network and privacy notes

- The relay's invite/control/signalling API is served through Caddy on HTTPS/WSS.
- STUN/TURN is served by coturn directly; the configured TURN secret is shared only between the Go relay and coturn.
- WebRTC gameplay can use a direct ICE candidate pair or a TURN-relayed pair. The authenticated WebSocket gameplay relay remains the fallback when WebRTC negotiation fails.
- TURN-only mode requires working coturn reachability and the matching shared secret.
- Open UDP 49160–49200 end-to-end to the coturn host. Docker host networking is used because TURN allocates a range of UDP relay ports.
- The relay stores rooms and guest approvals in memory. Restarting the process invalidates all active invites and sessions.
- The relay sees source addresses and transport metadata. The TURN operator can observe relay traffic metadata. Review the security and trust-boundary notes in [../README.md](../README.md).

This deployment bundle does not deploy itself to a cloud provider and does not create a Vanta-operated public service. The operator must provision the host, DNS, and firewall. Until an official service is deployed and configured as the client's default, users still need a trusted relay address.
