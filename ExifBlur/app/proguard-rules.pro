# Strip verbose logging from release builds.
# Log.v/d/i calls (including their string building) are removed by R8 via
# assumenosideeffects, so release builds log nothing and pay nothing.
# Warnings and errors are kept for diagnostics.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep source file names and line numbers for readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
