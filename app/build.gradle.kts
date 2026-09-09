plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.syslikoffnet.overglow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.syslikoffnet.overglow"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
}

// Игра не имеет ни одной внешней зависимости: вся графика, звук и музыка
// генерируются кодом. Это гарантирует сборку в любой IDE и мгновенный CI.
dependencies {
}
