plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.cosmic.core.common"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
