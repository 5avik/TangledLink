import { createPanel } from './xui.mjs';
import { createBilling } from './billing.mjs';

process.umask(0o077);
const url = process.env.XUI_URL, token = process.env.XUI_API_TOKEN;
if (Boolean(url) !== Boolean(token)) throw new Error('Set both XUI_URL and XUI_API_TOKEN');
export const panel = url ? createPanel({ url, token }) : null;
export const billing = panel && process.env.BILLING_INSTRUCTIONS ? createBilling({
  path: process.env.BILLING_DB ?? '/var/lib/tngl-auth/billing.sqlite',
  panel, instructions: process.env.BILLING_INSTRUCTIONS
}) : null;
