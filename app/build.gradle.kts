plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.isFile) {
    localPropertiesFile.inputStream().use(localProperties::load)
}

val localSigningProperties = Properties()
val localSigningFile = rootProject.file(".criosrango-debug-secrets")
if (localSigningFile.isFile) {
    localSigningFile.inputStream().use(localSigningProperties::load)
}

fun signingValue(name: String): String? = providers.environmentVariable(name).orNull ?: localSigningProperties.getProperty(name)
val debugKeystoreFile = providers.environmentVariable("CRIOSRANGO_DEBUG_KEYSTORE_FILE").orNull ?: rootProject.file("criosrango-debug.jks").absolutePath
val debugSigningPassword = signingValue("CRIOSRANGO_DEBUG_KEYSTORE_" + "PASSWORD")
val debugKeyAlias = signingValue("CRIOSRANGO_DEBUG_KEY_ALIAS")
val debugKeyPassword = signingValue("CRIOSRANGO_DEBUG_KEY_" + "PASSWORD")
check(file(debugKeystoreFile).isFile) { "Missing debug keystore: $debugKeystoreFile" }
check(!debugSigningPassword.isNullOrBlank()) { "Missing signing configuration" }
check(!debugKeyAlias.isNullOrBlank()) { "Missing debug key alias" }
check(!debugKeyPassword.isNullOrBlank()) { "Missing debug key configuration" }

android {
    namespace = "es.criosrango.app"
    compileSdk = 35
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "es.criosrango.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 125
        versionName = "1.0.125"
    }
    signingConfigs {
        getByName("debug") {
            storeType = "JKS"
            storeFile = file(debugKeystoreFile)
            storePassword = debugSigningPassword
            keyAlias = debugKeyAlias
            keyPassword = debugKeyPassword
        }
    }
    buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("debug") } }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("moe.tlaster.compose.icons:tabler-icons-android:1.3.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-crashlytics")

    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
}
