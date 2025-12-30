# Build Scripts Documentation

## Cloud Build Script (`build-cloud.sh`)

Automates the process of pushing changes, triggering a cloud build via GitHub Actions, monitoring the build, and downloading the resulting APK with logs.

### Prerequisites

1. **GitHub CLI (`gh`) installed and authenticated:**
   ```bash
   # Install gh (if not already installed)
   pkg install gh  # On Termux
   # or
   brew install gh  # On macOS
   # or
   sudo apt install gh  # On Ubuntu/Debian

   # Authenticate
   gh auth login
   ```

2. **Git repository configured:**
   - Must have changes to push or be ready to trigger a build
   - Remote repository should be set up

### Usage

**Basic usage:**
```bash
./build-cloud.sh
```

**What the script does:**

1. **Pushes changes to GitHub** - Commits must already be staged
2. **Triggers GitHub Actions workflow** - Builds cloud flavor only
3. **Monitors build progress** - Shows real-time status
4. **Downloads APK artifact** - Saves to `cloud-builds/TIMESTAMP/apk/`
5. **Downloads build logs** - Saves to `cloud-builds/TIMESTAMP/build.log`
6. **Creates summary** - Saves to `cloud-builds/TIMESTAMP/summary.txt`
7. **Creates `latest` symlink** - Points to most recent build

### Output Structure

```
cloud-builds/
├── latest -> 20251230_143052  # Symlink to latest build
├── 20251230_143052/            # Build from Dec 30, 2:30 PM
│   ├── apk/
│   │   └── app-cloud-debug.apk
│   ├── build.log               # Full build logs
│   └── summary.txt             # Workflow summary
├── 20251230_120000/            # Previous build
│   ├── apk/
│   │   └── app-cloud-debug.apk
│   ├── build.log
│   └── summary.txt
...
```

### Quick Install

After build completes:
```bash
# Install latest cloud APK
adb install cloud-builds/latest/apk/app-cloud-debug.apk

# Or use the full path shown in script output
adb install cloud-builds/20251230_143052/apk/app-cloud-debug.apk
```

### View Logs

```bash
# View latest build log
cat cloud-builds/latest/build.log

# View summary
cat cloud-builds/latest/summary.txt

# List all builds
ls -lh cloud-builds/
```

### Troubleshooting

**"Failed to push changes"**
- Ensure you have changes committed: `git status`
- Check remote is configured: `git remote -v`
- Verify you have push access to the repository

**"Failed to trigger workflow"**
- Ensure `gh` is authenticated: `gh auth status`
- Check workflow file exists: `.github/workflows/build-apks.yml`
- Verify repository name matches: `alexey-a-abramov/hyperwhisper`

**"Build failed or was cancelled"**
- Check the build logs saved to `cloud-builds/*/build.log`
- View workflow in GitHub web interface
- Common issues:
  - Gradle compilation errors
  - Missing dependencies
  - Syntax errors in code

**"Could not download full logs"**
- Logs may be too large
- Try viewing on GitHub: `gh run view <RUN_ID> --web`
- Check network connection

### Advanced Usage

**Manually trigger build without pushing:**
```bash
# Skip the push step, just trigger workflow
gh workflow run build-apks.yml \
  --repo alexey-a-abramov/hyperwhisper \
  --field build_local=false \
  --field build_cloud=true
```

**Download specific workflow run:**
```bash
# List recent runs
gh run list --repo alexey-a-abramov/hyperwhisper --limit 10

# Download specific run
gh run download <RUN_ID> \
  --repo alexey-a-abramov/hyperwhisper \
  --name "hyperwhisper-cloud-debug" \
  --dir ./my-output-dir
```

**Watch any workflow run:**
```bash
# Get run ID from list
gh run list --repo alexey-a-abramov/hyperwhisper

# Watch specific run
gh run watch <RUN_ID> --repo alexey-a-abramov/hyperwhisper
```

**View logs in browser:**
```bash
gh run view <RUN_ID> --web --repo alexey-a-abramov/hyperwhisper
```

