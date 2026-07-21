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
val computedVersionCode = (System.currentTimeMillis() / 60_000L).toInt()
println("CI-DEPLOY BUILD_NO GENERATED: $computedBuildNo")

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
        create("release") {
            storeFile = file("ci-deploy.jks")
            storePassword = "Tt35!2233"
            keyAlias = "cideploy"
            keyPassword = "Tt35!2233"
        }
    }

    buildTypes {
        debug {
            // Keep the debug artifact for testing, but sign it with the same
            // project key so it can OTA-update the installed release build.
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
