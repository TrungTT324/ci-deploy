import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun generateBuildNo(): Long {
    val formattedDate = SimpleDateFormat("yyyyMMddHHmm").format(Date())
    return formattedDate.toLong()
}

val computedBuildNo = generateBuildNo()
val computedVersionCode = (System.currentTimeMillis() / 60_000L).toInt()
println("CI-DEPLOY BUILD_NO GENERATED: $computedBuildNo")

// --- Release signing (2026-09-15 key rotation, WorkItem 5c928545) ---
// The previous release keystore (app/ci-deploy.jks) and its plaintext password
// were committed to git and must be treated as compromised (see
// CODE_REVIEW_2026-08-11.md CRITICAL-01). Neither the new keystore file nor its
// password is committed here. Provide them via:
//   - CI:        env vars CI_DEPLOY_RELEASE_STORE_FILE / _STORE_PASSWORD /
//                _KEY_ALIAS / _KEY_PASSWORD (see .github/workflows/build-release.yml)
//   - Local dev: gitignored root local.properties with keys
//                ciDeploy.release.storeFile / .storePassword / .keyAlias / .keyPassword
// If neither is present, the build falls back to Android's default debug
// signing so local builds still work — such a build is NOT signed with the
// production key and cannot OTA-update an existing release install.
val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}

fun releaseSigningProperty(envVar: String, localKey: String): String? =
    System.getenv(envVar)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(localKey)?.takeIf { it.isNotBlank() }

val releaseStoreFilePath = releaseSigningProperty("CI_DEPLOY_RELEASE_STORE_FILE", "ciDeploy.release.storeFile")
val releaseStorePassword = releaseSigningProperty("CI_DEPLOY_RELEASE_STORE_PASSWORD", "ciDeploy.release.storePassword")
val releaseKeyAlias = releaseSigningProperty("CI_DEPLOY_RELEASE_KEY_ALIAS", "ciDeploy.release.keyAlias") ?: "cideploy"
val releaseKeyPassword = releaseSigningProperty("CI_DEPLOY_RELEASE_KEY_PASSWORD", "ciDeploy.release.keyPassword")

val releaseSigningAvailable =
    releaseStoreFilePath != null && releaseStorePassword != null && releaseKeyPassword != null

if (!releaseSigningAvailable) {
    logger.warn(
        "CI-Deploy: release signing secrets not found (env vars or local.properties) — " +
            "falling back to debug signing. This build is NOT signed with the production key."
    )
}

android {
    namespace = "hdisoft.app.cideploy"
    sourceSets["main"].java.exclude(
        "hdisoft/app/cideploy/features/bluetooth/data/**",
        "hdisoft/app/cideploy/features/bluetooth/security/data/**"
    )
    compileSdk = 34

    defaultConfig {
        applicationId = "hdisoft.app.cideploy"
        minSdk = 21
        targetSdk = 34
        versionCode = computedVersionCode
        versionName = "1.0.0"

        buildConfigField("long", "BUILD_NO", "${computedBuildNo}L")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Keep the debug artifact for testing, but sign it with the same
            // project key so it can OTA-update the installed release build,
            // when that key is actually available (see releaseSigningAvailable above).
            signingConfig = if (releaseSigningAvailable) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseSigningAvailable) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":libs:bluetooth"))
    implementation(project(":libs:core"))
    implementation(project(":libs:appupdate"))
    implementation(project(":libs:logcat"))
    implementation(project(":libs:webserver"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
}
