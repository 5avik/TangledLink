import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { subscription } from './upstream.mjs';

test('subscription validates expiry, quota, response format and status headers', async () => {
  let info = 'upload=1; download=2; total=100; expire=0';
  let body = Buffer.from('vless://example@host:443?security=tls#test').toString('base64');
  const server = createServer((_req, res) => {
    if (info !== null) res.setHeader('subscription-userinfo', info);
    res.end(body);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = 'http://127.0.0.1:' + server.address().port + '/sub/';
  try {
    assert.equal((await subscription(base, 'mika')).body, body);
    info = 'upload=1; download=2; total=3; expire=0';
    await assert.rejects(subscription(base, 'mika'), { code: 'subscription_inactive' });
    info = 'upload=0; download=0; total=0; expire=1';
    await assert.rejects(subscription(base, 'mika'), { code: 'subscription_inactive' });
    info = null;
    await assert.rejects(subscription(base, 'mika'), { code: 'subscription_status_missing' });
    info = 'upload=0; download=0; total=0; expire=0';
    const config = 'vless://example@host:443?security=tls#test';
    for (const encode of [text => text, text => Buffer.from(text).toString('base64')]) {
      body = encode(config + '\n' + 'vpn://provider-entry');
      assert.equal((await subscription(base, 'mika')).body, encode(config));
      body = encode('vpn://provider-entry');
      await assert.rejects(subscription(base, 'mika'), { code: 'subscription_inactive' });
      body = encode(config + '\n' + 'unexpected://entry');
      await assert.rejects(subscription(base, 'mika'), { code: 'subscription_inactive' });
    }
    body = '<html>login</html>';
    await assert.rejects(subscription(base, 'mika'), { code: 'subscription_inactive' });
  } finally { await new Promise(resolve => server.close(resolve)); }
});
