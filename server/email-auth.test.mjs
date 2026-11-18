import test from 'node:test';
import assert from 'node:assert/strict';
import {openWebEmail} from './web-email.mjs';
import {createEmailAuth} from './email-auth.mjs';
import {createAuth} from './auth.mjs';
function fixture(){
 let now=1000000,sent;
 const auth=createAuth(':memory:');
 const email=openWebEmail({database:':memory:',key:'test-only-key',clock:()=>now,
  mailer:async(address,code)=>{sent={address,code};}});
 let provisions=0;
 const flow=createEmailAuth({auth,email,membership:{provision:async()=>{provisions++;}}});
 return {auth,email,flow,code:()=>sent.code,advance:ms=>{now+=ms;},provisions:()=>provisions,
 close(){auth.close();email.close();}};
}
test('signup does not create account or session before code; login and bind share website addresses',async()=>{
 const f=fixture();const data={id:'new-user',password:'test-password-123',email:'NEW@example.com'};
 try{
  assert.equal((await f.flow.register(data)).emailRequired,true);
  assert.equal(f.auth.listAccounts().length,0);assert.equal(f.provisions(),0);
  await assert.rejects(f.flow.register({...data,emailCode:'xxxxxx'}),{code:'invalid_email_code'});
  const session=await f.flow.register({...data,emailCode:f.code()});
  assert.equal(f.provisions(),1);assert.equal(f.email.emailFor('new-user'),'new@example.com');
  assert.equal(f.auth.session(session.token).account,'new-user');
  const before=f.auth.listAccounts()[0].activeSessions;
  assert.equal((await f.flow.login(data)).emailRequired,true);
  assert.equal(f.auth.listAccounts()[0].activeSessions,before);
  await assert.rejects(f.flow.login({...data,emailCode:'xxxxxx'}),{code:'invalid_email_code'});
  const login=await f.flow.login({...data,emailCode:f.code()});
  assert.equal(f.auth.session(login.token).account,'new-user');
  await assert.rejects(f.flow.login({...data,emailCode:f.code()}),{code:'invalid_email_code'});
  const legacy=await f.auth.activate('legacy',f.auth.invite('legacy'),'test-password-123');
  assert((await f.flow.login({id:'legacy',password:'test-password-123'})).token);
  assert.equal((await f.flow.bind(legacy.token,{email:'bind@example.com'})).emailRequired,true);
  assert.equal(f.email.emailFor('legacy'),undefined);
  await f.flow.bind(legacy.token,{email:'bind@example.com',emailCode:f.code()});
  assert.deepEqual(f.flow.address(legacy.token),{email:'bind@example.com'});
  assert.equal((await f.flow.login({id:'legacy',password:'test-password-123'})).emailRequired,true);
 }finally{f.close();}
});
test('expiry, resend cooldown, proof binding and bounded attempts',async()=>{
 const f=fixture(),data={id:'member',password:'test-password-123',email:'a@example.com'};
 try{
  await f.flow.register(data);const old=f.code();
  await assert.rejects(f.flow.register(data),e=>e.code==='email_wait'&&e.retryAfterSeconds===60);
  f.advance(60000);await f.flow.register(data);
  await assert.rejects(f.flow.register({...data,password:'changed-password',emailCode:f.code()}),{code:'invalid_email_code'});
  f.advance(600000);
  await assert.rejects(f.flow.register({...data,emailCode:f.code()}),{code:'email_code_expired'});
  await f.flow.register(data);
  for(let i=0;i<5;i++)await assert.rejects(f.flow.register({...data,emailCode:'bad'}),{code:'invalid_email_code'});
  // Malformed input is rejected without consuming a numeric guess.
  for(let i=0;i<5;i++)await assert.rejects(f.flow.register({...data,emailCode:f.code()==='000000'?'000001':'000000'}),{code:'invalid_email_code'});
  await assert.rejects(f.flow.register({...data,emailCode:f.code()}),{code:'email_attempts_exceeded'});
  assert.equal(f.provisions(),0);
 }finally{f.close();}
});
