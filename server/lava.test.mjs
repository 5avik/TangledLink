import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createBilling} from './billing.mjs';
import {createLava,LAVA_PLANS} from './lava.mjs';
import {DatabaseSync} from 'node:sqlite';

const invoiceId='11111111-1111-4111-8111-111111111111', secret='s'.repeat(64);
function fixture(plan=LAVA_PLANS[0]) {
  const dir=mkdtempSync(join(tmpdir(),'lava-test-')),path=join(dir,'billing.sqlite');
  let expiry=2000000000000, writes=0, creations=0, status='COMPLETED',amount=plan.amountKopecks/100, email='buyer@example.com',fail=false;
  const panel={
    getClient:async()=>({client:{id:1,enable:true,expiryTime:expiry,totalGB:0},usedTraffic:0,inboundIds:[1]}),
    setExpiry:async(account,before,target)=>{expiry=target;writes++;if(fail){fail=false;throw new Error('ambiguous');}return {};}
  };
  const billing=createBilling({path,panel,instructions:'Test',clock:()=>1900000000000});
  const fetcher=async(url,options)=>{
    if(options.method==='POST'){
      creations++;
      const request=JSON.parse(options.body);
      assert.equal(request.periodicity,'ONE_TIME');
      assert.equal(request.paymentProvider,'PAY2ME');
      assert.equal(request.paymentMethod,'SBP');
      for (const field of ['successful_return_url','failure_return_url','cancel_return_url'])
        assert.equal(request[field],'https://app.tngl.pro/billing/return');
      return {ok:true,json:async()=>({id:creations===1?invoiceId:'22222222-2222-4222-8222-222222222222',amountTotal:{currency:'RUB',amount:plan.amountKopecks/100},paymentUrl:'https://pay.lava.top/test'})};
    }
    return {ok:true,json:async()=>({id:invoiceId,type:'ONE_TIME',status,receipt:{amount,currency:'RUB'},buyer:{email}})};
  };
  let lava=createLava({path,billing,key:'test-key',secret,fetcher});
  return {billing,get lava(){return lava},get writes(){return writes},get creations(){return creations},get expiry(){return expiry},
    setAmount:v=>amount=v,setStatus:v=>status=v,setEmail:v=>email=v,ambiguous:()=>fail=true,
    restart(){lava.close();lava=createLava({path,billing,key:'test-key',secret,fetcher});},
    cleanup(){lava.close();billing.close();rmSync(dir,{recursive:true,force:true});},path};
}
test('authenticated webhook and API verification apply one absolute renewal across duplicate delivery/restart',async()=>{
 const f=fixture();try{
  assert.throws(()=>f.lava.authenticate('wrong'),{code:'invalid_webhook_key'});
  f.lava.authenticate(secret);
  await assert.rejects(f.lava.checkout('svk','month30','svk'),{code:'invalid_email'});
  await assert.rejects(f.lava.checkout('svk','unknown','buyer@example.com'),{code:'invalid_plan'});
  const order=await f.lava.checkout('svk','month30','buyer@example.com');
  assert.equal(order.account,'svk');assert.match(order.paymentUrl,/^https:/);assert.equal(f.writes,0);
  await f.lava.checkout('svk','month30','other@example.com');assert.equal(f.creations,1);
  await assert.rejects(f.billing.confirm(order.id),{code:'provider_confirmation_required'});
  await assert.rejects(f.billing.reset(order.id),{code:'provider_order_cannot_reset'});
  f.lava.receive({eventType:'payment.success',contractId:invoiceId});
  f.restart();await f.lava.processPending();
  assert.equal(f.billing.latest('svk').status,'paid');assert.equal(f.expiry,2000000000000+30*86400000);
  f.lava.receive({eventType:'payment.success',contractId:invoiceId});await f.lava.processPending();assert.equal(f.writes,1);
 }finally{f.cleanup();}
});
test('failed, forged, unknown, wrong amount or wrong buyer never grant access',async()=>{
 const f=fixture();try{
  await f.lava.checkout('svk','month30','buyer@example.com');
  f.setStatus('NEW');
  f.lava.receive({eventType:'payment.failed',contractId:invoiceId});
  f.lava.receive({eventType:'payment.success',contractId:'22222222-2222-4222-8222-222222222222'});
  await f.lava.processPending();assert.equal(f.writes,0);
  f.lava.receive({eventType:'payment.success',contractId:invoiceId,amount:150,status:'completed'});
  f.setStatus('COMPLETED');f.setAmount(1);await f.lava.processPending();assert.equal(f.writes,0);
  f.setAmount(150);f.setEmail('wrong@example.com');await f.lava.processPending();assert.equal(f.writes,0);
  f.setEmail('buyer@example.com');f.setStatus('NEW');await f.lava.processPending();assert.equal(f.writes,0);
  f.setStatus('COMPLETED');f.ambiguous();await f.lava.processPending();
  const target=f.expiry;assert.equal(f.billing.latest('svk').status,'retry');
  f.restart();await f.lava.processPending();assert.equal(f.expiry,target);assert.equal(f.billing.latest('svk').status,'paid');
 }finally{f.cleanup();}
});
test('ambiguous invoice creation is never repeated',async()=>{
 const f=fixture();try{
  f.lava.close();
  const bad=createLava({path:f.path,key:'key',secret,billing:f.billing,fetcher:async()=>{throw new Error('timeout')}});
  try{
   await assert.rejects(bad.checkout('svk','month30','buyer@example.com'),{code:'lava_unavailable'});
   await assert.rejects(bad.checkout('svk','month30','buyer@example.com'),{code:'invoice_requires_review'});
  }finally{bad.close();}
  f.restart();
 }finally{f.cleanup();}
});
test('every offer renews by its own stored duration and amount',async()=>{
 for(const plan of LAVA_PLANS){
  const f=fixture(plan);try{
   const o=await f.lava.checkout('svk',plan.id,'buyer@example.com');
   assert.equal(o.days,plan.days);assert.equal(o.amountKopecks,plan.amountKopecks);
   await f.lava.processPending();
   assert.equal(f.expiry,2000000000000+plan.days*86400000);
  }finally{f.cleanup();}
 }
});
test('legacy card invoice migrates once and both invoice IDs retain the same renewal',async()=>{
 const f=fixture();try{
  const o=await f.lava.checkout('svk','month30','buyer@example.com');
  const db=new DatabaseSync(f.path);
  db.prepare("UPDATE lava_invoices SET payment_method='legacy' WHERE order_id=?").run(o.id);db.close();
  f.setStatus('IN_PROGRESS');
  assert.deepEqual(await f.lava.upgradeLegacy(),{upgraded:1,waiting:0});
  assert.deepEqual(await f.lava.upgradeLegacy(),{upgraded:0,waiting:0});
  assert.equal(f.creations,2);
  f.setStatus('COMPLETED');
  // The old card invoice can still pay late. Its saved mapping must work.
  await f.lava.processPending();
  assert.equal(f.billing.latest('svk').status,'paid');
  assert.equal(f.writes,1);
  await f.billing.confirm(o.id,'lava:22222222-2222-4222-8222-222222222222');
  assert.equal(f.writes,1);
 }finally{f.cleanup();}
});
test('periods and prices are fixed server-side',()=>{
 assert.deepEqual(LAVA_PLANS.map(p=>[p.days,p.amountKopecks]),[[30,15000],[90,45000],[180,90000],[360,180000]]);
});
