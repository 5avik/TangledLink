# Email verification: implementation and release

## Inspected production contract
Read /opt/tngl-auth/server.mjs, web-email.mjs and auth.mjs over SSH without reading Gmail credentials or env secrets. The website source was ahead of the checkout; its existing web-auth and legacy-account status logic is retained here. Production has no mobile-auth routes yet.

Existing website POST /v1/web-auth/register accepts id/password/email and optional emailCode; first response emailRequired:true, confirmed response ok:true (no retained session). POST /v1/web-auth/login accepts id/password/emailCode; unbound accounts return a session, bound accounts require a code. POST bind uses Bearer session, email/emailCode; GET email returns email or null. All web-auth paths retain the site-only X-Web-Billing-Key requirement.

## Implemented mobile contract
- POST /v1/mobile-auth/register: id, password, email, optional emailCode. Initial response {emailRequired:true,retryAfterSeconds:60,expiresInSeconds:600}. Correct code provisions through the unchanged membership adapter, binds email and returns {token,expiresAt}. No auth account or VPN provisioning before a valid code.
- POST /v1/mobile-auth/login: id, password, optional emailCode. Unbound legacy accounts get the normal session. Bound accounts get the challenge response and receive a session only after code verification.
- POST /v1/mobile-auth/bind: Bearer session + email, optional emailCode. Challenge response then {ok:true}. GET /v1/mobile-auth/email: Bearer session, returns {email:string|null}.
- No web key on mobile routes. VPN-login remains the account/3x-ui identity. Receipt email is unchanged.
- Errors: invalid_email_code, email_code_expired, email_attempts_exceeded, email_wait, email_unavailable. Wait responses carry Retry-After and retryAfterSeconds; app uses returned delay. Codes expire at 10 minutes, resend at 60 seconds, five numeric wrong guesses exhaust a code. Existing recipient/global send quotas remain shared between site/app. Client secrets are not embedded in Android.
- Legacy /v1/login uses the same guarded flow. /v1/register now requires email confirmation; /v1/activate also checks a bound email before issuing a session. Missing mail configuration fails closed for new authentication rather than allowing bypass.

## Android
Existing login form gains registration email input. EmailVerificationController owns in-memory pending operations and immutable StateFlow; cancellation discards operations and ignores late results. Session storage is updated only after the final successful response. Passwords and codes are never SavedState/MMKV values. The six-digit text field supports normal Android selection/paste; no background clipboard read. Code UI has expiry guidance, resend timer and localized errors. Timer only advances while lifecycle is RESUMED. Profile loads server email and offers binding; the same server email table handles website/app bindings. Login completion still uses existing subscription/profile/trial initialization.

## Safe release sequence
1. Prepare a signed APK and its download/update instructions before enforcing the new policy. Test it against a staging copy with controlled Gmail recipient. Do not roll the new APK out against the current backend: mobile routes do not exist there yet.
2. Back up auth.sqlite and web-email.sqlite as consistent SQLite snapshots; retain existing Gmail systemd credential mount and server-only WEB_BILLING_KEY. Ship server.mjs, web-email.mjs and email-auth.mjs together, preserving the deployed site's other modules and paths. No secret transfer into APK/repository.
3. Deploy server with mobile routes AND legacy-route guards atomically; verify health, site login/bind and protected legacy/mobile login. Never enable an interim password-only fallback for email-bound accounts. Immediately make the prepared compatible APK available/announce mandatory update for bound accounts.
4. Existing sessions remain valid. Old APKs can still sign in to unbound accounts, but email-bound accounts cannot get a new session through old clients. Old clients do not understand emailRequired and will show an authentication error until updated. Old self-registration without email is intentionally rejected. Rollback must retain the guards; do not restore an old password-only server while confirmed bindings exist.

Deployment completed on 2026-11-18 for 0.9.9-test. Backup: /opt/tngl-auth-backups/email-099-1794989315687 (server files and consistent auth/email SQLite snapshots). Live HTTPS QA verified unbound login, binding, shared website/mobile address, legacy guard, wrong-code rejection and session issuance only after the correct code. QA records were removed. No Gmail messages were sent in this controlled check; real delivery remains a separate verification. Signed PlaystoreRelease APKs use the existing release certificate.
