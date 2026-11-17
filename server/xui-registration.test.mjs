import test from 'node:test';
import assert from 'node:assert/strict';
import {createPanel} from './xui.mjs';

test('detached creation uses independent credentials, no inbound and no unlimited expiry',async()=>{
 let record=null,attached=[],writes=0;
 const panel=createPanel({url:'https://panel.invalid/base/',token:'test',fetcher:async(url,init)=>{
  const route=url.pathname.split('/clients/')[1];
  if(route==='list')return Response.json({success:true,obj:record?[record]:[]});
  if(route==='add'){
   const payload=JSON.parse(init.body);writes++;
   assert.deepEqual(payload.inboundIds,[2]);assert.equal(payload.client.enable,false);attached=[2];assert.equal(payload.client.expiryTime,1);
   assert.equal(payload.client.id,'own-uuid');assert.equal(payload.client.subId,'own-sub');
   assert.equal(payload.client.privateKey,undefined);
   record={...payload.client,id:123,uuid:payload.client.id};delete record.privateKey;
   return Response.json({success:true});
  }
  if(route==='get/new-user')return Response.json({success:true,obj:{client:record,inboundIds:attached.length?attached:null,usedTraffic:0}});
  if(route==='new-user/detach'){attached=[];return Response.json({success:true});}
  if(route==='update/new-user'){const {id,...updated}=JSON.parse(init.body);Object.assign(record,updated);return Response.json({success:true});}
  if(route==='new-user/attach'){attached=JSON.parse(init.body).inboundIds;return Response.json({success:true,obj:{nodePending:true}});}
  assert.fail(route);
 }});
 const keys={uuid:'own-uuid',subId:'own-sub'},limits={totalGB:0,limitIp:3,limitHwid:0};
 await panel.createDetached('new-user',keys,limits,2);
 await panel.createDetached('new-user',keys,limits,2);assert.equal(writes,1);
 await assert.rejects(panel.getClient('new-user'),{code:'panel_client_mismatch'});
 const granted=await panel.grant('new-user',1,1800000000000,123,[1,2,3]);
 assert.equal(granted.pendingSync,true);assert.deepEqual(attached,[1,2,3]);
 await assert.rejects(panel.createDetached('new-user',{uuid:'other',subId:'other'},limits,2),{code:'account_exists'});
 await assert.rejects(panel.grant('new-user',1,1900000000000,123,[1]),{code:'panel_client_changed'});
});

test('existing panel email is never claimed by a new registration',async()=>{
 const panel=createPanel({url:'https://panel.invalid/',token:'test',fetcher:async(url,init)=>{
  assert.equal(init.method,'GET');
  return Response.json({success:true,obj:[{email:'Member',uuid:'old',subId:'old'}]});
 }});
 await assert.rejects(panel.createDetached('member',{uuid:'new',subId:'new'},{totalGB:0,limitIp:0,limitHwid:0},2),{code:'account_exists'});
});
