#!/data/data/com.termux/files/usr/bin/bash
#
# Setup Gradle Wrapper
# Run this once to create gradlew scripts
#

set -e

echo "========================================"
echo "  Setting up Gradle Wrapper"
echo "========================================"
echo ""

# Check if gradle is installed
if ! command -v gradle &> /dev/null; then
    echo "ERROR: gradle not found!"
    echo "Install with: pkg install gradle"
    exit 1
fi

# Get Gradle version
GRADLE_VERSION=$(gradle --version | grep "Gradle" | cut -d' ' -f2)
echo "Using Gradle version: $GRADLE_VERSION"
echo ""

# Generate wrapper
echo "Generating wrapper files..."
gradle wrapper --gradle-version $GRADLE_VERSION

# Check if successful
if [ -f "gradlew" ]; then
    chmod +x gradlew
    echo ""
    echo "✓ Gradle wrapper created successfully!"
    echo ""
    echo "You can now use:"
    echo "  ./gradlew assembleDebug"
    echo "  ./gradlew assembleRelease"
    echo ""
    echo "Or use the build script:"
    echo "  ./build.sh debug"
else
    echo ""
    echo "ERROR: Wrapper creation failed"
    exit 1
fi
