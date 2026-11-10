# TangledLink auth (test branch)

Backend for https://auth.tngl.pro:8443, behind the existing Nginx proxy to 127.0.0.1:8080.
Requires Node.js 24 LTS or newer (built-in node:sqlite). No npm dependencies.
Runtime docs: https://nodejs.org/docs/latest-v24.x/api/sqlite.html

## Install on Ubuntu with systemd

Transfer the contents of this directory to /opt/tngl-auth (do not transfer a live database).
Install Node.js 24 LTS from https://nodejs.org/en/download and confirm:

```bash
/opt/node24/bin/node --version
```

The supplied unit expects /opt/node24/bin/node. If installed elsewhere, change ExecStart to its absolute path.

Run as root:

```bash
useradd --system --home /var/lib/tngl-auth --shell /usr/sbin/nologin tngl-auth
install -d -m 700 -o tngl-auth -g tngl-auth /var/lib/tngl-auth
install -m 640 -o root -g tngl-auth /opt/tngl-auth/.env.example /etc/tngl-auth.env
nano /etc/tngl-auth.env
```

SUBSCRIPTION_BASE must be the actual raw/base64 3x-ui subscription prefix, ending in /.
The provided example is the address used by the previous app. The upstream must send
subscription-userinfo with numeric upload, download, total and expire fields. Missing
status fails closed. JSON/Clash/HTML formats are not supported by this prototype.
HTTPS certificates are verified; loopback HTTP is allowed if configured explicitly.

```bash
cd /opt/tngl-auth
/opt/node24/bin/node --test auth.test.mjs upstream.test.mjs
install -m 644 tngl-auth.service /etc/systemd/system/tngl-auth.service
systemctl daemon-reload
systemctl enable --now tngl-auth
curl --fail http://127.0.0.1:8080/health
curl --fail https://auth.tngl.pro:8443/health
```

Both health requests must return {"status":"ok"}.
Port 8080 binds only to loopback; do not expose it. Nginx must overwrite X-Real-IP
with $remote_addr as in the configuration already installed.
Logs: journalctl -u tngl-auth -n 50 --no-pager.
No request bodies, passwords, session tokens or subscription URLs are logged by the API.

## First activation

As root, create a code for a real active subscription:

```bash
cd /opt/tngl-auth
runuser -u tngl-auth -- /opt/node24/bin/node --env-file=/etc/tngl-auth.env admin.mjs invite SUBSCRIPTION_ID
```

The code is random, single-use and valid for 24 hours. Deliver it privately to the
subscription owner. In the app choose First activation, enter the ID, code and a
new password of 12–128 characters. Login subsequently needs only ID + password.
Creating another invitation before activation invalidates the previous code.

The admin CLI runs as the service user to preserve SQLite file ownership.

## Password recovery

Verify the owner's identity outside the app, then explicitly run:

```bash
cd /opt/tngl-auth
runuser -u tngl-auth -- /opt/node24/bin/node --env-file=/etc/tngl-auth.env admin.mjs reset SUBSCRIPTION_ID
```

This clears the app password, revokes every session and issues a fresh activation code.
It does not change credentials in 3x-ui. Never reset based only on knowledge of a Sub ID.

## API

POST /v1/activate: {id, code, password}
POST /v1/login: {id, password}
Both return {token, expiresAt}, where expiresAt is Unix seconds.
GET /v1/subscription: Authorization: Bearer TOKEN, returns raw subscription and usage header.
POST /v1/password: bearer token, {currentPassword, newPassword}.
POST /v1/logout: bearer token, {}.
GET /health: process health only; does not prove upstream connectivity.

Opaque 256-bit sessions last 7 days. The DB holds only their SHA-256 hashes.
Passwords use scrypt (N=32768, r=8, p=3), each with a random salt.
Changing password preserves the current session and revokes all other sessions.
The Android app stores the session encrypted with Android Keystore outside backups.

Rate limits: 30 auth attempts/IP and 10 login/activation attempts/ID per 15 minutes;
10 password attempts/account per 15 minutes, 4 concurrent password operations.
Counters are in memory and reset on restart; run a single API process. Add Nginx
rate limits before opening this prototype to a large audience.

## Access boundary and migration

This backend checks available subscription configs and traffic/expiry headers rather
than using the administrative 3x-ui API. It cannot revoke an already downloaded VPN
configuration. That requires disabling/rotating the corresponding client in 3x-ui.

The old public /tnglsubs/ID endpoint still grants access by ID until protected on the
server. This prototype therefore adds app authentication but is NOT an exclusive
password gate for existing public subscription URLs. Before enforcing it, arrange a
private upstream subscription listener/route and close the public ID-only route.
Check other subscription clients before changing that route. Do not remove the old
route until the backend can reach its replacement.

On first launch of the new app, the old token is cleared and the saved TNGL
subscription URL is migrated to the authenticated API. Old clients are unchanged.

Back up the SQLite database with SQLite backup facilities or stop the service before
copying the DB and its WAL. Store backups securely. No panel password/API key is required.

## Verification

Automated backend tests cover activation, invitation replay/rotation, concurrent
activation, login failure, password change, session revocation, recovery, and upstream
expiry/quota/header/body checks. Run /opt/node24/bin/node --test auth.test.mjs upstream.test.mjs.
Android validation and Compose form tests are in app/src/test and app/src/androidTest.
Gradle automatically provisions the pinned libv2ray AAR with SHA-256 verification; see app/libs/README.md.

The owner installed the backend on Ubuntu and verified local and HTTPS health checks,
then successfully generated an invitation for a real subscription. On the API 37
emulator the new APK launches, rejects empty fields and opens the activation form.

Not run: complete activation/login against the real server, VPN connectivity,
IME traversal, keyboard, D-pad, TalkBack, process recreation, Keystore persistence
and screen reopen checks. The owner performed server deployment; the coding agent
has no SSH access.
