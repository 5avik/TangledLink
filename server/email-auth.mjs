import {ApiError} from './auth.mjs';
export const emailRequired=()=>({emailRequired:true,retryAfterSeconds:60,expiresInSeconds:600});
export function createEmailAuth({auth,email,membership}) {
 const need=()=>new ApiError(202,'email_required');
 return {
  async register(data,{web=false}={}) {
   if(!membership)throw new ApiError(503,'registration_unavailable');
   if(typeof data.id!=='string'||!/^[a-z0-9_-]{3,32}$/.test(data.id))throw new ApiError(400,'registration_invalid_id');
   if(typeof data.password!=='string'||data.password.length<12||data.password.length>128)throw new ApiError(400,'password_policy');
   const address=email.normalizeEmail(data.email);
   if(!await email.challenge('register',data.id,address,data.password,data.emailCode))return emailRequired();
   const session=await auth.register(data.id,data.password,async(id,keys)=>{
    await membership.provision(id,keys);
    email.bind(id,address);
   });
   if(web){auth.logout(session.token);return {ok:true};}
   return session;
  },
  async verifyLogin(id,password,code) {
   const address=email.emailFor(id);
   if(address && !await email.challenge('login',id,address,password,code))throw need();
  },
  async login(data) {
   try {return await auth.login(data.id,data.password,()=>this.verifyLogin(data.id,data.password,data.emailCode));}
   catch(e){if(e.code==='email_required')return emailRequired();throw e;}
  },
  async bind(token,data) {
   const id=auth.session(token).account;
   if(email.emailFor(id))throw new ApiError(409,'email_already_bound');
   const address=email.normalizeEmail(data.email);
   if(!await email.challenge('bind',id,address,token,data.emailCode))return emailRequired();
   email.bind(id,address);
   return {ok:true};
  },
  address(token){return {email:email.emailFor(auth.session(token).account)??null};}
 };
}
