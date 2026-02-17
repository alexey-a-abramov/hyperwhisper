#!/data/data/com.termux/files/usr/bin/bash

set -e  # Exit on error

echo "🚀 Quick Release Script"
echo "======================"

# Read current version
VERSION_CODE=$(grep "VERSION_CODE=" gradle.properties | cut -d'=' -f2)
VERSION_NAME="1.$VERSION_CODE"
TAG_NAME="v${VERSION_NAME}"

echo "Version: ${TAG_NAME}"
echo

# Push current commit
echo "📤 Pushing to GitHub..."
git push origin master
echo "✓ Pushed to origin/master"
echo

# Create and push tag
echo "🏷️  Creating tag ${TAG_NAME}..."
if git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
    echo "⚠️  Tag $TAG_NAME already exists locally, skipping tag creation"
else
    git tag "$TAG_NAME"
    echo "✓ Tag created"
fi

if git ls-remote --tags origin "$TAG_NAME" | grep -q "$TAG_NAME"; then
    echo "⚠️  Tag $TAG_NAME already exists on remote, skipping push"
else
    git push origin "$TAG_NAME"
    echo "✓ Tag pushed"
fi
echo

# Create GitHub release using API (works better in Termux)
if command -v gh &> /dev/null; then
    echo "📦 Creating GitHub release..."

    # Get commits since last tag
    LAST_TAG=$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || echo "")
    if [ -n "$LAST_TAG" ]; then
        COMMITS=$(git log ${LAST_TAG}..HEAD --pretty=format:"- %s" --no-merges)
    else
        COMMITS=$(git log -5 --pretty=format:"- %s" --no-merges)
    fi

    # Escape for JSON
    NOTES="## Changes\n\n${COMMITS}\n\nBuilt on $(date '+%Y-%m-%d')"

    # Get GitHub token from gh CLI
    GH_TOKEN=$(gh auth token 2>/dev/null)

    if [ -n "$GH_TOKEN" ]; then
        # Create release using GitHub API
        RESPONSE=$(curl -s -X POST \
            -H "Accept: application/vnd.github+json" \
            -H "Authorization: Bearer $GH_TOKEN" \
            -H "X-GitHub-Api-Version: 2022-11-28" \
            https://api.github.com/repos/alexey-a-abramov/hyperwhisper/releases \
            -d "{\"tag_name\":\"$TAG_NAME\",\"name\":\"HyperWhisper $TAG_NAME\",\"body\":\"$NOTES\",\"draft\":false,\"prerelease\":false}")

        # Check if release was created
        if echo "$RESPONSE" | grep -q '"html_url"'; then
            RELEASE_URL=$(echo "$RESPONSE" | grep -o '"html_url": *"[^"]*"' | head -1 | cut -d'"' -f4)
            echo "✓ Release created!"
            echo
            echo "🔗 $RELEASE_URL"
        else
            echo "⚠️  Failed to create release. Response:"
            echo "$RESPONSE" | head -3
        fi
    else
        echo "⚠️  Could not get GitHub token. Create release manually at:"
        echo "   https://github.com/alexey-a-abramov/hyperwhisper/releases/new"
    fi
else
    echo "⚠️  gh CLI not found. Create release manually at:"
    echo "   https://github.com/alexey-a-abramov/hyperwhisper/releases/new"
    echo "   Tag: $TAG_NAME"
fi

echo
echo "✨ Done!"
