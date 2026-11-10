import { createServer } from 'node:http';
import { createAuth, ApiError } from './auth.mjs';
import { subscription } from './upstream.mjs';

process.umask(0o077);
const base = process.env.SUBSCRIPTION_BASE;
if (!base) throw new Error('SUBSCRIPTION_BASE is required');
const auth = createAuth(process.env.AUTH_DB ?? '/var/lib/tngl-auth/auth.sqlite');
const buckets = new Map();
let working = 0;
function limit(key, maximum) {
  const time = Date.now();
  if (buckets.size > 10000) {
    for (const [k, v] of buckets) if (v.until <= time) buckets.delete(k);
    if (buckets.size > 10000) throw new ApiError(429, 'rate_limited');
  }
  let item = buckets.get(key);
  if (!item || item.until <= time) { item = { count: 0, until: time + 15 * 60000 }; buckets.set(key, item); }
  if (++item.count > maximum) throw new ApiError(429, 'rate_limited');
}
async function json(req) {
  if (!(req.headers['content-type'] ?? '').startsWith('application/json'))
    throw new ApiError(415, 'json_required');
  let size = 0, chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 8192) throw new ApiError(413, 'request_too_large');
    chunks.push(chunk);
  }
  try {
    const value = JSON.parse(Buffer.concat(chunks).toString());
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error();
    return value;
  } catch { throw new ApiError(400, 'invalid_json'); }
}
const server = createServer(async (req, res) => {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  const reply = (status, body) => {
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(body));
  };
  try {
    const path = req.url;
    const ip = req.headers['x-real-ip'] ?? req.socket.remoteAddress;
    limit('all:' + ip, 300);
    if (req.method === 'GET' && path === '/health') return reply(200, { status: 'ok' });
    const token = (req.headers.authorization ?? '').replace(/^Bearer /, '');
    if (req.method === 'GET' && path === '/v1/subscription') {
      const session = auth.session(token);
      const sub = await subscription(base, session.account);
      res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8', 'subscription-userinfo': sub.info });
      return res.end(sub.body);
    }
    if (req.method !== 'POST' || !['/v1/login', '/v1/activate', '/v1/password', '/v1/logout'].includes(path))
      throw new ApiError(404, 'not_found');
    const data = await json(req);
    if (path === '/v1/logout') { auth.logout(token); return reply(200, { ok: true }); }
    limit('auth:' + ip, 30);
    if (typeof data.id === 'string') limit('account:' + data.id.slice(0, 128), 10);
    if (working >= 4) throw new ApiError(429, 'rate_limited');
    working++;
    try {
      if (path === '/v1/password') {
        limit('password:' + auth.session(token).account, 10);
        await auth.changePassword(token, data.currentPassword, data.newPassword);
        return reply(200, { ok: true });
      }
      // Verify subscription before committing activation or issuing a session.
      const verify = () => subscription(base, data.id);
      const session = path === '/v1/login'
        ? await auth.login(data.id, data.password, verify)
        : await auth.activate(data.id, data.code, data.password, verify);
      reply(200, session);
    } finally { working--; }
  } catch (error) {
    if (!res.headersSent) {
      if (error.status === 429) res.setHeader('Retry-After', '900');
      reply(error instanceof ApiError ? error.status : 500,
        { error: error instanceof ApiError ? error.code : 'internal_error' });
    } else res.destroy();
  }
});
server.requestTimeout = 15000;
server.headersTimeout = 10000;
server.maxConnections = 100;
server.listen(Number(process.env.PORT ?? 8080), process.env.HOST ?? '127.0.0.1', () => console.log('tngl-auth listening'));
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => {
  server.close(() => { auth.close(); process.exit(0); });
});
