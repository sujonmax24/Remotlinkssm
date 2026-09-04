# RemoteLink Signaling Relay

Minimal WebSocket relay used only for SDP/ICE/control signaling.

## Important

- Audio/video does **not** pass through this server.
- Clients sign signaling envelopes with their Android Keystore device key.
- Clients verify the sender signature, recipient, and message freshness before using a message.
- The relay keeps connections in memory only; restarting it disconnects clients.
- The Android app intentionally expects a `wss://` endpoint in production.

## Run

```bash
npm install
PORT=8080 npm start
```

For production, put the server behind a TLS reverse proxy and expose a `wss://` URL. Add authentication/rate limiting and monitoring before public deployment.

## Routing

Each client first sends a signed `HELLO` envelope. The relay maps `senderDeviceId` to that WebSocket. Subsequent envelopes are forwarded to `recipientDeviceId`.

The relay is deliberately not the trust authority: a recipient must still verify the signed envelope against the trusted device's public key stored during pairing.
