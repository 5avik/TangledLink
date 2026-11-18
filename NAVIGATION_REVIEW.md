# Main navigation refresh

## Changes
- Removed drawer access, search and overflow actions from the main toolbar.
- Added Settings footer after all servers in both list and grid layouts, and a Settings button in the empty state.
- New scrollable Compose settings hub: all seven former drawer destinations and former overflow actions. Routing appears once.
- Hub uses the website-based app theme, rounded outlined surfaces and localized English/Russian labels.
- Returning from a destination keeps the hub open. Back returns to the server list.
- Added Renew subscription in the account card and empty state; it opens the manual billing flow described in BILLING_REVIEW.md.

## Verified
- assemblePlaystoreDebug, assemblePlaystoreDebugAndroidTest and testPlaystoreDebugUnitTest passed.
- Medium_Phone: MainSettingsHubTest, 2 tests passed (account action, routing callback, lower menu availability; renewal dispatches its own billing callback without invoking refresh, ping or subscription-link actions).
- Installed APK on Medium Phone without clearing user data.
- Main toolbar visually has no hamburger, search or overflow buttons.
- Scrolled real 10-server list to Settings footer, tapped it, inspected hub.
- Opened Routing Settings via touch; back returned to hub, then back returned to main.
- git diff --check passed.

## Not run
- TalkBack spoken labels/order, hardware keyboard and D-pad activation.
- Activity recreation/rotation while hub or billing dialog is open.
- Complete light/dark, dynamic-color, list/grid, large-text and tablet interaction matrix.
- Actual payment verification and production renewal were not performed during navigation testing; manual billing behavior is documented in BILLING_REVIEW.md.
