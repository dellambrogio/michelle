plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.michelle"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.michelle"
        minSdk = 36           // the Pixel 10 only (Android 16)
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += "arm64-v8a" }
    }
    // the release key lives outside the repo: MICHELLE_KEYSTORE, MICHELLE_KEYSTORE_PASSWORD, MICHELLE_KEY_ALIAS
    // in ~/.gradle/gradle.properties; without them the release APK comes out unsigned
    val keystore = providers.gradleProperty("MICHELLE_KEYSTORE").orNull
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = providers.gradleProperty("MICHELLE_KEYSTORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("MICHELLE_KEY_ALIAS").getOrElse("michelle")
            keyPassword = storePassword
        }
    }
    buildTypes {
        release {
            // no shrinking: the API client's JSON and Room find classes by name
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.exifinterface)
    implementation(libs.anthropic.java)
    implementation(libs.jackson.kotlin)
    testImplementation(libs.junit)
}
