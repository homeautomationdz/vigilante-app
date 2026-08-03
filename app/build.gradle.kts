plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.vigilante.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vigilante.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // Two editions (user decision): "full" = everything; "stats" = no attendance,
    // volunteer statistics kept front and center. Separate applicationId so both
    // can be installed side by side.
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "ATTENDANCE_ENABLED", "true")
            resValue("string", "app_label", "Vigilante")
        }
        create("stats") {
            dimension = "edition"
            applicationIdSuffix = ".stats"
            versionNameSuffix = "-stats"
            buildConfigField("boolean", "ATTENDANCE_ENABLED", "false")
            resValue("string", "app_label", "Vigilante إحصاء")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed with the debug keystore so this internal test build installs
            // directly on a device. Replace with a real release keystore before
            // any Play Store submission (SRS ch. 44 deliverables).
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation + DI
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room (runtime store; Excel is the official interchange format)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (session & app settings)
    implementation(libs.androidx.datastore)

    // WorkManager (backups, long operations)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.work.compiler)

    // QR generation + scanning
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)

    // Excel streaming read/write (low memory footprint per NFR)
    implementation(libs.fastexcel)
    implementation(libs.fastexcel.reader)

    // Password hashing
    implementation(libs.bcrypt)

    // NOTE: Apache POI is deliberately NOT a runtime dependency — it cannot run
    // on Android (log4j2 + desktop-JVM classes → NoClassDefFoundError, which is
    // exactly what broke backup/export before 1.5). Office encryption is
    // implemented in data/excel/crypto with plain javax.crypto, and POI is used
    // only in unit tests to prove the output is Excel-compatible.

    // Images
    implementation(libs.coil.compose)

    // Tests
    // Apache POI on the JVM is our reference implementation: tests encrypt with
    // our code and decrypt with POI (and vice-versa) so a regression that would
    // produce a file Excel cannot open fails the build instead of the phone.
    testImplementation(libs.poi.core)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
}
