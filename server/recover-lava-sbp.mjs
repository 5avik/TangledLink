// Recovery for an ambiguous migration; no invoice is created, no money is charged.
// Exact buyer/price/product match and a unique unknown recent invoice are mandatory.
import {readFileSync} from 'node:fs';
import {parseEnv} from 'node:util';
import {DatabaseSync} from 'node:sqlite';
const env=parseEnv(readFileSync('/etc/tngl-auth.env','utf8'));
const db=new DatabaseSync(env.BILLING_DB??'/var/lib/tngl-auth/billing.sqlite');
async function api(path) {
 const r=await fetch('https://gate.lava.top'+path,{headers:{'X-Api-Key':env.LAVA_API_KEY},
  redirect:'error',signal:AbortSignal.timeout(30000)});
 if(!r.ok){const error=await r.json(); console.log(JSON.stringify({http:r.status,error:String(error.error??'').replaceAll(env.LAVA_API_KEY,'[key]').replace(/[^\\s@]+@[^\\s@]+/g,'[email]'),fields:Object.keys(error.details??{})}));throw new Error('Lava HTTP '+r.status);}
 return r.json();
}
const rows=db.prepare("SELECT * FROM lava_invoices WHERE state='review' AND payment_method='legacy'").all();
for(const row of rows){
 const query=new URLSearchParams({buyerEmail:row.email,beginDate:new Date(Date.now()-30*60000).toISOString(),
  size:'100'});
 for(const status of ['NEW','IN_PROGRESS','COMPLETED','FAILED'])query.append('invoiceStatuses',status);
 const page=await api('/api/v1/invoices?'+query);
 if(page.total>100)throw new Error('Too many candidates');
 const candidates=(page.items??[]).filter(x=>x.buyer?.email===row.email&&x.receipt?.amount*100===row.amount&&
  x.receipt?.currency==='RUB'&&x.type==='ONE_TIME'&&x.product?.name==='TangledLink Pro'&&
  !db.prepare('SELECT 1 FROM lava_invoices WHERE invoice_id=? UNION ALL SELECT 1 FROM lava_previous_invoices WHERE invoice_id=?').get(x.id,x.id));
 console.log('Unique recovery candidates: '+candidates.length);
 if(candidates.length!==1)continue;
 const candidate=candidates[0];
 const payment=await api('/api/v1/invoice?id='+encodeURIComponent(candidate.id));
 if(payment.id!==candidate.id||payment.amountTotal?.amount*100!==row.amount||payment.amountTotal?.currency!=='RUB')
   throw new Error('Invoice mismatch');
 console.log(JSON.stringify({candidateStatus:candidate.status,responseKeys:Object.keys(payment),status:payment.status}));
 if(!payment.paymentUrl){
  if(process.argv.includes('--apply')){
   db.exec('BEGIN IMMEDIATE');
   try{
    db.prepare("INSERT OR IGNORE INTO lava_previous_invoices(invoice_id,order_id,email,amount,state) VALUES(?,?,?,?,'ready')")
     .run(candidate.id,row.order_id,row.email,row.amount);
    db.prepare("UPDATE lava_invoices SET state='ready',last_error='recovered_without_link' WHERE order_id=? AND state='review'").run(row.order_id);
    db.exec('COMMIT');
   }catch(e){db.exec('ROLLBACK');throw e;}
   console.log('Recovered ambiguous invoice without checkout URL; preserved for reconciliation.');
  }
  continue;
 }
 const url=new URL(payment.paymentUrl);
 if(url.protocol!=='https:'||url.username||url.password)throw new Error('Unexpected checkout URL');
 console.log('Verified recovery payment link: '+Boolean(payment.paymentUrl));
 if(process.argv.includes('--apply')){
  db.prepare("UPDATE lava_invoices SET invoice_id=?,payment_url=?,payment_method='SBP',state='ready',last_error=NULL WHERE order_id=? AND state='review'")
    .run(candidate.id,url.href,row.order_id);
  console.log('Recovered SBP invoice mapping.');
 }
}
db.close();
