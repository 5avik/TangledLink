# Native core dependency

Gradle prepares and verifies AndroidLibXrayLite during project configuration, before Android Studio sync resolves dependencies.
No manual AAR download is required on a fresh checkout.

Pinned release: [2dust/AndroidLibXrayLite v26.11.9](https://github.com/2dust/AndroidLibXrayLite/releases/tag/v26.11.9).
SHA-256: `cdfef146218233a31a8b52c8b1016557730d3b63fc121d4c84cacea50e669155`

During configuration, Gradle verifies the SHA-256 and stores the AAR in
`.gradle/native-core/v26.11.9/libv2ray.aar` at the project root, outside build directories.
The AAR is available to IDE sync and the compile/runtime classpaths without running a producer task.
The `provisionNativeCore` task remains available as an explicit setup command for scripts and CI;
preparation happens during configuration even when invoking that task.
A verified local `app/libs/libv2ray.aar` can seed that cache; it is excluded from the
generic fileTree dependency to avoid duplicate classes.
AARs stay out of Git. Change the pinned release and hash together in app/build.gradle.kts.

```powershell
.\gradlew.bat :app:assemblePlaystoreDebug
```

JVM tests:

```powershell
.\gradlew.bat :app:testPlaystoreDebugUnitTest
```

The first sync or build requires network access or the verified local AAR.
After provisioning, offline sync and builds work with the cached AAR.
`clean` and deleting `app/build` do not remove this cache.
Deleting `.gradle/native-core`, using a fresh checkout, or changing the pinned version
requires provisioning again. With an empty cache in offline mode, Gradle reports how to supply the AAR.
A file lock serializes preparation, and a SHA-256 mismatch rejects the downloaded file.
APK output: `app/build/outputs/apk/playstore/debug/`.
The universal APK contains all configured architectures.

If a malformed quoted Windows PATH entry breaks forked JVM test startup:
A temporary workaround for tests, without changing Windows settings:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Djava.library.path=C:/Windows/System32'
.\gradlew.bat :app:testPlaystoreDebugUnitTest --no-daemon
Remove-Item Env:JAVA_TOOL_OPTIONS
```

Four existing RootProcessRunner tests require Unix sh, sleep, yes and head.
Run the full suite on Linux (as in GitHub Actions).
Device VPN and authorization flows require separate runtime verification.
