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
}
-keepclassmembers class * {
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

# Keep Koin metadata for generated code
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.android.annotation.* <fields>;
    @org.koin.android.annotation.* <methods>;
}

# Keep classes referenced in Koin modules
-keep class com.neilturner.perfview.di.** { *; }
-keep class com.neilturner.perfview.** { *; }

# ==============================================================================
# libadb-android (MuntashirAkon)
# ==============================================================================
-keep class io.github.muntashirakon.adb.** { *; }
-keep class io.github.muntashirakon.** { *; }
-dontwarn io.github.muntashirakon.**

# ==============================================================================
# Bouncy Castle
# ==============================================================================
-keep class org.bouncycastle.** { *; }
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider
-dontwarn org.bouncycastle.**
-keep class * extends org.bouncycastle.jce.provider.BouncyCastleProvider

# Keep Bouncy Castle ASN1 and certificate classes
-keep class org.bouncycastle.asn1.** { *; }
-keep class org.bouncycastle.cert.** { *; }
-keep class org.bouncycastle.operator.** { *; }
-keep class org.bouncycastle.jcajce.** { *; }
-keep class org.bouncycastle.util.** { *; }

# ==============================================================================
# Conscrypt (if still used elsewhere)
# ==============================================================================
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

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
# Data Classes & Models
# ==============================================================================
-keep class com.neilturner.perfview.data.** { *; }
-keep class com.neilturner.perfview.domain.** { *; }
-keep class com.neilturner.perfview.ui.** { *; }
-keep class com.neilturner.perfview.overlay.** { *; }

# Keep TopProcessUsage for serialization
-keep class com.neilturner.perfview.data.cpu.TopProcessUsage { *; }
-keep class com.neilturner.perfview.domain.cpu.CpuObservation { *; }
-keep class com.neilturner.perfview.domain.cpu.CpuUsageResult { *; }
-keep class com.neilturner.perfview.domain.cpu.CpuUsageResult$Success { *; }
-keep class com.neilturner.perfview.domain.cpu.CpuUsageResult$Unsupported { *; }

# ==============================================================================
# ViewModel
# ==============================================================================
-keep class com.neilturner.perfview.ui.dashboard.PerfViewViewModel { *; }
-keep class com.neilturner.perfview.ui.dashboard.PerfViewViewState { *; }
-keep class com.neilturner.perfview.ui.dashboard.PerfViewIntent { *; }
-keep class com.neilturner.perfview.ui.dashboard.PerfViewCommand { *; }
-keep class com.neilturner.perfview.ui.dashboard.PerfViewScreen { *; }

# ==============================================================================
# Services
# ==============================================================================
-keep class com.neilturner.perfview.overlay.CpuOverlayService { *; }

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
