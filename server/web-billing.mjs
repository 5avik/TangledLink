import { DatabaseSync } from 'node:sqlite';
import { createHash, timingSafeEqual } from 'node:crypto';
import { ApiError, validId } from './auth.mjs';
import { LAVA_PLANS, LAVA_WEB_RETURN_URL, validEmail } from './lava.mjs';

const hash = value => createHash('sha256').update(value).digest('hex');
const tokenPattern = /^[A-Za-z0-9_-]{43}$/;

// Public renewal is a gift to an exact 3x-ui email/login, not an account session.
// A browser capability permits access only to the order that browser created.
export function createWebBilling({ path, key, billing, lava }) {
  if (typeof key !== 'string' || key.length < 32) throw new Error('WEB_BILLING_KEY must have at least 32 characters');
  const db = new DatabaseSync(path);
  db.exec(`PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;
    CREATE TABLE IF NOT EXISTS web_checkouts (
      token_hash TEXT PRIMARY KEY, fingerprint TEXT NOT NULL,
      account TEXT NOT NULL, order_id TEXT UNIQUE,
      state TEXT NOT NULL, error TEXT, created INTEGER NOT NULL
    );`);
  const find = token => {
    if (typeof token !== 'string' || !tokenPattern.test(token)) throw new ApiError(400, 'invalid_checkout_token');
    return db.prepare('SELECT * FROM web_checkouts WHERE token_hash=?').get(hash(token));
  };
  function authenticate(value) {
    const received = Buffer.from(typeof value === 'string' ? value : ''), expected = Buffer.from(key);
    if (received.length !== expected.length || !timingSafeEqual(received, expected)) throw new ApiError(401, 'invalid_web_key');
  }
  function status(token) {
    const row = find(token);
    if (!row) throw new ApiError(404, 'checkout_not_found');
    if (row.state === 'rejected') return { state: 'rejected', order: null, error: row.error };
    if (!row.order_id) {
      if (row.state === 'rejected') return { state: 'rejected', order: null, error: row.error };
      return { state: 'creating', order: null };
    }
    const order = lava.payment(billing.get(row.order_id));
    if (!order || order.account !== row.account) throw new ApiError(409, 'order_requires_review');
    return { state: order.paymentState === 'ready' || order.status === 'paid' ? 'ready' : row.state,
      order, error: row.error };
  }
  async function checkout({ token, account, planId, email, promoCode = '' }) {
    find(token); // Validate before doing work or inserting a claim.
    if (!validId(account)) throw new ApiError(400, 'invalid_id');
    email = typeof email === 'string' ? email.trim().toLowerCase() : email;
    if (!validEmail(email)) throw new ApiError(400, 'invalid_email');
    if (typeof promoCode !== 'string') throw new ApiError(400, 'invalid_promo');
    promoCode = promoCode.trim().toUpperCase();
    if (promoCode && !/^[A-Z0-9_-]{3,36}$/.test(promoCode)) throw new ApiError(400, 'invalid_promo');
    const plan = LAVA_PLANS.find(p => p.id === planId);
    if (!plan) throw new ApiError(400, 'invalid_plan');
    const fields = [account, planId, email];
    if (promoCode) fields.push(promoCode); // Preserve existing browser tokens without a promo.
    const fingerprint = hash(JSON.stringify(fields));
    const inserted = db.prepare(`INSERT OR IGNORE INTO web_checkouts
      (token_hash,fingerprint,account,state,created) VALUES(?,?,?,'creating',?)`)
      .run(hash(token), fingerprint, account, Date.now()).changes;
    if (!inserted) {
      if (find(token).fingerprint !== fingerprint) throw new ApiError(409, 'checkout_changed');
      return status(token);
    }
    try {
      // A browser token owns one purchase; other purchases for this login never
      // block it and their invoices/payer details are never reused or exposed.
      const order = await billing.create(account, plan, { source: 'web' });
      db.prepare('UPDATE web_checkouts SET order_id=? WHERE token_hash=?').run(order.id, hash(token));
      await lava.checkout(account, planId, email, { returnUrl: LAVA_WEB_RETURN_URL, orderId: order.id, promoCode });
      db.prepare("UPDATE web_checkouts SET state='ready' WHERE token_hash=?").run(hash(token));
      return status(token);
    } catch (error) {
      const code = error instanceof ApiError ? error.code : 'billing_unavailable';
      const row = find(token);
      db.prepare('UPDATE web_checkouts SET state=?,error=? WHERE token_hash=?')
        .run(row.order_id && code !== 'invalid_promo' ? 'review' : 'rejected', code, hash(token));
      throw error;
    }
  }
  async function resume(token) {
    const row = find(token);
    if (!row?.order_id) throw new ApiError(404, 'checkout_not_found');
    const order = billing.get(row.order_id);
    if (!order || order.account !== row.account) throw new ApiError(409, 'order_requires_review');
    if (order.status === 'paid') return status(token);
    await lava.resume(row.account, row.order_id, { source: 'web' });
    return status(token);
  }
  return { authenticate, checkout, status, resume,
    plans: () => LAVA_PLANS.map(({ offerId, ...plan }) => plan), close: () => db.close() };
}
