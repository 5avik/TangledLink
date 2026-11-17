# Stable and test releases

Current test prerelease: **0.9.8-test** (v0.9.8-test, versionCode 17 / APK code 4000017).
Release notes: [RELEASE_NOTES_0.9.8-test.md](RELEASE_NOTES_0.9.8-test.md).

GitHub Releases belong to the repository, not individual branches.
Selecting test as the release target does not isolate the release from stable clients.

The current app calls GET /repos/5avik/TangledLink/releases/latest.
GitHub excludes draft and prerelease releases from that endpoint:
https://docs.github.com/en/rest/releases/releases#get-the-latest-release

## Publishing a test APK

1. Build the desired commit from test.
2. Create a unique test tag, for example test-v0.8.1, pointing to that commit.
3. Create the release with **Set as a pre-release** enabled.
4. Do not promote it to Latest or a full release.
5. Attach the corresponding APK and identify the commit and signing certificate.

Branch pushes only build, test and upload debug APKs as Actions artifacts.
Tags matching v*-test also run CI. Public releases are created explicitly after
both Android and backend jobs succeed, using --prerelease and --latest=false.
CI debug artifacts are QA-only: their signing key is ephemeral and does not match
published App Links. They are not automatically attached to public releases.
The workflow does not create tags or publish releases.

Starting with 0.9.4-test, public APKs use PlaystoreRelease and the persistent local
release keystore. Signing keys and signing.properties remain outside Git.
The certificate SHA-256 is 24:4C:E8:07:99:7D:BD:E6:44:17:B1:2C:1F:50:86:EA:00:57:CD:C5:89:4B:D4:B9:AC:C2:24:2F:52:5C:86:C6.
The published 0.9.3-test APK has a different CI debug certificate, so Android rejects
an in-place upgrade from that APK. Preserve local settings/profiles before reinstalling.
Do not uninstall user apps automatically to work around signature checks.

## Current limitations

Both branches currently use applicationId pro.tngl and the stable updater endpoint.
Test prereleases will not be offered by the default updater, even in the test APK.
Install test builds manually for now. Automatic test updates need a separate channel
and release filtering; that has not been implemented.

A test APK can replace the normal app only with a compatible signing certificate
and version code. Debug APKs use a debug certificate. Do not uninstall the main app
just to bypass a certificate mismatch without first preserving its data.
For side-by-side installs, add a dedicated application ID such as pro.tngl.test
and separate update-channel metadata.

The includePreRelease=true code path currently takes the first release in a list.
No current call site enables it. Enabling it in an older/custom build could offer a
test release, so do not rely on branch names as an access boundary.

Stable releases should use normal version tags (e.g. v0.9), contain stable APKs and
be published as full releases. Increment versionName and versionCode together.
