# HyperWhisper Auto-Update System

The in-app updater (`ime/update/UpdateManager.kt`, `ApkProber`) checks for a newer APK on app launch and on demand from the About screen, then installs it via the Android package installer. Versioning is a single monotonic `VERSION_CODE` (in `gradle.properties`); `versionName` is `1.$VERSION_CODE`.

## Update sources — checked in this order

1. **Local APK files first** (`ApkProber.checkLocalApkUpdates`) — for the on-device/Termux dev loop, a freshly built APK with a higher `versionCode` is picked up with no network. Probed paths include:
   - `/sdcard/HyperWhisper/app-debug.apk`
   - the repo `builds/app-debug.apk`
   - `app/build/outputs/apk/debug/app-debug.apk`
   - (dev only) a test descriptor at `/data/local/tmp/hyperwhisper-update.json`
2. **GitHub Releases** (`https://api.github.com/repos/alexey-a-abramov/hyperwhisper/releases/latest`) — used when no newer local APK is found. The release's `app-debug.apk` asset is downloaded and installed.

A newer local build therefore takes precedence over a remote release, which is what you want while iterating on-device.

## Version checking

```kotlin
// Update offered only when the candidate's versionCode is strictly greater
candidateVersionCode > currentVersionCode  // → update available
```

## Releasing

GitHub releases are produced by CI, not by `build-cloud.sh` (which references a removed `build-apks.yml` workflow — see the README "Known stale" note):

- `.github/workflows/release.yml` builds and publishes a Release on a `v*` tag push (or manual `workflow_dispatch`).
- `.github/workflows/ci-cd.yml` builds on pushes to `master`.

Tag a release so the asset is named `app-debug.apk` to match what `ApkProber`/`UpdateManager` expect:

```bash
git tag v1.<versionCode> && git push origin v1.<versionCode>
gh release view v1.<versionCode>   # confirm the app-debug.apk asset is attached
```
