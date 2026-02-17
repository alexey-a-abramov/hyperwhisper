#!/data/data/com.termux/files/usr/bin/bash

set -e  # Exit on error

echo "🚀 HyperWhisper Build and Release Script"
echo "========================================"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Read current version AFTER build (since build increments it)
# But we need to know what it will be for the tag
VERSION_CODE=$(grep "VERSION_CODE=" gradle.properties | cut -d'=' -f2)
NEXT_VERSION_CODE=$((VERSION_CODE + 1))
VERSION_NAME="1.$NEXT_VERSION_CODE"

echo -e "${BLUE}Building version: v${VERSION_NAME} (code: ${NEXT_VERSION_CODE})${NC}"
echo

# Step 1: Clean build
echo -e "${YELLOW}Step 1: Cleaning previous builds...${NC}"
./gradlew clean

# Step 2: Build release APK
echo -e "${YELLOW}Step 2: Building release APK...${NC}"
if ./gradlew assembleRelease; then
    echo -e "${GREEN}✓ Build completed${NC}"
else
    echo -e "${RED}❌ Build failed!${NC}"
    exit 1
fi

# Update version after build (gradle increments it)
ACTUAL_VERSION_CODE=$(grep "VERSION_CODE=" gradle.properties | cut -d'=' -f2)
VERSION_NAME="1.$ACTUAL_VERSION_CODE"
TAG_NAME="v${VERSION_NAME}"

echo -e "${BLUE}Built version: v${VERSION_NAME}${NC}"

# Find the generated APK
APK_PATH=$(find app/build/outputs/apk/release -name "*.apk" -type f | head -1)

if [ -z "$APK_PATH" ]; then
    echo -e "${RED}❌ Error: APK not found!${NC}"
    exit 1
fi

APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
echo -e "${GREEN}✓ APK built successfully: $APK_PATH ($APK_SIZE)${NC}"
echo

# Step 3: Check git status and commit
echo -e "${YELLOW}Step 3: Checking git status...${NC}"
if ! git diff-index --quiet HEAD --; then
    echo "📝 Uncommitted changes detected"
    git status --short
    echo

    # Auto-commit if AUTOCOMMIT is set, otherwise ask
    if [ "${AUTOCOMMIT}" = "1" ]; then
        COMMIT_MSG="${COMMIT_MESSAGE:-Build: Release v${VERSION_NAME}}"
        git add .
        git commit -m "$COMMIT_MSG

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
        echo -e "${GREEN}✓ Changes committed automatically${NC}"
    else
        read -p "Commit these changes? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            read -p "Enter commit message: " COMMIT_MSG
            git add .
            git commit -m "$COMMIT_MSG

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
            echo -e "${GREEN}✓ Changes committed${NC}"
        else
            echo -e "${YELLOW}⚠️  Skipping commit${NC}"
        fi
    fi
else
    echo -e "${GREEN}✓ Working tree is clean${NC}"
fi
echo

# Step 4: Push to GitHub
echo -e "${YELLOW}Step 4: Pushing to GitHub...${NC}"
git push origin master
echo -e "${GREEN}✓ Pushed to origin/master${NC}"
echo

# Step 5: Create and push tag
echo -e "${YELLOW}Step 5: Creating tag ${TAG_NAME}...${NC}"

# Check if tag exists locally
if git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Tag $TAG_NAME already exists locally${NC}"
    if [ "${FORCE_TAG}" = "1" ]; then
        git tag -d "$TAG_NAME"
        git push origin --delete "$TAG_NAME" 2>/dev/null || true
        echo "Recreating tag..."
    else
        read -p "Delete and recreate? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            git tag -d "$TAG_NAME"
            git push origin --delete "$TAG_NAME" 2>/dev/null || true
        else
            echo "Using existing tag"
        fi
    fi
fi

# Create tag if it doesn't exist
if ! git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
    git tag "$TAG_NAME"
    git push origin "$TAG_NAME"
    echo -e "${GREEN}✓ Tag ${TAG_NAME} created and pushed${NC}"
else
    echo -e "${GREEN}✓ Using existing tag ${TAG_NAME}${NC}"
fi
echo

# Step 6: Create GitHub release and upload APK
echo -e "${YELLOW}Step 6: Creating GitHub release and uploading APK...${NC}"

# Get recent commits for release notes
LAST_TAG=$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || echo "")

if [ -n "$LAST_TAG" ]; then
    COMMITS=$(git log ${LAST_TAG}..HEAD --pretty=format:"- %s" --no-merges)
else
    COMMITS=$(git log -10 --pretty=format:"- %s" --no-merges)
fi

# Create release notes
RELEASE_NOTES="## What's New in v${VERSION_NAME}

${COMMITS}

## Download

Install the APK file attached below.

---
Built on $(date '+%Y-%m-%d %H:%M:%S')"

