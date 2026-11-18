import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createAuth} from './auth.mjs';
import {openWebEmail} from './web-email.mjs';
import {recoverAccount} from './recovery.mjs';

test('recovery verifies ownership before delivery, installs passwords and revokes sessions',async()=>{
 const dir=mkdtempSync(join(tmpdir(),'tngl-recovery-'));
 const auth=createAuth(join(dir,'auth.sqlite'));
 let sent, sends=0, now=100000;
 const email=openWebEmail({database:join(dir,'auth.sqlite'),key:'test-only',clock:()=>now,
   mailer:async(address,code)=>{sent={address,code};sends++;}});
 const call=(operation,data)=>recoverAccount({auth,email,verifyProfile:async id=>assert.equal(id,'member')},operation,data);
 const password='new-password-123', id='member', address='owner@example.com';
 try{
  const inviteCode=auth.invite(id);
  const first={id,inviteCode,email:address};
  await assert.rejects(call('recover',{id:'unknown'}),{status:404,code:'recovery_unavailable'});
  assert.equal(sends,0);
  assert.equal((await call('recover',{id})).step,'activation');
  await assert.rejects(call('recover-first',{...first,inviteCode:'wrong'}),{code:'invalid_credentials'});
  assert.equal(sends,0);
  await call('recover-first',first);
  assert.equal(email.emailFor(id),undefined);
  await call('recover-first',{...first,emailCode:sent.code,password});
  assert.equal(email.emailFor(id),address);
  assert.equal(auth.recoveryState(id,inviteCode).mode,'reset');
  await assert.rejects(call('recover-first',{...first,emailCode:sent.code,password}),{code:'invalid_credentials'});
  const old=await auth.login(id,password);
  await call('recover',{id,email:'other@example.com'});
  assert.equal(sent.address,address);
  await assert.rejects(call('recover',{id}),{code:'email_wait'});
  const wrong=sent.code==='000000'?'000001':'000000';
  await assert.rejects(call('recover',{id,emailCode:wrong,password}),{code:'invalid_email_code'});
  now+=600000;
  await assert.rejects(call('recover',{id,emailCode:sent.code,password}),{code:'email_code_expired'});
  await call('recover',{id});
  const code=sent.code;
  await call('recover',{id,emailCode:code,password:'second-password-123'});
  assert.throws(()=>auth.session(old.token),{code:'session_expired'});
  await assert.rejects(auth.login(id,password),{code:'invalid_credentials'});
  assert((await auth.login(id,'second-password-123')).token);
  await assert.rejects(call('recover',{id,emailCode:code,password}),{code:'invalid_email_code'});
  await auth.activate('legacy',auth.invite('legacy'),password);
  assert.equal((await call('recover',{id:'legacy'})).step,'support');
 }finally{auth.close();email.close();rmSync(dir,{recursive:true,force:true});}
});
