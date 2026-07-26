plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("io.sentry.android.gradle") version "5.3.0"
}

fun loadMonorepoEnv(): Map<String, String> {
    val candidates = listOf(
        rootProject.projectDir.resolve("../../.env"),
        rootProject.projectDir.resolve("../.env"),
        file("../../.env"),
        file("../../../.env"),
    )
    val envFile = candidates.firstOrNull { it.isFile } ?: return emptyMap()
    val map = linkedMapOf<String, String>()
    envFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
        val eq = trimmed.indexOf('=')
        if (eq <= 0) return@forEach
        map[trimmed.substring(0, eq)] = trimmed.substring(eq + 1)
    }
    return map
}

fun buildGlitchtipDsn(baseUrl: String, publicKey: String, projectId: String): String {
    if (baseUrl.isBlank() || publicKey.isBlank() || projectId.isBlank()) return ""
    val normalized = baseUrl.trimEnd('/')
    val schemeEnd = normalized.indexOf("://")
    if (schemeEnd <= 0) return ""
    val scheme = normalized.substring(0, schemeEnd)
    val hostAndPath = normalized.substring(schemeEnd + 3)
    val host = hostAndPath.substringBefore('/')
    if (host.isBlank()) return ""
    return "$scheme://$publicKey@$host/glitchtip/$projectId"
}

val monorepoEnv = loadMonorepoEnv()
val glitchtipBaseUrl = (
    System.getenv("TONEZEN_BASE_URL")
        ?: monorepoEnv["TONEZEN_BASE_URL"]
        ?: "https://tonezen.tegozen.ru"
    ).trimEnd('/')
val glitchtipAndroidKey = (
    System.getenv("GLITCHTIP_ANDROID_PUBLIC_KEY")
        ?: monorepoEnv["GLITCHTIP_ANDROID_PUBLIC_KEY"]
        ?: ""
    ).trim()
// Must match docker/glitchtip-seed/seed.py ANDROID_PROJECT_ID.
val glitchtipAndroidProjectId = "1"
val glitchtipAuthToken = (
    System.getenv("GLITCHTIP_AUTH_TOKEN")
        ?: monorepoEnv["GLITCHTIP_AUTH_TOKEN"]
        ?: ""
    ).trim()
val glitchtipDsn = buildGlitchtipDsn(
    glitchtipBaseUrl,
    glitchtipAndroidKey,
    glitchtipAndroidProjectId,
)

android {
    namespace = "com.tonezen.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tonezen.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 40
        versionName = "0.19.1"
        buildConfigField("String", "BASE_URL", "\"https://tonezen.tegozen.ru\"")
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJvbGUiOiJhbm9uIiwiZXhwIjoyMDk2NTgzNzc3fQ._CP-vbYhhZ9MPZaShAUB_93enHnw9dfh3_sFLep_Jws\"",
        )
        buildConfigField("String", "GLITCHTIP_DSN", "\"${glitchtipDsn.replace("\"", "\\\"")}\"")
    }

    val releaseStoreFile = (System.getenv("TONEZEN_KEYSTORE_PATH")
        ?: providers.gradleProperty("TONEZEN_KEYSTORE_PATH").orNull)
        ?.takeIf { it.isNotBlank() }
    val releaseStorePassword = System.getenv("TONEZEN_KEYSTORE_PASSWORD")
        ?: providers.gradleProperty("TONEZEN_KEYSTORE_PASSWORD").orNull
    val releaseKeyAlias = System.getenv("TONEZEN_KEY_ALIAS")
        ?: providers.gradleProperty("TONEZEN_KEY_ALIAS").orNull
    val releaseKeyPassword = System.getenv("TONEZEN_KEY_PASSWORD")
        ?: providers.gradleProperty("TONEZEN_KEY_PASSWORD").orNull
    val hasReleaseKeystore =
        !releaseStoreFile.isNullOrBlank() &&
            !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                // Local/CI without a production keystore: keep previous debug-signed
                // release behavior so assembleRelease still works.
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
}

sentry {
    org.set("tonezen")
    projectName.set("tonezen-android")
    // No network upload at build time — optional: node scripts/upload-android-proguard.mjs
    url.set(glitchtipBaseUrl)
    authToken.set(glitchtipAuthToken)
    autoUploadProguardMapping.set(false)
    includeProguardMapping.set(true)
    autoInstallation {
        enabled.set(false)
    }
    tracingInstrumentation {
        enabled.set(false)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("dev.chrisbanes.haze:haze-materials:1.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    implementation("io.sentry:sentry-android:7.22.4")
    implementation("io.sentry:sentry-android-ndk:7.22.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
