-dontobfuscate

-dontwarn java.lang.ClassValue
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn com.google.common.**

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature

-keep class go.** { *; }
-keep class com.devqissy.dsky.** { *; }

-keep class * extends com.bumptech.glide.module.AppGlideModule
-dontwarn com.bumptech.glide.**

-dontwarn com.airbnb.lottie.**

-dontwarn com.squareup.picasso.**

-dontwarn androidx.compose.**

-dontwarn kotlinx.coroutines.**

-dontwarn androidx.datastore.**


-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}