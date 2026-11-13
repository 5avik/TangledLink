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
/opt/node24/bin/node --test auth.test.mjs upstream.test.mjs admin-panel.test.mjs billing.test.mjs billing-reset.test.mjs xui-renewal.test.mjs
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

## Administration panel

The panel is a separate process bound to 127.0.0.1:8081. It requires HTTP Basic
authentication and a request token for every change. Use an SSH tunnel or the
HTTPS Nginx configuration below. Do not expose the backend port 8081 directly.

Set a unique random ADMIN_PASSWORD of at least 24 characters in /etc/tngl-auth.env
and optionally set ADMIN_PORT (default 8081). Keep the environment file readable
only by root and the tngl-auth group. Restart the service after changing it.

```bash
install -m 644 /opt/tngl-auth/tngl-admin.service /etc/systemd/system/tngl-admin.service
systemctl daemon-reload
systemctl enable --now tngl-admin
systemctl status tngl-admin
```

On the administrator's computer, connect to the server with local port forwarding:

```bash
ssh -N -L 127.0.0.1:8081:127.0.0.1:8081 USER@SERVER
```

Open http://127.0.0.1:8081/ in a browser and enter any username and the
ADMIN_PASSWORD. Use a private browser window so closing it clears the Basic
authentication credential. The panel lists account IDs, activation state,
invitation expiry and live session count. It can create or rotate a code for an
unactivated subscription, reset an activated account's password, and revoke all
sessions without changing the password. A reset clears the password and all
sessions. Each invitation checks the upstream subscription first. Codes are shown
only in the response that creates them; the database stores their hashes.

Verify the owner's identity before resetting a password. Account IDs alone are
insufficient proof. The panel manages app accounts for subscriptions that already
exist in 3x-ui; it does not create 3x-ui subscriptions. The old public ID-only
subscription route remains a separate access boundary as described below.

### Optional public HTTPS access

For the existing auth.tngl.pro certificate, nginx-admin.conf proxies HTTPS port
9443 to loopback 8081 and limits requests per client IP. Review certificate paths
and server_name if deploying on a different host. Install the file from an http
context (Ubuntu's conf.d directory), test, then reload:

```bash
install -m 644 /opt/tngl-auth/nginx-admin.conf /etc/nginx/conf.d/tngl-admin.conf
nginx -t && systemctl reload nginx
```

Allow inbound TCP 9443 in the server and hosting firewalls, then open
https://auth.tngl.pro:9443/. An unauthenticated request returns 401.
Do not replace HTTPS with plain HTTP for Basic authentication. A nonstandard port
does not provide authentication. Keep ADMIN_PASSWORD random and private.

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

## Subscription renewal

Optional manual payment confirmation and renewal for 3x-ui v3.11.0 are implemented.
See [BILLING.md](BILLING.md) for setup, exact email/account binding, the 150 RUB / 30 day plan,
payment verification, retry behavior and limits. After creating an order, the app opens
https://choco.tips/qr/50156198 using the Pay button and asks the user to enter their login in Name.
Payment confirmation is manual; opening or returning from the site does not renew the subscription.

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

Without XUI configuration this backend checks available subscription configs and traffic/expiry headers.
With XUI configured it looks up the client by exact account/email, resolves the actual subId,
and allows authenticated account access for renewal even after VPN expiry. Configuration access
still requires an active upstream subscription. Session revocation alone does not revoke downloaded
VPN configurations; disabling/rotating clients is a separate operation.

The old public /tnglsubs/ID endpoint still grants access by ID until protected on the
server. This prototype therefore adds app authentication but is NOT an exclusive
password gate for existing public subscription URLs. Before enforcing it, arrange a
private upstream subscription listener/route and close the public ID-only route.
Check other subscription clients before changing that route. Do not remove the old
route until the backend can reach its replacement.

On first launch of the new app, the old token is cleared and the saved TNGL
subscription URL is migrated to the authenticated API. Old clients are unchanged.

Back up the SQLite database with SQLite backup facilities or stop the service before
copying the DB and its WAL. Store backups securely. The optional renewal integration also uses a
separate billing.sqlite database and a server-only 3x-ui API token; see BILLING.md.

## Verification

Automated backend tests cover activation, invitation replay/rotation, concurrent
activation, login failure, password change, session revocation, recovery, and upstream
expiry/quota/header/body checks. Run /opt/node24/bin/node --test auth.test.mjs upstream.test.mjs admin-panel.test.mjs billing.test.mjs billing-reset.test.mjs xui-renewal.test.mjs.
Android validation and Compose form tests are in app/src/test and app/src/androidTest.
Gradle automatically provisions the pinned libv2ray AAR with SHA-256 verification; see app/libs/README.md.

The owner installed the backend on Ubuntu and verified local and HTTPS health checks,
then successfully generated an invitation for a real subscription. On the API 37
emulator the new APK launches, rejects empty fields and opens the activation form.

Not run: complete activation/login against the real server, VPN connectivity,
IME traversal, keyboard, D-pad, TalkBack, process recreation, Keystore persistence
and screen reopen checks. The owner performed server deployment; the coding agent
has no SSH access.
