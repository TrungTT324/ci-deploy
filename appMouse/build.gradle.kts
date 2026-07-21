import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun generateBuildNo(): Long {
    val formattedDate = SimpleDateFormat("yyyyMMddHHmm").format(Date())
    return formattedDate.toLong()
}

val computedBuildNo = generateBuildNo()
println("APPMOUSE BUILD_NO GENERATED: $computedBuildNo")

android {
    namespace = "hdisoft.app.mouse"
    compileSdk = 34

    defaultConfig {
        applicationId = "hdisoft.app.mouse"
        // BluetoothHidDevice (HID Device Profile) only exists from API 28, but we
        // don't gate minSdk on it: MainActivity checks Build.VERSION.SDK_INT and
        // never touches that class below API 28, showing "unsupported" instead.
        // Keeping minSdk low lets the app install on older devices and explain
        // itself, rather than the OS silently refusing to install it at all.
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("long", "BUILD_NO", "${computedBuildNo}L")
    }

    signingConfigs {
        getByName("debug") {
            // Force legacy v1 (JAR) signing alongside v2: PackageManager's
            // getPackageArchiveInfo (used to verify a downloaded OTA update
            // before install) fails to read signingInfo for v2-only APKs on
            // some API 28/29 devices — v1 is what it reliably parses.
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation(project(":libs:appupdate"))
    implementation(project(":libs:core"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
