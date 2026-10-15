import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val firebaseApiKey = providers.gradleProperty("BRUJULA_FIREBASE_API_KEY").orNull ?: System.getenv("BRUJULA_FIREBASE_API_KEY") ?: ""
val firebaseAppId = providers.gradleProperty("BRUJULA_FIREBASE_APP_ID").orNull ?: System.getenv("BRUJULA_FIREBASE_APP_ID") ?: ""
val firebaseProjectId = providers.gradleProperty("BRUJULA_FIREBASE_PROJECT_ID").orNull ?: System.getenv("BRUJULA_FIREBASE_PROJECT_ID") ?: ""
val firebaseEmulatorHost = providers.gradleProperty("BRUJULA_FIREBASE_EMULATOR_HOST").orNull ?: System.getenv("BRUJULA_FIREBASE_EMULATOR_HOST") ?: ""
fun quotedBuildConfig(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android { namespace = "cl.brujula.app"; compileSdk = 35
    defaultConfig {
        applicationId = "cl.brujula.app"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("versionCode").getOrElse("28").toInt()
        versionName = providers.gradleProperty("versionName").getOrElse("0.28.0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "FIREBASE_API_KEY", quotedBuildConfig(firebaseApiKey))
        buildConfigField("String", "FIREBASE_APP_ID", quotedBuildConfig(firebaseAppId))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quotedBuildConfig(firebaseProjectId))
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", quotedBuildConfig(firebaseEmulatorHost))
    }

    val releaseStoreFile = providers.gradleProperty("BRUJULA_STORE_FILE").orNull ?: System.getenv("BRUJULA_STORE_FILE")
    val releaseStorePassword = providers.gradleProperty("BRUJULA_STORE_PASSWORD").orNull ?: System.getenv("BRUJULA_STORE_PASSWORD")
    val releaseKeyAlias = providers.gradleProperty("BRUJULA_KEY_ALIAS").orNull ?: System.getenv("BRUJULA_KEY_ALIAS")
    val releaseKeyPassword = providers.gradleProperty("BRUJULA_KEY_PASSWORD").orNull ?: System.getenv("BRUJULA_KEY_PASSWORD")

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
