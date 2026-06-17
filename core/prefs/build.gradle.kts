plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.cosmic.core.prefs"
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
    implementation(libs.androidx.core)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    coreLibraryDesugaring(libs.desugar.jdk)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
