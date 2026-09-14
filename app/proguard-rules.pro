# The engine maps yt-dlp's JSON onto VideoInfo with Jackson, by field name.
-keep class com.yausername.youtubedl_android.mapper.** { *; }
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.yausername.aria2c.** { *; }
-dontwarn com.fasterxml.jackson.**
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# kotlinx.serialization keeps Modes and Routines readable across app updates.
-keepclassmembers class com.pylo.yoinker.** {
    *** Companion;
}
-keepclasseswithmembers class com.pylo.yoinker.** {
    kotlinx.serialization.KSerializer serializer(...);
}
