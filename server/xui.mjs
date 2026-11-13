import { ApiError, validId } from './auth.mjs';

// 3x-ui v3.11.0 uses a global client record identified by email.
// A client update fans out to all attached inbounds; subId is never a lookup key here.
export function renewalPayload(client, target) {
  const payload = { ...client, id: client.uuid ?? '', expiryTime: target,
    created_at: client.createdAt, updated_at: client.updatedAt };
  // 3.11.0 UpdateInboundClient carries omitted peer fields forward from EACH
  // existing WireGuard/AmneziaWG peer. The global record may describe a different
  // tunnel's keys/address; sending it back could overwrite a peer on another inbound.
  for (const field of ['uuid', 'createdAt', 'updatedAt', 'privateKey', 'publicKey',
    'preSharedKey', 'allowedIPs', 'allowedIPsByInbound', 'keepAlive', 'forwardedPorts'])
    delete payload[field];
  if (typeof payload.id !== 'string') throw new ApiError(409, 'panel_client_mismatch');
  return payload;
}
export function createPanel({ url, token, fetcher = fetch }) {
  const root = new URL(url.endsWith('/') ? url : url + '/');
  if (root.username || root.password || root.search || root.hash ||
      (root.protocol !== 'https:' && !(root.protocol === 'http:' &&
      ['127.0.0.1', 'localhost', '[::1]'].includes(root.hostname))))
    throw new Error('XUI_URL must be HTTPS or loopback HTTP, without credentials/query');
  if (!token || /[\r\n]/.test(token)) throw new Error('XUI_API_TOKEN is required');
  const endpoint = new URL('panel/api/clients/', root);
  async function request(path, payload) {
    let response;
    try {
      response = await fetcher(new URL(path, endpoint), {
        method: payload ? 'POST' : 'GET', redirect: 'error',
        signal: AbortSignal.timeout(15000),
        headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
        body: payload ? JSON.stringify(payload) : undefined
      });
    } catch { throw new ApiError(503, 'panel_unavailable'); }
    if (!response.ok) throw new ApiError(503, 'panel_unavailable');
    let data;
    try { data = await response.json(); } catch { throw new ApiError(503, 'panel_invalid_response'); }
    if (data?.success !== true) throw new ApiError(503, 'panel_operation_failed');
    return data.obj;
  }
  async function getClient(email) {
    if (!validId(email)) throw new ApiError(400, 'invalid_id');
    const data = await request('get/' + encodeURIComponent(email));
    const c = data?.client;
    if (!c || c.email !== email || !Number.isSafeInteger(c.id) || c.id <= 0 ||
        !Number.isSafeInteger(c.expiryTime) || !Number.isSafeInteger(c.totalGB) ||
        c.totalGB < 0 || !validId(c.subId) || typeof c.enable !== 'boolean' ||
        !Array.isArray(data.inboundIds) || !data.inboundIds.length ||
        !Number.isSafeInteger(data.usedTraffic) || data.usedTraffic < 0)
      throw new ApiError(409, 'panel_client_mismatch');
    return data;
  }
  async function setExpiry(email, expected, target, recordId) {
    const data = await getClient(email), c = data.client;
    if (c.id !== recordId || ![expected, target].includes(c.expiryTime))
      throw new ApiError(409, 'panel_client_changed');
    if (!c.enable) throw new ApiError(409, 'client_disabled');
    if (c.totalGB > 0 && data.usedTraffic >= c.totalGB) throw new ApiError(409, 'traffic_depleted');
    // Read current values on every attempt. Do not rotate credentials, reset counters,
    // overwrite subId, or recompute/add another period after an ambiguous timeout.
    const payload = renewalPayload(c, target);
    const result = await request('update/' + encodeURIComponent(email), payload);
    const checked = await getClient(email);
    if (checked.client.id !== recordId || checked.client.expiryTime !== target)
      throw new ApiError(503, 'panel_update_not_verified');
    return { pendingSync: result?.pending === true || result?.nodePending === true };
  }
  return { getClient, setExpiry };
}
