import test from 'node:test';
import assert from 'node:assert/strict';
import {createAuth, ApiError} from './auth.mjs';

process.env.SUBSCRIPTION_BASE='https://example.invalid/sub/';
process.env.AUTH_DB=':memory:';
for(const key of ['XUI_URL','XUI_API_TOKEN','BILLING_INSTRUCTIONS','LAVA_API_KEY','LAVA_WEBHOOK_SECRET','WEB_BILLING_KEY'])delete process.env[key];
const {createApiServer}=await import('./server.mjs');
const password='test-password-long';
async function listen(api){
 await new Promise(resolve=>api.listen(0,'127.0.0.1',resolve));
 return 'http://127.0.0.1:'+api.address().port;
}
const post=(root,path,body)=>fetch(root+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});

test('activated account login survives panel outage, while config access still fails closed',async()=>{
 const auth=createAuth(':memory:');
 await auth.activate('member',auth.invite('member'),password);
 auth.invite('pending-member');
 let panelCalls=0;
 const panel={getClient:async()=>{panelCalls++;throw new ApiError(503,'panel_unavailable');}};
 const api=createApiServer({emailStore:{emailFor:()=>null},authStore:auth,panelClient:panel,billingStore:null,lavaStore:null,webBillingStore:null,
  subscriptionReader:()=>assert.fail('Subscription must not bypass a failed panel lookup')});
 const root=await listen(api);
 try{
  const response=await post(root,'/v1/login',{id:'member',password});
  assert.equal(response.status,200);
  const session=await response.json();
  assert.equal(auth.session(session.token).account,'member');
  assert.equal(panelCalls,0);
  const config=await fetch(root+'/v1/subscription',{headers:{Authorization:'Bearer '+session.token}});
  assert.equal(config.status,503);
  assert.equal((await config.json()).error,'panel_unavailable');
  assert.equal(panelCalls,1);
  for(const [id,pw] of [['member','wrong-password'],['unknown',password],['pending-member',password]]){
   const denied=await post(root,'/v1/login',{id,password:pw});
   assert.equal(denied.status,401);
   assert.equal((await denied.json()).error,'invalid_credentials');
  }
  assert.equal(panelCalls,1);
 }finally{await new Promise(resolve=>api.close(resolve));auth.close();}
});

test('first activation still requires a verified panel client and can retry after outage',async()=>{
 const auth=createAuth(':memory:');
 const code=auth.invite('new-member');
 let unavailable=true,calls=0;
 const api=createApiServer({emailStore:{emailFor:()=>null},authStore:auth,billingStore:null,lavaStore:null,webBillingStore:null,panelClient:{
  getClient:async id=>{assert.equal(id,'new-member');calls++;if(unavailable)throw new ApiError(503,'panel_unavailable');return {};}
 }});
 const root=await listen(api);
 try{
  const blocked=await post(root,'/v1/activate',{id:'new-member',code,password});
  assert.equal(blocked.status,503);
  assert.equal(auth.listAccounts()[0].activated,false);
  assert.equal(calls,1);
  const login=await post(root,'/v1/login',{id:'new-member',password});
  assert.equal(login.status,401);
  assert.equal(calls,1);
  unavailable=false;
  const activated=await post(root,'/v1/activate',{id:'new-member',code,password});
  assert.equal(activated.status,200);
  assert.equal(auth.session((await activated.json()).token).account,'new-member');
  assert.equal(calls,2);
 }finally{await new Promise(resolve=>api.close(resolve));auth.close();}
});
