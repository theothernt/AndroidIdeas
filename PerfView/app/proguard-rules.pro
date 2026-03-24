# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ==============================================================================
# Koin Dependency Injection
# ==============================================================================
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
    @org.koin.core.annotation.* <fields>;
}
-keepclassmembers class ** {
    @org.koin.core.annotation.* <init>(...);
}
-keep class * extends org.koin.core.component.KoinComponent
-keep class * implements org.koin.core.component.KoinComponent
-keepclassmembers class * implements org.koin.core.component.KoinComponent {
    <fields>;
}
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.android.annotation.* <fields>;
    @org.koin.android.annotation.* <methods>;
}

# Keep classes referenced in Koin modules
-keep class com.neilturner.perfview.di.** { *; }

# ==============================================================================
# libadb-android (MuntashirAkon)
# ==============================================================================
-keep class io.github.muntashirakon.** { *; }
-dontwarn io.github.muntashirakon.**

# ==============================================================================
# Bouncy Castle
# ==============================================================================
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ==============================================================================
# General Android & Compose
# ==============================================================================
-keep class androidx.compose.** { *; }
-keep class androidx.tv.** { *; }
-keepclassmembers class * extends androidx.compose.runtime.Composable {
    <init>(...);
}

# Keep AndroidX core components
-keep class androidx.core.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }

# ==============================================================================
# Reflection & Serialization
# ==============================================================================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep generic signature information
-keepclassmembers,allowobfuscation class * {
    <fields>;
    <methods>;
}
