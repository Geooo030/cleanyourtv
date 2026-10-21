import http from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { resolve, extname, sep } from 'node:path';
import { WebSocketServer, WebSocket } from 'ws';
import { MirrorSession } from './mirror.mjs';

const port = Number(process.env.PORT || 5173);
const production = process.argv.includes('--production');
const session = new MirrorSession();
const clients = new Set();
let idleTimer;
const allowedOrigins = new Set([`http://localhost:${port}`, `http://127.0.0.1:${port}`]);
const allowed = req => !req.headers.origin || allowedOrigins.has(req.headers.origin);
const sendJson = (res, code, data) => { res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(data)); };
let vite;
const server = http.createServer(async (req, res) => {
  try {
    if (!allowed(req) || req.headers['sec-fetch-site'] === 'cross-site') return sendJson(res, 403, { error: 'Only local application requests are allowed' });
    const url = new URL(req.url, `http://127.0.0.1:${port}`);
    if (url.pathname === '/api/status' && req.method === 'GET') return sendJson(res, 200, session.snapshot());
    if (url.pathname === '/api/apps' && req.method === 'GET') return sendJson(res, 200, await session.apps());
    if (url.pathname === '/api/app' && req.method === 'POST') {
      let body = '';
      for await (const chunk of req) { body += chunk; if (body.length > 1024) return sendJson(res, 413, { error: 'Request too large' }); }
      return sendJson(res, 200, await session.launch(JSON.parse(body).id));
    }
    if (url.pathname === '/api/key' && req.method === 'POST') {
      let body = '';
      for await (const chunk of req) { body += chunk; if (body.length > 1024) return sendJson(res, 413, { error: 'Request too large' }); }
      const { key } = JSON.parse(body);
      await session.key(key);
      return sendJson(res, 200, { ok: true, key });
    }
    if (url.pathname === '/api/config' && req.method === 'POST') {
      let body = '';
      for await (const chunk of req) { body += chunk; if (body.length > 4096) return sendJson(res, 413, { error: 'Request too large' }); }
      await session.configure(JSON.parse(body));
      if (clients.size) void session.start();
      return sendJson(res, 200, session.snapshot());
    }
    if (url.pathname.startsWith('/api/')) return sendJson(res, 404, { error: 'Not found' });
    if (url.pathname === '/media/hawaii.mp4') {
      const file = resolve(import.meta.dirname, '../public/media/hawaii.mp4');
      const { size } = await stat(file);
      const range = req.headers.range?.match(/^bytes=(\d+)-(\d*)$/);
      const start = range ? Number(range[1]) : 0, end = range?.[2] ? Math.min(Number(range[2]), size - 1) : size - 1;
      if (start >= size || end < start) { res.writeHead(416, { 'Content-Range': `bytes */${size}` }); return res.end(); }
      res.writeHead(range ? 206 : 200, { 'Content-Type': 'video/mp4', 'Accept-Ranges': 'bytes', 'Content-Length': end - start + 1, ...(range ? { 'Content-Range': `bytes ${start}-${end}/${size}` } : {}) });
      if (req.method === 'HEAD') return res.end();
      const stream = createReadStream(file, { start, end });
      res.on('close', () => stream.destroy()); stream.on('error', () => res.destroy()); return stream.pipe(res);
    }
    if (vite) return vite.middlewares(req, res, () => { res.writeHead(404); res.end(); });
    const base = resolve(import.meta.dirname, '../dist');
    let file = resolve(base, `.${decodeURIComponent(url.pathname)}`);
    if (file !== base && !file.startsWith(base + sep)) return sendJson(res, 403, { error: 'Invalid path' });
    if (!(await stat(file).catch(() => null))?.isFile()) file = resolve(base, 'index.html');
    const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' };
    res.writeHead(200, { 'Content-Type': types[extname(file)] || 'application/octet-stream' }); res.end(await readFile(file));
  } catch (error) { sendJson(res, 400, { error: error.message }); }
});
const wss = new WebSocketServer({ noServer: true, perMessageDeflate: false, maxPayload: 1024 });
server.on('upgrade', (req, socket, head) => {
  if (req.url?.split('?')[0] !== '/stream') return; // Vite owns its HMR websocket.
  if (!allowed(req)) return socket.destroy();
  wss.handleUpgrade(req, socket, head, ws => wss.emit('connection', ws));
});
wss.on('connection', ws => {
  clearTimeout(idleTimer); clients.add(ws);
  ws.send(JSON.stringify({ type: 'status', ...session.snapshot() }));
  if (session.codecPacket) ws.send(session.codecPacket);
  void session.start();
  ws.on('error', () => {});
  ws.on('close', () => { clients.delete(ws); if (!clients.size) idleTimer = setTimeout(() => void session.stop(), 1500); });
});
function broadcast(value) { for (const ws of clients) if (ws.readyState === WebSocket.OPEN) ws.send(value); }
session.on('status', status => broadcast(JSON.stringify({ type: 'status', ...status })));
session.on('packet', packet => {
  for (const ws of clients) if (ws.readyState === WebSocket.OPEN) {
    // A slow viewer reconnects at a clean keyframe instead of accumulating stale video.
    if (ws.bufferedAmount > 2 * 1024 * 1024) ws.close(1013, 'Viewer is too slow');
    else ws.send(packet);
  }
});
const statsTimer = setInterval(() => { if (clients.size) broadcast(JSON.stringify({ type: 'status', ...session.snapshot() })); }, 1000);
if (!production) {
  const { createServer } = await import('vite');
  vite = await createServer({ root: resolve(import.meta.dirname, '..'), server: { middlewareMode: true, hmr: { server } }, appType: 'spa' });
}
server.listen(port, '127.0.0.1', () => console.log(`TV Mirror ready: http://localhost:${port}`));
async function shutdown() { clearInterval(statsTimer); clearTimeout(idleTimer); for (const ws of clients) ws.close(); await session.stop(); await vite?.close(); server.close(() => process.exit(0)); }
process.on('SIGINT', shutdown); process.on('SIGTERM', shutdown);
