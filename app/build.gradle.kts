import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localConfiguration = Properties().apply {
    val configurationFile = rootProject.file("local.properties")
    if (configurationFile.isFile) {
        configurationFile.inputStream().use { load(it) }
    }
}

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val dropboxAppKey = providers.gradleProperty("DROPBOX_APP_KEY")
    .orElse(providers.environmentVariable("DROPBOX_APP_KEY"))
    .getOrElse(localConfiguration.getProperty("DROPBOX_APP_KEY", ""))
    .trim()

val feedbackUrl = providers.gradleProperty("FEEDBACK_URL")
    .orElse(providers.environmentVariable("FEEDBACK_URL"))
    .getOrElse(localConfiguration.getProperty("FEEDBACK_URL", "https://feedback.sakru.ch/"))
    .trim()

require(dropboxAppKey.isEmpty() || dropboxAppKey.matches(Regex("[A-Za-z0-9]+"))) {
    "DROPBOX_APP_KEY must contain only letters and numbers."
}

require(feedbackUrl.isEmpty() || feedbackUrl.startsWith("https://")) {
    "FEEDBACK_URL must be empty or use HTTPS."
}

android {
    namespace = "ch.sakru.calibrereader"
    compileSdk = 36

    defaultConfig {
        applicationId = "ch.sakru.calibrereader"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "DROPBOX_APP_KEY", buildConfigString(dropboxAppKey))
        buildConfigField("String", "FEEDBACK_URL", buildConfigString(feedbackUrl))
        manifestPlaceholders["dropboxRedirectScheme"] =
            if (dropboxAppKey.isEmpty()) "db-unconfigured" else "db-" + dropboxAppKey
        manifestPlaceholders["dropboxEnabled"] = dropboxAppKey.isNotEmpty().toString()
    }

    androidResources {
        localeFilters += listOf("en", "de", "fr", "tr")
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat:1.8.0")

    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.21.8")
    implementation("com.microsoft.identity.client:msal:8.4.1")
    implementation("com.dropbox.core:dropbox-android-sdk:7.1.1")
    implementation("com.dropbox.core:dropbox-core-sdk:7.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
