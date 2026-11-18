import test from 'node:test';
import assert from 'node:assert/strict';
import {createAuth} from './auth.mjs';
import {openWebEmail} from './web-email.mjs';
process.env.SUBSCRIPTION_BASE='https://example.invalid/sub/';
process.env.AUTH_DB=':memory:';
for(const k of ['XUI_URL','XUI_API_TOKEN','LAVA_API_KEY','WEB_BILLING_KEY','CREDENTIALS_DIRECTORY'])delete process.env[k];
const {createApiServer}=await import('./server.mjs');
test('legacy and mobile login cannot bypass codes; web key stays server-side',async()=>{
 const auth=createAuth(':memory:');let code;
 const email=openWebEmail({database:':memory:',key:'test-only',mailer:async(_,value)=>{code=value;}});
 const api=createApiServer({authStore:auth,emailStore:email,billingStore:null,panelClient:null,subscriptionReader:async()=>({}),membershipStore:null,lavaStore:null,
  webBillingStore:{authenticate:key=>{if(key!=='test-web-key')throw Object.assign(new Error(),{status:401});}}});
 await auth.activate('bound',auth.invite('bound'),'test-password-123');email.bind('bound','bound@example.com');
 const baseline=auth.listAccounts()[0].activeSessions;
 await new Promise(r=>api.listen(0,'127.0.0.1',r));
 const base='http://127.0.0.1:'+api.address().port;
 const post=(path,data,headers={})=>fetch(base+path,{method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify(data)});
 try{
  const body={id:'bound',password:'test-password-123'};
  const first=await post('/v1/login',body);assert.equal(first.status,200);assert.equal((await first.json()).emailRequired,true);
  assert.equal(auth.listAccounts()[0].activeSessions,baseline);
  const bad=await post('/v1/mobile-auth/login',{...body,emailCode:'bad'});
  assert.equal(bad.status,400);assert.equal(auth.listAccounts()[0].activeSessions,baseline);
  const valid=await post('/v1/mobile-auth/login',{...body,emailCode:code});
  assert.equal(valid.status,200);const session=await valid.json();assert(session.token);
  assert.equal(auth.session(session.token).account,'bound');
  const address=await fetch(base+'/v1/mobile-auth/email',{headers:{Authorization:'Bearer '+session.token}});
  assert.deepEqual(await address.json(),{email:'bound@example.com'});
  const web=await post('/v1/web-auth/login',body);
  assert.notEqual(web.status,200);
  const invite=auth.invite('bound',true);
  const activated=await post('/v1/activate',{...body,code:invite});
  assert.equal((await activated.json()).emailRequired,true);
  assert.equal(auth.listAccounts()[0].activeSessions,0);
  const confirmed=await post('/v1/activate',{...body,code:invite,emailCode:code});
  assert.equal(confirmed.status,200);assert((await confirmed.json()).token);
 }finally{await new Promise(r=>api.close(r));auth.close();email.close();}
});
