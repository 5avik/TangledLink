# Manual renewal verification

## Scope
- 150 RUB / 30-day plan. Manual administrator verification of an actual payment.
- User ID matches 3x-ui email exactly; subId may differ and is resolved from that client's record.
- Server-only 3x-ui v3.11.0 API adapter; Choco is opened as an external link with manual administrator confirmation (no payment API/webhook).
- Orders are tied to bearer sessions, fixed price, a server-generated per-order confirmation reference, persisted absolute expiry target and retry state. External payment references are no longer entered or deduplicated across orders.
- Existing app settings/navigation changes in this workspace remain included; no release was created.

## Initial validation (before admin simplification)
- npm test: 11 tests passed, covering duplicate payment references, repeated and concurrent confirmation, restart after an ambiguous remote write, expired clients, exact email matching, different subId, UUID/settings preservation, bearer ownership, admin authentication and CSRF.
- Gradle assemblePlaystoreDebug, assemblePlaystoreDebugAndroidTest and testPlaystoreDebugUnitTest passed.
- BillingControllerTest covers initial/empty, pending, paid, error, create failure and account isolation states.
- Medium_Phone: BillingDialogTest and MainSettingsHubTest, 4 UI tests passed.
- Installed debug app without deleting data; actual account session stayed available.
- Manually opened renewal on Medium Phone. The undeployed billing endpoint shows the localized unavailable state; it does not claim payment or create a real order.
- Supplied panel URL responded HTTP 200 with normal HTTPS certificate verification, without login/token or any client mutation.
- Setup and limitations documented in server/BILLING.md.

## Production verification — 2026-11-13
- Deployed server modules to /opt/tngl-auth; tngl-auth and tngl-admin are active.
- Preserved existing production authentication settings and databases. Added supplied panel credentials server-side; production secrets were not downloaded.
- Server-side backup: /var/backups/tngl-auth/2026-11-13T08-41-19-412Z (application, environment, stopped-service state databases, Nginx admin config).
- Re-ran all 11 backend tests in an isolated server staging directory: passed.
- Real API token authenticated successfully; exact email svk resolves to 13 inbounds.
- Admin billing list, protected page, CSRF-protected order creation and preview passed. Unauthorized admin and client requests return 401.
- Created pending test order TL-59F8AAA7CF0F9C2AD431EB29 for svk, 150 RUB / 30 days.
- Preview: 2099-12-13T18:59:59Z → 2100-01-12T18:59:59Z. No confirmation endpoint called; original expiry verified unchanged.
- Public HTTPS health passed; public admin billing endpoint requires authentication.
- Medium Phone loaded the real tariff, generic administrator-payment instructions and pending svk order from production. Status refresh was exercised.
- Initial deployment used generic administrator-payment instructions; these were replaced by the Choco flow described below.

## Choco external-link update — 2026-11-13
- After an order is created, the pending-order UI displays the exact order account as the login to enter in the website's Name field.
- Pay dispatches MainAction.OpenBillingPayment; MainViewModel/BillingController allow only a visible, idle, error-free pending order and return exactly https://choco.tips/qr/50156198.
- No account, password, token, amount or order details are appended to that URL. Opening the browser does not mutate order/payment/subscription state.
- Processing/retry/paid orders do not offer another payment. Users are told to return and check status after payment.
- Updated English/Russian resources, server instructions, admin matching guidance and deployment documentation.
- Gradle assemblePlaystoreDebug, assemblePlaystoreDebugAndroidTest and testPlaystoreDebugUnitTest passed after these changes. Added payment URL/state guard coverage.
- Backend tests: 11 passed. Medium Phone: both updated BillingDialogTest cases passed (pre-order pay absent, pending login/Pay action present, manual verification state unchanged).
- Installed updated debug app and test APK on Medium Phone without clearing account data.
- Updated production instructions and billing admin page. Backup: /var/backups/tngl-auth/payment-2026-11-13T09-05-34-182Z. Existing orders and svk expiry verified unchanged by this update.
- Real svk order is now retry with tunnel_client_requires_review and a payment reference already recorded. This update did not confirm/reset that order; the app correctly withholds another Pay button.
- Independently opened the exact URL via Android ACTION_VIEW on Medium Phone; Chrome displayed choco.tips/qr/50156198. No payment submitted.
- Full real-account pending-order-to-browser round trip was not exercised because the existing svk order is retry. Pending UI action and guarded URL routing were tested separately.
- The website's Name field and successful payment were not verified; the web research tool could not fetch the page. No undocumented URL prefill is assumed.

