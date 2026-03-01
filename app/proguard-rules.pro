# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep our core classes
-keep class com.example.myapp.** { *; }
-keep interface com.example.myapp.** { *; }

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }

# Keep WebKit classes
-keep class android.webkit.** { *; }
-keep interface android.webkit.** { *; }

# Keep all Device Admin and Broadcast Receiver implementations
-keep class * extends android.app.admin.DeviceAdminReceiver { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.app.Service { *; }

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep service callbacks
-keepclasseswithmembernames class * {
    *** on*(android.content.Context, ...);
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep RootBeer library classes
-keep class com.scottyab.rootbeer.** { *; }

# Keep Encryption library
-keep class androidx.security.crypto.** { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep all class names for reflection
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Aggressive optimization
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose

# Remove logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
