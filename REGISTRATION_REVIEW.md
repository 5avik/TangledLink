# Registration and trial review

Implemented in the existing client and deployed to the existing authentication backend. No release/tag/push was requested for this change.

Android: TnglAuthClient supports registration/account status/trial; MainContract and MainViewModel own registration actions, validation, trial busy/error/access state and config refresh. AuthValidation checks signup IDs and password policy. TnglAuthDialogs enables registration using the existing form, without changing activation/login callbacks. RegistrationAccessScreen provides the dormant account card on the existing server picker. PremiumScreens preserves Terminal's console route and shows the same card in its server-picker child. English and Russian registration resources added.

Backend: auth.mjs reserves hashed credentials and stable registration identity before provisioning, blocks login while pending, and resumes only with matching credentials. membership.mjs stores ownership, fixed trial target, once-only and paid-intent markers, plus per-account leases in the existing AUTH_DB. xui.mjs verifies owned panel clients, creates a disabled/expired bootstrap attachment then detaches it, and verifies grants by record ID and email. server.mjs exposes session-protected account/trial routes and registration rate limits. billing-runtime/billing prepare a first paid account using the same membership adapter. Existing accounts retain the previous renewal path. See server/REGISTRATION.md for API/configuration and the 3x-ui limitation.

Verified real template: svk has 13 attachments; only 2,3,4,12,13,14,24,25,26,32 are configured for new accounts. Limits are currently totalGB=0, limitIp=0, limitHwid=0. Extra AmneziaWG, XDRIVE and MASQUE attachments are not granted. Independent UUID/subId per registration.

Validation:
- npm test: 60 backend tests passed, including existing billing/webhook/renewal regressions, signup reservation/collision/retry, one trial with fixed-target retry, first paid billing confirmation, independent credentials, disabled bootstrap/detachment and panel identity collision.
- installPlaystoreDebug, installPlaystoreDebugAndroidTest and testPlaystoreDebugUnitTest --tests com.v2ray.ang.ui.main.AuthValidationTest passed on Medium Phone emulator-5554.
- TnglLoginDialogTest: four component cases passed (login, activation, explicit registration callback, disabled fallback when registration callback is absent).
- RegistrationAccessScreenTest: one passed covering touch, Enter, D-pad center activation; separate trial/purchase actions; busy disabled state; trial-unavailable and error presentation. The initial test needed explicit keyboard input mode before requesting focus; production uses standard Material buttons.
- Real 3x-ui smoke: independent client with no final inbounds; 30-day trial; exact ten attachments; repeat rejected; subscription body fetched. Test client removed.
- Public HTTPS smoke on auth.tngl.pro:8443: signup, dormant status, subscription 403 before trial, trial success, repeat 409, subscription 200, login 200, duplicate signup 409, billing availability 200. Only owned QA account/session/membership records were cleaned.
- Backend backup: /opt/tngl-auth-backups/registration-1794915818447. Initial simultaneous startup hit SQLite recovery lock, auto-recovered; busy_timeout is now set before journal_mode in auth/membership startup. Both services restarted afterward.
- Existing signed-in device account and local data preserved.

Not run: a real paid transaction/debit through Lava, full first-install app-to-backend walkthrough, a new VPN Internet-connectivity smoke, activity recreation/restart after signup, all screen sizes/two-column/light/dark variants, full form keyboard/D-pad traversal, TalkBack speech/focus order, large-font/complete IME traversal, comprehensive injected ViewModel network error/cancellation tests. Component form tests submit with the IME after scrolling, but do not establish every accessibility or restoration behavior.

Without email confirmation the trial is once per account, not once per person; public signup limits reduce automated abuse but do not prevent multiple accounts by one person.
