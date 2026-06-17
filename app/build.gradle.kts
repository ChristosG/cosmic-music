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
        // versionCode is driven by CI (github.run_number) so every published
        // build is monotonically higher and Android always accepts the update.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // The published APK is signed with the same key that signed the early
        // sideload installs (the machine's Android debug key), so GitHub builds
        // install as in-place UPDATES over existing installs — preserving the
        // Room DB (playlists, history) and DataStore prefs. CI injects the key
        // via env from GitHub Secrets; local builds fall back to ~/.android.
        create("release") {
            val ksPath = System.getenv("KEYSTORE_FILE")
            if (ksPath != null && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else {
                storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Match the existing on-device applicationId (app.cosmic.player.debug)
            // so the published build updates it in place instead of installing
            // as a separate app. NOT debuggable — this is a real release build.
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("release")
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
