// Run on the deployment host as root. Never logs credentials or buyer data.
import { readFileSync, appendFileSync, chmodSync } from 'node:fs';
import { parseEnv } from 'node:util';
import { randomBytes } from 'node:crypto';

const envPath = '/etc/tngl-auth.env';
const env = parseEnv(readFileSync(envPath, 'utf8'));
const key = env.LAVA_API_KEY;
if (process.argv.includes('--prepare-secret')) {
  if (!env.LAVA_WEBHOOK_SECRET) {
    appendFileSync(envPath, '\nLAVA_WEBHOOK_SECRET=' + randomBytes(32).toString('hex') + '\n');
    chmodSync(envPath, 0o600);
    console.log('Webhook secret saved securely.');
  } else {
    console.log('Existing webhook secret preserved.');
  }
}
console.log('LAVA_API_KEY configured: ' + Boolean(key));
if (!key) process.exit(2);
let next = new URL('https://gate.lava.top/api/v2/products?feedVisibility=ALL&showAllSubscriptionPeriods=true');
let product;
for (let page = 0; next && page < 20; page++) {
  if (next.origin !== 'https://gate.lava.top' || next.pathname !== '/api/v2/products')
    throw new Error('Unexpected pagination URL');
  const response = await fetch(next, {
    headers: { 'X-Api-Key': key, Accept: 'application/json' },
    redirect: 'error', signal: AbortSignal.timeout(15000)
  });
  if (!response.ok) { console.log('Lava API HTTP ' + response.status); process.exit(3); }
  const data = await response.json();
  console.log('Product page keys: ' + Object.keys(data).join(', '));
  console.log('Products: ' + JSON.stringify(data.items?.map(item => ({ type: item.type, id: item.data?.id, title: item.data?.title, keys: Object.keys(item) })) ?? []));
  product = data.items?.map(item => item.data ?? item).find(item =>
    item?.id === '0cee2edb-1432-4c9a-a239-592d51538413');
  if (product) break;
  next = data.nextPage ? new URL(data.nextPage, next) : null;
}
if (!product) { console.log('Requested product not found in API products.'); process.exit(4); }
// Product definitions only; no invoices, buyer data or secrets are requested.
console.log(JSON.stringify(product, null, 2));
