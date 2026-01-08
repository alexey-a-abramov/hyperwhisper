#!/bin/bash

# HyperWhisper Release Script
# Automates the process of creating a new release

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Get current version from gradle.properties
VERSION=$(grep VERSION_CODE gradle.properties | cut -d= -f2)
TAG="v1.$VERSION"

echo ""
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}  HyperWhisper Release Creator${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

# Check if we're on master branch
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "master" ]; then
    echo -e "${YELLOW}⚠ Warning: You're on branch '$BRANCH', not 'master'${NC}"
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 0
    fi
fi

# Check for uncommitted changes
if ! git diff-index --quiet HEAD --; then
    echo -e "${RED}❌ You have uncommitted changes!${NC}"
    echo "   Please commit or stash them first."
    echo ""
    git status --short
    exit 1
fi

# Check if tag already exists
if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo -e "${RED}❌ Tag $TAG already exists!${NC}"
    echo ""
    echo "Options:"
    echo "  1. Increment VERSION_CODE in gradle.properties"
    echo "  2. Delete existing tag: git tag -d $TAG && git push origin :refs/tags/$TAG"
    exit 1
fi

echo -e "${GREEN}Version:${NC} $TAG"
echo -e "${GREEN}Commit:${NC} $(git log -1 --oneline)"
echo ""

# Get release notes
echo -e "${YELLOW}Enter release notes (press Ctrl+D when done):${NC}"
echo -e "${YELLOW}(Or press Enter for default message)${NC}"
RELEASE_NOTES=$(cat)

if [ -z "$RELEASE_NOTES" ]; then
    RELEASE_NOTES="Bug fixes and improvements"
fi

echo ""
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}  Release Summary${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""
echo -e "${GREEN}Tag:${NC} $TAG"
echo -e "${GREEN}Release Notes:${NC}"
echo "$RELEASE_NOTES"
echo ""
echo "This will:"
echo "  1. Create annotated tag: $TAG"
echo "  2. Push tag to GitHub"
echo "  3. Trigger GitHub Actions workflow"
echo "  4. Build and create release at:"
echo "     https://github.com/alexey-a-abramov/hyperwhisper/releases/tag/$TAG"
echo ""

read -p "Continue? (y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Cancelled.${NC}"
    exit 0
fi

# Create annotated tag with release notes
echo ""
echo -e "${BLUE}Creating tag...${NC}"
git tag -a "$TAG" -m "$RELEASE_NOTES"

# Push tag
echo -e "${BLUE}Pushing tag to GitHub...${NC}"
git push --tags

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}  ✓ Release Created!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "Release: https://github.com/alexey-a-abramov/hyperwhisper/releases/tag/$TAG"
echo "Actions: https://github.com/alexey-a-abramov/hyperwhisper/actions"
echo ""
echo "The GitHub Actions workflow is now building the APK."
echo "It will take a few minutes to complete."
echo ""
