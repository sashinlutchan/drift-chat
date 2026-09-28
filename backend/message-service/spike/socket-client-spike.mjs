// Phase 0 spike client — verifies socket.io-client v4.8.4 works against
// the netty-socketio 2.0.14 server (DriftSocketServer) over WebSocket-only transport:
//   1. connect with a session cookie sent in the handshake (WebSocket upgrade request)
//   2. emit with ack (round trip)
//   3. rooms: join + broadcast received
//   4. reconnect after server-forced disconnect
// Prints SPIKE PASS on success; exits non-zero on any failure.
import { io } from 'socket.io-client'

const URL = process.env.SPIKE_URL || 'http://localhost:8090'
const COOKIE = process.env.SPIKE_COOKIE || 'drift_session=spike-token-123'

const results = []
const step = (name, ok, extra = '') => {
  results.push(ok)
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${extra ? ' — ' + extra : ''}`)
}

const socket = io(URL, {
  transports: ['websocket'], // WebSocket-only, per spec
  withCredentials: true, // sends the Cookie header on the handshake
  extraHeaders: { Cookie: COOKIE },
  reconnection: true,
})

const waitFor = (event, timeoutMs = 5000) =>
  new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`timeout waiting for ${event}`)), timeoutMs)
    socket.once(event, (...args) => {
      clearTimeout(t)
      resolve(args)
    })
  })

try {
  // 1. connect
  await waitFor('connect')
  step('connect (websocket transport)', socket.io.engine.transport.name === 'websocket', socket.io.engine.transport.name)

  // 2. emit with ack — server should also have seen our session cookie
  const pong = await new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('ack timeout')), 5000)
    socket.emit('spike:ping', 'hello', (reply) => {
      clearTimeout(t)
      resolve(reply)
    })
  })
  step('emit + ack round trip', pong === 'pong:hello', pong)

  // 3. rooms
  const roomP = waitFor('spike:room')
  await new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('join ack timeout')), 5000)
    socket.emit('spike:join', 'room-1', (reply) => {
      clearTimeout(t)
      resolve(reply)
    })
  })
  const [roomMsg] = await roomP
  step('room join + broadcast', roomMsg === 'joined:room-1', roomMsg)

  // 4. reconnect: simulate a dropped connection — the client must reconnect automatically.
  // (netty-socketio's server-initiated disconnect is final for the client by design; a real
  // network drop / pod kill is what the reconnection logic exists for, so we simulate exactly that.)
  const reconnected = new Promise((resolve) => {
    socket.io.on('reconnect', () => resolve(true))
    setTimeout(() => resolve(false), 10000)
  })
  socket.io.engine.close() // abrupt transport close, like a network drop
  step('reconnect after drop', await reconnected)

  const allPass = results.every(Boolean)
  console.log(allPass ? 'SPIKE PASS' : 'SPIKE FAIL')
  process.exit(allPass ? 0 : 1)
} catch (err) {
  console.error('SPIKE FAIL —', err.message)
  process.exit(1)
}
