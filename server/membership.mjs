import { DatabaseSync } from 'node:sqlite';
import {randomBytes} from 'node:crypto';
import {ApiError,validId} from './auth.mjs';

// Owned accounts only. Existing panel accounts keep the legacy renewal path.
export function createMembership({path,panel,templateEmail,inboundIds=null,clock=Date.now}) {
  if (!panel || !validId(templateEmail)) throw new Error('REGISTRATION_TEMPLATE_EMAIL is required');
  const db=new DatabaseSync(path);
  db.exec(`PRAGMA busy_timeout=5000; PRAGMA journal_mode=WAL;
    CREATE TABLE IF NOT EXISTS memberships(
      account TEXT PRIMARY KEY, client_id INTEGER NOT NULL, uuid TEXT NOT NULL,
      sub_id TEXT NOT NULL, trial_target INTEGER, trial_done INTEGER NOT NULL DEFAULT 0,
      paid_started INTEGER NOT NULL DEFAULT 0);
    CREATE TABLE IF NOT EXISTS membership_leases(account TEXT PRIMARY KEY, token TEXT, until_ms INTEGER);`);
  const row=id=>db.prepare('SELECT * FROM memberships WHERE account=?').get(id);
  async function locked(id,work) {
    const token=randomBytes(16).toString('hex');
    const lease=db.prepare(`INSERT INTO membership_leases VALUES(?,?,?)
      ON CONFLICT(account) DO UPDATE SET token=excluded.token,until_ms=excluded.until_ms
      WHERE membership_leases.until_ms<=?`).run(id,token,clock()+300000,clock());
    if (!lease.changes) throw new ApiError(409,'membership_busy');
    try {return await work();}
    finally {db.prepare('DELETE FROM membership_leases WHERE account=? AND token=?').run(id,token);}
  }
  async function template() {
    const data=await panel.getClient(templateEmail);
    const c=data.client;
    if (!data.inboundIds.length || !Number.isSafeInteger(c.limitIp ?? 0) ||
        !Number.isSafeInteger(c.limitHwid ?? 0)) throw new ApiError(503,'registration_unavailable');
    const ids=inboundIds ?? data.inboundIds;
    if (!Array.isArray(ids) || !ids.length || ids.some(id=>!Number.isSafeInteger(id) || id<=0 || !data.inboundIds.includes(id)))
      throw new ApiError(503,'registration_unavailable');
    return {ids:[...new Set(ids)],limits:{totalGB:c.totalGB,limitIp:c.limitIp??0,limitHwid:c.limitHwid??0}};
  }
  async function checked(id) {
    const m=row(id), data=await panel.getClient(id,{allowDetached:true});
    if (m && (data.client.id!==m.client_id || data.client.uuid!==m.uuid || data.client.subId!==m.sub_id))
      throw new ApiError(409,'panel_client_mismatch');
    return data;
  }
  async function provision(id,keys) {
    return locked(id,async()=>{
      const config=await template();
      const data=await panel.createDetached(id,keys,config.limits,config.ids[0]);
      const m=row(id);
      if (m && (m.uuid!==keys.uuid || m.client_id!==data.client.id)) throw new ApiError(409,'panel_client_mismatch');
      db.prepare('INSERT OR IGNORE INTO memberships(account,client_id,uuid,sub_id) VALUES(?,?,?,?)')
        .run(id,data.client.id,keys.uuid,keys.subId);
    });
  }
  async function finishTrial(id) {
    const m=row(id);
    if (!m?.trial_target || m.trial_done) return;
    const data=await checked(id), config=await template();
    await panel.grant(id,1,m.trial_target,m.client_id,config.ids);
    db.prepare('UPDATE memberships SET trial_done=1 WHERE account=?').run(id);
  }
  async function startTrial(id) {
    return locked(id,async()=>{
      const m=row(id);
      if (!m || m.paid_started || m.trial_done) throw new ApiError(409,'trial_unavailable');
      const data=await checked(id);
      if (!m.trial_target) {
        if (data.inboundIds.length || data.client.expiryTime>clock()) throw new ApiError(409,'trial_unavailable');
        await template(); // Fail before recording the grant if the template is unavailable.
        db.prepare('UPDATE memberships SET trial_target=? WHERE account=?').run(clock()+30*86400000,id);
      }
      await finishTrial(id);
      return status(id);
    });
  }
  async function status(id) {
    const m=row(id);
    if (!m) return {managed:false,trialAvailable:false};
    const data=await checked(id);
    return {managed:true,subscribed:data.inboundIds.length>0 && data.client.enable && data.client.expiryTime>clock(),
      trialAvailable:!m.paid_started && !m.trial_done && (!data.inboundIds.length || !!m.trial_target),
      trialUsed:!!m.trial_done,expiresAt:data.client.expiryTime>1?data.client.expiryTime:null};
  }
  const billingData=data=>{
    if (!data.inboundIds.length && data.client.expiryTime===1)
      return {...data,client:{...data.client,enable:true}};
    return data;
  };
  const billingPanel={
    async getClient(id) { return row(id)?billingData(await checked(id)):panel.getClient(id); },
    async prepareRenewal(id) {
      if (!row(id)) return panel.getClient(id);
      return locked(id,async()=>{
        await finishTrial(id);
        const data=await checked(id);
        db.prepare('UPDATE memberships SET paid_started=1 WHERE account=?').run(id);
        return billingData(data);
      });
    },
    async setExpiry(id,expected,target,recordId) {
      if (!row(id)) return panel.setExpiry(id,expected,target,recordId);
      return locked(id,async()=>{
        await finishTrial(id);
        const config=await template();
        return panel.grant(id,expected,target,recordId,config.ids);
      });
    }
  };
  return {provision,startTrial,status,billingPanel,close(){db.close();}};
}
