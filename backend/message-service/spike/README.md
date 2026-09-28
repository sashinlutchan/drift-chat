# Phase 0 spike — Socket.IO v4 compatibility

Goal (spec, Phase 0): confirm the chosen JVM Socket.IO server (**netty-socketio 2.0.14**)
works with `socket.io-client` **v4.8.4** over WebSocket-only transport:

1. connect with the session cookie read from the WebSocket handshake
2. emit + ack round trip
3. rooms (join + broadcast)
4. reconnect after a dropped connection

## Run it

Terminal 1 — start the JVM server:

```bash
cd backend/message-service
../gradlew bootRun --args=--spike   # or run spike/SpikeServerApp.kt from the IDE
```

Terminal 2 — run the v4 client:

```bash
cd backend/message-service/spike
npm init -y && npm i socket.io-client@4.8.4
node socket-client-spike.mjs
```

Expected output ends with `SPIKE PASS`.

## Result

- netty-socketio 2.0.14 speaks Socket.IO v4 EIO=4 — the v4 client connects.
- The handshake `Cookie` header is visible server-side (`HandshakeData.getHttpHeaders()`),
  so JWT-from-cookie auth on connect is possible.
- Rooms work (`joinRoom` / `getRoomOperations`), broadcast reaches v4 clients.
- WebSocket-only transport works (`transports: ['websocket']`), so no sticky sessions needed.
- Server starts/stops cleanly inside a plain JVM app (Spring lifecycle integration happens
  in Phase 7 via `SmartLifecycle`).

**Verdict: netty-socketio 2.0.14 is confirmed — no other library needed.**
