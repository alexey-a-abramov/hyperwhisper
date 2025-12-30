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
echo "   Downloading cloud-debug.apk..."
if gh run download "$RUN_ID" --repo "$REPO" --name "cloud-debug" --dir "$BUILD_DIR" 2>/dev/null; then
    echo "   ✅ Downloaded cloud-debug.apk"
else
    echo "   ⚠️  Warning: cloud-debug.apk not found (build may have failed for this variant)"
fi

# Download local APK artifact (if it exists)
echo "   Downloading local-debug.apk (if available)..."
if gh run download "$RUN_ID" --repo "$REPO" --name "local-debug" --dir "$BUILD_DIR" 2>/dev/null; then
    echo "   ✅ Downloaded local-debug.apk"
else
    echo "   ℹ️  local-debug.apk not available (expected if only cloud variant built)"
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
for file in "$BUILD_DIR"/*; do
    if [ -f "$file" ]; then
        SIZE=$(du -h "$file" | cut -f1)
        FILENAME=$(basename "$file")
        echo "   $FILENAME ($SIZE)"

        # Show full path for APK files
        if [[ "$FILENAME" == *.apk ]]; then
            ABS_PATH=$(cd "$(dirname "$file")" && pwd)/$(basename "$file")
            echo "   📍 Full path: $ABS_PATH"
        fi
    fi
done

echo ""
echo "📂 Build directory: $BUILD_DIR/"
echo "🔗 Latest symlink: $OUTPUT_DIR/latest -> $TIMESTAMP"
echo ""

# If cloud APK exists, show its path prominently
CLOUD_APK="$BUILD_DIR/cloud-debug.apk"
if [ -f "$CLOUD_APK" ]; then
    ABS_APK_PATH=$(cd "$(dirname "$CLOUD_APK")" && pwd)/$(basename "$CLOUD_APK")
    echo "🎯 Cloud APK ready for installation:"
    echo "   $ABS_APK_PATH"
    echo ""
    echo "📱 Install with: adb install \"$ABS_APK_PATH\""
fi

echo ""
echo "✅ Done!"
