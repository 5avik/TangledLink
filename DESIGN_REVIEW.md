# TangledLink visual refresh

Reference: https://app.tngl.pro/ and its styles.css, reviewed 2026-11-12.

## Implementation
- Website dark palette: background #050806, surfaces #0B1210 / #0F1915, text #EFF6F1 and #95A89E.
- Lime-to-mint-to-teal gradient (#C3FF92, #6EF5A8, #21D4A4).
- Bundled Manrope body and Unbounded heading fonts, including Cyrillic; explicit variable font weights.
- Shared typography and rounded Material shapes also apply to settings and other Compose screens.
- Branded login/password dialogs, gradient action buttons, rounded outlined form fields.
- Unified main-screen surface behind server list and bottom panel, logo empty state and outlined connection panel.
- Subscription card displays the signed-in subscription ID in Unbounded; the ID is stored as the authenticated subscription's remarks at login and preserved on refresh. Existing sessions from older builds need one new login to populate it.
- Server rows omit the address/port description and subscription badge row; protocol and latency remain visible.
- Follow-up: assemblePlaystoreDebug and testPlaystoreDebugUnitTest passed; Medium Phone showed the ID and no address/port rows, with ID retained after subscription refresh and app restart.
- Connection panel color transition (450 ms) and auth form height transition (250 ms), using Compose animation duration scaling.
- Existing system/light/dark and dynamic-color preferences remain available. Website colors appear with dark theme and dynamic colors disabled.
- Font SIL OFL licenses bundled in assets/licenses.

## Verified
- assemblePlaystoreDebug, assemblePlaystoreDebugAndroidTest and testPlaystoreDebugUnitTest passed.
- Medium_Phone (emulator-5554): two TnglLoginDialogTest tests passed.
- Final variable font adjustment: assemblePlaystoreDebug passed and APK installed on Medium Phone.
- Visually inspected login dialog in dark and light themes.
- Manually focused ID and password with IME visible, then activated Login; empty-input validation displayed.
- UI hierarchy inspected: labels and primary actions present.
- git diff --check passed.
- Live sign-in on Medium Phone succeeded and loaded 10 subscription servers.
- VPN connected using the automatic server entry. In-app connectivity check succeeded in 81 ms with a Netherlands exit node.
- Disconnected VPN and force-stopped/relaunched the app: session and subscription persisted, no login dialog appeared, VPN remained disconnected.

## Not run
- First activation and exhaustive testing of all subscription servers.
- TalkBack spoken feedback, hardware keyboard and D-pad activation.
- Populated server grid, dynamic-color combinations, landscape/tablet and large-font layout matrix. Populated single-column list inspected on Medium Phone.
- Manual password-change form and complete activation-form traversal with IME.
