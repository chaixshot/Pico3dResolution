# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\chai5\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools-proguard.html

# Add any custom keep rules here.

# Keep Compose internal classes that might be accessed via reflection or are otherwise needed
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable *;
}

# Root execution might use reflection or need to stay un-obfuscated for some reason,
# but usually it's fine. We'll keep the ConfigValues data class just in case.
-keep class com.hamer.res3d.ConfigValues { *; }
