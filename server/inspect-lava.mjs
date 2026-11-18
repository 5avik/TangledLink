// Read-only diagnostics. Never prints invoice IDs, URLs, emails or credentials.
import {readFileSync} from 'node:fs';
import {parseEnv} from 'node:util';
import {DatabaseSync} from 'node:sqlite';
const env=parseEnv(readFileSync('/etc/tngl-auth.env','utf8'));
const db=new DatabaseSync(env.BILLING_DB??'/var/lib/tngl-auth/billing.sqlite',{readOnly:true});
for(const row of db.prepare("SELECT invoice_id,email,amount,payment_method FROM lava_invoices WHERE state='ready'").all()){
 const r=await fetch('https://gate.lava.top/api/v1/invoices/'+row.invoice_id,{
 headers:{'X-Api-Key':env.LAVA_API_KEY},redirect:'error',signal:AbortSignal.timeout(7000)});
 const data=await r.json();
 console.log(JSON.stringify({http:r.status,keys:Object.keys(data),status:data.status,type:data.type,
  idMatches:data.id===row.invoice_id,amountMatches:data.receipt?.amount*100===row.amount,
  currency:data.receipt?.currency,emailMatches:data.buyer?.email===row.email,method:row.payment_method}));
}
db.close();
