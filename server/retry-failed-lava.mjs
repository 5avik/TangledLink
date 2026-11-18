// Explicit operator recovery: one account, existing pending order, no charge.
// resume verifies FAILED through Lava before creating a replacement SBP invoice.
import {readFileSync} from 'node:fs';
import {parseEnv} from 'node:util';
import {DatabaseSync} from 'node:sqlite';
Object.assign(process.env,parseEnv(readFileSync('/etc/tngl-auth.env','utf8')));
const account=process.argv[2];
if (!account || !process.argv.includes('--apply')) throw new Error('Usage: retry-failed-lava.mjs ACCOUNT --apply');
const {billing,lava}=await import('./billing-runtime.mjs');
if (!billing || !lava) throw new Error('Billing unavailable');
try {
 const order=billing.latest(account);
 if (!order || order.status!=='pending') throw new Error('No pending order');
 const result=await lava.resume(account,order.id);
 const db=new DatabaseSync(process.env.BILLING_DB??'/var/lib/tngl-auth/billing.sqlite',{readOnly:true});
 const row=db.prepare('SELECT invoice_id FROM lava_invoices WHERE order_id=?').get(order.id);
 db.close();
 const response=await fetch('https://gate.lava.top/api/v1/invoices/'+row.invoice_id,{
  headers:{'X-Api-Key':process.env.LAVA_API_KEY},signal:AbortSignal.timeout(12000)});
 if(!response.ok) throw new Error('Lava verification HTTP '+response.status);
 const invoice=await response.json();
 console.log(JSON.stringify({orderStatus:result.status,checkoutReady:Boolean(result.paymentUrl),
  providerStatus:invoice.status,amount:invoice.receipt?.amount,currency:invoice.receipt?.currency}));
} finally {lava.close();billing.close();}
