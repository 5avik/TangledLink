# Neon Portal authentication

AuthPortal.kt provides the Canvas rings and the success/reveal layer. TnglAuthDialogs.kt adds loading visuals to the existing form. MainScreen.kt layers the original form over the single PremiumShell instance during reveal. MainContract.kt and MainViewModel.kt expose a success sequence updated only when login completes. EN/RU resources provide progress and welcome labels.

Debug build and instrumentation build succeeded. EmailVerificationControllerTest passed, covering a pending duplicate request, rejection, success and a late result after cancellation. Medium Phone passed five component tests for the existing login form and a confirmed-success transition with one home composition. The portal test uses the Compose clock and a stand-in home, without submitting real credentials.

Not run: end-to-end successful or failed live login, measured fast/slow server responses, device background/resume and process recreation, system animation setting changes, TalkBack, physical keyboard/D-pad and FPS profiling. The shared scene motion policy gates animations, but these device behaviors were not runtime-verified.
