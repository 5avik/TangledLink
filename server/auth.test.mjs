import test from 'node:test';
import assert from 'node:assert/strict';
import { createAuth } from './auth.mjs';

test('activation, login, password rotation and revocation', async () => {
  const auth = createAuth(':memory:');
  try {
    const id = 'mika';
    const code = auth.invite(id);
    await assert.rejects(auth.activate(id, 'wrong', 'initial-password'), { code: 'invalid_credentials' });
    await assert.rejects(auth.activate(id, code, 'short'), { code: 'password_policy' });
    await assert.rejects(auth.activate(id, code, 'initial-password', async () => {
      throw new Error('upstream down');
    }), /upstream down/);
    const first = await auth.activate(id, code, 'initial-password');
    assert.equal(auth.session(first.token).account, id);
    await assert.rejects(auth.activate(id, code, 'initial-password'), { code: 'invalid_credentials' });
    await assert.rejects(auth.login(id, 'wrong-password'), { code: 'invalid_credentials' });
    await assert.rejects(auth.login('unknown_id', 'initial-password'), { code: 'invalid_credentials' });
    const other = await auth.login(id, 'initial-password');
    await assert.rejects(auth.changePassword(first.token, 'incorrect-password', 'new-password-123'), { code: 'invalid_credentials' });
    assert.equal(auth.session(other.token).account, id);
    await auth.changePassword(first.token, 'initial-password', 'new-password-123');
    assert.equal(auth.session(first.token).account, id);
    assert.throws(() => auth.session(other.token), { code: 'session_expired' });
    await assert.rejects(auth.login(id, 'initial-password'), { code: 'invalid_credentials' });
    const next = await auth.login(id, 'new-password-123');
    auth.logout(next.token);
    assert.throws(() => auth.session(next.token), { code: 'session_expired' });
    assert.throws(() => auth.invite(id), { code: 'already_activated' });
    const resetCode = auth.invite(id, true);
    assert.throws(() => auth.session(first.token), { code: 'session_expired' });
    await auth.activate(id, resetCode, 'recovered-password');
  } finally { auth.close(); }
});

test('activation is atomic and invitation rotation invalidates previous code', async () => {
  const auth = createAuth(':memory:');
  try {
    const id = 'another_sub_123', previous = auth.invite(id), current = auth.invite(id);
    await assert.rejects(auth.activate(id, previous, 'some-password-123'), { code: 'invalid_credentials' });
    const results = await Promise.allSettled([
      auth.activate(id, current, 'some-password-123'),
      auth.activate(id, current, 'other-password-123')
    ]);
    assert.equal(results.filter(r => r.status === 'fulfilled').length, 1);
    assert.equal(results.filter(r => r.status === 'rejected').length, 1);
    assert.throws(() => auth.session('invalid'), { code: 'session_expired' });
    assert.throws(() => auth.invite('../bad'), { code: 'invalid_id' });
  } finally { auth.close(); }
});
