const { WebSocketServer } = require('ws');

const PORT = Number(process.env.PORT || 8080);
const clients = new Map();

const server = new WebSocketServer({ port: PORT });

function sendError(ws, message) {
  if (ws.readyState === ws.OPEN) {
    ws.send(JSON.stringify({ type: 'ERROR', error: message }));
  }
}

function cleanup(ws) {
  if (ws.deviceId && clients.get(ws.deviceId) === ws) {
    clients.delete(ws.deviceId);
  }
}

server.on('connection', (ws) => {
  ws.isAlive = true;

  ws.on('pong', () => { ws.isAlive = true; });

  ws.on('message', (raw) => {
    let message;
    try {
      message = JSON.parse(raw.toString());
    } catch {
      sendError(ws, 'Invalid JSON');
      return;
    }

    // HELLO registers the socket for routing. The app still authenticates
    // actual signaling messages with the device public key.
    if (message.type === 'HELLO') {
      const deviceId = message.senderDeviceId;
      if (typeof deviceId !== 'string' || !deviceId || typeof message.signature !== 'string') {
        sendError(ws, 'Invalid HELLO');
        return;
      }
      const previous = clients.get(deviceId);
      if (previous && previous !== ws) previous.close(4001, 'Replaced by newer connection');
      ws.deviceId = deviceId;
      clients.set(deviceId, ws);
      return;
    }

    if (!ws.deviceId) {
      sendError(ws, 'HELLO required');
      return;
    }

    if (typeof message.recipientDeviceId !== 'string' || !message.recipientDeviceId) {
      sendError(ws, 'recipientDeviceId required');
      return;
    }

    // This server only routes the already-signed envelope. It does not carry
    // audio/video and does not make authorization decisions for the clients.
    const recipient = clients.get(message.recipientDeviceId);
    if (!recipient || recipient.readyState !== recipient.OPEN) {
      sendError(ws, 'Recipient is offline');
      return;
    }

    recipient.send(JSON.stringify(message));
  });

  ws.on('close', () => cleanup(ws));
  ws.on('error', () => cleanup(ws));
});

const heartbeat = setInterval(() => {
  server.clients.forEach((ws) => {
    if (!ws.isAlive) {
      cleanup(ws);
      return ws.terminate();
    }
    ws.isAlive = false;
    ws.ping();
  });
}, 30000);

server.on('close', () => clearInterval(heartbeat));

console.log(`RemoteLink signaling relay listening on port ${PORT}`);
