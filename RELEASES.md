# HyperWhisper Release Process

This document describes the automated release process for HyperWhisper.

## Overview

HyperWhisper uses **Git tag-based releases** with GitHub Actions automation. When you push a version tag, GitHub automatically:
1. Builds the APK
2. Creates a GitHub Release
3. Uploads the APK as a release asset
4. Makes it available via GitHub Releases API for auto-update

## Version Numbering

- **Version Name**: `1.XX` (e.g., 1.96, 1.97)
- **Version Code**: `XX` (e.g., 96, 97)
- Format: `v{version}` (e.g., v1.96)

The version code is automatically extracted from `gradle.properties`.

## Release Methods

### Method 1: Tag-based Release (Recommended)

This is the simplest and most automated method.

1. **Ensure latest code is pushed**:
```bash
git add -A
git commit -m "Your changes"
git push
```

2. **Create and push a version tag**:
```bash
# Get current version from gradle.properties
VERSION=$(grep VERSION_CODE gradle.properties | cut -d= -f2)
echo "Current version code: $VERSION"

# Create tag
git tag v1.$VERSION

# Push tag to trigger release
git push --tags
```

3. **GitHub Actions will automatically**:
   - Build debug APK
   - Create GitHub Release at `https://github.com/alexey-a-abramov/hyperwhisper/releases/tag/v1.$VERSION`
   - Upload `app-debug.apk` and `hyperwhisper-v1.$VERSION-debug.apk`

4. **Check progress**:
   - Go to: https://github.com/alexey-a-abramov/hyperwhisper/actions
   - Watch the "Build and Release APK" workflow

### Method 2: Manual Dispatch (Custom Version)

Use this when you want to create a release without changing the version in gradle.properties.

1. Go to: https://github.com/alexey-a-abramov/hyperwhisper/actions
2. Click "Build and Release APK" workflow
3. Click "Run workflow" button
4. Enter:
   - **Version name**: e.g., `1.96`
   - **Release notes**: Description of changes
5. Click "Run workflow"

## Auto-Update System

### How It Works

The app checks for updates from multiple sources in this order:

1. **GitHub Releases API** (Primary):
   - Endpoint: `https://api.github.com/repos/alexey-a-abramov/hyperwhisper/releases/latest`
   - Parses: `tag_name`, `assets`, `browser_download_url`
   - No authentication required (public repo)

2. **Local APK files** (Development):
   - `/storage/emulated/0/HyperWhisper/app-debug.apk`
   - `~/projects/hyperwhisper/builds/app-debug.apk`
   - `~/projects/hyperwhisper/app/build/outputs/apk/debug/app-debug.apk`

### API Response Format

GitHub Releases API returns:
```json
{
  "tag_name": "v1.96",
  "name": "HyperWhisper v1.96",
  "body": "Release notes...",
  "published_at": "2024-01-08T12:00:00Z",
  "assets": [
    {
      "name": "app-debug.apk",
      "browser_download_url": "https://github.com/alexey-a-abramov/hyperwhisper/releases/download/v1.96/app-debug.apk",
      "size": 16777216
    }
  ]
}
```

### Update Check Flow

1. User opens app → UpdateManager checks GitHub Releases API
2. If newer version found → Show update notification
3. User taps "Update" → Download APK from `browser_download_url`
4. Install APK using Android package installer

## Release Checklist

Before creating a release:

- [ ] All changes committed and pushed
- [ ] App tested on device
- [ ] Version code incremented in `gradle.properties`
- [ ] Release notes prepared
- [ ] Create tag: `git tag v1.XX`
- [ ] Push tag: `git push --tags`
- [ ] Verify build on GitHub Actions
- [ ] Test auto-update from previous version

## Quick Release Script

Save this as `release.sh` in the project root:

```bash
#!/bin/bash

# Get current version
VERSION=$(grep VERSION_CODE gradle.properties | cut -d= -f2)
TAG="v1.$VERSION"

echo "========================================="
echo "  Creating Release: $TAG"
echo "========================================="

# Check if tag already exists
if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "❌ Tag $TAG already exists!"
    echo "   Increment VERSION_CODE in gradle.properties first"
    exit 1
fi

# Confirm
echo ""
echo "This will:"
echo "  1. Create tag: $TAG"
echo "  2. Push to GitHub"
echo "  3. Trigger automated build & release"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 0
fi

# Create and push tag
git tag -a "$TAG" -m "Release $TAG"
git push --tags

echo ""
echo "✓ Tag pushed! Release will be created at:"
echo "  https://github.com/alexey-a-abramov/hyperwhisper/releases/tag/$TAG"
echo ""
echo "Monitor build progress at:"
echo "  https://github.com/alexey-a-abramov/hyperwhisper/actions"
```

Make it executable:
```bash
chmod +x release.sh
```

Then simply run:
```bash
./release.sh
```

## Troubleshooting

### Build Failed
- Check GitHub Actions logs
- Ensure `gradlew` has execute permissions
- Verify `VERSION_CODE` in gradle.properties is valid

### No APK in Release
- Check if build completed successfully
- Look for APK in Actions artifacts
- Re-run workflow if needed

### Auto-update Not Working
1. Check About screen → Update Check section
2. Verify GitHub release exists
3. Check app logs for API errors
4. Test with manual update check

## Development Workflow

For local development with auto-update:

1. Build locally: `./gradlew assembleDebug`
2. Copy to SD card:
```bash
mkdir -p /storage/emulated/0/HyperWhisper
cp app/build/outputs/apk/debug/app-debug.apk /storage/emulated/0/HyperWhisper/
```
3. App will detect newer local APK and prompt update

## Release Artifacts

Each release includes:
- `app-debug.apk` - Standard debug build (for auto-update)
- `hyperwhisper-v1.XX-debug.apk` - Versioned name (for manual download)

Download links:
- Latest: `https://github.com/alexey-a-abramov/hyperwhisper/releases/latest/download/app-debug.apk`
- Specific: `https://github.com/alexey-a-abramov/hyperwhisper/releases/download/v1.96/app-debug.apk`

## GitHub Actions Workflow

File: `.github/workflows/release.yml`

Key steps:
1. Checkout code
2. Set up JDK 17
3. Extract version from tag or input
4. Update gradle.properties
5. Build APKs (debug + release)
6. Create GitHub Release
7. Upload APK assets

The workflow uses the `GITHUB_TOKEN` automatically provided by GitHub Actions, no additional secrets needed.
