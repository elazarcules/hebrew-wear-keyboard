plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI run number becomes the version code, so every build installs over the previous one.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "ar.elaz.wearkeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.elaz.wearkeyboard"
        minSdk = 30
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.0.$buildNumber"
    }

    // A fixed debug key, committed on purpose: every CI build has the same
    // signature, so `adb install -r` upgrades in place without uninstalling.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
