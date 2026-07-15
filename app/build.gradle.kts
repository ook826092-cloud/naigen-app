import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// ── 从 version.properties 读取版本号（单一来源）───────────────────────────
// 双层版本号：
//   - BUILD_NUMBER: 累计构建次数，每次 bump +1，永不动。只显示在「关于」页
//   - MAJOR.MINOR.PATCH: 标准 SemVer，逢 9 进位
//
// Android 看到的：
//   - versionName = "MAJOR.MINOR.PATCH"（例如 "2.1.9"）—— 用户在系统设置看到的
//   - versionCode = major*10000 + minor*100 + patch（例如 20109）—— 用于升级判定
//
// 关于页通过 BuildConfig.BUILD_NUMBER 显示构建次数
data class AppVersion(
    val buildNumber: Int,
    val major: Int,
    val minor: Int,
    val patch: Int
) {
    val semver: String get() = "$major.$minor.$patch"
    val versionName: String get() = semver
    val versionCode: Int get() = major * 10000 + minor * 100 + patch
}

val appVersion: AppVersion = run {
    val f = rootProject.file("version.properties")
    if (!f.exists()) return@run AppVersion(1, 1, 0, 0)
    val props = Properties()
    f.inputStream().use { props.load(it) }
    fun propInt(key: String, fallback: Int): Int =
        (props.getProperty(key) ?: props.getProperty("VERSION_$key") ?: "").let {
            it.toIntOrNull() ?: fallback
        }
    AppVersion(
        buildNumber = propInt("BUILD_NUMBER", 1),
        major = propInt("MAJOR", 1),
        minor = propInt("MINOR", 0),
        patch = propInt("PATCH", 0)
    )
}

// ── Release 签名配置 ─────────────────────────────────────────────────────
// 优先级：
//   1. 环境变量（CI 注入，GitHub Actions 用 Secrets）
//      SIGNING_KEYSTORE_FILE  → keystore 文件路径
//      SIGNING_STORE_PASSWORD → store 密码
//      SIGNING_KEY_ALIAS      → key alias
//      SIGNING_KEY_PASSWORD   → key 密码
//   2. 本地 signing/keystore.properties（开发自用，不入库）
//   3. 都没有 → fallback 到 debug 签名
data class SigningConfig(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String
)

val releaseSigning: SigningConfig? = run {
    // 1) 环境变量（CI 优先）
    val envStoreFile = System.getenv("SIGNING_KEYSTORE_FILE")
    val envStorePass = System.getenv("SIGNING_STORE_PASSWORD")
    val envKeyAlias = System.getenv("SIGNING_KEY_ALIAS")
    val envKeyPass = System.getenv("SIGNING_KEY_PASSWORD")
    if (!envStoreFile.isNullOrBlank() && !envStorePass.isNullOrBlank()) {
        return@run SigningConfig(
            storeFile = File(envStoreFile),
            storePassword = envStorePass,
            keyAlias = envKeyAlias ?: "",
            keyPassword = envKeyPass ?: envStorePass
        )
    }
    // 2) 本地 signing/keystore.properties（开发自用，已被 .gitignore 排除）
    val propsFile = rootProject.file("signing/keystore.properties")
    if (propsFile.exists()) {
        val props = Properties()
        propsFile.inputStream().use { props.load(it) }
        val storeFilePath = props.getProperty("storeFile") ?: return@run null
        return@run SigningConfig(
            storeFile = rootProject.file(storeFilePath),
            storePassword = props.getProperty("storePassword") ?: "",
            keyAlias = props.getProperty("keyAlias") ?: "",
            keyPassword = props.getProperty("keyPassword") ?: ""
        )
    }
    null
}

android {
    namespace = "com.naigen.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.naigen.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersion.versionCode
        versionName = appVersion.versionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 暴露给关于页用的版本信息
        buildConfigField("int", "BUILD_NUMBER", appVersion.buildNumber.toString())
        buildConfigField("String", "SEMVER", "\"${appVersion.semver}\"")
        buildConfigField("String", "VERSION_DISPLAY", "\"${appVersion.buildNumber}/${appVersion.semver}\"")
    }

    signingConfigs {
        create("release") {
            releaseSigning?.let {
                storeFile = it.storeFile
                storePassword = it.storePassword
                keyAlias = it.keyAlias
                keyPassword = it.keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // 开启 R8 代码混淆与压缩：
            //   - 移除未使用代码，减小 APK 体积
            //   - 业务逻辑（含 token 处理路径）混淆，提升反编译难度
            //   - 配合 proguard-rules.pro 的 keep 规则
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 用 signing/keystore.properties 配置的 keystore 签 release
            // 如果没有 keystore 文件，fallback 到 debug 签名（仅本地调试用）
            signingConfig = signingConfigs.getByName("release").takeIf { releaseSigning != null }
                ?: signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // 安全存储：用 EncryptedSharedPreferences 加密 API Token
    // 1.1.0-alpha06 是目前最新稳定可用的版本（alpha 但已被广泛生产使用）
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Shizuku (用于启动隐藏的厂商设置 Activity, 绕过权限限制)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Unit tests (纯 JVM，无需 instrumented)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // MockWebServer：用于 NaiApiClient / NaiRepository 的 HTTP 单测
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
