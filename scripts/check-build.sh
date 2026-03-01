#!/bin/bash

# Build Check Script for IPE Parental Control App
echo "=========================================="
echo "IPE Build Environment Check"
echo "=========================================="

PROJECT_ROOT="/vercel/share/v0-project"
cd "$PROJECT_ROOT"

# Check Java installation
echo ""
echo "Checking Java installation..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | grep -oP 'version "\K[^"]*' | head -1)
    echo "✅ Java found: $JAVA_VERSION"
else
    echo "❌ Java not found"
    exit 1
fi

# Check Gradle wrapper
echo ""
echo "Checking Gradle wrapper..."
if [ -f "gradlew" ]; then
    echo "✅ Gradle wrapper found"
else
    echo "❌ Gradle wrapper not found"
    exit 1
fi

# Check critical files
echo ""
echo "Checking critical files..."
CRITICAL_FILES=(
    "app/src/main/AndroidManifest.xml"
    "app/src/main/java/com/example/myapp/security/SecurityManager.kt"
    "app/src/main/java/com/example/myapp/utils/PackageController.kt"
    "app/src/main/java/com/example/myapp/services/MonitoringService.kt"
    "app/src/main/java/com/example/myapp/services/StorageRestrictionService.kt"
    "app/build.gradle.kts"
    "build.gradle.kts"
)

for file in "${CRITICAL_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo "✅ Found: $file"
    else
        echo "❌ Missing: $file"
        exit 1
    fi
done

echo ""
echo "=========================================="
echo "✅ All checks passed! Ready to build."
echo "=========================================="
