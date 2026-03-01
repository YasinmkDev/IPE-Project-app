#!/bin/bash

# Build script for generating APK
# This script will build a release APK with all security enhancements

set -e

echo "========================================="
echo "IPE Parental Control App - APK Build"
echo "========================================="

# Get the directory of this script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "Project directory: $PROJECT_DIR"

# Change to project directory
cd "$PROJECT_DIR"

echo ""
echo "Step 1: Cleaning previous builds..."
./gradlew clean

echo ""
echo "Step 2: Building release APK with optimization..."
./gradlew assembleRelease \
    -Pandroid.enableR8=true \
    -Pandroid.enableProguardInReleaseBuilds=true

echo ""
echo "Step 3: Building debug APK for testing..."
./gradlew assembleDebug

echo ""
echo "========================================="
echo "Build Complete!"
echo "========================================="

# Find the built APKs
RELEASE_APK=$(find . -name "*release*.apk" -type f | grep -v "unaligned" | head -1)
DEBUG_APK=$(find . -name "*debug*.apk" -type f | head -1)

if [ -f "$RELEASE_APK" ]; then
    echo "Release APK: $RELEASE_APK"
    echo "Size: $(du -h "$RELEASE_APK" | cut -f1)"
else
    echo "Warning: Release APK not found"
fi

if [ -f "$DEBUG_APK" ]; then
    echo "Debug APK: $DEBUG_APK"
    echo "Size: $(du -h "$DEBUG_APK" | cut -f1)"
else
    echo "Warning: Debug APK not found"
fi

echo ""
echo "Build process finished successfully!"
echo "Check app/build/outputs/apk/ for the generated APKs"
