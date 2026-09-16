plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android { namespace = "hdisoft.app.bluetooth"; compileSdk = 35
    defaultConfig { minSdk = 21 }
    kotlinOptions { jvmTarget = "1.8" }
}

// The Bluetooth implementation is shared from the app source tree during this
// extraction phase, allowing existing package imports to remain source-compatible.
android.sourceSets.getByName("main").java.srcDirs(
    "../../app/src/main/java/hdisoft/app/cideploy/features/bluetooth/data",
    "../../app/src/main/java/hdisoft/app/cideploy/features/bluetooth/security/data"
)

dependencies {
    implementation("androidx.annotation:annotation:1.7.1")
}
