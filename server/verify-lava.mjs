// Deployment smoke test: no payment is created and no subscription is changed.
import {readFileSync} from 'node:fs';
import {parseEnv} from 'node:util';
const env=parseEnv(readFileSync('/etc/tngl-auth.env','utf8'));
const url='https://auth.tngl.pro:8443/v1/billing/webhooks/lava';
for (const authorized of [false,true]) {
 const response=await fetch(url,{method:'POST',redirect:'error',signal:AbortSignal.timeout(10000),
   headers:{'Content-Type':'application/json',...(authorized?{'X-Api-Key':env.LAVA_WEBHOOK_SECRET}:{})},
   body:JSON.stringify({eventType:'tngl.integration.healthcheck'})});
 console.log((authorized?'Authenticated webhook':'Unauthenticated webhook')+': HTTP '+response.status);
 if(response.status!==(authorized?200:401))process.exitCode=1;
}
