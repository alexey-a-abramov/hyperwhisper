# HyperWhisper Build and Release Scripts

This directory contains scripts to automate building and releasing HyperWhisper.

## Scripts

### `build-and-release.sh` (Recommended)

**Complete build and release workflow:**
- Clean previous builds
- Build release APK
- Commit changes (optional)
- Push to GitHub
- Create/update git tag
- Create/update GitHub release
- Upload APK to release

**Usage:**
```bash
./build-and-release.sh
```

The script will interactively prompt you for:
- Whether to commit uncommitted changes
- Whether to recreate existing tags
- Commit messages

**Non-interactive mode (for automation):**
```bash
# Auto-commit changes with default message
AUTOCOMMIT=1 ./build-and-release.sh

# Force recreate tag without prompting
FORCE_TAG=1 ./build-and-release.sh

# Force recreate release without prompting
FORCE_RELEASE=1 ./build-and-release.sh

# Combine all for fully automated release
AUTOCOMMIT=1 FORCE_TAG=1 FORCE_RELEASE=1 ./build-and-release.sh

# Custom commit message
AUTOCOMMIT=1 COMMIT_MESSAGE="Fix: Critical bug fix" ./build-and-release.sh
```

### `quick-release.sh`

**Quick release for already-built APKs:**
- Push current commit
- Create/push tag
- Create GitHub release (without building)

Use this when you've already built the APK and just need to create the release.

**Usage:**
```bash
./quick-release.sh
```

## Requirements

- **gh CLI**: Required for creating releases and uploading APKs
  - Install: https://cli.github.com/
  - Authenticate: `gh auth login`
- **git**: For version control
- **Gradle**: For building the APK

## What the script does

1. **Reads version** from `gradle.properties`
2. **Cleans** previous build artifacts
3. **Builds** release APK (increments version automatically)
4. **Finds** the generated APK file
5. **Commits** changes (gradle.properties with new version)
6. **Pushes** to GitHub
7. **Creates tag** (e.g., `v1.142`)
8. **Creates GitHub release** with:
   - Release notes from recent commits
   - Uploaded APK asset
   - Direct download link

## Troubleshooting

### "gh CLI not found"
Install gh CLI: https://cli.github.com/

### "APK not found"
Build failed. Check the gradle output for errors.

### "Release already exists"
The script will prompt you to delete and recreate, or upload to the existing release.

### Permission denied on git
Check your git permissions in Termux.

## Examples

**Standard release:**
```bash
./build-and-release.sh
# Follow prompts...
```

**Quick automated release:**
```bash
AUTOCOMMIT=1 COMMIT_MESSAGE="Feat: Add new feature" ./build-and-release.sh
```

**Upload APK to existing release:**
If a release already exists, the script will automatically upload the APK to it.

## Files Generated

- `app/build/outputs/apk/release/app-release.apk` - The release APK
- `gradle.properties` - Updated with new version code
- Git tag - e.g., `v1.142`
- GitHub release - with APK attached
