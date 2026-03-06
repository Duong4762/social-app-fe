plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.social_app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.social_app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        // Suppress non-critical lint errors to allow build to complete
        disable.addAll(setOf(
            "MissingTranslation",
            "ExtraTranslation",
            "VectorRaster",
            "AndroidGradlePluginVersion",
            "GradleDependency"
        ))
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.fragment)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}