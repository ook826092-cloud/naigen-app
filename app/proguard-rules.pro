# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses
-dontwarn javax.annotation.**

# Kotlin Serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.naigen.app.**$$serializer { *; }
-keepclassmembers class com.naigen.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.naigen.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Coroutines
-dontwarn kotlinx.coroutines.debug.**
