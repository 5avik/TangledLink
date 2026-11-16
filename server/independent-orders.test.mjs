import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createBilling } from './billing.mjs';

const DAY=86400000, NOW=1900000000000;
function fixture() {
 const dir=mkdtempSync(join(tmpdir(),'tngl-independent-')),path=join(dir,'billing.sqlite');
 let expiry=NOW+DAY,writes=0,fail=false,held=false,release;
 const barrier=new Promise(r=>release=r);
 const panel={getClient:async()=>{
  if(held) await barrier;
  return {client:{id:7,email:'svk',enable:true,expiryTime:expiry,totalGB:0},usedTraffic:0,inboundIds:[1]};
 },setExpiry:async(account,before,target)=>{
  assert.equal(account,'svk');assert.ok([before,target].includes(expiry));expiry=target;writes++;
  if(fail){fail=false;throw new Error('write applied, response lost');}return {};
 }};
 const opts={path,panel,instructions:'Test',clock:()=>NOW};
 const a=createBilling(opts),b=createBilling(opts);
 return {a,b,path,opts,get expiry(){return expiry;},get writes(){return writes;},hold:()=>held=true,release:()=>{held=false;release();},ambiguous:()=>fail=true,
  cleanup(){release();a.close();b.close();rmSync(dir,{recursive:true,force:true});}};
}
test('different purchases for one account serialize across database connections and add both paid durations',async()=>{
 const f=fixture();try{
  const app=await f.a.create('svk'),web=await f.a.create('svk',{days:90,amountKopecks:45000},{source:'web'});
  assert.notEqual(app.id,web.id);assert.equal(f.a.latest('svk').id,app.id);
  f.hold();const first=f.a.confirm(web.id);
  await assert.rejects(f.b.confirm(app.id),{code:'account_confirmation_in_progress'});
  f.release();await first;await f.b.confirm(app.id);await f.a.confirm(web.id);await f.b.confirm(app.id);
  assert.equal(f.expiry,NOW+121*DAY);assert.equal(f.writes,2);
 }finally{f.cleanup();}
});
test('ambiguous applied purchase blocks later settlement until the original target is recovered after restart',async()=>{
 const f=fixture();let restarted;try{
  const first=await f.a.create('svk',undefined,{source:'web'}),second=await f.b.create('svk',undefined,{source:'web'});
  f.ambiguous();await assert.rejects(f.a.confirm(first.id));assert.equal(f.expiry,NOW+31*DAY);
  restarted=createBilling(f.opts);
  await assert.rejects(restarted.confirm(second.id),{code:'account_confirmation_in_progress'});
  await restarted.confirm(first.id);assert.equal(f.expiry,NOW+31*DAY);
  await f.b.confirm(second.id);assert.equal(f.expiry,NOW+61*DAY);
  await restarted.confirm(first.id);assert.equal(f.expiry,NOW+61*DAY);
 }finally{restarted?.close();f.cleanup();}
});
test('legacy pending website purchase migrates without blocking a fresh website or app purchase',async()=>{
 const dir=mkdtempSync(join(tmpdir(),'tngl-order-migration-')),path=join(dir,'billing.sqlite');let billing;
 try{
  const db=new DatabaseSync(path);
  db.exec(`CREATE TABLE billing_orders(id TEXT PRIMARY KEY,account TEXT NOT NULL,amount INTEGER NOT NULL,
   days INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'pending',created INTEGER NOT NULL,reference TEXT UNIQUE,
   confirmed INTEGER,before_expiry INTEGER,target_expiry INTEGER,client_id INTEGER,lock_until INTEGER NOT NULL DEFAULT 0,
   last_error TEXT,pending_sync INTEGER NOT NULL DEFAULT 0);
   CREATE UNIQUE INDEX billing_one_open_order ON billing_orders(account) WHERE status<>'paid';
   INSERT INTO billing_orders(id,account,amount,days,created) VALUES('TL-old-web','svk',15000,30,1);
   CREATE TABLE web_checkouts(token_hash TEXT PRIMARY KEY,order_id TEXT UNIQUE);
   INSERT INTO web_checkouts VALUES('legacy-browser','TL-old-web');`);db.close();
  const panel={getClient:async()=>({client:{id:7,enable:true,expiryTime:NOW+DAY,totalGB:0},usedTraffic:0,inboundIds:[1]})};
  billing=createBilling({path,panel,instructions:'Test',clock:()=>NOW});
  assert.equal(billing.latest('svk'),null);assert.ok(billing.get('TL-old-web','web'));
  const app=await billing.create('svk'),web=await billing.create('svk',undefined,{source:'web'});
  assert.notEqual(app.id,web.id);assert.equal(billing.list().length,3);
  assert.equal((await billing.create('svk')).id,app.id);
  billing.close();billing=createBilling({path,panel,instructions:'Test',clock:()=>NOW});
  assert.equal(billing.get('TL-old-web','web').status,'pending');assert.equal(billing.latest('svk').id,app.id);
 }finally{billing?.close();rmSync(dir,{recursive:true,force:true});}
});
test('an expired account lease cannot persist a stale target or erase another completed purchase',async()=>{
 const dir=mkdtempSync(join(tmpdir(),'tngl-lease-expiry-')),path=join(dir,'billing.sqlite');
 let now=NOW,expiry=NOW+DAY,hold=false,release;
 const barrier=new Promise(r=>release=r);
 const panel={getClient:async()=>{
  const snapshot={client:{id:7,enable:true,expiryTime:expiry,totalGB:0},usedTraffic:0,inboundIds:[1]};
  if(hold)await barrier;return snapshot;
 },setExpiry:async(a,b,t)=>{expiry=t;return {};}};
 const options={path,panel,instructions:'Test',clock:()=>now},first=createBilling(options),second=createBilling(options);
 try{
  const a=await first.create('svk',undefined,{source:'web'}),b=await first.create('svk',undefined,{source:'web'});
  hold=true;const stale=first.confirm(a.id);now+=61000;hold=false;
  await second.confirm(b.id);release();await assert.rejects(stale,{code:'confirmation_in_progress'});
  assert.equal(second.get(b.id).status,'paid');assert.equal(first.get(a.id).expiresAt,null);
  await first.confirm(a.id);assert.equal(expiry,NOW+61*DAY);
 }finally{release();first.close();second.close();rmSync(dir,{recursive:true,force:true});}
});
