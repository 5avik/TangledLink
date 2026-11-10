# Stable and test releases

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

The build workflow only uploads debug APKs as Actions artifacts. It does not create
tags or publish releases automatically. A branch push therefore cannot replace the
stable release.

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
