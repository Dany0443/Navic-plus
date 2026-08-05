import java.util.Properties
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.impl.VariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Versioning

val versionMajor = project.property("versionMajor").toString().toInt()
val versionMinor = project.property("versionMinor").toString().toInt()
val versionPatch = project.property("versionPatch").toString().toInt()
val versionPrerelease = project.property("versionPrerelease").toString()
val versionPrereleaseVersion = project.property("versionPrereleaseVersion").toString().toInt()

fun buildVersionName(): String = buildString {
    append("$versionMajor.$versionMinor.$versionPatch")
    if (versionPrerelease.isNotBlank()) {
        append("-$versionPrerelease")
    }
}

fun buildVersionCode(): Int =
    versionMajor * 1_000_000 +
        versionMinor * 10_000 +
        versionPatch * 100 +
        versionPrereleaseVersion

// Signing
// Env vars take priority over keystore.properties, so CI can override local config.

val isRelease = System.getenv("RELEASE")?.toBoolean() ?: false

val keystoreProperties = Properties().apply {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

data class SigningInfo(
    val storeFile: File?,
    val storePassword: String?,
    val keyAlias: String?,
    val keyPassword: String?,
) {
    val isComplete: Boolean
        get() = storeFile?.exists() == true && !storePassword.isNullOrEmpty() &&
            !keyAlias.isNullOrEmpty() && !keyPassword.isNullOrEmpty()
}

val envSigningInfo = SigningInfo(
    storeFile = System.getenv("SIGNING_STORE_FILE")?.let(::File),
    storePassword = System.getenv("SIGNING_STORE_PASSWORD"),
    keyAlias = System.getenv("SIGNING_KEY_ALIAS"),
    keyPassword = System.getenv("SIGNING_KEY_PASSWORD"),
)

val resolvedSigningInfo = SigningInfo(
    storeFile = envSigningInfo.storeFile ?: keystoreProperties.getProperty("storeFile")?.let { rootProject.file(it) },
    storePassword = envSigningInfo.storePassword ?: keystoreProperties.getProperty("storePassword"),
    keyAlias = envSigningInfo.keyAlias ?: keystoreProperties.getProperty("keyAlias"),
    keyPassword = envSigningInfo.keyPassword ?: keystoreProperties.getProperty("keyPassword"),
)

val hasReleaseSigning = resolvedSigningInfo.isComplete

if (isRelease && !hasReleaseSigning) {
    throw GradleException("Missing or incomplete signing configuration for a release workflow!")
}

// Android configuration

extensions.configure<ApplicationExtension> {
    namespace = "app.sonora"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        resValues = true
    }

    defaultConfig {
        applicationId = "app.sonora"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = buildVersionCode()
        versionName = buildVersionName()

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
            // Keep x86_64 for local/emulator builds, drop it from release to save size.
            if (!isRelease) {
                abiFilters.add("x86_64")
            }
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = resolvedSigningInfo.storeFile
                storePassword = resolvedSigningInfo.storePassword
                keyAlias = resolvedSigningInfo.keyAlias
                keyPassword = resolvedSigningInfo.keyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Sonora (Dev)")
        }

        getByName("release") {
            isMinifyEnabled = true
            isDebuggable = false
            isProfileable = false
            isJniDebuggable = false
            isShrinkResources = true
            // Fall back to debug signing when no release keystore is available (e.g. local builds).
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        // A release build signed with the debug key so it can be installed
        // alongside the real release build for personal/testing use.
        create("personal") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".personal"
            versionNameSuffix = "-personal"
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
            resValue("string", "app_name", "Sonora Personal")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/okhttp3/**",
                "/*.properties",
                "/org/antlr/**",
                "/com/android/tools/smali/**",
                "/org/eclipse/jgit/**",
                "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "/org/bouncycastle/**",
                "/META-INF/{AL2.0,LGPL2.1}",
                "/**/*.version",
                "/kotlin-tooling-metadata.json",
                "/DebugProbesKt.bin",
                "/**/*.kotlin_builtins",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// Name each variant's APK after its version, e.g. Sonora-v1.5.2-RC2.apk.
extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is VariantOutputImpl) {
                output.outputFileName = if (variant.buildType == "release") {
                    "Sonora-v${buildVersionName()}.apk"
                } else {
                    "Sonora-${variant.buildType}.apk"
                }
            }
        }
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.cmp.material3)
    implementation(libs.koin.android)
    implementation(libs.koin.core)
    implementation(libs.bundles.glance)
    implementation(libs.bundles.coil)
    implementation(libs.bundles.media3)
}