# Keep Kotlin metadata / serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * { @kotlinx.serialization.Serializable *; }

# Hilt / generated
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.hilt.work.HiltWorker { *; }

# Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Media3
-keep class androidx.media3.** { *; }

# JAudioTagger / NewPipeExtractor reflective
-keep class org.jaudiotagger.** { *; }
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.slf4j.**

# JDK desktop classes referenced by libraries we depend on but never call
# on Android. JAudioTagger has Swing/AWT/ImageIO image-handling code, Jsoup
# has an optional re2j regex backend, and NewPipe pulls in Mozilla Rhino
# which references javax.script and java.beans.Introspector. None of these
# code paths execute at runtime on Android — but R8 still has to be told
# not to fail the build over the dangling references.
-dontwarn com.google.re2j.**
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.imageio.**
-dontwarn javax.script.**
-dontwarn javax.swing.**
