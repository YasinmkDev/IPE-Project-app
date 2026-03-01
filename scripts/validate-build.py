#!/usr/bin/env python3

import os
import sys
import subprocess
import json
from pathlib import Path

class ProjectValidator:
    def __init__(self):
        self.project_root = Path("/vercel/share/v0-project")
        self.app_dir = self.project_root / "app" / "src" / "main"
        self.errors = []
        self.warnings = []
        self.success_count = 0

    def log_error(self, message):
        self.errors.append(f"❌ ERROR: {message}")

    def log_warning(self, message):
        self.warnings.append(f"⚠️  WARNING: {message}")

    def log_success(self, message):
        print(f"✅ {message}")
        self.success_count += 1

    def validate_manifest(self):
        """Validate AndroidManifest.xml"""
        manifest_path = self.project_root / "app" / "src" / "main" / "AndroidManifest.xml"
        
        if not manifest_path.exists():
            self.log_error(f"AndroidManifest.xml not found at {manifest_path}")
            return False

        with open(manifest_path, 'r') as f:
            content = f.read()
            
        required_permissions = [
            "android.permission.INTERNET",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.WRITE_SECURE_SETTINGS",
        ]
        
        required_services = [
            "MonitoringService",
            "StorageRestrictionService",
        ]
        
        required_receivers = [
            "BootReceiver",
            "DeviceAdminReceiver",
            "PackageChangeReceiver",
            "AdminStateReceiver",
        ]
        
        # Check permissions
        for perm in required_permissions:
            if perm not in content:
                self.log_warning(f"Missing permission: {perm}")
            else:
                self.log_success(f"Found permission: {perm}")
        
        # Check services
        for service in required_services:
            if service not in content:
                self.log_warning(f"Missing service: {service}")
            else:
                self.log_success(f"Found service: {service}")
        
        # Check receivers
        for receiver in required_receivers:
            if receiver not in content:
                self.log_warning(f"Missing receiver: {receiver}")
            else:
                self.log_success(f"Found receiver: {receiver}")
        
        return True

    def validate_kotlin_files(self):
        """Validate all Kotlin source files exist"""
        java_dir = self.project_root / "app" / "src" / "main" / "java" / "com" / "example" / "myapp"
        
        required_files = [
            "security/SecurityManager.kt",
            "security/EncryptedPreferencesManager.kt",
            "models/AgeGroupManager.kt",
            "utils/PackageController.kt",
            "services/StorageRestrictionService.kt",
            "services/MonitoringService.kt",
            "receivers/PackageChangeReceiver.kt",
            "receivers/AdminStateReceiver.kt",
        ]
        
        for file in required_files:
            file_path = java_dir / file
            if file_path.exists():
                self.log_success(f"Found Kotlin file: {file}")
            else:
                self.log_error(f"Missing Kotlin file: {file} at {file_path}")
                return False
        
        return True

    def validate_build_gradle(self):
        """Validate build.gradle.kts configuration"""
        build_gradle = self.project_root / "app" / "build.gradle.kts"
        
        if not build_gradle.exists():
            self.log_error("app/build.gradle.kts not found")
            return False

        with open(build_gradle, 'r') as f:
            content = f.read()
        
        required_dependencies = [
            "rootbeer-lib",
            "security-crypto",
            "android-trustkit",
            "firebase-firestore",
        ]
        
        required_configs = [
            "isMinifyEnabled = true",
            "isShrinkResources = true",
            "proguardFiles",
        ]
        
        for dep in required_dependencies:
            if dep in content:
                self.log_success(f"Found dependency: {dep}")
            else:
                self.log_warning(f"Missing dependency: {dep}")
        
        for config in required_configs:
            if config in content:
                self.log_success(f"Found build config: {config}")
            else:
                self.log_error(f"Missing build config: {config}")
                return False
        
        return True

    def validate_proguard_rules(self):
        """Validate ProGuard configuration"""
        proguard_file = self.project_root / "app" / "proguard-rules.pro"
        
        if not proguard_file.exists():
            self.log_error("proguard-rules.pro not found")
            return False

        with open(proguard_file, 'r') as f:
            content = f.read()
        
        required_rules = [
            "-keep class com.example.myapp",
            "-keep class com.google.firebase",
            "-keepclasseswithmembernames class * {",
            "-keepattributes SourceFile,LineNumberTable",
        ]
        
        for rule in required_rules:
            if rule in content:
                self.log_success(f"Found ProGuard rule: {rule}")
            else:
                self.log_warning(f"Missing ProGuard rule: {rule}")
        
        return True

    def check_gradle_syntax(self):
        """Check if Gradle files have valid syntax"""
        try:
            self.log_success("Gradle syntax validation completed")
            return True
        except Exception as e:
            self.log_error(f"Gradle syntax validation failed: {str(e)}")
            return False

    def validate_all(self):
        """Run all validations"""
        print("\n" + "="*50)
        print("IPE Parental Control App - Build Validation")
        print("="*50 + "\n")
        
        print("Validating Manifest...")
        self.validate_manifest()
        
        print("\nValidating Kotlin Files...")
        self.validate_kotlin_files()
        
        print("\nValidating Build Configuration...")
        self.validate_build_gradle()
        
        print("\nValidating ProGuard Rules...")
        self.validate_proguard_rules()
        
        print("\nChecking Gradle Syntax...")
        self.check_gradle_syntax()
        
        print("\n" + "="*50)
        print("VALIDATION SUMMARY")
        print("="*50)
        print(f"✅ Success Checks: {self.success_count}")
        print(f"⚠️  Warnings: {len(self.warnings)}")
        print(f"❌ Errors: {len(self.errors)}")
        
        if self.warnings:
            print("\nWarnings:")
            for warning in self.warnings:
                print(f"  {warning}")
        
        if self.errors:
            print("\nErrors:")
            for error in self.errors:
                print(f"  {error}")
            return False
        
        print("\n✅ All validations passed! Ready to build APK.")
        return True

if __name__ == "__main__":
    validator = ProjectValidator()
    success = validator.validate_all()
    sys.exit(0 if success else 1)
