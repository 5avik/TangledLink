import { createServer } from 'node:http';
import { randomBytes, timingSafeEqual } from 'node:crypto';
import { createAuth, ApiError, validId } from './auth.mjs';
import { subscription } from './upstream.mjs';
import { panel, billing } from './billing-runtime.mjs';
import { billingPage } from './billing-page.mjs';

process.umask(0o077);
const password = process.env.ADMIN_PASSWORD;
const base = process.env.SUBSCRIPTION_BASE;
if (!password || password.length < 24 || password.startsWith('REPLACE_') || !base) throw new Error('ADMIN_PASSWORD (24+ chars) and SUBSCRIPTION_BASE are required');
const port = Number(process.env.ADMIN_PORT ?? 8081);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid ADMIN_PORT');
const auth = createAuth(process.env.AUTH_DB ?? '/var/lib/tngl-auth/auth.sqlite');
const csrf = randomBytes(32).toString('base64url');
const equal = (a, b) => {
  const left = Buffer.from(a), right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
};
function authenticated(req) {
  const value = req.headers.authorization ?? '';
  if (!value.startsWith('Basic ')) return false;
  const raw = Buffer.from(value.slice(6), 'base64').toString('utf8');
  const colon = raw.indexOf(':');
  return colon >= 0 && equal(raw.slice(colon + 1), password);
}
async function body(req) {
  if (!(req.headers['content-type'] ?? '').startsWith('application/json')) throw new ApiError(415, 'json_required');
  let size = 0, chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 4096) throw new ApiError(413, 'request_too_large');
    chunks.push(chunk);
  }
  try {
    const value = JSON.parse(Buffer.concat(chunks).toString());
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error();
    return value;
  } catch { throw new ApiError(400, 'invalid_json'); }
}
const html = nonce => `<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>TangledLink · Администрирование</title>
<style>
:root{font-family:system-ui,sans-serif;color:#e9f0f8;background:#101923}
body{margin:0;padding:32px 18px} main{max-width:980px;margin:auto}
h1{font-size:1.8rem;margin:0 0 8px}p{color:#a9bacb;line-height:1.5}
.card{background:#1a2734;border:1px solid #344657;border-radius:16px;padding:20px;margin:20px 0}
.row{display:flex;gap:10px;flex-wrap:wrap;align-items:end}
label{display:grid;gap:7px;font-size:.95rem}
input{background:#0d1720;color:#fff;border:1px solid #567088;border-radius:8px;padding:10px;font:inherit;min-width:220px}
button{border:0;border-radius:8px;padding:10px 14px;background:#7fd4bd;color:#082018;font:inherit;font-weight:650;cursor:pointer}
button.secondary{background:#384e60;color:#fff}button.danger{background:#f4adac;color:#351919}
button:disabled{opacity:.55;cursor:wait}button:focus-visible,input:focus-visible{outline:3px solid #fff;outline-offset:2px}
table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:12px 8px;border-bottom:1px solid #344657}
th{color:#a9bacb;font-weight:550}td.actions{display:flex;gap:7px;flex-wrap:wrap}
#message{min-height:24px}#codeBox{display:none;border:1px solid #7fd4bd;border-radius:9px;padding:14px;overflow-wrap:anywhere}
#code{font-family:ui-monospace,monospace;font-size:1.1rem;color:#b7f8e4}
@media(max-width:700px){table,thead,tbody,tr,td{display:block}thead{display:none}tr{padding:10px 0;border-bottom:1px solid #344657}td{border:0;padding:4px 0}td::before{content:attr(data-label) ': ';color:#a9bacb}}
</style></head><body><main>
<h1>TangledLink · Администрирование</h1><p><a href="/billing" style="color:#7fd4bd">Оплаты и продления →</a></p>
<p>Управление аккаунтами приложения. Подписка должна существовать в 3x-ui. Код показывается один раз.</p>
<section class="card"><h2>Выдать код активации</h2><form id="inviteForm" class="row">
<label>ID подписки<input id="newId" autocomplete="off" required maxlength="128" pattern="[A-Za-z0-9_-]+"></label>
<button type="submit">Создать / перевыпустить код</button></form></section>
<section id="codeBox" role="status"><strong>Одноразовый код (24 часа)</strong><p id="code"></p>
<p>Передайте код владельцу по защищённому каналу. После закрытия страницы его нельзя посмотреть повторно.</p></section>
<p id="message" role="status" aria-live="polite"></p>
<section class="card"><div class="row"><h2>Аккаунты</h2><button id="refresh" class="secondary" type="button">Обновить</button></div>
<div style="overflow-x:auto"><table><thead><tr><th>ID</th><th>Состояние</th><th>Код до</th><th>Сессии</th><th>Действия</th></tr></thead><tbody id="accounts"></tbody></table></div></section>
</main><script nonce="${nonce}">
const csrf = ${JSON.stringify(csrf)};
const message = document.getElementById('message');
const codeBox = document.getElementById('codeBox');
const code = document.getElementById('code');
let busy = false;
async function api(path, payload) {
  const response = await fetch(path, {
    method: payload ? 'POST' : 'GET',
    headers: payload ? {'Content-Type':'application/json','X-CSRF-Token':csrf} : {},
    body: payload ? JSON.stringify(payload) : undefined,
    cache:'no-store', credentials:'same-origin'
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || 'server_error');
  return data;
}
async function refresh() {
  try {
    const data = await api('/api/accounts');
    const target = document.getElementById('accounts');
    target.replaceChildren();
    for (const item of data.accounts) {
      const tr = document.createElement('tr');
      const cell = (label, value) => {
        const td = document.createElement('td'); td.dataset.label = label;
        td.textContent = String(value); tr.append(td); return td;
      };
      cell('ID',item.id);
      cell('Состояние',item.activated ? 'Активирован' : 'Ожидает активации');
      cell('Код до',item.inviteExpires ? new Date(item.inviteExpires * 1000).toLocaleString() : '—');
      cell('Сессии',item.activeSessions);
      const actions = cell('Действия',''); actions.className = 'actions';
      if (!item.activated) action(actions,'Перевыпустить код','invite',item.id,false);
      else action(actions,'Сбросить пароль','reset',item.id,true);
      if (item.activeSessions) action(actions,'Завершить сессии','revoke',item.id,true);
      target.append(tr);
    }
    if (!data.accounts.length) {
      const tr = document.createElement('tr'); const td = document.createElement('td');
      td.colSpan = 5; td.textContent = 'Аккаунтов пока нет'; tr.append(td); target.append(tr);
    }
  } catch (error) { message.textContent = 'Не удалось загрузить аккаунты: ' + error.message; }
}
function action(container, label, operation, id, confirmRequired) {
  const button = document.createElement('button');
  button.type = 'button'; button.textContent = label;
  button.className = confirmRequired ? 'danger' : 'secondary';
  button.addEventListener('click', () => perform(operation,id,confirmRequired));
  container.append(button);
}
async function perform(operation, id, confirmRequired = false) {
  if (busy) return;
  if (confirmRequired && !confirm(operation === 'reset'
    ? 'Сбросить пароль ' + id + '? Все сессии завершатся, потребуется новая активация.'
    : 'Завершить все сессии ' + id + '?')) return;
  busy = true; message.textContent = 'Выполняется…'; code.textContent = ''; codeBox.style.display = 'none';
  try {
    const result = await api('/api/' + operation,{id});
    if (result.code) { code.textContent = result.code; codeBox.style.display = 'block'; }
    message.textContent = operation === 'revoke' ? 'Сессии завершены.' : 'Новый код создан.';
    await refresh();
  } catch (error) { message.textContent = 'Ошибка: ' + error.message; }
  finally { busy = false; }
}
document.getElementById('inviteForm').addEventListener('submit', event => {
  event.preventDefault();
  perform('invite',document.getElementById('newId').value.trim());
});
document.getElementById('refresh').addEventListener('click', refresh);
refresh();
</script></body></html>`;
export function createAdminServer({ authStore = auth, verify = id => panel ? panel.getClient(id) : subscription(base, id), billingStore = billing } = {}) {
  return createServer(async (req, res) => {
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Referrer-Policy', 'no-referrer');
    const reply = (status, data) => {
      res.writeHead(status, {'Content-Type':'application/json; charset=utf-8'});
      res.end(JSON.stringify(data));
    };
    if (!authenticated(req)) {
      res.writeHead(401, {'WWW-Authenticate':'Basic realm="TangledLink admin", charset="UTF-8"'}); res.end(); return;
    }
    try {
      if (req.method === 'GET' && ['/', '/billing'].includes(req.url)) {
        if (req.url === '/billing' && !billingStore) throw new ApiError(503, 'billing_unavailable');
        const nonce = randomBytes(16).toString('base64');
        res.setHeader('Content-Security-Policy', `default-src 'none'; script-src 'nonce-${nonce}'; style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; form-action 'self'`);
        res.writeHead(200, {'Content-Type':'text/html; charset=utf-8'}); res.end(req.url === '/billing' ? billingPage(nonce, csrf) : html(nonce)); return;
      }
      if (req.method === 'GET' && req.url === '/api/billing/orders') {
        if (!billingStore) throw new ApiError(503, 'billing_unavailable');
        return reply(200, { orders: billingStore.list(), plan: billingStore.plan });
      }
      if (req.method === 'GET' && req.url === '/api/accounts') return reply(200, {accounts:authStore.listAccounts()});
      if (req.method !== 'POST' || !['/api/invite','/api/reset','/api/revoke','/api/billing/create','/api/billing/preview','/api/billing/confirm','/api/billing/reset'].includes(req.url))
        throw new ApiError(404, 'not_found');
      if (!equal(req.headers['x-csrf-token'] ?? '', csrf)) throw new ApiError(403, 'forbidden');
      const data = await body(req);
      if (req.url.startsWith('/api/billing/')) {
        if (!billingStore) throw new ApiError(503, 'billing_unavailable');
        if (req.url === '/api/billing/create') {
          if (!authStore.listAccounts().some(a => a.id === data.id && a.activated))
            throw new ApiError(404, 'account_not_found');
          return reply(200, { order: await billingStore.create(data.id) });
        }
        if (typeof data.orderId !== 'string' || !/^TL-[A-F0-9]{24}$/.test(data.orderId))
          throw new ApiError(400, 'invalid_order_id');
        if (req.url === '/api/billing/preview') return reply(200, await billingStore.preview(data.orderId));
        if (req.url === '/api/billing/reset') return reply(200, { order: await billingStore.reset(data.orderId) });
        return reply(200, { order: await billingStore.confirm(data.orderId) });
      }
      const { id } = data;
      if (!validId(id)) throw new ApiError(400, 'invalid_id');
      if (req.url === '/api/revoke') return reply(200, {revoked:authStore.revokeSessions(id)});
      const existing = authStore.listAccounts().find(item => item.id === id);
      if (req.url === '/api/invite' && existing?.activated)
        throw new ApiError(409, 'already_activated');
      if (req.url === '/api/reset' && !existing?.activated)
        throw new ApiError(409, 'not_activated');
      await verify(id);
      return reply(200, {code:authStore.invite(id, req.url === '/api/reset')});
    } catch (error) {
      reply(error instanceof ApiError ? error.status : 500,
        {error:error instanceof ApiError ? error.code : 'internal_error'});
    }
  });
}
if (process.argv[1] && import.meta.url === new URL('file://' + process.argv[1]).href) {
  const server = createAdminServer();
  server.requestTimeout = 15000;
  server.headersTimeout = 10000;
  server.listen(port, '127.0.0.1', () => console.log('tngl-admin listening on loopback'));
  for (const signal of ['SIGINT','SIGTERM']) process.on(signal, () => server.close(() => {
    auth.close(); billing?.close(); process.exit(0);
  }));
}