### Configuration

Edit the script to change:

```bash
# Output directory (default: cloud-builds)
OUTPUT_DIR="my-builds"

# Workflow file name (default: build-apks.yml)
WORKFLOW_NAME="my-workflow.yml"

# Repository (default: alexey-a-abramov/hyperwhisper)
REPO="myusername/myrepo"
```

### Script Features

- ✅ Automatic push and build trigger
- ✅ Real-time build monitoring
- ✅ Automatic artifact download
- ✅ Build log archival
- ✅ Timestamped output directories
- ✅ Latest build symlink
- ✅ Colored terminal output
- ✅ Error handling and validation
- ✅ APK size display
- ✅ Easy installation commands

### Example Run

```bash
$ ./build-cloud.sh

════════════════════════════════════════════════
   HyperWhisper Cloud Build & Download Script
════════════════════════════════════════════════

[1/5] Pushing changes to GitHub...
✓ Changes pushed successfully

[2/5] Triggering cloud build workflow...
✓ Workflow triggered

Waiting 5 seconds for workflow to start...

[3/5] Finding workflow run...
✓ Found workflow run: #42

[4/5] Monitoring build progress...
Press Ctrl+C to stop watching (build will continue in background)

Build Cloud Flavor (Debug)  in_progress  42
✓ Build completed successfully!

[5/5] Downloading APK and logs...
Output directory: cloud-builds/20251230_143052

Downloading cloud APK...
✓ APK downloaded
  File: app-cloud-debug.apk
  Size: 18M
  Path: cloud-builds/20251230_143052/apk/app-cloud-debug.apk

Downloading build logs...
✓ Build logs saved to: cloud-builds/20251230_143052/build.log

Saving workflow summary...
✓ Summary saved to: cloud-builds/20251230_143052/summary.txt

✓ Created symlink: cloud-builds/latest -> 20251230_143052

════════════════════════════════════════════════
Build completed successfully!
════════════════════════════════════════════════

Build artifacts location:
  cloud-builds/20251230_143052/

Contents:
total 18M
drwxr-xr-x 2 user user 4.0K Dec 30 14:30 apk
-rw-r--r-- 1 user user  15K Dec 30 14:30 build.log
-rw-r--r-- 1 user user 1.2K Dec 30 14:30 summary.txt

Quick access:
  Latest build: cloud-builds/latest/
  APK: cloud-builds/latest/apk/*.apk
  Logs: cloud-builds/latest/build.log

To install APK:
  adb install "cloud-builds/20251230_143052/apk/app-cloud-debug.apk"
```

### CI/CD Integration

This script can be integrated into your development workflow:

**After making changes:**
```bash
# 1. Make changes to code
vim app/src/main/...

# 2. Commit changes
git add -A
git commit -m "Feature: New cloud feature"

# 3. Build and download
./build-cloud.sh

# 4. Install and test
adb install cloud-builds/latest/apk/app-cloud-debug.apk
```

**Automated testing:**
```bash
#!/bin/bash
# test-cloud-build.sh

./build-cloud.sh || exit 1

# Install on connected device
adb install -r cloud-builds/latest/apk/app-cloud-debug.apk

# Run tests
adb shell am instrument -w com.hyperwhisper.cloud/androidx.test.runner.AndroidJUnitRunner
```

---

## Future Scripts

### Local Build Script (TODO)

Similar script for local flavor (with native code):
- `build-local.sh` - Triggers local flavor build
- Downloads ~103MB APK with native libraries
- Build time: ~15-20 minutes

### Both Flavors Script (TODO)

Build both flavors in parallel:
- `build-all.sh` - Triggers both local and cloud builds
- Downloads both APKs
- Compares sizes and features

---

## See Also

- [GitHub Actions Workflow](.github/workflows/build-apks.yml)
- [Implementation Plan](/.claude/plans/shiny-napping-quiche.md)
- [GitHub CLI Documentation](https://cli.github.com/manual/)
