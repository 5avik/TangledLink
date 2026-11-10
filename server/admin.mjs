import { createAuth } from './auth.mjs';
import { subscription } from './upstream.mjs';

process.umask(0o077);
const [command, id] = process.argv.slice(2);
if (!['invite', 'reset'].includes(command) || !id || !process.env.SUBSCRIPTION_BASE) {
  console.error('Usage: node --env-file=/etc/tngl-auth.env admin.mjs invite|reset SUB_ID');
  process.exit(1);
}
const auth = createAuth(process.env.AUTH_DB ?? '/var/lib/tngl-auth/auth.sqlite');
try {
  await subscription(process.env.SUBSCRIPTION_BASE, id);
  const code = auth.invite(id, command === 'reset');
  console.log('One-time activation code (valid 24h): ' + code);
} catch (error) {
  console.error(error.code ?? 'Operation failed');
  process.exitCode = 1;
} finally { auth.close(); }
