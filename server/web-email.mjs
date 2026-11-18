import { createHmac, randomInt, timingSafeEqual } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { ApiError } from './auth.mjs';

const sender = 'tangledlink@gmail.com';
const scope = 'https://www.googleapis.com/auth/gmail.send';
const minute = 60_000;

export function openWebEmail({ database, credentials, key, fetcher = fetch, mailer = null, clock = Date.now }) {
  const config = mailer ? null : JSON.parse(readFileSync(credentials, 'utf8'));
  const db = new DatabaseSync(database);
  db.exec(`
    PRAGMA busy_timeout=5000;
    CREATE TABLE IF NOT EXISTS web_verified_email (account TEXT PRIMARY KEY, email TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS web_email_codes (
      purpose TEXT NOT NULL, account TEXT NOT NULL, digest TEXT NOT NULL,
      created INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (purpose,account)
    );
    CREATE TABLE IF NOT EXISTS web_email_sends (recipient TEXT NOT NULL, created INTEGER NOT NULL);
    CREATE INDEX IF NOT EXISTS web_email_send_times ON web_email_sends(created);
  `);
  let token, expires = 0;
  const wait = seconds => Object.assign(new ApiError(429,'email_wait'),{retryAfterSeconds:Math.max(1,Math.ceil(seconds))});
  async function send(email, code) {
    if (mailer) return mailer(email,code);
    if (Date.now() >= expires) {
      const response = await fetcher('https://oauth2.googleapis.com/token', {
        method: 'POST', body: new URLSearchParams({ ...config, grant_type: 'refresh_token' }),
        signal: AbortSignal.timeout(8000)
      });
      if (!response.ok) throw new Error('Gmail authorization failed');
      const data = await response.json();
      if (!data.access_token || !data.scope?.split(' ').includes(scope)) throw new Error('Gmail scope missing');
      token = data.access_token;
      expires = Date.now() + Math.max(0, data.expires_in - 60) * 1000;
    }
    const message = [
      'From: ' + sender, 'To: ' + email, 'Subject: TangledLink verification code',
      'MIME-Version: 1.0', 'Content-Type: text/plain; charset=UTF-8', '',
      'Код TangledLink: ' + code + '. Действует 10 минут. Никому его не сообщайте.'
    ].join('\r\n');
    const response = await fetcher('https://gmail.googleapis.com/gmail/v1/users/me/messages/send', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
      body: JSON.stringify({ raw: Buffer.from(message).toString('base64url') }),
      signal: AbortSignal.timeout(8000)
    });
    if (!response.ok) throw new Error('Gmail rejected message');
  }
  const emailFor = account => db.prepare('SELECT email FROM web_verified_email WHERE account=?').get(account)?.email;
  function normalizeEmail(value) {
    const email = typeof value === 'string' ? value.trim().toLowerCase() : '';
    if (email.length > 254 || !/^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9-]+(?:\.[a-z0-9-]+)+$/.test(email))
      throw new ApiError(400, 'invalid_email');
    return email;
  }
  async function challenge(purpose, account, email, proof, code) {
    const digest = value => createHmac('sha256', key)
      .update(JSON.stringify([purpose, account, email, proof, value])).digest('hex');
    const now = clock();
    const row = db.prepare('SELECT * FROM web_email_codes WHERE purpose=? AND account=?').get(purpose, account);
    if (code !== undefined) {
      if (row && now-row.created>=10*minute) throw new ApiError(400,'email_code_expired');
      if (row?.attempts>=5) throw Object.assign(new ApiError(429,'email_attempts_exceeded'),
        {retryAfterSeconds:Math.max(0,Math.ceil((minute-(now-row.created))/1000))});
      if (typeof code !== 'string' || !/^\d{6}$/.test(code) || !row || now - row.created >= 10 * minute || row.attempts >= 5)
        throw new ApiError(400, 'invalid_email_code');
      if (!timingSafeEqual(Buffer.from(row.digest), Buffer.from(digest(code)))) {
        db.prepare('UPDATE web_email_codes SET attempts=attempts+1 WHERE purpose=? AND account=?').run(purpose, account);
        throw new ApiError(400, 'invalid_email_code');
      }
      db.prepare('DELETE FROM web_email_codes WHERE purpose=? AND account=?').run(purpose, account);
      return true;
    }
    if (row && now - row.created < minute) throw wait((minute-(now-row.created))/1000);
    const value = String(randomInt(1_000_000)).padStart(6, '0');
    const fingerprint = digest(value);
    db.exec('BEGIN IMMEDIATE');
    try {
      const latest=db.prepare('SELECT created FROM web_email_codes WHERE purpose=? AND account=?').get(purpose,account);
      if (latest && now-latest.created<minute) throw wait((minute-(now-latest.created))/1000);
      db.prepare('DELETE FROM web_email_sends WHERE created<=?').run(now - 1440 * minute);
      db.prepare('DELETE FROM web_email_codes WHERE created<=?').run(now - 10 * minute);
      if (db.prepare('SELECT count(*) AS n FROM web_email_sends').get().n >= 400 ||
          db.prepare('SELECT count(*) AS n FROM web_email_sends WHERE recipient=? AND created>?').get(email, now - 60 * minute).n >= 5)
        throw wait(3600);
      db.prepare('INSERT INTO web_email_sends VALUES (?,?)').run(email, now);
      db.prepare('INSERT OR REPLACE INTO web_email_codes VALUES (?,?,?,?,0)').run(purpose, account, fingerprint, now);
      db.exec('COMMIT');
    } catch (error) {
      db.exec('ROLLBACK');
      throw error;
    }
    try { await send(email, value); }
    catch {
      db.prepare('DELETE FROM web_email_codes WHERE purpose=? AND account=? AND digest=?').run(purpose, account, fingerprint);
      throw new ApiError(503, 'email_unavailable');
    }
    return false;
  }
  function bind(account, email) {
    db.prepare('INSERT OR IGNORE INTO web_verified_email VALUES (?,?)').run(account, email);
    if (emailFor(account) !== email) throw new ApiError(409, 'email_already_bound');
  }
  return { challenge, emailFor, normalizeEmail, bind, close(){db.close();} };
}
