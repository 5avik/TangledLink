import { createPanel } from './xui.mjs';
import { createBilling } from './billing.mjs';

process.umask(0o077);
const url = process.env.XUI_URL, token = process.env.XUI_API_TOKEN;
if (Boolean(url) !== Boolean(token)) throw new Error('Set both XUI_URL and XUI_API_TOKEN');
export const panel = url ? createPanel({ url, token }) : null;
const {createMembership}=await import('./membership.mjs');
export const membership = panel && process.env.REGISTRATION_TEMPLATE_EMAIL && process.env.REGISTRATION_INBOUND_IDS ? createMembership({
  path:process.env.AUTH_DB ?? '/var/lib/tngl-auth/auth.sqlite',panel,
  templateEmail:process.env.REGISTRATION_TEMPLATE_EMAIL,
  inboundIds:process.env.REGISTRATION_INBOUND_IDS.split(',').map(Number)
}) : null;
export const billing = panel && process.env.BILLING_INSTRUCTIONS ? createBilling({
  path: process.env.BILLING_DB ?? '/var/lib/tngl-auth/billing.sqlite',
  panel:membership?.billingPanel ?? panel, instructions: process.env.LAVA_API_KEY ? 'Оплатите заказ через lava.top. После оплаты вернитесь в приложение и нажмите «Проверить статус». Продление применяется автоматически.' : process.env.BILLING_INSTRUCTIONS
}) : null;

const { createLava } = await import('./lava.mjs');
export const lava = billing && process.env.LAVA_API_KEY && process.env.LAVA_WEBHOOK_SECRET ? createLava({
  path: process.env.BILLING_DB ?? '/var/lib/tngl-auth/billing.sqlite',
  key: process.env.LAVA_API_KEY, secret: process.env.LAVA_WEBHOOK_SECRET, billing
}) : null;
