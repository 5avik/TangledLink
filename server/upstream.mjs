import { ApiError, validId } from './auth.mjs';

export async function subscription(base, id) {
  if (!validId(id)) throw new ApiError(401, 'invalid_credentials');
  const url = new URL(encodeURIComponent(id), base.endsWith('/') ? base : base + '/');
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)))
    throw new Error('SUBSCRIPTION_BASE must use HTTPS or loopback HTTP');
  let response;
  try {
    response = await fetch(url, { redirect: 'error', signal: AbortSignal.timeout(10000),
      headers: { Accept: 'text/plain', 'User-Agent': 'TangledLink/1.0' } });
  } catch { throw new ApiError(503, 'subscription_unavailable'); }
  if (response.status === 404) throw new ApiError(403, 'subscription_inactive');
  if (!response.ok) throw new ApiError(503, 'subscription_unavailable');
  const info = response.headers.get('subscription-userinfo');
  const usage = Object.fromEntries((info ?? '').split(';').map(part => part.trim().split('=')));
  for (const key of ['upload', 'download', 'total', 'expire']) {
    if (!/^\d+$/.test(usage[key] ?? '')) throw new ApiError(503, 'subscription_status_missing');
  }
  const { upload, download, total, expire } = Object.fromEntries(
    Object.entries(usage).map(([k, v]) => [k, Number(v)]));
  if ((expire > 0 && expire <= Date.now() / 1000) || (total > 0 && upload + download >= total))
    throw new ApiError(403, 'subscription_inactive');
  const reader = response.body.getReader();
  const chunks = []; let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > 2 * 1024 * 1024) throw new ApiError(503, 'subscription_invalid');
      chunks.push(Buffer.from(value));
    }
  } finally { await reader.cancel(); }
  const body = Buffer.concat(chunks).toString('utf8').trim();
  const decoded = body.includes('://') ? body : Buffer.from(body, 'base64').toString('utf8');
  // Some subscription providers append vpn:// entries unsupported by this app.
  // Drop those entries, but still require real supported configurations and reject
  // every other unexpected format. Never forward unsupported entries to the app.
  const lines = decoded.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
  const links = lines.filter(line => !line.startsWith('vpn://'));
  if (!links.length || !links.every(line => /^(vless|vmess|trojan|ss|hysteria2|hy2|tuic|socks|wireguard):\/\//.test(line)))
    throw new ApiError(403, 'subscription_inactive');
  const filtered = links.join('\n');
  const normalizedBody = lines.length === links.length ? body
    : body.includes('://') ? filtered : Buffer.from(filtered).toString('base64');
  return { body: normalizedBody, info };
}
