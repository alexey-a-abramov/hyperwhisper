#!/bin/bash

# Build script for Android/Termux environment
# Automatically handles AAPT2 configuration for ARM64 builds
# All APKs are output to the builds/ directory

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_PROPS="$SCRIPT_DIR/gradle.properties"
AAPT2_LINE="android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}HyperWhisper Android Build Script${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Parse arguments
FLAVOR="${1:-cloudonly}"
VALID_FLAVORS=("cloudonly" "cloud" "local" "all")

if [[ ! " ${VALID_FLAVORS[@]} " =~ " ${FLAVOR} " ]]; then
    echo -e "${RED}❌ Invalid flavor: $FLAVOR${NC}"
    echo -e "Usage: $0 [cloudonly|cloud|local|all]"
    echo -e "  cloudonly - Cloud-only build (no native code, no local option) - for local Android builds"
    echo -e "  cloud     - Cloud build (with local mode option available) - for GitHub Actions"
    echo -e "  local     - Local build (with pre-built native libs) - for GitHub Actions"
    echo -e "  all       - Build all flavors"
    exit 1
fi

echo -e "${YELLOW}📋 Build flavor: $FLAVOR${NC}"
echo ""

# Check if running on Android
if [[ ! -d "/data/data/com.termux" ]]; then
    echo -e "${YELLOW}⚠️  Warning: Not running on Termux/Android${NC}"
    echo -e "This script is designed for Android/Termux environment."
    echo -e "For other platforms, use: ./gradlew assemble<Flavor>Debug"
    echo ""
fi

# Backup gradle.properties
echo -e "${BLUE}📦 Backing up gradle.properties...${NC}"
cp "$GRADLE_PROPS" "$GRADLE_PROPS.bak"

# Function to restore gradle.properties
restore_gradle_props() {
    if [[ -f "$GRADLE_PROPS.bak" ]]; then
        echo -e "${BLUE}🔄 Restoring gradle.properties...${NC}"
        mv "$GRADLE_PROPS.bak" "$GRADLE_PROPS"
    fi
}

# Trap to ensure restoration even on error
trap restore_gradle_props EXIT

# Uncomment AAPT2 override for ARM64
echo -e "${BLUE}🔧 Configuring ARM64 AAPT2...${NC}"
sed -i "s|# $AAPT2_LINE|$AAPT2_LINE|g" "$GRADLE_PROPS"

# Verify AAPT2 is available
if [[ ! -f "/data/data/com.termux/files/usr/bin/aapt2" ]]; then
    echo -e "${RED}❌ Error: AAPT2 not found at /data/data/com.termux/files/usr/bin/aapt2${NC}"
    echo -e "Install it with: ${YELLOW}pkg install aapt2${NC}"
    exit 1
fi

echo -e "${GREEN}✓ AAPT2 configured${NC}"
echo ""

# Build function
build_flavor() {
    local flavor_name=$1
    local gradle_task=$2
    local output_dir=$3

    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}Building $flavor_name flavor...${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""

    START_TIME=$(date +%s)

    if ./gradlew "$gradle_task" --stacktrace; then
        END_TIME=$(date +%s)
        DURATION=$((END_TIME - START_TIME))
        MINUTES=$((DURATION / 60))
        SECONDS=$((DURATION % 60))

        echo ""
        echo -e "${GREEN}✅ $flavor_name build completed successfully!${NC}"
        echo -e "${GREEN}⏱️  Build time: ${MINUTES}m ${SECONDS}s${NC}"
        echo ""

        # Show APK location (in builds/ directory)
        APK_PATH="builds/${output_dir}/app-${flavor_name,,}-debug.apk"
        if [[ -f "$APK_PATH" ]]; then
            APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
            APK_FULL_PATH=$(realpath "$APK_PATH")
            echo -e "${GREEN}📱 APK Location:${NC}"
            echo -e "   $APK_FULL_PATH"
            echo -e "${GREEN}📦 Size:${NC} $APK_SIZE"
            echo ""
        fi

        return 0
    else
        END_TIME=$(date +%s)
        DURATION=$((END_TIME - START_TIME))
        MINUTES=$((DURATION / 60))
        SECONDS=$((DURATION % 60))

        echo ""
        echo -e "${RED}❌ $flavor_name build failed after ${MINUTES}m ${SECONDS}s${NC}"
        echo ""
        return 1
    fi
}

# Build based on flavor
BUILD_SUCCESS=true

if [[ "$FLAVOR" == "all" ]] || [[ "$FLAVOR" == "cloudonly" ]]; then
    if ! build_flavor "CloudOnly" "assembleCloudOnlyDebug" "cloudonly"; then
        BUILD_SUCCESS=false
    fi
fi

if [[ "$FLAVOR" == "all" ]] || [[ "$FLAVOR" == "cloud" ]]; then
    if ! build_flavor "Cloud" "assembleCloudDebug" "cloud"; then
        BUILD_SUCCESS=false
    fi
fi

if [[ "$FLAVOR" == "all" ]] || [[ "$FLAVOR" == "local" ]]; then
    if ! build_flavor "Local" "assembleLocalDebug" "local"; then
        BUILD_SUCCESS=false
    fi
fi

# Summary
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Build Summary${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

if [[ "$BUILD_SUCCESS" == true ]]; then
    echo -e "${GREEN}✅ All builds completed successfully!${NC}"
    echo ""
    echo -e "${YELLOW}📱 APKs are in the builds/ directory:${NC}"
    echo -e "   builds/cloudonly/  - Cloud-only build (for local Android builds)"
    echo -e "   builds/cloud/      - Cloud build (with local option)"
    echo -e "   builds/local/      - Local build (with native libs)"
    echo ""
    echo -e "${YELLOW}📲 To install:${NC}"
    echo -e "   adb install \"<path-to-apk>\""
    echo ""
    echo -e "${YELLOW}💡 Note:${NC} gradle.properties has been restored to keep"
    echo -e "   AAPT2 override commented for cloud builds."
    echo ""
    exit 0
else
    echo -e "${RED}❌ Some builds failed${NC}"
    echo ""
    exit 1
fi
