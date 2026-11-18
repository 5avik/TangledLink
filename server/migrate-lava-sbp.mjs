// Run once on deployment host with --env-file=/etc/tngl-auth.env.
// Emits counts only; does not pay invoices or print buyer information.
const {billing,lava}=await import('./billing-runtime.mjs');
if(!lava)throw new Error('Lava is not configured');
try {
 console.log(JSON.stringify(await lava.upgradeLegacy()));
} catch(e) {
 console.error(e.code ?? 'sbp_migration_failed');
 process.exitCode=1;
} finally {lava.close();billing.close();}
