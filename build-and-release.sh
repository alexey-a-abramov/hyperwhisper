#!/data/data/com.termux/files/usr/bin/bash

set -e  # Exit on error

echo "🚀 HyperWhisper Build and Release Script"
echo "========================================"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Read current version
VERSION_CODE=$(grep "VERSION_CODE=" gradle.properties | cut -d'=' -f2)
VERSION_NAME="1.$VERSION_CODE"

echo -e "${BLUE}Current version: v${VERSION_NAME} (code: ${VERSION_CODE})${NC}"
echo

# Step 1: Clean build
echo -e "${YELLOW}Step 1: Cleaning previous builds...${NC}"
./gradlew clean

# Step 2: Build release APK
echo -e "${YELLOW}Step 2: Building release APK...${NC}"
./gradlew assembleRelease

# Find the generated APK
APK_PATH=$(find app/build/outputs/apk/release -name "*.apk" -type f | head -1)

if [ -z "$APK_PATH" ]; then
    echo "❌ Error: APK not found!"
    exit 1
fi

echo -e "${GREEN}✓ APK built successfully: $APK_PATH${NC}"
echo

# Step 3: Check git status
echo -e "${YELLOW}Step 3: Checking git status...${NC}"
if ! git diff-index --quiet HEAD --; then
    echo "📝 Uncommitted changes detected"
    git status --short
    echo
    read -p "Commit these changes? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        read -p "Enter commit message: " COMMIT_MSG
        git add .
        git commit -m "$COMMIT_MSG

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
        echo -e "${GREEN}✓ Changes committed${NC}"
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
TAG_NAME="v${VERSION_NAME}"
echo -e "${YELLOW}Step 5: Creating tag ${TAG_NAME}...${NC}"

if git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
    echo "⚠️  Tag $TAG_NAME already exists"
    read -p "Delete and recreate? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git tag -d "$TAG_NAME"
        git push origin --delete "$TAG_NAME" 2>/dev/null || true
    else
        echo "Skipping tag creation"
        TAG_NAME=""
    fi
fi

if [ -n "$TAG_NAME" ]; then
    git tag "$TAG_NAME"
    git push origin "$TAG_NAME"
    echo -e "${GREEN}✓ Tag ${TAG_NAME} created and pushed${NC}"
fi
echo

# Step 6: Create GitHub release
if [ -n "$TAG_NAME" ]; then
    echo -e "${YELLOW}Step 6: Creating GitHub release...${NC}"

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

    # Create release using GitHub API (works better in Termux)
    if command -v gh &> /dev/null; then
        # Get GitHub token from gh CLI
        GH_TOKEN=$(gh auth token 2>/dev/null)

        if [ -n "$GH_TOKEN" ]; then
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

            if [ -n "$RELEASE_URL" ]; then
                echo -e "${GREEN}✓ GitHub release created!${NC}"

                # Upload APK if we have the upload URL
                if [ -n "$UPLOAD_URL" ] && [ -f "$APK_PATH" ]; then
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
                        echo -e "${YELLOW}⚠️  Failed to upload APK. You can manually attach it at: $RELEASE_URL${NC}"
                    fi
                fi

                echo
                echo -e "${BLUE}Release URL: $RELEASE_URL${NC}"
            else
                echo -e "${YELLOW}⚠️  Failed to create release. Response:${NC}"
                echo "$RESPONSE" | head -3
            fi
        else
            echo "⚠️  Could not get GitHub token."
            echo "Create the release manually at: https://github.com/alexey-a-abramov/hyperwhisper/releases/new"
            echo
            echo "Tag: $TAG_NAME"
            echo "APK: $APK_PATH"
        fi
    else
        echo "⚠️  gh CLI not found. Please install it to create releases automatically."
        echo "Or create the release manually at: https://github.com/alexey-a-abramov/hyperwhisper/releases/new"
        echo
        echo "Tag: $TAG_NAME"
        echo "APK: $APK_PATH"
        echo
        echo "Release notes:"
        echo "$RELEASE_NOTES"
    fi
else
    echo "⚠️  Skipping GitHub release creation"
fi

echo
echo -e "${GREEN}✨ Build and release complete!${NC}"