## Order reset and simplified admin confirmation — 2026-11-13
- Removed payment-reference, amount and checkbox inputs from the confirmation form. Confirm API takes orderId only; saved server price applies and a stable internal reference is created automatically. Legacy references stay valid.
- Added CSRF-protected admin reset for unfinished orders, returning the same order ID to pending. No 3x-ui writes occur during reset.
- Reset checks the original client/expiry when a target was recorded, blocks active work and paid orders, and preserves a snapshot in billing_order_resets. Already-applied/ambiguous remote changes require review rather than erasing retry data.
- Updated npm test and deployment instructions. All 15 backend tests passed locally and on server Node 24, including repeated confirmation, restart, legacy references, reset concurrency/lease, audit persistence, unchanged subscription, and HTTP auth/CSRF.
- Deployed to production. Backup: /var/backups/tngl-auth/orders-2026-11-13T09-24-31-630Z.
- Verified new page omits manual inputs and includes reset control; checked both admin handlers with authenticated/CSRF and unauthorized requests against a nonexistent order. No real order confirmed or reset.
- Production orders and svk expiry verified unchanged; tngl-auth and tngl-admin active.
- Cross-order reuse of one external payment can no longer be detected automatically without entering a payment reference; administrator reconciliation is required.
- Android code/build unchanged in this update. Page JavaScript syntax tested; browser visual/keyboard QA remains not run.

## Tunnel renewal adapter — 2026-11-13
- Removed the blanket tunnel_client_requires_review check for nonempty WireGuard/AmneziaWG fields.
- Renewal payload explicitly omits privateKey/publicKey/preSharedKey/allowedIPs/allowedIPsByInbound/keepAlive/forwardedPorts. 3x-ui 3.11.0 carries those values forward from each existing inbound peer; a global record is not broadcast over distinct peers.
- Contract verified against the pinned model.Client, ClientService.Update and UpdateInboundClient sources, including multi-tunnel behavior. No raw client credentials are logged.
- Added five regression tests: single WG, single AWG, multiple independent tunnel peers, side-effect-free payload mapping, and timeout-after-write with fixed-target retry.
- All 20 backend tests passed locally and on server Node 24.
- Ran the adapter with real svk GET data using an intercepted writer; the prepared update succeeded without forwarding any POST to 3x-ui.
- Deployed xui.mjs, regression tests and package.json. Backup: /var/backups/tngl-auth/adapter-2026-11-13T09-39-39-992Z.
- Production orders, svk expiry and recorded peer settings verified unchanged by deployment; both services active.
- For a verified payment, retry the existing order confirmation after reloading the admin page; no reset or fresh order is required.
- A live paid renewal and actual per-inbound peer read-back remain untested. The multi-peer assertions use a mock implementing the pinned panel carry-forward semantics.

## Not run / pending setup
- A real payment and actual expiry mutation on a production 3x-ui client. Confirmation requires a verified payment; no payment was simulated as received.
- Administrator-page visual/browser QA (generated JavaScript syntax and HTTP API authorization tested).
- TalkBack spoken labels, hardware keyboard/D-pad activation, complete large-font, landscape, tablet and dynamic-color matrix.
- Process-death restoration and real expired-account login on Android; server expired-account login covered with mock panel.
- Live WireGuard/AmneziaWG renewal on production has not been performed; the supported 3.11.0 carry-forward contract is covered by adapter mock tests.
