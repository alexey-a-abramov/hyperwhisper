# Signing Configuration Setup

This document explains how to set up consistent APK signing across local builds and GitHub Actions builds, preventing signature mismatch errors when installing APKs from different sources.

## Problem

Without consistent signing:
- Local builds use your local debug keystore (or different keys)
- GitHub Actions builds use a different debug keystore
- Android refuses to install one over the other without uninstalling first
- You lose app data every time you switch between build sources

## Solution

Both local and CI builds now use the **same shared keystore** through environment variables.

---

## Quick Start (Using Debug Keystore)

If you want to quickly fix the signature mismatch issue for development, you can share your local debug keystore with GitHub Actions:

### 1. Locate Your Debug Keystore

**Linux/Mac:**
```bash
~/.android/debug.keystore
```

**Windows:**
```
C:\Users\YourName\.android\debug.keystore
```

### 2. Encode and Upload to GitHub Secrets

```bash
# Encode keystore as base64
base64 ~/.android/debug.keystore > keystore.b64

# Display the content (copy this)
cat keystore.b64
```

### 3. Add to GitHub Repository Secrets

Go to: `Settings` → `Secrets and variables` → `Actions` → `New repository secret`

Add these secrets:
- **Name:** `KEYSTORE_FILE`
  **Value:** (paste the base64 content from keystore.b64)

- **Name:** `KEYSTORE_PASSWORD`
  **Value:** `android`

- **Name:** `KEY_ALIAS`
  **Value:** `androiddebugkey`

- **Name:** `KEY_PASSWORD`
  **Value:** `android`

### 4. Done!

Now both local and GitHub Actions builds will use the same debug keystore. You can install APKs from either source without uninstalling.

---

## Production Setup (Using Release Keystore)

For production/release builds, create a dedicated release keystore:

### 1. Generate Release Keystore

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -alias hyperwhisper-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

You'll be prompted for:
- Keystore password (choose a strong password)
- Key password (can be same as keystore password)
- Your name, organization, etc.

**⚠️ IMPORTANT:** Save the passwords securely! You'll need them forever.

### 2. Store Keystore Securely

**Never commit the keystore to git!** It's already in `.gitignore`.

```bash
# Encode as base64 for GitHub Secrets
base64 release.keystore > release-keystore.b64
cat release-keystore.b64
```

### 3. Add to GitHub Secrets

Add these secrets with your chosen passwords:
- `KEYSTORE_FILE` - (base64 content)
- `KEYSTORE_PASSWORD` - (your keystore password)
- `KEY_ALIAS` - `hyperwhisper-release`
- `KEY_PASSWORD` - (your key password)

### 4. Local Build Setup

For local builds to use the release keystore, set environment variables:

**Linux/Mac (.bashrc or .zshrc):**
```bash
export KEYSTORE_FILE="$HOME/path/to/release.keystore"
export KEYSTORE_PASSWORD="your-password"
export KEY_ALIAS="hyperwhisper-release"
export KEY_PASSWORD="your-password"
```

**Or set them per-build:**
```bash
KEYSTORE_FILE=./release.keystore \
KEYSTORE_PASSWORD=yourpass \
KEY_ALIAS=hyperwhisper-release \
KEY_PASSWORD=yourpass \
./gradlew assembleRelease
```

---

## How It Works

### Build Configuration (build.gradle.kts)

The signing config in `app/build.gradle.kts` uses environment variables:

```kotlin
signingConfigs {
    create("shared") {
        // Use keystore from environment or fall back to debug keystore
        val keystorePath = System.getenv("KEYSTORE_FILE")
            ?: file("${System.getProperty("user.home")}/.android/debug.keystore").absolutePath
        val keystorePass = System.getenv("KEYSTORE_PASSWORD") ?: "android"
        val keyAliasName = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
        val keyPass = System.getenv("KEY_PASSWORD") ?: "android"

        storeFile = file(keystorePath)
        storePassword = keystorePass
        keyAlias = keyAliasName
        keyPassword = keyPass
    }
}
```

**Behavior:**
- ✅ If environment variables are set → uses specified keystore
- ✅ If not set → falls back to default debug keystore (`~/.android/debug.keystore`)

Both `debug` and `release` build types use this shared config.

### GitHub Actions Workflow

The workflow (`.github/workflows/build-apks.yml`) decodes the keystore from secrets:

```yaml
- name: Decode and setup keystore
  run: |
    if [ -n "${{ secrets.KEYSTORE_FILE }}" ]; then
      echo "${{ secrets.KEYSTORE_FILE }}" | base64 -d > ${{ github.workspace }}/shared.keystore
      echo "KEYSTORE_FILE=${{ github.workspace }}/shared.keystore" >> $GITHUB_ENV
    else
      echo "⚠️  No keystore secret found, using default debug keystore"
    fi

- name: Build APK
  run: ./gradlew assembleCloudDebug
  env:
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
```

**Behavior:**
- ✅ If secrets are configured → decodes and uses shared keystore
- ✅ If secrets not configured → falls back to GitHub's default debug keystore

---

## Verification

### Check Local Build Signature

```bash
# Build locally
./gradlew assembleCloudDebug

# Check signature
keytool -printcert -jarfile app/build/outputs/apk/cloud/debug/app-cloud-debug.apk

# Look for:
# Owner: CN=Android Debug, ...  (if using debug keystore)
# or your release keystore details
```

### Check GitHub Actions Build Signature

After a GitHub Actions build completes:

```bash
# Download the APK from GitHub Actions artifacts

# Check signature
keytool -printcert -jarfile app-cloud-debug.apk

# Should match the local build signature exactly!
```

If the `Owner:` and `SHA256:` fingerprint match, you're all set!

---

## Troubleshooting

### "Installation failed" or "Signature mismatch"

- Make sure GitHub Secrets are set correctly
- Verify environment variables are set for local builds
- Both builds must use the **exact same keystore file**

### "Keystore was tampered with, or password was incorrect"

- Check `KEYSTORE_PASSWORD` and `KEY_PASSWORD` match your keystore
- Verify the keystore file wasn't corrupted during base64 encoding

### Workflow shows "No keystore secret found"

- This is okay! It means it's using the default debug keystore
- To use a shared keystore, add the GitHub Secrets as described above

### Local builds fail with "Keystore file not found"

- If you set `KEYSTORE_FILE` env var, make sure the path is correct
- Unset the env var to fall back to debug keystore: `unset KEYSTORE_FILE`

---

## Security Best Practices

1. **Never commit keystores to git** (already in `.gitignore`)
2. **Never commit keystore passwords to git**
3. **Use GitHub Secrets for CI** - they're encrypted and only accessible during builds
4. **Backup your release keystore** - if you lose it, you can never update your app on Google Play
5. **Use strong passwords** for production keystores
6. **Limit access** to GitHub repository settings where secrets are stored

---

## Summary

✅ **Local builds:** Use `~/.android/debug.keystore` by default, or custom keystore via env vars
✅ **GitHub Actions:** Uses keystore from GitHub Secrets (or default debug if not configured)
✅ **Consistent signatures:** Both environments can use the same keystore
✅ **No more uninstalls:** APKs from different sources can be installed over each other
✅ **Secure:** Keystore files protected by `.gitignore` and GitHub Secrets

For quick development setup, share your debug keystore (see "Quick Start" above).
For production, create and use a dedicated release keystore (see "Production Setup" above).
