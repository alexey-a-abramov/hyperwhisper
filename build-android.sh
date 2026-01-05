#!/bin/bash

# Build script for Android/Termux environment
# Automatically handles AAPT2 configuration for ARM64 builds
# Output APK is in the builds/ directory

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AAPT2_OVERRIDE="-Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2"
GRADLE_OPTS=""

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

# Check if running on Android/Termux
if [[ -d "/data/data/com.termux" ]]; then
    # Running on Termux - add AAPT2 override
    if [[ ! -f "/data/data/com.termux/files/usr/bin/aapt2" ]]; then
        echo -e "${RED}❌ Error: AAPT2 not found at /data/data/com.termux/files/usr/bin/aapt2${NC}"
        echo -e "Install it with: ${YELLOW}pkg install aapt2${NC}"
        exit 1
    fi
    GRADLE_OPTS="$AAPT2_OVERRIDE"
    echo -e "${GREEN}✓ Termux detected: Using AAPT2 override${NC}"
    echo ""
else
    echo -e "${YELLOW}⚠️  Not running on Termux/Android${NC}"
    echo -e "This script is designed for Android/Termux environment."
    echo -e "For other platforms, use: ./gradlew assembleDebug"
    echo ""
fi

# Build
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Building HyperWhisper...${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

START_TIME=$(date +%s)

if ./gradlew $GRADLE_OPTS assembleDebug --stacktrace; then
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    MINUTES=$((DURATION / 60))
    SECONDS=$((DURATION % 60))

    echo ""
    echo -e "${GREEN}✅ Build completed successfully!${NC}"
    echo -e "${GREEN}⏱️  Build time: ${MINUTES}m ${SECONDS}s${NC}"
    echo ""

    # Show APK location (in builds/ directory)
    APK_PATH="builds/app-debug.apk"
    if [[ -f "$APK_PATH" ]]; then
        APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
        APK_FULL_PATH=$(realpath "$APK_PATH")
        echo -e "${GREEN}📱 APK Location:${NC}"
        echo -e "   $APK_FULL_PATH"
        echo -e "${GREEN}📦 Size:${NC} $APK_SIZE"
        echo ""
    fi

    exit 0
else
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    MINUTES=$((DURATION / 60))
    SECONDS=$((DURATION % 60))

    echo ""
    echo -e "${RED}❌ Build failed after ${MINUTES}m ${SECONDS}s${NC}"
    echo ""
    exit 1
fi
