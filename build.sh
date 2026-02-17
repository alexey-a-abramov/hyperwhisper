#!/data/data/com.termux/files/usr/bin/bash
#
# HyperWhisper Build Script
# Auto-increments version code on each build
#

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print colored message
print_msg() {
    echo -e "${2}${1}${NC}"
}

print_header() {
    echo ""
    echo "========================================"
    echo "  $1"
    echo "========================================"
    echo ""
}

# Check for gradle
if [ ! -f "gradlew" ]; then
    if ! command -v gradle &> /dev/null; then
        print_msg "ERROR: Neither gradlew nor gradle found!" "$RED"
        print_msg "Install gradle with: pkg install gradle" "$YELLOW"
        exit 1
    fi
    GRADLE_CMD="gradle"
    print_msg "Using system gradle" "$YELLOW"
else
    GRADLE_CMD="./gradlew"
    print_msg "Using gradle wrapper" "$GREEN"
fi

# Show current version
print_header "Current Version Info"
if [ -f "gradle.properties" ]; then
    VERSION_NAME=$(grep "VERSION_NAME=" gradle.properties | cut -d'=' -f2)
    VERSION_CODE=$(grep "VERSION_CODE=" gradle.properties | cut -d'=' -f2)
    print_msg "Version Name: $VERSION_NAME" "$BLUE"
    print_msg "Version Code: $VERSION_CODE (will auto-increment)" "$BLUE"
else
    print_msg "WARNING: gradle.properties not found" "$YELLOW"
fi

# Parse arguments
BUILD_TYPE="${1:-debug}"
case "$BUILD_TYPE" in
    debug|d)
        BUILD_TYPE="debug"
        TASK="assembleDebug"
        APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
        ;;
    release|r)
        BUILD_TYPE="release"
        TASK="assembleRelease"
        APK_PATH="app/build/outputs/apk/release/app-release.apk"
        ;;
    install|i)
        BUILD_TYPE="install"
        TASK="installDebug"
        APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
        ;;
    clean)
        print_header "Cleaning Build"
        $GRADLE_CMD clean
        print_msg "✓ Clean complete" "$GREEN"
        exit 0
        ;;
    *)
        echo "Usage: $0 [debug|release|install|clean]"
        echo ""
        echo "Options:"
        echo "  debug    - Build debug APK (default)"
        echo "  release  - Build release APK"
        echo "  install  - Build and install debug APK to device"
        echo "  clean    - Clean build artifacts"
        echo ""
        echo "Examples:"
        echo "  $0           # Build debug"
        echo "  $0 debug     # Build debug"
        echo "  $0 release   # Build release"
        echo "  $0 install   # Build and install"
        exit 1
        ;;
esac

# Build
print_header "Building $BUILD_TYPE"
print_msg "Running: $GRADLE_CMD $TASK" "$BLUE"
echo ""

$GRADLE_CMD $TASK

# Check if build succeeded
if [ $? -eq 0 ]; then
    echo ""
    print_header "Build Successful!"

    # Show new version code
    if [ -f "gradle.properties" ]; then
        NEW_VERSION_CODE=$(grep "VERSION_CODE=" gradle.properties | cut -d'=' -f2)
        print_msg "New Version Code: $NEW_VERSION_CODE" "$GREEN"
    fi

    # Show APK location (if not install task)
    if [ "$BUILD_TYPE" != "install" ]; then
        if [ -f "$APK_PATH" ]; then
            APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
            print_msg "APK Location: $APK_PATH" "$GREEN"
            print_msg "APK Size: $APK_SIZE" "$GREEN"
            echo ""
            print_msg "Install with: adb install -r $APK_PATH" "$YELLOW"
        else
            print_msg "WARNING: APK not found at expected location" "$YELLOW"
            print_msg "Search for it with: find app/build -name '*.apk'" "$YELLOW"
        fi
    else
        print_msg "APK installed to device" "$GREEN"
    fi
else
    echo ""
    print_msg "Build failed!" "$RED"
    exit 1
fi

echo ""
