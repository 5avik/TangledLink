import {createEmailAuth,emailRequired} from './email-auth.mjs';
import { openWebEmail } from './web-email.mjs';
import { createServer } from 'node:http';
import { createAuth, ApiError } from './auth.mjs';
import { subscription } from './upstream.mjs';
import { panel, billing, lava, membership } from './billing-runtime.mjs';
import { LAVA_PLANS } from './lava.mjs';
import { createWebBilling } from './web-billing.mjs';

process.umask(0o077);
const base = process.env.SUBSCRIPTION_BASE;
if (!base) throw new Error('SUBSCRIPTION_BASE is required');
const auth = createAuth(process.env.AUTH_DB ?? '/var/lib/tngl-auth/auth.sqlite');
const webBilling = lava && process.env.WEB_BILLING_KEY ? createWebBilling({
  path: process.env.BILLING_DB ?? '/var/lib/tngl-auth/billing.sqlite',
  key: process.env.WEB_BILLING_KEY, billing, lava
}) : null;
const webEmail = process.env.CREDENTIALS_DIRECTORY && process.env.WEB_BILLING_KEY ? openWebEmail({
  database: '/var/lib/tngl-auth/web-email.sqlite',
  credentials: process.env.CREDENTIALS_DIRECTORY + '/gmail.json',
  key: process.env.WEB_BILLING_KEY
}) : null;
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
export function createApiServer({ authStore = auth, billingStore = billing, panelClient = panel, subscriptionReader = subscription, lavaStore = lava, webBillingStore = webBilling, membershipStore = membership, emailStore = webEmail } = {}) {
const auth = authStore;
const emailFlow=emailStore?createEmailAuth({auth,email:emailStore,membership:membershipStore}):null;
return createServer(async (req, res) => {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  const reply = (status, body) => {
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(body));
  };
  try {
    const path = req.url;
    const ip = req.headers['x-real-ip'] ?? req.socket.remoteAddress;
    if (path.startsWith('/v1/web-billing/')) {
      if (!webBillingStore) throw new ApiError(503, 'billing_unavailable');
      webBillingStore.authenticate(req.headers['x-web-billing-key']);
      const client = String(req.headers['x-web-client-ip'] ?? ip).slice(0, 64);
      limit('web:' + client, 180);
      if (req.method === 'GET' && path === '/v1/web-billing/plans')
        return reply(200, { plans: webBillingStore.plans() });
      if (req.method !== 'POST') throw new ApiError(404, 'not_found');
      const data = await json(req);
      if (path === '/v1/web-billing/orders') {
        limit('web-orders:' + client, 10);
        return reply(200, await webBillingStore.checkout(data));
      }
      if (path === '/v1/web-billing/status') return reply(200, webBillingStore.status(data.token));
      if (path === '/v1/web-billing/resume') {
        limit('web-resume:' + client, 10);
        return reply(200, await webBillingStore.resume(data.token));
      }
      throw new ApiError(404, 'not_found');
    }
    limit('all:' + ip, 300);
    if (req.method === 'GET' && path === '/health') return reply(200, { status: 'ok' });
    if (req.method === 'POST' && path === '/v1/billing/webhooks/lava') {
      if (!lavaStore) throw new ApiError(503, 'billing_unavailable');
      lavaStore.authenticate(req.headers['x-api-key']);
      lavaStore.receive(await json(req));
      return reply(200, {ok:true});
    }
    const token = (req.headers.authorization ?? '').replace(/^Bearer /, '');
    if (path.startsWith('/v1/web-auth/') || path.startsWith('/v1/mobile-auth/')) {
      const web=path.startsWith('/v1/web-auth/');
      if (!emailFlow) throw new ApiError(503,'email_unavailable');
      if (web) {
        if (!webBillingStore) throw new ApiError(503,'email_unavailable');
        webBillingStore.authenticate(req.headers['x-web-billing-key']);
      }
      const client=web?String(req.headers['x-web-client-ip']??ip).slice(0,64):ip;
      const operation=path.split('/').at(-1);
      if (req.method==='GET' && operation==='email') return reply(200,emailFlow.address(token));
      if (req.method!=='POST') throw new ApiError(404,'not_found');
      limit('email-auth:'+client,20);
      if (!web && operation==='register') limit('register:'+client,5);
      const data=await json(req);
      if (operation==='bind') return reply(200,await emailFlow.bind(token,data));
      if (typeof data.id==='string') limit('email-auth-account:'+data.id.slice(0,128),15);
      if (working>=4) throw new ApiError(429,'rate_limited');
      working++;
      try {
        if(operation==='register')return reply(200,await emailFlow.register(data,{web}));
        if(operation==='login')return reply(200,await emailFlow.login(data));
        throw new ApiError(404,'not_found');
      } finally {working--;}
    }
    if (req.method === 'GET' && path === '/v1/account') {
      const session=auth.session(token);
      const account = membershipStore ? await membershipStore.status(session.account) : { managed:false, trialAvailable:false };
      if (!account.managed && panelClient && webBillingStore && req.headers['x-web-billing-key']) {
        webBillingStore.authenticate(req.headers['x-web-billing-key']);
        try {
          const { client, inboundIds, usedTraffic } = await panelClient.getClient(session.account, { allowDetached:true });
          const accessEnabled = client.enable && inboundIds.length > 0 &&
            (client.totalGB === 0 || usedTraffic < client.totalGB);
          const expiresAt = client.expiryTime > 0 ? client.expiryTime : null;
          const subscribed = !accessEnabled ? false
            : client.expiryTime === 0 ? true
            : expiresAt === null ? undefined
            : expiresAt > Date.now();
          return reply(200, { ...account, subscribed, expiresAt });
        } catch (error) {
          if (!(error instanceof ApiError)) throw error;
          // Keep the cabinet usable when 3x-ui cannot confirm the legacy subscription.
        }
      }
      return reply(200, account);
    }
    if (req.method === 'POST' && path === '/v1/trial') {
      const session=auth.session(token);
      limit('trial:'+session.account,10);
      if (!membershipStore) throw new ApiError(503,'registration_unavailable');
      return reply(200,await membershipStore.startTrial(session.account));
    }
    if (req.method === 'GET' && path === '/v1/billing') {
      const session = auth.session(token);
      if (!billingStore) throw new ApiError(503, 'billing_unavailable');
      return reply(200, { plan: billingStore.plan, instructions: billingStore.instructions,
        plans: lavaStore ? LAVA_PLANS.map(({offerId, ...p}) => p) : [billingStore.plan],
        order: lavaStore ? lavaStore.payment(billingStore.latest(session.account)) : billingStore.latest(session.account) });
    }
    if (req.method === 'POST' && path === '/v1/billing/orders') {
      const session = auth.session(token);
      if (!billingStore) throw new ApiError(503, 'billing_unavailable');
      limit('orders:' + session.account, 10);
      const data = await json(req);
      if (lavaStore) return reply(200, {order: data.orderId !== undefined
        ? await lavaStore.resume(session.account, data.orderId)
        : await lavaStore.checkout(session.account, data.planId, data.email, {promoCode:data.promoCode ?? ''})});
      if (data.planId !== billingStore.plan.id) throw new ApiError(400, 'invalid_plan');
      return reply(200, { order: await billingStore.create(session.account) });
    }
    if (req.method === 'GET' && path === '/v1/subscription') {
      const session = auth.session(token);
      const access = membershipStore ? await membershipStore.status(session.account) : null;
      if (access?.managed && !access.subscribed) throw new ApiError(403,'subscription_inactive');
      const subId = panelClient ? (await panelClient.getClient(session.account)).client.subId : session.account;
      const sub = await subscriptionReader(base, subId);
      res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8', 'subscription-userinfo': sub.info });
      return res.end(sub.body);
    }
    if (req.method !== 'POST' || !['/v1/register', '/v1/login', '/v1/activate', '/v1/password', '/v1/logout'].includes(path))
      throw new ApiError(404, 'not_found');
    const data = await json(req);
    if (path === '/v1/logout') { auth.logout(token); return reply(200, { ok: true }); }
    limit('auth:' + ip, 30);
    if (typeof data.id === 'string') limit('account:' + data.id.slice(0, 128), 10);
    if (working >= 4) throw new ApiError(429, 'rate_limited');
    working++;
    try {
      if (path === '/v1/register') {
        limit('register:'+ip,5);
        if (!membershipStore) throw new ApiError(503,'registration_unavailable');
        if(!emailFlow)throw new ApiError(503,'email_unavailable');
        return reply(200,await emailFlow.register(data));
      }
      if (path === '/v1/password') {
        limit('password:' + auth.session(token).account, 10);
        await auth.changePassword(token, data.currentPassword, data.newPassword);
        return reply(200, { ok: true });
      }
      // Activated accounts authenticate locally even while the control panel is down.
      // First activation and protected config/billing operations still verify the panel.
      if(!emailFlow)throw new ApiError(503,'email_unavailable');
      const verify = async () => {
        await (panelClient ? panelClient.getClient(data.id) : subscriptionReader(base, data.id));
        await emailFlow.verifyLogin(data.id,data.password,data.emailCode);
      };
      const session = path === '/v1/login'
        ? await emailFlow.login(data)
        : await auth.activate(data.id, data.code, data.password, verify);
      reply(200, session);
    } finally { working--; }
  } catch (error) {
    if (!res.headersSent) {
      if(error.code==='email_required')return reply(200,emailRequired());
      if (error.status === 429) res.setHeader('Retry-After', String(error.retryAfterSeconds??900));
      reply(error instanceof ApiError ? error.status : 500,
        { error: error instanceof ApiError ? error.code : 'internal_error', ...(error.retryAfterSeconds?{retryAfterSeconds:error.retryAfterSeconds}:{}) });
    } else res.destroy();
  }
});
}
if (process.argv[1] && import.meta.url === new URL('file://' + process.argv[1]).href) {
const server = createApiServer();
const paymentWorker = lava ? setInterval(() => { lava.processPending().catch(() => {}); }, 5000) : null;
paymentWorker?.unref();
if (lava) lava.processPending().catch(() => {});
server.requestTimeout = 15000;
server.headersTimeout = 10000;
server.maxConnections = 100;
server.listen(Number(process.env.PORT ?? 8080), process.env.HOST ?? '127.0.0.1', () => console.log('tngl-auth listening'));
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => {
  server.close(() => { auth.close(); billing?.close(); process.exit(0); });
});
}
