plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.cosmic.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.cosmic.player"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") // sideload-only; replace with real keystore later
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures { compose = true }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
        )
        // youtubedl-android ships its Python interpreter as libpython.zip.so —
        // a zip masquerading as a native lib so Android's installer extracts
        // it next to the real .so files. With useLegacyPackaging=false (AGP 8
        // default) the .so files stay compressed inside the APK and the
        // Python loader gets ENOENT. Forcing legacy packaging makes Android
        // extract them to /data/app/.../lib/arm64/ on install.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:db"))
    implementation(project(":core:metadata"))
    implementation(project(":core:player"))
    implementation(project(":core:download"))
    implementation(project(":core:extractor"))
    implementation(project(":feature:library"))
    implementation(project(":feature:nowplaying"))
    implementation(project(":feature:download"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:search"))
    implementation(project(":feature:common"))
    implementation(project(":feature:playlists"))
    implementation(project(":core:prefs"))
    implementation(project(":core:shuffle"))
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.text.google.fonts)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.nav.compose)

    implementation(libs.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.media3.session) // MediaController in the app process

    coreLibraryDesugaring(libs.desugar.jdk)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
    }
}
