import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes, randomUUID } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createBilling } from './billing.mjs';
import { createLava, LAVA_PLANS } from './lava.mjs';
import { createWebBilling } from './web-billing.mjs';
import { ApiError } from './auth.mjs';

const token = () => randomBytes(32).toString('base64url');
function fixture() {
  const dir = mkdtempSync(join(tmpdir(), 'tngl-web-')), path = join(dir, 'billing.sqlite');
  const invoices = new Map(); let writes = 0, expiry = 2000000000000, failCreation = false;
  const panel = { getClient: async account => {
    if (account !== 'vpn_login') throw new ApiError(409, 'panel_client_mismatch');
    return { client: { email: 'vpn_login', subId: 'different_subscription_id', id: 7, enable: true, expiryTime: expiry, totalGB: 0 }, usedTraffic: 0, inboundIds: [1] };
  }, setExpiry: async (account, before, target) => {
    assert.equal(account, 'vpn_login'); expiry = target; writes++; return {};
  } };
  const billing = createBilling({ path, panel, instructions: 'Test', clock: () => 1900000000000 });
  const fetcher = async (url, options) => {
    if (options.method === 'POST') {
      const body = JSON.parse(options.body), id = randomUUID();
      if (body.promoCode === 'EXPIRED') return Response.json({error:'invalid promo'}, {status:400});
      const amount = body.promoCode === 'SOTKA' ? 100.01 : LAVA_PLANS.find(p => p.offerId === body.offerId).amountKopecks / 100;
      invoices.set(id, { body, status: 'NEW', amount });
      if (failCreation) throw new Error('response lost');
      return Response.json({ id, amountTotal: { amount, currency: 'RUB' }, paymentUrl: 'https://pay.lava.top/' + id });
    }
    const id = url.split('/').at(-1), item = invoices.get(id);
    return Response.json({ id, status: item.status, type: 'ONE_TIME', receipt: { amount: item.amount, currency: 'RUB' }, buyer: { email: item.body.email } });
  };
  const lava = createLava({ path, billing, key: 'test', secret: 's'.repeat(64), fetcher });
  const config = { path, key: 'w'.repeat(64), billing, lava };
  let web = createWebBilling(config);
  return { billing, lava, get web() { return web; }, invoices, get writes() { return writes; }, get expiry() { return expiry; },
    fail: () => { failCreation = true; },
    restart: () => { web.close(); web = createWebBilling(config); },
    cleanup: () => { web.close(); lava.close(); billing.close(); rmSync(dir, { recursive: true, force: true }); } };
}
const request = () => ({ token: token(), account: 'vpn_login', email: 'buyer@example.com', planId: 'month30' });

