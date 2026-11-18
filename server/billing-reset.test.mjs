import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createBilling } from './billing.mjs';

const NOW=1800000000000, DAY=86400000;
const client=()=>({client:{id:7,email:'svk',enable:true,expiryTime:NOW+DAY,totalGB:0},
  usedTraffic:0,inboundIds:[1]});
const options=(panel,path=':memory:')=>({path,panel,instructions:'Test instructions',clock:()=>NOW});

test('reset returns an unapplied failed order to pending, preserves audit and survives restart',async()=>{
  const dir=mkdtempSync(join(tmpdir(),'tngl-reset-')),path=join(dir,'billing.sqlite');
  const data=client();let fail=true,writes=0;
  const panel={getClient:async()=>data,setExpiry:async(e,b,t)=>{
    if(fail)throw new Error('failure before write');
    writes++;data.client.expiryTime=t;return {};
  }};
  let billing=createBilling(options(panel,path));
  try {
    const o=await billing.create('svk');
    await assert.rejects(billing.confirm(o.id));
    assert.equal(billing.latest('svk').status,'retry');
    const reset=await billing.reset(o.id);
    assert.equal(reset.id,o.id);assert.equal(reset.status,'pending');assert.equal(reset.expiresAt,null);
    assert.equal(data.client.expiryTime,NOW+DAY);assert.equal(writes,0);
    assert.equal((await billing.create('svk')).id,o.id);
    billing.close();
    const audit=new DatabaseSync(path);
    const row=audit.prepare('SELECT * FROM billing_order_resets').get();
    assert.equal(row.order_id,o.id);assert.equal(row.previous_status,'retry');
    assert.equal(row.target_expiry,NOW+31*DAY);
    audit.close();
    billing=createBilling(options(panel,path));fail=false;
    await billing.confirm(o.id);await billing.confirm(o.id);
    assert.equal(data.client.expiryTime,NOW+31*DAY);assert.equal(writes,1);
    await assert.rejects(billing.reset(o.id),{code:'paid_order_cannot_reset'});
  } finally {billing.close();rmSync(dir,{recursive:true,force:true});}
});

test('reset cannot erase an ambiguous applied renewal or externally changed client',async()=>{
  const data=client();let mode='applied';
  const panel={getClient:async()=>data,setExpiry:async(e,b,t)=>{
    if(mode==='applied')data.client.expiryTime=t;
    throw new Error('ambiguous write');
  }};
  const billing=createBilling(options(panel));
  try {
    const o=await billing.create('svk');
    await assert.rejects(billing.confirm(o.id));
    await assert.rejects(billing.reset(o.id),{code:'order_reset_requires_review'});
    assert.equal(billing.latest('svk').status,'retry');
    assert.equal(billing.latest('svk').expiresAt,NOW+31*DAY);
    data.client.expiryTime=NOW+DAY;data.client.id=8;
    await assert.rejects(billing.reset(o.id),{code:'order_reset_requires_review'});
    data.client.id=7;data.client.expiryTime=NOW+2*DAY;
    await assert.rejects(billing.reset(o.id),{code:'order_reset_requires_review'});
    assert.equal(data.client.expiryTime,NOW+2*DAY);
  } finally {billing.close();}
});

test('reset and confirmation share a lease across connections and release it after lookup failure',async()=>{
  const dir=mkdtempSync(join(tmpdir(),'tngl-reset-lock-')),path=join(dir,'billing.sqlite');
  const data=client();let wait=false,unblock,offline=false;
  const barrier=new Promise(r=>unblock=r);
  const panel={getClient:async()=>{
    if(wait)await barrier;
    if(offline)throw new Error('offline');
    return data;
  },setExpiry:async()=>{throw new Error('failure before write');}};
  const first=createBilling(options(panel,path)),second=createBilling(options(panel,path));
  try {
    const o=await first.create('svk');await assert.rejects(first.confirm(o.id));
    wait=true;
    const reset=first.reset(o.id);
    await assert.rejects(first.reset(o.id),{code:'confirmation_in_progress'});
    await assert.rejects(second.confirm(o.id),{code:'confirmation_in_progress'});
    await assert.rejects(second.reset(o.id),{code:'confirmation_in_progress'});
    unblock();await reset;
    wait=false;
    await assert.rejects(first.confirm(o.id));offline=true;
    await assert.rejects(first.reset(o.id));
    assert.equal(first.latest('svk').status,'retry');
    offline=false;
    assert.equal((await second.reset(o.id)).status,'pending');
    await assert.rejects(first.reset('missing'),{code:'order_not_found'});
  } finally {unblock();first.close();second.close();rmSync(dir,{recursive:true,force:true});}
});

test('legacy order references remain compatible with confirm without input',async()=>{
  const dir=mkdtempSync(join(tmpdir(),'tngl-reset-legacy-')),path=join(dir,'billing.sqlite');
  const data=client();let fail=true,writes=0;
  const panel={getClient:async()=>data,setExpiry:async(e,b,t)=>{
    if(fail)throw new Error('not applied');writes++;data.client.expiryTime=t;return {};
  }};
  let billing=createBilling(options(panel,path));
  try {
    const o=await billing.create('svk');await assert.rejects(billing.confirm(o.id));billing.close();
    const db=new DatabaseSync(path);
    db.prepare('UPDATE billing_orders SET reference=? WHERE id=?').run('legacy:receipt-123',o.id);db.close();
    billing=createBilling(options(panel,path));fail=false;
    await billing.confirm(o.id);await billing.confirm(o.id);
    assert.equal(writes,1);assert.equal(billing.list()[0].reference,'legacy:receipt-123');
  } finally {billing.close();rmSync(dir,{recursive:true,force:true});}
});
