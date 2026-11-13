import { DatabaseSync } from 'node:sqlite';
import { randomBytes } from 'node:crypto';
import { ApiError, validId } from './auth.mjs';

export const PLAN = Object.freeze({ id: 'month30', amountKopecks: 15000, currency: 'RUB', days: 30 });
export function renewalTarget(data, now = Date.now(), days = PLAN.days) {
  const c = data.client;
  if (!c.enable) throw new ApiError(409, 'client_disabled');
  if (c.expiryTime <= 0) throw new ApiError(409, 'client_expiry_requires_review');
  if (c.totalGB > 0 && data.usedTraffic >= c.totalGB) throw new ApiError(409, 'traffic_depleted');
  if (c.reset > 0 || c.resetDay > 0 || c.resetWeekday > 0)
    throw new ApiError(409, 'client_auto_renewal_requires_review');
  const target = Math.max(now, c.expiryTime) + days * 86400000;
  if (!Number.isSafeInteger(target) || target > 8640000000000000)
    throw new ApiError(409, 'client_expiry_requires_review');
  return target;
}

export function createBilling({ path, panel, instructions, clock = Date.now }) {
  if (!instructions?.trim() || instructions.length > 1000) throw new Error('BILLING_INSTRUCTIONS is required (max 1000 chars)');
  const db = new DatabaseSync(path);
  db.exec(`
    PRAGMA journal_mode=WAL;
    PRAGMA busy_timeout=5000;
    CREATE TABLE IF NOT EXISTS billing_orders (
      id TEXT PRIMARY KEY, account TEXT NOT NULL, amount INTEGER NOT NULL,
      days INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'pending',
      created INTEGER NOT NULL, reference TEXT UNIQUE, confirmed INTEGER,
      before_expiry INTEGER, target_expiry INTEGER, client_id INTEGER,
      lock_until INTEGER NOT NULL DEFAULT 0, last_error TEXT, pending_sync INTEGER NOT NULL DEFAULT 0
    );
    CREATE TABLE IF NOT EXISTS billing_order_resets (
      id INTEGER PRIMARY KEY, order_id TEXT NOT NULL, reset_at INTEGER NOT NULL,
      previous_status TEXT NOT NULL, previous_reference TEXT, before_expiry INTEGER,
      target_expiry INTEGER, client_id INTEGER, last_error TEXT
    );
    CREATE UNIQUE INDEX IF NOT EXISTS billing_one_open_order
      ON billing_orders(account) WHERE status <> 'paid';
  `);
  const tx = work => {
    db.exec('BEGIN IMMEDIATE');
    try { const result = work(); db.exec('COMMIT'); return result; }
    catch (e) { db.exec('ROLLBACK'); throw e; }
  };
  const find = id => db.prepare('SELECT * FROM billing_orders WHERE id=?').get(id);
  const publicOrder = o => o ? ({
    id: o.id, account: o.account, amountKopecks: o.amount, currency: 'RUB',
    days: o.days, status: o.status, createdAt: o.created,
    expiresAt: o.target_expiry, pendingSync: Boolean(o.pending_sync)
  }) : null;
  function latest(account) {
    return publicOrder(db.prepare('SELECT * FROM billing_orders WHERE account=? ORDER BY created DESC, rowid DESC LIMIT 1').get(account));
  }
  async function create(account) {
    if (!validId(account)) throw new ApiError(400, 'invalid_id');
    const existing = db.prepare("SELECT * FROM billing_orders WHERE account=? AND status<>'paid'").get(account);
    if (existing) return publicOrder(existing);
    renewalTarget(await panel.getClient(account), clock());
    return tx(() => {
      const concurrent = db.prepare("SELECT * FROM billing_orders WHERE account=? AND status<>'paid'").get(account);
      if (concurrent) return publicOrder(concurrent);
      const id = 'TL-' + randomBytes(12).toString('hex').toUpperCase();
      db.prepare('INSERT INTO billing_orders(id,account,amount,days,created) VALUES(?,?,?,?,?)')
        .run(id, account, PLAN.amountKopecks, PLAN.days, clock());
      return publicOrder(find(id));
    });
  }
  async function preview(id) {
    const order = find(id);
    if (!order) throw new ApiError(404, 'order_not_found');
    const data = await panel.getClient(order.account);
    return { order: publicOrder(order), currentExpiry: data.client.expiryTime,
      targetExpiry: order.target_expiry ?? renewalTarget(data, clock()),
      inboundCount: data.inboundIds.length };
  }
  const active = new Set();
  async function confirm(id) {
    if (active.has(id)) throw new ApiError(409, 'confirmation_in_progress');
    active.add(id);
    let claimed = false;
    try {
      const order = tx(() => {
        const o = find(id);
        if (!o) throw new ApiError(404, 'order_not_found');
        if (o.status === 'paid') return o;
        if (o.lock_until > clock()) throw new ApiError(409, 'confirmation_in_progress');
        // Keep legacy references; new confirmations have a stable server-only order reference.
        const reference = o.reference ?? 'manual:' + o.id;
        db.prepare("UPDATE billing_orders SET reference=?,status='applying',lock_until=?,last_error=NULL WHERE id=?")
          .run(reference, clock() + 60000, id);
        claimed = true;
        return find(id);
      });
      if (order.status === 'paid') return publicOrder(order);
      if (order.target_expiry == null) {
        const data = await panel.getClient(order.account);
        const target = renewalTarget(data, clock(), order.days);
        // Persist an absolute target BEFORE any remote mutation. A restart or timeout
        // always retries this same target, never adds another paid period.
        db.prepare('UPDATE billing_orders SET before_expiry=?,target_expiry=?,client_id=? WHERE id=?')
          .run(data.client.expiryTime, target, data.client.id, id);
      }
      const fixed = find(id);
      const result = await panel.setExpiry(fixed.account, fixed.before_expiry, fixed.target_expiry, fixed.client_id);
      db.prepare("UPDATE billing_orders SET status='paid',confirmed=?,lock_until=0,pending_sync=?,last_error=NULL WHERE id=?")
        .run(clock(), result.pendingSync ? 1 : 0, id);
      return publicOrder(find(id));
    } catch (e) {
      if (claimed) db.prepare("UPDATE billing_orders SET status='retry',lock_until=0,last_error=? WHERE id=?")
        .run(e instanceof ApiError ? e.code : 'confirmation_failed', id);
      throw e;
    } finally { active.delete(id); }
  }
  async function reset(id) {
    if (active.has(id)) throw new ApiError(409, 'confirmation_in_progress');
    active.add(id);
    let claimed = false;
    try {
      const order = tx(() => {
        const o = find(id);
        if (!o) throw new ApiError(404, 'order_not_found');
        if (o.status === 'paid') throw new ApiError(409, 'paid_order_cannot_reset');
        if (o.lock_until > clock()) throw new ApiError(409, 'confirmation_in_progress');
        db.prepare('UPDATE billing_orders SET lock_until=? WHERE id=?').run(clock() + 60000, id);
        claimed = true;
        return o;
      });
      if (order.target_expiry != null) {
        // An ambiguous remote write may already have extended the client. Never
        // erase the absolute target in that case: retry the original confirmation.
        const data = await panel.getClient(order.account);
        if (data.client.id !== order.client_id || data.client.expiryTime !== order.before_expiry)
          throw new ApiError(409, 'order_reset_requires_review');
      }
      return tx(() => {
        db.prepare(`INSERT INTO billing_order_resets(order_id,reset_at,previous_status,
          previous_reference,before_expiry,target_expiry,client_id,last_error) VALUES(?,?,?,?,?,?,?,?)`)
          .run(id, clock(), order.status, order.reference, order.before_expiry,
            order.target_expiry, order.client_id, order.last_error);
        db.prepare(`UPDATE billing_orders SET status='pending',reference=NULL,confirmed=NULL,
          before_expiry=NULL,target_expiry=NULL,client_id=NULL,lock_until=0,last_error=NULL,pending_sync=0 WHERE id=?`).run(id);
        return publicOrder(find(id));
      });
    } finally {
      if (claimed) db.prepare('UPDATE billing_orders SET lock_until=0 WHERE id=?').run(id);
      active.delete(id);
    }
  }
  return {
    plan: PLAN, instructions, create, latest, preview, confirm, reset,
    list: () => db.prepare('SELECT * FROM billing_orders ORDER BY created DESC, rowid DESC LIMIT 100').all()
      .map(o => ({ ...publicOrder(o), reference: o.reference, error: o.last_error })),
    close: () => db.close()
  };
}
