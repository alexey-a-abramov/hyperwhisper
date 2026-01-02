#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
OUTPUT_DIR="builds/downloads"
WORKFLOW_NAME="build-apks.yml"
REPO="alexey-a-abramov/hyperwhisper"

echo -e "${BLUE}════════════════════════════════════════════════${NC}"
echo -e "${BLUE}   HyperWhisper Cloud Build & Download Script   ${NC}"
echo -e "${BLUE}════════════════════════════════════════════════${NC}"
echo ""

# Step 1: Push changes to GitHub
echo -e "${YELLOW}[1/5] Pushing changes to GitHub...${NC}"
if git push; then
    echo -e "${GREEN}✓ Changes pushed successfully${NC}"
else
    echo -e "${RED}✗ Failed to push changes${NC}"
    exit 1
fi
echo ""

# Step 2: Trigger GitHub Actions workflow
echo -e "${YELLOW}[2/5] Triggering cloud build workflow...${NC}"
if gh workflow run "$WORKFLOW_NAME" \
    --repo "$REPO" \
    --field build_local=false \
    --field build_cloud=true; then
    echo -e "${GREEN}✓ Workflow triggered${NC}"
else
    echo -e "${RED}✗ Failed to trigger workflow${NC}"
    exit 1
fi
echo ""

# Wait a bit for workflow to appear in the list
echo -e "${BLUE}Waiting 5 seconds for workflow to start...${NC}"
sleep 5
echo ""

# Step 3: Get the latest workflow run ID
echo -e "${YELLOW}[3/5] Finding workflow run...${NC}"
RUN_ID=$(gh run list \
    --repo "$REPO" \
    --workflow "$WORKFLOW_NAME" \
    --limit 1 \
    --json databaseId \
    --jq '.[0].databaseId')

if [ -z "$RUN_ID" ]; then
    echo -e "${RED}✗ Could not find workflow run${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Found workflow run: #$RUN_ID${NC}"
echo ""

# Step 4: Watch workflow status
echo -e "${YELLOW}[4/5] Monitoring build progress...${NC}"
echo -e "${BLUE}Press Ctrl+C to stop watching (build will continue in background)${NC}"
echo ""

# Watch the workflow run
if gh run watch "$RUN_ID" --repo "$REPO"; then
    echo -e "${GREEN}✓ Build completed successfully!${NC}"
else
    echo -e "${RED}✗ Build failed or was cancelled${NC}"
    echo ""
    echo -e "${YELLOW}Fetching logs...${NC}"
    gh run view "$RUN_ID" --repo "$REPO" --log-failed
    exit 1
fi
echo ""

# Step 5: Download artifacts
echo -e "${YELLOW}[5/5] Downloading APK and logs...${NC}"

# Create output directory with timestamp
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BUILD_DIR="$OUTPUT_DIR/$TIMESTAMP"
mkdir -p "$BUILD_DIR"

echo -e "${BLUE}Output directory: $BUILD_DIR${NC}"
echo ""

# Download cloud APK artifact
echo -e "${BLUE}Downloading cloud APK...${NC}"
if gh run download "$RUN_ID" \
    --repo "$REPO" \
    --name "hyperwhisper-cloud-debug" \
    --dir "$BUILD_DIR/cloud"; then
    echo -e "${GREEN}✓ Cloud APK downloaded${NC}"

    # Find and display APK info
    APK_FILE=$(find "$BUILD_DIR/cloud" -name "*.apk" -type f)
    if [ -n "$APK_FILE" ]; then
        APK_SIZE=$(du -h "$APK_FILE" | cut -f1)
        echo -e "${GREEN}  File: $(basename "$APK_FILE")${NC}"
        echo -e "${GREEN}  Size: $APK_SIZE${NC}"
        echo -e "${GREEN}  Path: $APK_FILE${NC}"
    fi
else
    echo -e "${RED}✗ Failed to download cloud APK${NC}"
fi
echo ""

# Download local APK artifact (if available)
echo -e "${BLUE}Downloading local APK...${NC}"
if gh run download "$RUN_ID" \
    --repo "$REPO" \
    --name "hyperwhisper-local-debug" \
    --dir "$BUILD_DIR/local" 2>/dev/null; then
    echo -e "${GREEN}✓ Local APK downloaded${NC}"

    # Find and display APK info
    APK_FILE_LOCAL=$(find "$BUILD_DIR/local" -name "*.apk" -type f)
    if [ -n "$APK_FILE_LOCAL" ]; then
        APK_SIZE_LOCAL=$(du -h "$APK_FILE_LOCAL" | cut -f1)
        echo -e "${GREEN}  File: $(basename "$APK_FILE_LOCAL")${NC}"
        echo -e "${GREEN}  Size: $APK_SIZE_LOCAL${NC}"
        echo -e "${GREEN}  Path: $APK_FILE_LOCAL${NC}"
    fi
else
    echo -e "${YELLOW}⚠ Local APK not available (workflow may have built cloud only)${NC}"
fi
echo ""

# Download build logs
echo -e "${BLUE}Downloading build logs...${NC}"
if gh run view "$RUN_ID" \
    --repo "$REPO" \
    --log > "$BUILD_DIR/build.log"; then
    echo -e "${GREEN}✓ Build logs saved to: $BUILD_DIR/build.log${NC}"
else
    echo -e "${YELLOW}⚠ Could not download full logs${NC}"
fi
echo ""

# Save workflow summary
echo -e "${BLUE}Saving workflow summary...${NC}"
gh run view "$RUN_ID" \
    --repo "$REPO" > "$BUILD_DIR/summary.txt"
echo -e "${GREEN}✓ Summary saved to: $BUILD_DIR/summary.txt${NC}"
echo ""

# Create a "latest" symlink
LATEST_LINK="$OUTPUT_DIR/latest"
if [ -L "$LATEST_LINK" ]; then
    rm "$LATEST_LINK"
fi
# Use relative path for symlink
cd "$OUTPUT_DIR"
ln -s "$TIMESTAMP" "latest"
cd - > /dev/null
echo -e "${GREEN}✓ Created symlink: $OUTPUT_DIR/latest -> $TIMESTAMP${NC}"
echo ""

# Final summary
echo -e "${BLUE}════════════════════════════════════════════════${NC}"
echo -e "${GREEN}Build completed successfully!${NC}"
echo -e "${BLUE}════════════════════════════════════════════════${NC}"
echo ""
echo -e "Build artifacts location:"
echo -e "  ${BLUE}$BUILD_DIR/${NC}"
echo ""
echo -e "Contents:"
ls -lh "$BUILD_DIR"
echo ""
echo -e "Quick access:"
echo -e "  ${BLUE}Latest build: $OUTPUT_DIR/latest/${NC}"
echo -e "  ${BLUE}Cloud APK: $OUTPUT_DIR/latest/cloud/*.apk${NC}"
echo -e "  ${BLUE}Local APK: $OUTPUT_DIR/latest/local/*.apk${NC}"
echo -e "  ${BLUE}Logs: $OUTPUT_DIR/latest/build.log${NC}"
echo ""
echo -e "${GREEN}To install APKs:${NC}"
if [ -n "$APK_FILE" ]; then
    echo -e "  ${BLUE}adb install \"$APK_FILE\"${NC} (cloud)"
fi
if [ -n "$APK_FILE_LOCAL" ]; then
    echo -e "  ${BLUE}adb install \"$APK_FILE_LOCAL\"${NC} (local)"
fi
echo ""
