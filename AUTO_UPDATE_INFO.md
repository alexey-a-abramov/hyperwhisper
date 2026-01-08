# HyperWhisper Auto-Update System

## How It Works

1. **App checks for updates** when launched or manually via About screen
2. **GitHub Releases API** is queried:
   ```
   https://api.github.com/repos/alexey-a-abramov/hyperwhisper/releases/latest
   ```
3. **Compares versions**: 
   - Current: v1.96 (code 96)
   - Latest from API
4. **If newer found**: Shows update notification
5. **Downloads APK** from GitHub release asset
6. **Installs** using Android package installer

## Testing Auto-Update

After the release is live:

1. **Open app** → Go to About screen
2. **Scroll to bottom** → See "Update Check" section
3. **Tap Refresh** icon to manually check
4. **You should see**:
   - ✓ GitHub Releases: Latest v1.96
   - Download URL shown
   - All local paths checked

## For Future Versions

When v1.97 is released:

1. **User opens app** with v1.96 installed
2. **UpdateManager checks** GitHub API
3. **Finds v1.97** is available
4. **Shows notification**: "Update available: v1.97"
5. **User taps Update** → Downloads and installs

## API Response Example

```json
{
  "tag_name": "v1.96",
  "name": "HyperWhisper v1.96",
  "published_at": "2026-01-08T20:00:00Z",
  "assets": [
    {
      "name": "app-debug.apk",
      "browser_download_url": "https://github.com/alexey-a-abramov/hyperwhisper/releases/download/v1.96/app-debug.apk"
    }
  ]
}
```

## Download Links

- **Latest release**: https://github.com/alexey-a-abramov/hyperwhisper/releases/latest
- **Direct APK**: https://github.com/alexey-a-abramov/hyperwhisper/releases/latest/download/app-debug.apk
- **Specific version**: https://github.com/alexey-a-abramov/hyperwhisper/releases/download/v1.96/app-debug.apk

## Version Checking Logic

```kotlin
// Current app
currentVersionCode = 96

// API returns
latestVersionCode = 96  → No update
latestVersionCode = 97  → Update available!
latestVersionCode = 95  → No update (older)
```

## Update Sources Priority

1. **GitHub Releases API** (Primary - online)
2. **Local APK files** (Development - offline)
   - /sdcard/HyperWhisper/app-debug.apk
   - ~/projects/hyperwhisper/builds/app-debug.apk
   - ~/projects/hyperwhisper/app/build/outputs/apk/debug/app-debug.apk

## Monitoring

Check if workflow completed:
```bash
# View GitHub Actions
gh run list --limit 5

# Or visit:
# https://github.com/alexey-a-abramov/hyperwhisper/actions
```

Check if release exists:
```bash
# Using curl
curl -s https://api.github.com/repos/alexey-a-abramov/hyperwhisper/releases/latest | jq '.tag_name'

# Should return: "v1.96"
```
