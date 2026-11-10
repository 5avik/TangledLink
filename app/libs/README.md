# Native core dependency

Gradle automatically provisions AndroidLibXrayLite before compiling the app.
No manual AAR download is required on a fresh checkout.

Pinned release: [2dust/AndroidLibXrayLite v26.11.9](https://github.com/2dust/AndroidLibXrayLite/releases/tag/v26.11.9).
SHA-256: `cdfef146218233a31a8b52c8b1016557730d3b63fc121d4c84cacea50e669155`

The `provisionNativeCore` task verifies the SHA-256, stores the AAR in
`app/build/native-core/v26.11.9/libv2ray.aar`, and wires it into the compile/runtime classpaths.
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

The first build requires network access or the verified local AAR.
After provisioning, offline builds work until the build directory is cleaned.
APK output: `app/build/outputs/apk/playstore/debug/`.
The universal APK contains all configured architectures.

On this Windows machine, a malformed quoted PATH entry breaks forked JVM test startup.
A temporary workaround for tests, without changing Windows settings:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Djava.library.path=C:/Windows/System32'
.\gradlew.bat :app:testPlaystoreDebugUnitTest --no-daemon
Remove-Item Env:JAVA_TOOL_OPTIONS
```

Four existing RootProcessRunner tests require Unix sh, sleep, yes and head.
Run the full suite on Linux (as in GitHub Actions).
Device VPN and authorization flows require separate runtime verification.
