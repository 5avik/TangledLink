# Email verification review

Changed Android: TnglAuthClient, EmailAuthResult, MainContract, MainViewModel, MainScreen, TnglAuthDialogs, AccountScreen, PremiumScreens; new EmailVerificationController/Screen and default/ru email_verification.xml. Changed server: imported actual deployed web-email.mjs and website additions in server.mjs; new email-auth.mjs shared service and mobile routes, legacy endpoint guards, retry metadata and expired-code errors. No Gmail OAuth or web billing secret was read into the checkout or embedded in Android.

Confirmed production formats from server sources, not guessed from a mock. Mobile routes intentionally do not call closed web-auth routes or send X-Web-Billing-Key. Verified addresses, code HMAC proof and quotas use the same web-email.sqlite service. Existing 3x-ui membership/provision/trial operations are unchanged. A code-required login validates credentials through the verifier callback before issue(), not by issuing and then revoking a temporary session. Registration verifies email code before auth.register() or membership provisioning.

Checks performed:
- Full npm test: 63 tests passed, including shared signup/login/bind, proof binding, incorrect/expired/consumed codes, resend wait, numeric attempt cap, legacy HTTP login guard, no session before code, and prior registration/billing regressions.
- assemblePlaystoreDebug and compilePlaystoreDebugAndroidTestKotlin succeeded.
- EmailVerificationControllerTest: three JVM tests passed (including duplicate-request gate and a late token response after cancellation) covering initial/direct login, challenge, valid/empty/invalid code, expiry, network error, server wait, resend gate, success cleanup and cancellation reset.
- Medium Phone emulator-5554: initial EmailVerificationScreenTest + TnglLoginDialogTest, 5 component tests passed. Registration email is forwarded separately; code submission/expiry/resend action mapping works with IME scrolling.
- Final UI group: 8 passed on Medium Phone, including actual Clipboard paste via PasteText semantics and profile email/binding action presentation. No real user codes were placed in the clipboard.
- Source review: no auth action logging added; pending passwords/codes are not SavedState/MMKV/analytics fields. Existing session persistence is used only after the final successful response. Result is not a data class, so its default string output does not include its token.

Not run: real Gmail delivery or use of real user codes; full staging Android-to-Gmail-to-backend sign-in and binding; full external keyboard/D-pad/TalkBack spoken output and focus traversal; rotation/process death; all screen sizes/font scales/dynamic/light-dark variants; recheck of live VPN or payments (not changed). Existing device account data preserved; no logout/uninstall/data reset.

See EMAIL_VERIFICATION_RELEASE.md for precise mobile/server contracts, shared website behavior, backup requirements and the safe release order. Production updated on 2026-11-18 for 0.9.9-test. Live HTTPS guard test passed: unbound login, binding, shared website/mobile email lookup, legacy bypass prevention, wrong-code denial and token issuance after a correct seeded code. No real Gmail messages sent; only owned QA records were created/removed. Backup: /opt/tngl-auth-backups/email-099-1794989315687.
