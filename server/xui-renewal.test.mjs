import test from 'node:test';
import assert from 'node:assert/strict';
import { createPanel, renewalPayload } from './xui.mjs';
import { createBilling } from './billing.mjs';

const NOW=1800000000000, DAY=86400000;
const peerFields=['privateKey','publicKey','preSharedKey','allowedIPs','allowedIPsByInbound','keepAlive','forwardedPorts'];
const makeRecord=()=>({id:7,email:'svk',uuid:'keep-uuid',subId:'keep-sub-id',
  password:'keep-password',auth:'keep-auth',enable:true,totalGB:987654321,expiryTime:NOW+DAY,
  limitHwid:3,limitIp:4,flow:'',reverse:null,group:'keep-group',comment:'keep-comment',
  reset:0,resetDay:0,resetWeekday:0,resetMax:3,trafficReset:'weekly',trafficResetDay:2,
  clientHostRuleIds:[4,5],createdAt:123,updatedAt:456,
  privateKey:'global-private',publicKey:'global-public',preSharedKey:'global-psk',
  allowedIPs:'10.0.0.2/32',keepAlive:'10-30',forwardedPorts:'8000-8010'});
const makePeer=(protocol,number)=>({protocol,client:{email:'svk',id:'keep-uuid',
  privateKey:'private-'+number,publicKey:'public-'+number,preSharedKey:'psk-'+number,
  allowedIPs:['10.'+number+'.0.2/32','fd00:'+number+'::2/128'],keepAlive:number===1?25:'10-30',
  forwardedPorts:protocol==='amneziawg'?'8000-8010':'',expiryTime:NOW+DAY}});

for(const protocols of [['wireguard'],['amneziawg'],['wireguard','amneziawg','amneziawg']]){
  test('renewal keeps independent peer settings for '+protocols.join('+'),async()=>{
    const record=makeRecord(),peers=protocols.map((p,i)=>makePeer(p,i+1));
    const originalPeers=structuredClone(peers),originalRecord=structuredClone(record);
    let writes=0;
    const panel=createPanel({url:'https://panel.invalid/base/',token:'test-key',fetcher:async(url,init)=>{
      assert.equal(url.pathname,'/base/panel/api/clients/'+(init.method==='POST'?'update/':'get/')+'svk');
      if(init.method==='GET')return Response.json({success:true,obj:{
        client:record,usedTraffic:123,inboundIds:peers.map((_,i)=>i+1)}});
      writes++;const payload=JSON.parse(init.body);
      for(const field of peerFields)assert.equal(Object.hasOwn(payload,field),false,field+' must be preserved by the panel');
      assert.equal(payload.id,'keep-uuid');assert.equal(payload.subId,'keep-sub-id');
      assert.equal(payload.password,'keep-password');assert.equal(payload.auth,'keep-auth');
      assert.equal(payload.totalGB,record.totalGB);assert.equal(payload.limitHwid,3);
      assert.deepEqual(payload.clientHostRuleIds,[4,5]);
      // 3.11.0 UpdateInboundClient fills omitted fields from each stored peer,
      // rather than applying one global key/address to every tunnel.
      for(const peer of peers){
        for(const field of peerFields)if(Object.hasOwn(payload,field))peer.client[field]=payload[field];
        peer.client.expiryTime=payload.expiryTime;
      }
      record.expiryTime=payload.expiryTime;
      return Response.json({success:true,obj:{nodePending:true}});
    }});
    const billing=createBilling({path:':memory:',panel,instructions:'Test',clock:()=>NOW});
    try {
      const order=await billing.create('svk'),paid=await billing.confirm(order.id);
      assert.equal(paid.status,'paid');assert.equal(paid.pendingSync,true);
      assert.equal(paid.expiresAt,NOW+31*DAY);
      for(let i=0;i<peers.length;i++)assert.deepEqual(peers[i],{
        ...originalPeers[i],client:{...originalPeers[i].client,expiryTime:NOW+31*DAY}});
      assert.deepEqual(record,{...originalRecord,expiryTime:NOW+31*DAY});
      await billing.confirm(order.id);assert.equal(writes,1);
    } finally {billing.close();}
  });
}
test('payload omits per-inbound override maps and never mutates its source',()=>{
  const record={...makeRecord(),allowedIPsByInbound:{1:['10.9.0.2/32']}};
  const before=structuredClone(record),payload=renewalPayload(record,NOW+31*DAY);
  for(const field of peerFields)assert.equal(Object.hasOwn(payload,field),false);
  assert.deepEqual(record,before);
  assert.equal(payload.expiryTime,NOW+31*DAY);assert.equal(payload.created_at,123);
  assert.equal(Object.hasOwn(payload,'uuid'),false);
  assert.throws(()=>renewalPayload({...record,uuid:7},NOW+31*DAY),{code:'panel_client_mismatch'});
});
test('failed remote renewal with tunnel fields retries the same absolute date',async()=>{
  const record=makeRecord();let failed=true;const targets=[];
  const panel=createPanel({url:'https://panel.invalid/',token:'test',fetcher:async(url,init)=>{
    if(init.method==='GET')return Response.json({success:true,obj:{client:record,usedTraffic:0,inboundIds:[1,2]}});
    const payload=JSON.parse(init.body);targets.push(payload.expiryTime);record.expiryTime=payload.expiryTime;
    if(failed){failed=false;throw new Error('timeout after write');}
    return Response.json({success:true,obj:{}});
  }});
  const billing=createBilling({path:':memory:',panel,instructions:'Test',clock:()=>NOW});
  try {
    const order=await billing.create('svk');await assert.rejects(billing.confirm(order.id),{code:'panel_unavailable'});
    assert.equal(billing.latest('svk').status,'retry');
    const paid=await billing.confirm(order.id);assert.equal(paid.status,'paid');
    assert.deepEqual(targets,[NOW+31*DAY,NOW+31*DAY]);
    assert.equal(record.privateKey,'global-private');assert.equal(record.allowedIPs,'10.0.0.2/32');
  } finally {billing.close();}
});
