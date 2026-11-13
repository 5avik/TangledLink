import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Script } from 'node:vm';
import { createBilling, PLAN, renewalTarget } from './billing.mjs';
import { createPanel } from './xui.mjs';
import { createAuth } from './auth.mjs';
import { billingPage } from './billing-page.mjs';

const NOW = 1800000000000, DAY = 86400000;
const client = () => ({client:{id:7,email:'svk',subId:'different_subid',uuid:'keep-uuid',
  enable:true,expiryTime:NOW+DAY,totalGB:0,limitHwid:3,reset:0,resetDay:0,resetWeekday:0,
  group:'keep-group',reverse:null,allowedIPs:'',keepAlive:'',createdAt:123},
  inboundIds:[1,2],usedTraffic:1234});
const options = panel => ({path:':memory:',panel,instructions:'Contact administrator with order number',clock:()=>NOW});
test('renewal uses exact paid duration and preserves active/expired semantics', () => {
  const c=client();
  assert.equal(renewalTarget(c,NOW), NOW+31*DAY);
  c.client.expiryTime=NOW-DAY;assert.equal(renewalTarget(c,NOW),NOW+30*DAY);
  c.client.expiryTime=0;assert.throws(()=>renewalTarget(c,NOW),{code:'client_expiry_requires_review'});
  c.client.expiryTime=-DAY;assert.throws(()=>renewalTarget(c,NOW),{code:'client_expiry_requires_review'});
  c.client.expiryTime=NOW;c.client.enable=false;assert.throws(()=>renewalTarget(c,NOW),{code:'client_disabled'});
  c.client.enable=true;c.client.totalGB=100;assert.throws(()=>renewalTarget(c,NOW),{code:'traffic_depleted'});
  c.client.totalGB=0;c.client.resetDay=3;assert.throws(()=>renewalTarget(c,NOW),{code:'client_auto_renewal_requires_review'});
});
test('one open order, server price and repeated manual confirmation without extra input', async () => {
  const data=client();let writes=0;
  const panel={getClient:async email=>{assert.equal(email,'svk');return data;},
    setExpiry:async(email,before,target,id)=>{assert.equal(id,7);writes++;data.client.expiryTime=target;return {pendingSync:false};}};
  const b=createBilling(options(panel));
  try {
    const o=await b.create('svk');assert.equal(o.amountKopecks,15000);
    assert.equal((await b.create('svk')).id,o.id);
    const paid=await b.confirm(o.id);assert.equal(paid.status,'paid');
    assert.equal(paid.expiresAt,NOW+31*DAY);
    assert.equal((await b.confirm(o.id)).id,o.id);assert.equal(writes,1);
    const next=await b.create('svk');assert.notEqual(next.id,o.id);
    assert.equal(next.amountKopecks,15000);assert.equal(writes,1);
  } finally {b.close();}
});
test('timeout after remote write and process restart reuse fixed absolute expiry', async () => {
  const dir=mkdtempSync(join(tmpdir(),'tngl-billing-')),data=client();let fail=true;
  const targets=[];
  const panel={getClient:async()=>data,setExpiry:async(email,before,target)=>{
    targets.push(target);data.client.expiryTime=target;
    if(fail)throw new Error('simulated timeout after write');return {pendingSync:true};
  }};
  const opts={...options(panel),path:join(dir,'billing.sqlite')};let b=createBilling(opts);
  try {
    const o=await b.create('svk');
    await assert.rejects(b.confirm(o.id));
    assert.equal(b.latest('svk').status,'retry');b.close();b=createBilling(opts);fail=false;
    const paid=await b.confirm(o.id);
    assert.equal(paid.pendingSync,true);assert.deepEqual(targets,[NOW+31*DAY,NOW+31*DAY]);
    assert.equal(data.client.totalGB,0);assert.equal(data.usedTraffic,1234);
  } finally {b.close();rmSync(dir,{recursive:true,force:true});}
});
test('concurrent confirmations cannot apply twice', async()=>{
  let unblock;const wait=new Promise(r=>unblock=r);let calls=0;
  const panel={getClient:async()=>client(),setExpiry:async()=>{calls++;await wait;return {};}};
  const b=createBilling(options(panel));
  try {
    const o=await b.create('svk'),first=b.confirm(o.id);
    await assert.rejects(b.confirm(o.id),{code:'confirmation_in_progress'});
    unblock();await first;assert.equal(calls,1);
  } finally {unblock();b.close();}
});
test('3x-ui 3.11 client update maps UUID and preserves all settings, exact email not subId', async()=>{
  let data=client(),sent,fail=false;
  const panel=createPanel({url:'https://panel.invalid/secret/',token:'test-key',fetcher:async(url,init)=>{
    assert.equal(init.headers.Authorization,'Bearer test-key');
    assert.equal(init.redirect,'error');
    if(init.method==='GET') {
      assert.equal(url.pathname,'/secret/panel/api/clients/get/svk');
      return Response.json({success:true,obj:data});
    }
    assert.equal(url.pathname,'/secret/panel/api/clients/update/svk');
    sent=JSON.parse(init.body);
    if(fail)return Response.json({success:false,msg:'partial application'});
    data.client.expiryTime=sent.expiryTime;
    return Response.json({success:true,obj:{pending:true}});
  }});
  assert.equal((await panel.getClient('svk')).client.subId,'different_subid');
  assert.equal((await panel.setExpiry('svk',NOW+DAY,NOW+31*DAY,7)).pendingSync,true);
  assert.equal(sent.id,'keep-uuid');assert.equal(sent.email,'svk');assert.equal(sent.subId,'different_subid');
  assert.equal(sent.limitHwid,3);assert.equal(sent.totalGB,0);assert.equal(sent.group,'keep-group');
  assert.equal(sent.created_at,123);assert.equal(sent.allowedIPs,undefined);
  await assert.rejects(panel.setExpiry('svk',NOW+DAY,NOW+32*DAY,7),{code:'panel_client_changed'});
  data.client.email='svk2';await assert.rejects(panel.getClient('svk'),{code:'panel_client_mismatch'});
  data=client();fail=true;
  await assert.rejects(panel.setExpiry('svk',NOW+DAY,NOW+31*DAY,7),{code:'panel_operation_failed'});
  assert.throws(()=>createPanel({url:'http://remote.invalid/',token:'x'}));
  assert.throws(()=>createPanel({url:'https://u:p@remote.invalid/',token:'x'}));
});
test('billing page emits syntactically valid client JavaScript',()=>{
  const html=billingPage('nonce','csrf');
  new Script(html.match(/<script nonce="nonce">([\s\S]*?)<\/script>/)[1]);
});

