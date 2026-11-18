import { DatabaseSync } from 'node:sqlite';
import { timingSafeEqual } from 'node:crypto';
import { ApiError } from './auth.mjs';

export const LAVA_RETURN_URL = 'https://app.tngl.pro/billing/return';
export const LAVA_WEB_RETURN_URL = 'https://app.tngl.pro/renew';
const returnUrls = url => ({ successful_return_url:url, failure_return_url:url, cancel_return_url:url });
export const LAVA_PRODUCT = '0cee2edb-1432-4c9a-a239-592d51538413';
export const LAVA_PLANS = Object.freeze([
  { id:'month30', days:30, amountKopecks:15000, currency:'RUB', offerId:'0919fe4f-5ff5-4a4e-9ad1-e1d319b9bd37' },
  { id:'month90', days:90, amountKopecks:45000, currency:'RUB', offerId:'9f8eb17d-db04-45da-b9ed-4e0822363e24' },
  { id:'month180', days:180, amountKopecks:90000, currency:'RUB', offerId:'afa25900-a372-490b-a9ec-b300c2fdcef3' },
  { id:'month360', days:360, amountKopecks:180000, currency:'RUB', offerId:'180e53b7-a191-4e28-b531-585480ecc93f' }
]);
export const validEmail = v => typeof v === 'string' && v.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
const money = v => typeof v === 'number' && Number.isFinite(v) && Math.abs(Math.round(v * 100) / 100 - v) < 1e-8 ? Math.round(v * 100) : NaN;
const uuid = v => typeof v === 'string' && /^[a-f0-9-]{36}$/i.test(v);
export function createLava({ path, key, secret, billing, fetcher = fetch }) {
  if (!key || !secret || secret.length < 32 || secret.length > 80) throw new Error('Lava credentials missing or invalid');
  const db = new DatabaseSync(path);
  db.exec(`PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;
    CREATE TABLE IF NOT EXISTS lava_invoices(
      order_id TEXT PRIMARY KEY, invoice_id TEXT UNIQUE, email TEXT NOT NULL,
      offer_id TEXT NOT NULL, amount INTEGER NOT NULL, state TEXT NOT NULL,
      payment_url TEXT, received INTEGER NOT NULL DEFAULT 0, last_error TEXT, checked INTEGER NOT NULL DEFAULT 0);
  `);
  if (!db.prepare('PRAGMA table_info(lava_invoices)').all().some(c => c.name === 'payment_method'))
    db.exec("ALTER TABLE lava_invoices ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'legacy'");
  db.exec(`CREATE TABLE IF NOT EXISTS lava_previous_invoices (
    invoice_id TEXT PRIMARY KEY, order_id TEXT NOT NULL, email TEXT NOT NULL,
    amount INTEGER NOT NULL, state TEXT NOT NULL, checked INTEGER NOT NULL DEFAULT 0, last_error TEXT);
  `);
  db.exec('BEGIN IMMEDIATE');
  try {
    if (!db.prepare('PRAGMA table_info(lava_invoices)').all().some(c => c.name === 'return_url'))
      db.exec("ALTER TABLE lava_invoices ADD COLUMN return_url TEXT");
    if (!db.prepare('PRAGMA table_info(lava_invoices)').all().some(c => c.name === 'promo_code'))
      db.exec('ALTER TABLE lava_invoices ADD COLUMN promo_code TEXT');
    db.exec('COMMIT');
  } catch (error) { db.exec('ROLLBACK'); throw error; }
  async function api(route, body) {
    let response;
    try {
      response = await fetcher('https://gate.lava.top' + route, {
        method:body ? 'POST':'GET', redirect:'error',
        headers:{'X-Api-Key':key, Accept:'application/json', 'Content-Type':'application/json'},
        body:body ? JSON.stringify(body):undefined, signal:AbortSignal.timeout(body ? 30000 : 7000)
      });
    } catch { throw new ApiError(503,'lava_unavailable'); }
    if (!response.ok) {
      if (body?.promoCode && [400,422].includes(response.status)) throw new ApiError(400,'invalid_promo');
      throw new ApiError(503,'lava_unavailable');
    }
    try { return await response.json(); } catch { throw new ApiError(503,'lava_invalid_response'); }
  }
  const find = id => db.prepare('SELECT * FROM lava_invoices WHERE order_id=?').get(id);
  function payment(order) {
    const item = order && find(order.id);
    return order ? {...order, paymentUrl: item?.state === 'ready' && order.status === 'pending' ? item.payment_url : null,
      promoCode:item?.promo_code ?? null, paymentState: item?.state ?? null} : null;
  }
  async function checkout(account, planId, email, { returnUrl = LAVA_RETURN_URL, orderId, promoCode = '' } = {}) {
    if (typeof promoCode !== 'string') throw new ApiError(400,'invalid_promo');
    promoCode = promoCode.trim().toUpperCase();
    if (promoCode && !/^[A-Z0-9_-]{3,36}$/.test(promoCode)) throw new ApiError(400,'invalid_promo');
    if (![LAVA_RETURN_URL, LAVA_WEB_RETURN_URL].includes(returnUrl)) throw new ApiError(400,'invalid_return_url');
    if (!validEmail(email)) throw new ApiError(400,'invalid_email');
    email = email.trim().toLowerCase();
    const plan = LAVA_PLANS.find(p => p.id === planId);
    if (!plan) throw new ApiError(400,'invalid_plan');
    const order = orderId === undefined ? await billing.create(account, plan) : billing.get(orderId, 'web');
    if (!order || order.account !== account) throw new ApiError(404, 'order_not_found');
    if (order.status !== 'pending') throw new ApiError(409,'order_requires_review');
    const existingInvoice = find(order.id);
    if ((existingInvoice ? existingInvoice.offer_id !== plan.offerId : order.amountKopecks !== plan.amountKopecks) || order.days !== plan.days)
      throw new ApiError(409,'existing_order_different_plan');
    // INSERT OR IGNORE claims creation across processes. An ambiguous network
    // failure never creates another invoice or loses the original mapping.
    const inserted = db.prepare(`INSERT OR IGNORE INTO lava_invoices
      (order_id,email,offer_id,amount,return_url,promo_code,state) VALUES(?,?,?,?,?,?,'creating')`)
      .run(order.id,email,plan.offerId,plan.amountKopecks,returnUrl,promoCode || null).changes;
    if (!inserted) {
      const row = find(order.id);
      if (row.state !== 'ready') throw new ApiError(409,'invoice_requires_review');
      if ((row.promo_code || '') !== promoCode) throw new ApiError(409,'promo_order_locked');
      return payment(order);
    }
    try {
      const invoice = await api('/api/v3/invoice', {
        email, offerId:plan.offerId, currency:'RUB', periodicity:'ONE_TIME', buyerLanguage:'RU', paymentProvider:'PAY2ME', paymentMethod:'SBP', ...(promoCode ? {promoCode} : {}), ...returnUrls(returnUrl)
      });
      const amount = money(invoice.amountTotal?.amount);
      if (!uuid(invoice.id) || invoice.amountTotal?.currency !== 'RUB' ||
          !Number.isSafeInteger(amount) || amount <= 0 || amount > plan.amountKopecks ||
          (!promoCode && amount !== plan.amountKopecks))
        throw new ApiError(503,'lava_invoice_mismatch');
      const url = new URL(invoice.paymentUrl);
      if (url.protocol !== 'https:' || url.username || url.password) throw new ApiError(503,'lava_invalid_response');
      db.exec('BEGIN IMMEDIATE');
      try {
        const changed = db.prepare("UPDATE billing_orders SET amount=? WHERE id=? AND status='pending' AND target_expiry IS NULL")
          .run(amount,order.id).changes;
        if (changed !== 1) throw new ApiError(409,'order_requires_review');
        db.prepare("UPDATE lava_invoices SET invoice_id=?,payment_url=?,amount=?,state='ready',payment_method='SBP' WHERE order_id=?")
          .run(invoice.id,url.href,amount,order.id);
        db.exec('COMMIT');
      } catch(e) { db.exec('ROLLBACK'); throw e; }
      return payment(billing.get(order.id));
    } catch (e) {
      if (e instanceof ApiError && e.code === 'invalid_promo') {
        db.prepare("DELETE FROM lava_invoices WHERE order_id=? AND state='creating' AND invoice_id IS NULL").run(order.id);
        throw e;
      }
      db.prepare("UPDATE lava_invoices SET state='review',last_error=? WHERE order_id=?")
        .run(e instanceof ApiError ? e.code:'lava_invalid_response',order.id);
      throw e;
    }
  }
  async function resume(account, orderId, { source = 'app' } = {}) {
    const order = billing.get(orderId, source);
    // Account comes only from the authenticated session. Never accept a payer email
    // or an arbitrary order ID as authority to inspect/retry another user's payment.
    if (!order || order.account !== account) throw new ApiError(404,'order_not_found');
    if (order.status !== 'pending') return payment(order);
    const row = find(order.id);
    if (!row || row.state !== 'ready') throw new ApiError(409,'invoice_requires_review');
    const current = await api('/api/v1/invoices/'+row.invoice_id);
    if (current.id !== row.invoice_id || current.type !== 'ONE_TIME' ||
        current.receipt?.currency !== 'RUB' || money(current.receipt?.amount) !== row.amount ||
        current.buyer?.email !== row.email) throw new ApiError(409,'lava_payment_mismatch');
    if (['NEW','IN_PROGRESS'].includes(current.status)) return payment(billing.get(order.id));
    if (current.status === 'COMPLETED') {
      await processPending();
      const latest = billing.get(order.id);
      if (latest.status === 'pending') throw new ApiError(409,'order_requires_review');
      return payment(latest);
    }
    if (current.status !== 'FAILED') throw new ApiError(409,'invoice_requires_review');
    // An explicit retry may replace only a verified failed invoice. Claim it
    // atomically across API/admin processes and preserve it for late settlement.
    db.exec('BEGIN IMMEDIATE');
    try {
      const claimed = db.prepare(`UPDATE lava_invoices SET state='replacing'
        WHERE order_id=? AND invoice_id=? AND state='ready' AND EXISTS
        (SELECT 1 FROM billing_orders WHERE id=? AND status='pending'
          AND target_expiry IS NULL AND lock_until<=?)`)
        .run(order.id,row.invoice_id,order.id,Date.now()).changes;
      if (!claimed) throw new ApiError(409,'invoice_requires_review');
      db.prepare(`INSERT OR IGNORE INTO lava_previous_invoices
        (invoice_id,order_id,email,amount,state) VALUES(?,?,?,?,'ready')`)
        .run(row.invoice_id,order.id,row.email,row.amount);
      db.exec('COMMIT');
    } catch(e) { db.exec('ROLLBACK'); throw e; }
    try {
      const invoice = await api('/api/v3/invoice', {
        email:row.email,offerId:row.offer_id,currency:'RUB',periodicity:'ONE_TIME',
        buyerLanguage:'RU',paymentProvider:'PAY2ME',paymentMethod:'SBP',...(row.promo_code ? {promoCode:row.promo_code} : {}),...returnUrls(row.return_url || LAVA_RETURN_URL)
      });
      const url = new URL(invoice.paymentUrl);
      if (!uuid(invoice.id) || invoice.id === row.invoice_id || url.protocol !== 'https:' ||
          url.username || url.password || invoice.amountTotal?.currency !== 'RUB' ||
          money(invoice.amountTotal?.amount) !== row.amount)
        throw new ApiError(503,'lava_invoice_mismatch');
      db.prepare(`UPDATE lava_invoices SET invoice_id=?,payment_url=?,state='ready',
        payment_method='SBP',last_error=NULL,checked=0,received=0 WHERE order_id=?`)
        .run(invoice.id,url.href,order.id);
      return payment(billing.get(order.id));
    } catch(e) {
      // A timeout might have created a real invoice; never POST again blindly.
      db.prepare("UPDATE lava_invoices SET state='review',last_error=? WHERE order_id=?")
        .run(e instanceof ApiError ? e.code:'lava_invalid_response',order.id);
      throw e;
    }
  }
  function authenticate(value) {
    const provided=Buffer.from(typeof value === 'string' ? value:'');
    const expected=Buffer.from(secret);
    if (provided.length !== expected.length || !timingSafeEqual(provided,expected))
      throw new ApiError(401,'invalid_webhook_key');
  }
  function receive(event) {
    if (event.eventType !== 'payment.success') return;
    if (!uuid(event.contractId)) throw new ApiError(400,'invalid_webhook');
    // Unknown contracts never grant access. Notifications contain no trusted
    // account identifier; the local invoice-to-order mapping is authoritative.
    db.prepare("UPDATE lava_invoices SET received=1 WHERE invoice_id=? AND state='ready'").run(event.contractId);
    db.prepare('UPDATE lava_previous_invoices SET checked=0 WHERE invoice_id=?').run(event.contractId);
  }
  let working=false;
  async function processPending() {
    if (working) return;
    working=true;
    try {
      const rows=db.prepare(`SELECT order_id,invoice_id,email,amount,checked,'lava_invoices' AS source
        FROM lava_invoices WHERE state='ready'
        UNION ALL SELECT order_id,invoice_id,email,amount,checked,'lava_previous_invoices' AS source
        FROM lava_previous_invoices WHERE state='ready' ORDER BY checked LIMIT 20`).all();
      for (const row of rows) {
        const table = row.source === 'lava_previous_invoices' ? 'lava_previous_invoices' : 'lava_invoices';
        db.prepare('UPDATE '+table+' SET checked=? WHERE invoice_id=?').run(Date.now(),row.invoice_id);
        try {
          const invoice=await api('/api/v1/invoices/'+row.invoice_id);
          if (invoice.id === row.invoice_id && ['NEW','IN_PROGRESS','FAILED'].includes(invoice.status)) {
            db.prepare('UPDATE '+table+' SET last_error=? WHERE invoice_id=?')
              .run(invoice.status === 'FAILED' ? 'lava_payment_failed' : null,row.invoice_id);
            continue;
          }
          if (invoice.id !== row.invoice_id || invoice.status !== 'COMPLETED' || invoice.type !== 'ONE_TIME' ||
              invoice.receipt?.currency !== 'RUB' || money(invoice.receipt?.amount) !== row.amount ||
              invoice.buyer?.email !== row.email) throw new ApiError(409,'lava_payment_mismatch');
          // Re-check through authenticated API; browser redirects/webhook body
          // alone never authorize renewal. confirm retains its absolute target.
          await billing.confirm(row.order_id, 'lava:'+row.invoice_id);
          db.prepare("UPDATE "+table+" SET state='paid',last_error=NULL WHERE invoice_id=?").run(row.invoice_id);
        } catch(e) {
          db.prepare('UPDATE '+table+' SET last_error=? WHERE invoice_id=?')
            .run(e instanceof ApiError ? e.code:'renewal_failed',row.invoice_id);
        }
      }
    } finally { working=false; }
  }
  async function upgradeLegacy() {
    // Deployment-only migration. Preserve every prior payable invoice.
    const rows = db.prepare(`SELECT i.* FROM lava_invoices i JOIN billing_orders o ON o.id=i.order_id
      WHERE i.state='ready' AND i.payment_method='legacy' AND o.status='pending'
      AND o.target_expiry IS NULL`).all();
    const result = {upgraded:0, waiting:0};
    for (const row of rows) {
      const current = await api('/api/v1/invoices/'+row.invoice_id);
      if (!['NEW','IN_PROGRESS','FAILED'].includes(current.status) || current.id !== row.invoice_id) {result.waiting++;continue;}
      const claimed=db.prepare("UPDATE lava_invoices SET state='migrating' WHERE order_id=? AND state='ready' AND payment_method='legacy'").run(row.order_id).changes;
      if (!claimed) continue;
      db.prepare(`INSERT OR IGNORE INTO lava_previous_invoices(invoice_id,order_id,email,amount,state)
        VALUES(?,?,?,?,'ready')`).run(row.invoice_id,row.order_id,row.email,row.amount);
      try {
        const invoice=await api('/api/v3/invoice',{email:row.email,offerId:row.offer_id,
          currency:'RUB',periodicity:'ONE_TIME',buyerLanguage:'RU',paymentProvider:'PAY2ME',paymentMethod:'SBP',...(row.promo_code ? {promoCode:row.promo_code} : {}),...returnUrls(row.return_url || LAVA_RETURN_URL)});
        const url=new URL(invoice.paymentUrl);
        if (!uuid(invoice.id) || invoice.id===row.invoice_id || url.protocol!=='https:' || url.username || url.password ||
            invoice.amountTotal?.currency!=='RUB' || invoice.amountTotal?.amount*100!==row.amount)
          throw new ApiError(503,'lava_invoice_mismatch');
        db.prepare("UPDATE lava_invoices SET invoice_id=?,payment_url=?,state='ready',payment_method='SBP',last_error=NULL WHERE order_id=?")
          .run(invoice.id,url.href,row.order_id);
        result.upgraded++;
      } catch (e) {
        db.prepare("UPDATE lava_invoices SET state='review',last_error=? WHERE order_id=?")
          .run(e instanceof ApiError?e.code:'lava_invalid_response',row.order_id);
        throw e;
      }
    }
    return result;
  }
  return {checkout,resume,payment,authenticate,receive,processPending,upgradeLegacy,close:()=>{if(db.isOpen)db.close();}};
}
