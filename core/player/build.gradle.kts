plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.cosmic.core.player"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:db"))
    implementation(project(":core:prefs"))
    implementation(libs.androidx.core)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    coreLibraryDesugaring(libs.desugar.jdk)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