process.env.ADMIN_PASSWORD='test-admin-password-at-least-24-chars';
process.env.SUBSCRIPTION_BASE='https://example.invalid/sub/';
process.env.AUTH_DB=':memory:';
delete process.env.XUI_URL;delete process.env.XUI_API_TOKEN;delete process.env.BILLING_INSTRUCTIONS;
const {createApiServer}=await import('./server.mjs');
const {createAdminServer}=await import('./admin-panel.mjs');

test('API binds orders to bearer account; only CSRF-authenticated admin confirms',async()=>{
  const auth=createAuth(':memory:');
  const code=auth.invite('svk');await auth.activate('svk',code,'test-password-long');
  const session=await auth.login('svk','test-password-long');
  const code2=auth.invite('other');await auth.activate('other',code2,'test-password-long');
  const other=await auth.login('other','test-password-long');
  const data=client();data.client.expiryTime=NOW-DAY;
  const panel={getClient:async email=>{assert.equal(email,'svk');return data;},
    setExpiry:async(e,b,t)=>{data.client.expiryTime=t;return {};}};
  const b=createBilling(options(panel));
  const api=createApiServer({authStore:auth,billingStore:b,panelClient:panel,
    subscriptionReader:async(base, subId)=>{assert.equal(subId,'different_subid');return {body:'test-config',info:'upload=0;download=0;total=0;expire=0'};}});
  const admin=createAdminServer({authStore:auth,billingStore:b});
  const listen=async s=>{await new Promise(r=>s.listen(0,'127.0.0.1',r));return 'http://127.0.0.1:'+s.address().port;};
  const root=await listen(api),adm=await listen(admin);
  const bearer={Authorization:'Bearer '+session.token,'Content-Type':'application/json'};
  const basic={Authorization:'Basic '+Buffer.from('admin:'+process.env.ADMIN_PASSWORD).toString('base64')};
  try {
    assert.equal((await fetch(root+'/v1/billing')).status,401);
    // Expired clients can authenticate for renewal. No config availability check at login.
    const login=await fetch(root+'/v1/login',{method:'POST',headers:{'Content-Type':'application/json'},
      body:JSON.stringify({id:'svk',password:'test-password-long'})});
    assert.equal(login.status,200);
    const sub=await fetch(root+'/v1/subscription',{headers:bearer});
    assert.equal(sub.status,200);assert.equal(await sub.text(),'test-config');
    const created=await fetch(root+'/v1/billing/orders',{method:'POST',headers:bearer,
      body:JSON.stringify({planId:PLAN.id,id:'other',amountKopecks:1})});
    assert.equal(created.status,200);const order=(await created.json()).order;assert.equal(order.account,'svk');
    const otherState=await fetch(root+'/v1/billing',{headers:{Authorization:'Bearer '+other.token}});
    assert.equal((await otherState.json()).order,null);
    assert.equal((await fetch(root+'/v1/billing/confirm',{method:'POST',headers:bearer,body:'{}'})).status,404);
    assert.equal((await fetch(adm+'/api/billing/orders')).status,401);
    const html=await (await fetch(adm+'/billing',{headers:basic})).text();
    const csrf=html.match(/const csrf="([^"]+)"/)[1];
    const payload=JSON.stringify({orderId:order.id});
    const resetPayload=JSON.stringify({orderId:order.id});
    assert.equal((await fetch(root+'/v1/billing/reset',{method:'POST',headers:bearer,body:resetPayload})).status,404);
    assert.equal((await fetch(adm+'/api/billing/reset',{method:'POST',headers:{'Content-Type':'application/json'},body:resetPayload})).status,401);
    assert.equal((await fetch(adm+'/api/billing/reset',{method:'POST',headers:{...basic,'Content-Type':'application/json'},body:resetPayload})).status,403);
    const reset=await fetch(adm+'/api/billing/reset',{method:'POST',
      headers:{...basic,'Content-Type':'application/json','X-CSRF-Token':csrf},body:resetPayload});
    assert.equal(reset.status,200);assert.equal((await reset.json()).order.status,'pending');
    assert.equal(data.client.expiryTime,NOW-DAY);
    assert.ok(!html.includes('id="reference"'));assert.ok(!html.includes('id="amount"'));
    assert.equal((await fetch(adm+'/api/billing/confirm',{method:'POST',headers:{...basic,'Content-Type':'application/json'},body:payload})).status,403);
    const paid=await fetch(adm+'/api/billing/confirm',{method:'POST',
      headers:{...basic,'Content-Type':'application/json','X-CSRF-Token':csrf},body:payload});
    assert.equal(paid.status,200);assert.equal((await paid.json()).order.status,'paid');
    assert.equal(data.client.expiryTime,NOW+30*DAY);
    const repeat=await fetch(adm+'/api/billing/confirm',{method:'POST',
      headers:{...basic,'Content-Type':'application/json','X-CSRF-Token':csrf},body:payload});
    assert.equal(repeat.status,200);assert.equal(data.client.expiryTime,NOW+30*DAY);
    const resetPaid=await fetch(adm+'/api/billing/reset',{method:'POST',
      headers:{...basic,'Content-Type':'application/json','X-CSRF-Token':csrf},body:resetPayload});
    assert.equal(resetPaid.status,409);
  } finally {await Promise.all([new Promise(r=>api.close(r)),new Promise(r=>admin.close(r))]);b.close();auth.close();}
});
