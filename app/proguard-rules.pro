# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontwarn javax.annotation.**

# ── Kotlin Serialization ──────────────────────────────────────────────────
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.naigen.app.**$$serializer { *; }
-keepclassmembers class com.naigen.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.naigen.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 保留所有 @Serializable 标注的类（kotlinx-serialization-json 编译期生成 Serializer）
-keep,allowobfuscation,allowshrinking class com.naigen.app.data.api.dto.** { *; }
-keep,allowobfuscation,allowshrinking class com.naigen.app.data.model.** { *; }

# ── OkHttp ───────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Room ────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ── Coroutines ───────────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.debug.**

# ── androidx.security:security-crypto（开启混淆后必需保留 Tink 内部类） ────
# EncryptedSharedPreferences 内部用反射访问 MasterKey 与各种 Keyset
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.proto.**
# Tink 的 KeysDownloader 引用了 google-http-client / errorprone-annotations / joda-time，
# 这些是 Tink 的可选依赖，运行时不会被 EncryptedSharedPreferences 调用到（本地 keyset，
# 不走网络下载）。R8 报 Missing class 会阻断 release 构建，用 dontwarn 忽略。
-dontwarn com.google.api.client.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.joda.time.**

# ── Shizuku（启动隐藏的厂商设置 Activity，反射调用 IBinder） ─────────────
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }
-keep interface dev.rikka.shizuku.** { *; }

# ── Coil（ImageLoader 用 OkHttp，反射加载 ServiceLoader） ─────────────────
-dontwarn coil.**

# ── 通知 extras 字段（miui.focus.param / android.locusId） ────────────────
# 这些字段是运行时通过 Bundle putString/getString 访问的字符串，无类需 keep
# 但 BuildConfig 必须保留（关于页用 BuildConfig.SEMVER）
-keep class com.naigen.app.BuildConfig { *; }

# ── Compose ──────────────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ── Kotlin Metadata ──────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
