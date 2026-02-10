# Add project specific ProGuard rules here.

# === DEAD CODE ANALYSIS ===
# Uncomment to generate dead code report during release build
-printusage build/outputs/mapping/release/unused-code.txt
-printseeds build/outputs/mapping/release/entry-points.txt

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# NOTE: Package was renamed from com.pocketcode to dev.blazelight.p4oc
-keep,includedescriptorclasses class dev.blazelight.p4oc.**$$serializer { *; }
-keepclassmembers class dev.blazelight.p4oc.** {
    *** Companion;
}
-keepclasseswithmembers class dev.blazelight.p4oc.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Retrofit
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
