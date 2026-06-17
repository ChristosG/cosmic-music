plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.cosmic.core.extractor"
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
    implementation(libs.okhttp)
    implementation(libs.newpipe.extractor)
    implementation(libs.jsoup)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // youtubedl-android wrapper bundled as a local AAR (built with Python 3.11
    // from upstream tag 0.18.1; see scripts/build-youtubedl-aar.sh). We can't
    // pull from jitpack because their builders lack JDK 17.
    //
    // AAR file dependencies don't carry transitive POM info, so we add the
    // wrapper's runtime deps (jackson, commons-io, commons-compress) here.
    // commons-compress in particular is needed by ZipUtils to extract the
    // bundled Python tarball on first run.
    implementation(files("libs/youtubedl-android-library-0.18.1.aar"))
    implementation(files("libs/youtubedl-android-common-0.18.1.aar"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.commons.io)
    implementation(libs.commons.compress)

    coreLibraryDesugaring(libs.desugar.jdk)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
