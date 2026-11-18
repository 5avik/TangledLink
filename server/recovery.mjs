import {ApiError,validId} from './auth.mjs';
import {emailRequired} from './email-auth.mjs';

export async function recoverAccount({auth,email,verifyProfile},operation,data) {
  const {id,inviteCode,emailCode,password}=data;
  if(!validId(id)) throw new ApiError(400,'invalid_id');
  const first=operation==='recover-first';
  const state=auth.recoveryState(id,inviteCode);
  if(!first && state?.mode!=='reset' && emailCode===undefined) {
    if(state?.mode==='activate') return {step:'activation'};
    if(auth.hasRecoveryPassword(id)) return {step:'support'};
    throw new ApiError(404,'recovery_unavailable');
  }
  if(!state || (first ? state.mode!=='activate' || !state.inviteValid : state.mode!=='reset'))
    throw new ApiError(401,'invalid_credentials');
  if(emailCode!==undefined && !/^[0-9]{6}$/.test(typeof emailCode==='string'?emailCode:''))
    throw new ApiError(400,'invalid_email_code');
  const version=state.version;
  const address=state.email;
  const recipient=first?email.normalizeEmail(data.email):address;
  if(first)await verifyProfile(id);
  const challenge=()=>email.challenge(first?'first-password':'recover-password',id,recipient,
    JSON.stringify([version,first?inviteCode:null]),emailCode);
  if(emailCode===undefined) {
    await challenge();
    return {step:'code',...emailRequired()};
  }
  await auth.recover(id,state,recipient,password,inviteCode,challenge);
  return {ok:true};
}
