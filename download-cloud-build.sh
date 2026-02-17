#!/bin/bash

# Script to download the most recent cloud build
# Waits for build to complete if in progress

set -e

REPO="alexey-a-abramov/hyperwhisper"
WORKFLOW_NAME="Build APKs"
OUTPUT_DIR="cloud-builds"
MAX_WAIT_TIME=1800  # 30 minutes max wait
CHECK_INTERVAL=30   # Check every 30 seconds

echo "============================================"
echo "Cloud Build Downloader"
echo "============================================"
echo ""

# Check if gh is installed
if ! command -v gh &> /dev/null; then
    echo "❌ Error: GitHub CLI (gh) is not installed"
    echo "Install it with: pkg install gh"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "❌ Error: Not authenticated with GitHub CLI"
    echo "Run: gh auth login"
    exit 1
fi

# Get the most recent workflow run
echo "🔍 Fetching most recent workflow run..."
RUN_INFO=$(gh run list --repo "$REPO" --workflow "$WORKFLOW_NAME" --limit 1 --json databaseId,status,conclusion,displayTitle,createdAt)

if [ -z "$RUN_INFO" ] || [ "$RUN_INFO" = "[]" ]; then
    echo "❌ No workflow runs found"
    exit 1
fi

RUN_ID=$(echo "$RUN_INFO" | jq -r '.[0].databaseId')
RUN_STATUS=$(echo "$RUN_INFO" | jq -r '.[0].status')
RUN_CONCLUSION=$(echo "$RUN_INFO" | jq -r '.[0].conclusion')
RUN_TITLE=$(echo "$RUN_INFO" | jq -r '.[0].displayTitle')
RUN_CREATED=$(echo "$RUN_INFO" | jq -r '.[0].createdAt')

echo "📋 Run ID: $RUN_ID"
echo "📝 Title: $RUN_TITLE"
echo "🕐 Created: $RUN_CREATED"
echo "📊 Status: $RUN_STATUS"
echo ""

# Wait for build to complete if in progress
if [ "$RUN_STATUS" = "in_progress" ] || [ "$RUN_STATUS" = "queued" ] || [ "$RUN_STATUS" = "waiting" ]; then
    echo "⏳ Build is $RUN_STATUS, waiting for completion..."
    echo "   (Checking every $CHECK_INTERVAL seconds, max wait: $((MAX_WAIT_TIME/60)) minutes)"
    echo ""

    ELAPSED=0
    while [ "$RUN_STATUS" = "in_progress" ] || [ "$RUN_STATUS" = "queued" ] || [ "$RUN_STATUS" = "waiting" ]; do
        if [ $ELAPSED -ge $MAX_WAIT_TIME ]; then
            echo "❌ Timeout: Build did not complete within $((MAX_WAIT_TIME/60)) minutes"
            exit 1
        fi

        sleep $CHECK_INTERVAL
        ELAPSED=$((ELAPSED + CHECK_INTERVAL))

        # Refresh status
        RUN_INFO=$(gh run view "$RUN_ID" --repo "$REPO" --json status,conclusion)
        RUN_STATUS=$(echo "$RUN_INFO" | jq -r '.status')
        RUN_CONCLUSION=$(echo "$RUN_INFO" | jq -r '.conclusion')

        echo "   ⏱️  Waited $ELAPSED seconds... Status: $RUN_STATUS"
    done

    echo ""
    echo "✅ Build completed!"
    echo ""
fi

# Check if build was successful
if [ "$RUN_CONCLUSION" != "success" ]; then
    echo "❌ Build failed with conclusion: $RUN_CONCLUSION"
    echo ""
    echo "📄 View build logs:"
    echo "   gh run view $RUN_ID --repo $REPO --log-failed"
    echo "   Or visit: https://github.com/$REPO/actions/runs/$RUN_ID"
    exit 1
fi

echo "✅ Build completed successfully!"
echo ""

# Create output directory with timestamp
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BUILD_DIR="$OUTPUT_DIR/$TIMESTAMP"
mkdir -p "$BUILD_DIR"

echo "📥 Downloading artifacts to: $BUILD_DIR/"
echo ""

# Download cloud APK artifact
echo "   Downloading cloud APK..."
if gh run download "$RUN_ID" --repo "$REPO" --name "hyperwhisper-cloud-debug" --dir "$BUILD_DIR/cloud" 2>/dev/null; then
    echo "   ✅ Downloaded cloud APK"
else
    echo "   ⚠️  Warning: cloud APK not found (build may have failed for this variant)"
fi

# Download local APK artifact (if it exists)
echo "   Downloading local APK..."
if gh run download "$RUN_ID" --repo "$REPO" --name "hyperwhisper-local-debug" --dir "$BUILD_DIR/local" 2>/dev/null; then
    echo "   ✅ Downloaded local APK"
else
    echo "   ℹ️  local APK not available (build may have failed for this variant)"
fi

# Download build summary
echo "   Downloading build summary..."
gh run view "$RUN_ID" --repo "$REPO" > "$BUILD_DIR/summary.txt" 2>&1 || true
echo "   ✅ Downloaded build summary"

# Create a symlink to latest
cd "$OUTPUT_DIR"
rm -f latest
ln -s "$TIMESTAMP" latest
cd - > /dev/null

echo ""
echo "============================================"
echo "✅ Download Complete!"
echo "============================================"
echo ""

# Show downloaded files
echo "📦 Downloaded files:"
find "$BUILD_DIR" -name "*.apk" -o -name "summary.txt" | while read file; do
    if [ -f "$file" ]; then
        SIZE=$(du -h "$file" | cut -f1)
        RELPATH=$(echo "$file" | sed "s|$BUILD_DIR/||")
        echo "   $RELPATH ($SIZE)"
    fi
done

echo ""
echo "📂 Build directory: $BUILD_DIR/"
echo "🔗 Latest symlink: $OUTPUT_DIR/latest -> $TIMESTAMP"
echo ""

# Show APK paths
CLOUD_APK="$BUILD_DIR/cloud/app-cloud-debug.apk"
LOCAL_APK="$BUILD_DIR/local/app-local-debug.apk"

if [ -f "$CLOUD_APK" ]; then
    ABS_CLOUD_PATH=$(cd "$(dirname "$CLOUD_APK")" && pwd)/$(basename "$CLOUD_APK")
    echo "☁️  Cloud APK (cloud APIs only):"
    echo "   $ABS_CLOUD_PATH"
fi

if [ -f "$LOCAL_APK" ]; then
    ABS_LOCAL_PATH=$(cd "$(dirname "$LOCAL_APK")" && pwd)/$(basename "$LOCAL_APK")
    echo "📱 Local APK (on-device whisper.cpp):"
    echo "   $ABS_LOCAL_PATH"
fi

if [ -f "$CLOUD_APK" ] || [ -f "$LOCAL_APK" ]; then
    echo ""
    echo "📲 Install with: adb install \"<path-to-apk>\""
fi

echo ""
echo "✅ Done!"
