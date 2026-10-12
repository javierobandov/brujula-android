import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val firebaseApiKey = providers.gradleProperty("BRUJULA_FIREBASE_API_KEY").orNull ?: System.getenv("BRUJULA_FIREBASE_API_KEY") ?: ""
val firebaseAppId = providers.gradleProperty("BRUJULA_FIREBASE_APP_ID").orNull ?: System.getenv("BRUJULA_FIREBASE_APP_ID") ?: ""
val firebaseProjectId = providers.gradleProperty("BRUJULA_FIREBASE_PROJECT_ID").orNull ?: System.getenv("BRUJULA_FIREBASE_PROJECT_ID") ?: ""
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
    }
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "FIREBASE_API_KEY", quotedBuildConfig(firebaseApiKey))
        buildConfigField("String", "FIREBASE_APP_ID", quotedBuildConfig(firebaseAppId))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quotedBuildConfig(firebaseProjectId))
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

