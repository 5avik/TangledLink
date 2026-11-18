# Standalone authorization entry

MainScreen renders the authorization screen instead of PremiumShell while showTnglLoginDialog is true. The existing credential/activation actions remain unchanged. The form uses an opaque Scaffold, consumed content insets, IME padding and vertical scrolling. Registration was initially disabled; the registration feature now supplies its action (see server/REGISTRATION.md). Default English and Russian resources are provided.

Validation: installPlaystoreDebug and installPlaystoreDebugAndroidTest succeeded on Medium Phone emulator-5554. TnglLoginDialogTest: 3 passed (login callback without code, activation callback with code, disabled registration). Form submission tests scroll to the action after editing password with the IME.

Not run: live first-install authentication against backend, activity recreation, TalkBack spoken output/focus order, external keyboard and D-pad traversal, complete first-to-last IME traversal, all screen sizes and themes. Existing account data was preserved; no logout/data clearing was performed. Compilation and component tests do not verify those behaviors.