# Try using gh CLI first (simpler and more reliable)
if command -v gh &> /dev/null; then
    echo "Using gh CLI..."

    # Check if release already exists
    if gh release view "$TAG_NAME" >/dev/null 2>&1; then
        echo -e "${YELLOW}Release $TAG_NAME already exists${NC}"

        # Delete and recreate if FORCE_RELEASE is set
        if [ "${FORCE_RELEASE}" = "1" ]; then
            echo "Deleting existing release..."
            gh release delete "$TAG_NAME" --yes

            echo "Creating new release..."
            gh release create "$TAG_NAME" \
                --title "HyperWhisper $TAG_NAME" \
                --notes "$RELEASE_NOTES" \
                "$APK_PATH"

            echo -e "${GREEN}✓ Release recreated and APK uploaded!${NC}"
        else
            # Just upload APK to existing release
            echo "Uploading APK to existing release..."

            # Delete old APK assets first
            APK_NAME=$(basename "$APK_PATH")
            gh release delete-asset "$TAG_NAME" "$APK_NAME" --yes 2>/dev/null || true

            # Upload new APK
            gh release upload "$TAG_NAME" "$APK_PATH" --clobber

            echo -e "${GREEN}✓ APK uploaded to existing release!${NC}"
        fi
    else
        # Create new release with APK
        echo "Creating new release..."
        gh release create "$TAG_NAME" \
            --title "HyperWhisper $TAG_NAME" \
            --notes "$RELEASE_NOTES" \
            "$APK_PATH"

        echo -e "${GREEN}✓ Release created and APK uploaded!${NC}"
    fi

    # Get release URL
    RELEASE_URL=$(gh release view "$TAG_NAME" --json url --jq .url)
    echo
    echo -e "${BLUE}🔗 Release URL: $RELEASE_URL${NC}"

else
    # Fallback to curl/GitHub API
    echo "gh CLI not found, using GitHub API..."

    GH_TOKEN=$(gh auth token 2>/dev/null || echo "")

    if [ -z "$GH_TOKEN" ]; then
        echo -e "${RED}❌ Cannot create release: gh CLI not found and no token available${NC}"
        echo "Please install gh CLI: https://cli.github.com/"
        echo
        echo "Or manually create release at:"
        echo "https://github.com/alexey-a-abramov/hyperwhisper/releases/new"
        echo "Tag: $TAG_NAME"
        echo "APK: $APK_PATH"
        exit 1
    fi

    # Escape newlines and quotes for JSON
    RELEASE_NOTES_JSON=$(echo "$RELEASE_NOTES" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g' | awk '{printf "%s\\n", $0}' | sed '$ s/\\n$//')

    # Create release using GitHub API
    RESPONSE=$(curl -s -X POST \
        -H "Accept: application/vnd.github+json" \
        -H "Authorization: Bearer $GH_TOKEN" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        https://api.github.com/repos/alexey-a-abramov/hyperwhisper/releases \
        -d "{\"tag_name\":\"$TAG_NAME\",\"name\":\"HyperWhisper $TAG_NAME\",\"body\":\"$RELEASE_NOTES_JSON\",\"draft\":false,\"prerelease\":false}")

    # Extract upload URL and release URL
    RELEASE_URL=$(echo "$RESPONSE" | grep -o '"html_url": *"[^"]*"' | head -1 | cut -d'"' -f4)
    UPLOAD_URL=$(echo "$RESPONSE" | grep -o '"upload_url": *"[^"]*"' | head -1 | cut -d'"' -f4 | sed 's/{.*//')

    if [ -n "$RELEASE_URL" ] && [ -n "$UPLOAD_URL" ]; then
        echo -e "${GREEN}✓ GitHub release created${NC}"

        # Upload APK
        echo "Uploading APK..."
        APK_NAME=$(basename "$APK_PATH")

        UPLOAD_RESPONSE=$(curl -s -X POST \
            -H "Accept: application/vnd.github+json" \
            -H "Authorization: Bearer $GH_TOKEN" \
            -H "Content-Type: application/vnd.android.package-archive" \
            "${UPLOAD_URL}?name=${APK_NAME}" \
            --data-binary @"$APK_PATH")

        if echo "$UPLOAD_RESPONSE" | grep -q '"browser_download_url"'; then
            echo -e "${GREEN}✓ APK uploaded successfully!${NC}"
        else
            echo -e "${YELLOW}⚠️  Failed to upload APK${NC}"
            echo "Upload response:"
            echo "$UPLOAD_RESPONSE" | head -5
            echo
            echo "You can manually attach the APK at: $RELEASE_URL"
            echo "APK location: $APK_PATH"
        fi

        echo
        echo -e "${BLUE}🔗 Release URL: $RELEASE_URL${NC}"
    else
        echo -e "${RED}❌ Failed to create release${NC}"
        echo "Response:"
        echo "$RESPONSE" | head -5
        exit 1
    fi
fi

echo
echo -e "${GREEN}✨ Build and release complete!${NC}"
echo -e "${BLUE}Version: $VERSION_NAME${NC}"
echo -e "${BLUE}APK: $APK_PATH ($APK_SIZE)${NC}"
