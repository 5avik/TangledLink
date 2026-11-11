import test from 'node:test';
import assert from 'node:assert/strict';
import { createAuth } from './auth.mjs';

process.env.ADMIN_PASSWORD = 'test-admin-password-at-least-24-chars';
process.env.SUBSCRIPTION_BASE = 'https://example.invalid/sub/';
process.env.AUTH_DB = ':memory:';
const { createAdminServer } = await import('./admin-panel.mjs');

test('panel requires authentication and CSRF, verifies subscriptions, and manages accounts', async () => {
  const store = createAuth(':memory:');
  const checked = [];
  const server = createAdminServer({authStore:store, verify:async id => { checked.push(id); }});
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const root = 'http://127.0.0.1:' + server.address().port;
  const authorization = 'Basic ' + Buffer.from('admin:' + process.env.ADMIN_PASSWORD).toString('base64');
  try {
    const unauthorized = await fetch(root + '/api/accounts');
    assert.equal(unauthorized.status, 401);
    const page = await fetch(root, {headers:{Authorization:authorization}});
    assert.equal(page.status, 200);
    const html = await page.text();
    const csrf = html.match(/const csrf = "([^"]+)"/)?.[1];
    assert.ok(csrf);
    const post = (path, id, token = csrf) => fetch(root + '/api/' + path, {
      method:'POST', headers:{Authorization:authorization,'Content-Type':'application/json','X-CSRF-Token':token},
      body:JSON.stringify({id})
    });
    assert.equal((await post('invite','mika','wrong')).status,403);
    assert.equal((await post('invite','../bad')).status,400);
    assert.equal((await post('reset','missing')).status,409);
    const invitation = await post('invite','mika');
    assert.equal(invitation.status,200);
    const firstCode = (await invitation.json()).code;
    assert.equal(checked.join(','),'mika');
    const listing = await fetch(root + '/api/accounts',{headers:{Authorization:authorization}});
    assert.deepEqual((await listing.json()).accounts.map(x=>x.id),['mika']);
    await store.activate('mika',firstCode,'initial-password');
    assert.equal((await post('invite','mika')).status,409);
    const session = await store.login('mika','initial-password');
    const reset = await post('reset','mika');
    assert.equal(reset.status,200);
    assert.throws(()=>store.session(session.token),{code:'session_expired'});
    const resetCode = (await reset.json()).code;
    await store.activate('mika',resetCode,'another-password');
    const next = await store.login('mika','another-password');
    assert.equal((await post('revoke','mika')).status,200);
    assert.throws(()=>store.session(next.token),{code:'session_expired'});
  } finally {
    await new Promise(resolve=>server.close(resolve));
    store.close();
  }
});
