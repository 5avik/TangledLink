import { DatabaseSync } from 'node:sqlite';
import { randomBytes, createHash, scrypt, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';

const derive = promisify(scrypt);
const digest = value => createHash('sha256').update(value).digest('hex');
const secret = () => randomBytes(32).toString('base64url');
const now = () => Math.floor(Date.now() / 1000);
export class ApiError extends Error {
  constructor(status, code) { super(code); this.status = status; this.code = code; }
}
export const validId = id => typeof id === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(id);
const checkPassword = password => {
  if (typeof password !== 'string' || password.length < 12 || password.length > 128)
    throw new ApiError(400, 'password_policy');
};
async function passwordHash(password, salt) {
  return (await derive(password, salt, 32, { N: 32768, r: 8, p: 3, maxmem: 64 * 1024 * 1024 })).toString('hex');
}
const equalHash = (a, b) => {
  const left = Buffer.from(a, 'hex'), right = Buffer.from(b, 'hex');
  return left.length === right.length && timingSafeEqual(left, right);
};

export function createAuth(path) {
  const db = new DatabaseSync(path);
  db.exec(`
    PRAGMA journal_mode=WAL;
    PRAGMA busy_timeout=5000;
    PRAGMA foreign_keys=ON;
    CREATE TABLE IF NOT EXISTS accounts (
      id TEXT PRIMARY KEY, salt TEXT, hash TEXT, version INTEGER NOT NULL DEFAULT 0,
      invite TEXT, invite_expires INTEGER
    );
    CREATE TABLE IF NOT EXISTS sessions (
      hash TEXT PRIMARY KEY, account TEXT NOT NULL REFERENCES accounts(id),
      expires INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS sessions_account ON sessions(account);
  `);
  const account = id => db.prepare('SELECT * FROM accounts WHERE id = ?').get(id);
  const tx = work => {
    db.exec('BEGIN IMMEDIATE');
    try { const result = work(); db.exec('COMMIT'); return result; }
    catch (error) { db.exec('ROLLBACK'); throw error; }
  };
  function issue(id) {
    const token = secret(), expires = now() + 7 * 86400;
    db.prepare('DELETE FROM sessions WHERE expires <= ?').run(now());
    db.prepare('INSERT INTO sessions VALUES (?, ?, ?)').run(digest(token), id, expires);
    return { token, expiresAt: expires };
  }
  // Called only from the local admin CLI. Reset requires an explicit flag.
  function invite(id, reset = false) {
    if (!validId(id)) throw new ApiError(400, 'invalid_id');
    const code = secret();
    tx(() => {
      const row = account(id);
      if (row?.hash && !reset) throw new ApiError(409, 'already_activated');
      db.prepare(`INSERT INTO accounts(id, invite, invite_expires) VALUES (?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET salt=NULL, hash=NULL, version=version+1,
        invite=excluded.invite, invite_expires=excluded.invite_expires`)
        .run(id, digest(code), now() + 86400);
      db.prepare('DELETE FROM sessions WHERE account = ?').run(id);
    });
    return code;
  }
  async function activate(id, code, password, verifySubscription = async () => {}) {
    checkPassword(password);
    const row = validId(id) ? account(id) : null;
    if (!row || row.hash || typeof code !== 'string' || !row.invite ||
        row.invite_expires <= now() || !equalHash(row.invite, digest(code)))
      throw new ApiError(401, 'invalid_credentials');
    await verifySubscription();
    const salt = secret(), hash = await passwordHash(password, salt);
    return tx(() => {
      const result = db.prepare(`UPDATE accounts SET salt=?, hash=?, invite=NULL,
        invite_expires=NULL, version=version+1
        WHERE id=? AND version=? AND hash IS NULL AND invite_expires>?`)
        .run(salt, hash, id, row.version, now());
      if (!result.changes) throw new ApiError(401, 'invalid_credentials');
      return issue(id);
    });
  }
  async function login(id, password, verifySubscription = async () => {}) {
    const row = validId(id) ? account(id) : null;
    // Do password work for unknown IDs as well.
    const bounded = typeof password === 'string' && password.length <= 128 ? password : '';
    const hash = await passwordHash(bounded, row?.salt ?? 'unknown-account-fixed-salt');
    if (!row?.hash || !equalHash(hash, row.hash))
      throw new ApiError(401, 'invalid_credentials');
    await verifySubscription();
    return tx(() => {
      if (account(id)?.version !== row.version) throw new ApiError(401, 'invalid_credentials');
      return issue(id);
    });
  }
  function session(token) {
    if (typeof token !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(token))
      throw new ApiError(401, 'session_expired');
    const row = db.prepare('SELECT * FROM sessions WHERE hash=? AND expires>?').get(digest(token), now());
    if (!row) throw new ApiError(401, 'session_expired');
    return row;
  }
  async function changePassword(token, currentPassword, newPassword) {
    checkPassword(newPassword);
    const current = session(token), row = account(current.account);
    const bounded = typeof currentPassword === 'string' && currentPassword.length <= 128 ? currentPassword : '';
    const checked = await passwordHash(bounded, row.salt);
    if (!equalHash(checked, row.hash)) throw new ApiError(401, 'invalid_credentials');
    const salt = secret(), hash = await passwordHash(newPassword, salt);
    tx(() => {
      session(token);
      const result = db.prepare('UPDATE accounts SET salt=?, hash=?, version=version+1 WHERE id=? AND version=?')
        .run(salt, hash, row.id, row.version);
      if (!result.changes) throw new ApiError(401, 'session_expired');
      db.prepare('DELETE FROM sessions WHERE account=? AND hash<>?').run(row.id, current.hash);
    });
  }
  return { invite, activate, login, session, changePassword,
    logout(token) { db.prepare('DELETE FROM sessions WHERE hash=?').run(digest(token)); },
    close() { db.close(); } };
}
