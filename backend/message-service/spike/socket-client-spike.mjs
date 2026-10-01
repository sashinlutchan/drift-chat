import { io } from 'socket.io-client';
const URL = process.env.SPIKE_URL || 'http://localhost:8090';
const COOKIE = process.env.SPIKE_COOKIE || 'drift_session=spike-token-123';
const results = [];
const step = (name, ok, extra = '') => {
    results.push(ok);
    console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${extra ? ' — ' + extra : ''}`);
};
const socket = io(URL, {
    transports: ['websocket'],
    withCredentials: true,
    extraHeaders: { Cookie: COOKIE },
    reconnection: true,
});
const waitFor = (event, timeoutMs = 5000) => new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`timeout waiting for ${event}`)), timeoutMs);
    socket.once(event, (...args) => {
        clearTimeout(t);
        resolve(args);
    });
});
try {
    await waitFor('connect');
    step('connect (websocket transport)', socket.io.engine.transport.name === 'websocket', socket.io.engine.transport.name);
    const pong = await new Promise((resolve, reject) => {
        const t = setTimeout(() => reject(new Error('ack timeout')), 5000);
        socket.emit('spike:ping', 'hello', (reply) => {
            clearTimeout(t);
            resolve(reply);
        });
    });
    step('emit + ack round trip', pong === 'pong:hello', pong);
    const roomP = waitFor('spike:room');
    await new Promise((resolve, reject) => {
        const t = setTimeout(() => reject(new Error('join ack timeout')), 5000);
        socket.emit('spike:join', 'room-1', (reply) => {
            clearTimeout(t);
            resolve(reply);
        });
    });
    const [roomMsg] = await roomP;
    step('room join + broadcast', roomMsg === 'joined:room-1', roomMsg);
    const reconnected = new Promise((resolve) => {
        socket.io.on('reconnect', () => resolve(true));
        setTimeout(() => resolve(false), 10000);
    });
    socket.io.engine.close();
    step('reconnect after drop', await reconnected);
    const allPass = results.every(Boolean);
    console.log(allPass ? 'SPIKE PASS' : 'SPIKE FAIL');
    process.exit(allPass ? 0 : 1);
}
catch (err) {
    console.error('SPIKE FAIL —', err.message);
    process.exit(1);
}