test('public renewal targets exact login/email, keeps payer separate and applies once after provider verification', async () => {
  const f = fixture(), r = request();
  try {
    const first = await f.web.checkout(r);
    assert.equal(first.order.account, 'vpn_login'); assert.equal(f.writes, 0);
    assert.equal(f.invoices.size, 1);
    const invoice = [...f.invoices.values()][0];
    assert.equal(invoice.body.email, 'buyer@example.com');
    for (const field of ['successful_return_url', 'failure_return_url', 'cancel_return_url']) assert.equal(invoice.body[field], 'https://app.tngl.pro/renew');
    assert.equal((await f.web.checkout(r)).order.id, first.order.id);
    assert.equal(f.invoices.size, 1);
    f.restart(); assert.equal(f.web.status(r.token).order.id, first.order.id);
    // Neither browser return nor reading status grants renewal.
    await f.lava.processPending(); assert.equal(f.writes, 0);
    invoice.status = 'COMPLETED'; await f.lava.processPending(); await f.lava.processPending();
    assert.equal(f.writes, 1); assert.equal(f.expiry, 2000000000000 + 30 * 86400000);
    assert.equal(f.web.status(r.token).order.status, 'paid');
    await f.web.resume(r.token); assert.equal(f.invoices.size, 1);
    // A later app order cannot change the website's receipt.
    await f.billing.create('vpn_login'); assert.equal(f.web.status(r.token).order.id, first.order.id);
  } finally { f.cleanup(); }
});
test('invalid/unknown login and client-supplied prices never issue invoices', async () => {
  const f = fixture(); try {
    await assert.rejects(f.web.checkout({ ...request(), account: 'unknown' }), { code: 'panel_client_mismatch' });
    await assert.rejects(f.web.checkout({ ...request(), account: '../vpn_login' }), { code: 'invalid_id' });
    await assert.rejects(f.web.checkout({ ...request(), email: 'vpn_login' }), { code: 'invalid_email' });
    await assert.rejects(f.web.checkout({ ...request(), planId: 'free', amountKopecks: 1 }), { code: 'invalid_plan' });
    assert.equal(f.invoices.size, 0); assert.equal(f.writes, 0);
  } finally { f.cleanup(); }
});
test('unknown capabilities and another browser cannot read or reuse app invoices', async () => {
  const f = fixture(); try {
    assert.throws(() => f.web.status(token()), { code: 'checkout_not_found' });
    assert.throws(() => f.web.authenticate('wrong'), { code: 'invalid_web_key' });
    f.web.authenticate('w'.repeat(64));
    const app = await f.lava.checkout('vpn_login', 'month30', 'private@example.com');
    const web = await f.web.checkout(request());
    assert.notEqual(web.order.id, app.id); assert.notEqual(web.order.paymentUrl, app.paymentUrl);
    assert.equal(f.billing.latest('vpn_login').id, app.id);
    await assert.rejects(f.lava.resume('vpn_login', web.order.id), {code:'order_not_found'});
    await assert.rejects(f.lava.resume('vpn_login', app.id, {source:'web'}), {code:'order_not_found'});
    assert.equal(f.invoices.size, 2);
  } finally { f.cleanup(); }
});
test('two browser tokens create independent purchases; repeated token creates only one invoice', async () => {
  const f = fixture(), a = request(), b = {...request(),planId:'month90',email:'second@example.com'}; try {
    const [first,second] = await Promise.all([f.web.checkout(a),f.web.checkout(b)]);
    assert.notEqual(first.order.id,second.order.id); assert.equal(f.invoices.size,2);
    assert.equal(first.order.days,30);assert.equal(second.order.days,90);
    const repeated = await Promise.all([f.web.checkout(a),f.web.checkout(a),f.web.checkout(b)]);
    assert.equal(f.invoices.size,2);assert.equal(repeated[0].order.id,first.order.id);
    assert.equal(f.web.status(a.token).order.id,first.order.id);
    await assert.rejects(f.web.checkout({...a,account:'other'}),{code:'checkout_changed'});
    const invoices=[...f.invoices.values()];invoices[1].status='COMPLETED';await f.lava.processPending();
    assert.equal(f.expiry,2000000000000+90*86400000);
    invoices[0].status='COMPLETED';await f.lava.processPending();await f.lava.processPending();
    assert.equal(f.expiry,2000000000000+120*86400000);assert.equal(f.writes,2);
    assert.equal(f.web.status(a.token).order.status,'paid');assert.equal(f.web.status(b.token).order.status,'paid');
  } finally {f.cleanup();}
});
test('concurrent identical browser requests create a single purchase and invoice', async()=>{
 const f=fixture(),r=request();try{
  await Promise.all([f.web.checkout(r),f.web.checkout(r),f.web.checkout(r)]);
  assert.equal(f.invoices.size,1);assert.equal(f.billing.list().length,1);
 }finally{f.cleanup();}
});
test('ambiguous invoice response stays review after retries and restart; no second invoice', async () => {
  const f = fixture(), r = request(); try {
    f.fail(); await assert.rejects(f.web.checkout(r), { code: 'lava_unavailable' });
    f.restart(); assert.equal(f.web.status(r.token).state, 'review');
    await f.web.checkout(r); await assert.rejects(f.web.resume(r.token), { code: 'invoice_requires_review' });
    assert.equal(f.invoices.size, 1); assert.equal(f.writes, 0);
  } finally { f.cleanup(); }
});
test('failed website invoice retry preserves the website return destination', async () => {
  const f = fixture(), r = request(); try {
    await f.web.checkout(r);
    await f.web.checkout({...request(),planId:'month90'});
    [...f.invoices.values()][0].status = 'FAILED';
    await f.web.resume(r.token); assert.equal(f.invoices.size, 3);
    assert.ok([...f.invoices.values()].every(i => i.body.successful_return_url === 'https://app.tngl.pro/renew'));
  } finally { f.cleanup(); }
});

test('website promo quote uses provider amount, persists code and grants full term exactly once',async()=>{
 const f=fixture(),r={...request(),promoCode:' sotka ',amountKopecks:1};try{
  const result=await f.web.checkout(r);
  assert.equal(result.order.promoCode,'SOTKA');assert.equal(result.order.amountKopecks,10001);assert.equal(result.order.days,30);
  const invoice=[...f.invoices.values()][0];assert.equal(invoice.body.promoCode,'SOTKA');
  await f.web.checkout({...r,promoCode:'SOTKA'});assert.equal(f.invoices.size,1);
  await assert.rejects(f.web.checkout({...r,promoCode:'OTHER'}),{code:'checkout_changed'});
  f.restart();assert.equal(f.web.status(r.token).order.amountKopecks,10001);
  invoice.status='COMPLETED';invoice.amount=100.02;await f.lava.processPending();assert.equal(f.writes,0);
  invoice.amount=100.01;await f.lava.processPending();await f.lava.processPending();
  assert.equal(f.writes,1);assert.equal(f.expiry,2000000000000+30*86400000);
 }finally{f.cleanup();}
});
test('website invalid promo allows correction and old rejected token never reveals an unpaid order',async()=>{
 const f=fixture(),r={...request(),promoCode:'EXPIRED'};try{
  await assert.rejects(f.web.checkout({...r,promoCode:'bad code'}),{code:'invalid_promo'});assert.equal(f.billing.list().length,0);
  await assert.rejects(f.web.checkout(r),{code:'invalid_promo'});assert.equal(f.invoices.size,0);
  assert.deepEqual(f.web.status(r.token),{state:'rejected',order:null,error:'invalid_promo'});
  const corrected=await f.web.checkout({...r,token:token(),promoCode:'SOTKA'});
  assert.equal(corrected.order.amountKopecks,10001);assert.equal(f.invoices.size,1);
 }finally{f.cleanup();}
});
test('website promo retry keeps the exact saved price and code for the same purchase',async()=>{
 const f=fixture(),r={...request(),promoCode:'SOTKA'};try{
  const first=await f.web.checkout(r);[...f.invoices.values()][0].status='FAILED';
  const retry=await f.web.resume(r.token);
  assert.equal(retry.order.id,first.order.id);assert.equal(retry.order.amountKopecks,10001);
  assert.equal(f.invoices.size,2);assert.ok([...f.invoices.values()].every(x=>x.body.promoCode==='SOTKA'));
 }finally{f.cleanup();}
});
