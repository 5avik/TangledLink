import test from 'node:test';
import assert from 'node:assert/strict';
import {createAuth,ApiError} from './auth.mjs';
import {createMembership} from './membership.mjs';
import {createBilling} from './billing.mjs';

test('registration reserves credentials, rejects collisions and resumes ambiguous provisioning',async()=>{
 const auth=createAuth(':memory:');let firstKeys,calls=0;
 try {
  for(const id of ['SVK','ab','x@y','../abc'])await assert.rejects(auth.register(id,'test-password-123',()=>{}),{code:'registration_invalid_id'});
  await assert.rejects(auth.register('new-user','short',()=>{}),{code:'password_policy'});
  auth.invite('Legacy');
  await assert.rejects(auth.register('legacy','test-password-123',()=>assert.fail()),{code:'account_exists'});
  await assert.rejects(auth.register('new-user','test-password-123',async(id,keys)=>{
   firstKeys=keys;calls++;throw new ApiError(503,'panel_unavailable');
  }),{code:'panel_unavailable'});
  await assert.rejects(auth.login('new-user','test-password-123'),{code:'registration_pending'});
  await assert.rejects(auth.register('new-user','other-password-123',()=>assert.fail()),{code:'account_exists'});
  const session=await auth.register('new-user','test-password-123',async(id,keys)=>{
   assert.equal(id,'new-user');assert.deepEqual(keys,firstKeys);calls++;
  });
  assert.equal(auth.session(session.token).account,'new-user');assert.equal(calls,2);
  assert.equal(auth.session((await auth.login('new-user','test-password-123')).token).account,'new-user');
  await assert.rejects(auth.register('new-user','test-password-123',()=>assert.fail()),{code:'account_exists'});
 }finally{auth.close();}
});

function fixture(){
 let now=1000000000,fail=false;
 const clients=new Map(), grants=[];
 const template={client:{id:99,email:'template',enable:true,expiryTime:2000000000,totalGB:0,limitIp:3,limitHwid:2},inboundIds:[1,2,3,4,5,6,7,8,9,10],usedTraffic:0};
 const panel={
  async getClient(id){return structuredClone(id==='template'?template:clients.get(id));},
  async createDetached(id,keys,limits){
   if(!clients.has(id))clients.set(id,{client:{id:1,email:id,uuid:keys.uuid,subId:keys.subId,enable:false,expiryTime:1,...limits},inboundIds:[],usedTraffic:0});
   return this.getClient(id);
  },
  async grant(id,expected,target,record,ids){
   const c=clients.get(id);assert.equal(c.client.id,record);
   if(![expected,target].includes(c.client.expiryTime))throw new ApiError(409,'panel_client_changed');
   grants.push(target);c.client.expiryTime=target;
   if(fail){fail=false;throw new ApiError(503,'panel_unavailable');}
   c.inboundIds=[...ids];c.client.enable=true;return {pendingSync:false};
  },
  async setExpiry(){assert.fail('Managed accounts must use grant');}
 };
 const service=createMembership({path:':memory:',panel,templateEmail:'template',clock:()=>now});
 return {service,clients,grants,advance(){now+=100000},fail(){fail=true},now:()=>now};
}

test('dormant registration, one trial, fixed target retry, paid renewal and collision protection',async()=>{
 const f=fixture(),s=f.service;
 try{
  await s.provision('new-user',{uuid:'new-uuid',subId:'new-sub'});
  assert.equal(f.clients.get('new-user').inboundIds.length,0);
  assert.deepEqual(await s.status('new-user'),{managed:true,subscribed:false,trialAvailable:true,trialUsed:false,expiresAt:null});
  f.fail();
  await assert.rejects(s.startTrial('new-user'),{code:'panel_unavailable'});
  const target=f.grants[0];f.advance();
  const state=await s.startTrial('new-user');
  assert.equal(state.subscribed,true);assert.equal(state.trialAvailable,false);
  assert.deepEqual(f.grants,[target,target]);assert.equal(f.clients.get('new-user').inboundIds.length,10);
  await assert.rejects(s.startTrial('new-user'),{code:'trial_unavailable'});
  const current=await s.billingPanel.prepareRenewal('new-user');
  await s.billingPanel.setExpiry('new-user',current.client.expiryTime,target+86400000,1);
  assert.equal(f.clients.get('new-user').client.expiryTime,target+86400000);
  f.clients.get('new-user').client.uuid='changed';
  await assert.rejects(s.status('new-user'),{code:'panel_client_mismatch'});
 }finally{s.close();}
});

test('billing creates and applies a first paid order for a disabled detached client',async()=>{
 const f=fixture(),s=f.service;
 const billing=createBilling({path:':memory:',panel:s.billingPanel,instructions:'Test',clock:f.now});
 try{
  await s.provision('first-paid',{uuid:'paid-uuid',subId:'paid-sub'});
  assert.equal(f.clients.get('first-paid').client.enable,false);
  const order=await billing.create('first-paid');
  const paid=await billing.confirm(order.id);
  assert.equal(paid.status,'paid');assert.equal(paid.expiresAt,f.now()+30*86400000);
  assert.equal((await s.status('first-paid')).subscribed,true);
  await billing.confirm(order.id);assert.equal(f.grants.length,1);
 }finally{billing.close();s.close();}
});

test('first paid subscription attaches servers and prevents a later free trial',async()=>{
 const f=fixture(),s=f.service;
 try{
  await s.provision('paid-user',{uuid:'own-uuid',subId:'own-sub'});
  const data=await s.billingPanel.prepareRenewal('paid-user');
  await assert.rejects(s.startTrial('paid-user'),{code:'trial_unavailable'});
  await s.billingPanel.setExpiry('paid-user',data.client.expiryTime,f.now()+30*86400000,1);
  assert.equal((await s.status('paid-user')).subscribed,true);
  assert.equal((await s.status('legacy')).managed,false);
 }finally{s.close();}
});
