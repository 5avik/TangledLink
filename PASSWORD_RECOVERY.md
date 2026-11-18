# Password recovery

POST /v1/mobile-auth/recover accepts a VPN login (id) and returns step code, activation, or support. It never returns the confirmed address. Unknown local logins and unactivated logins share the activation response.

POST /v1/mobile-auth/recover-first accepts id, inviteCode and email. The server validates the invitation for that login and verifies the existing VPN profile before sending. Both routes accept emailCode and password to complete recovery and return ok:true without issuing a session. Requests without emailCode send or resend under the shared email quotas and cooldown.

The Android and website routes use the same recovery transaction and confirmed email table in AUTH_DB. Password installation, first-time email binding, invitation consumption and session revocation commit together. The email code is consumed during verification before the transaction. The VPN profile and subscription are preserved.

Deployed to auth.tngl.pro:8443. Backup: /opt/tngl-auth-backups/mobile-recovery.RMiwvK. After restart, health passed, an empty request returned HTTP 400 invalid_id, and an unknown login returned the activation step without sending mail.

Local npm test passed 64 tests with a test mailer. Covered both password scenarios, invalid invitation, wrong/expired/replayed email codes, resend cooldown and session revocation. Not run: production Gmail delivery, end-to-end recovery on a phone, TalkBack, hardware keyboard/D-pad, activity recreation and full IME traversal.
