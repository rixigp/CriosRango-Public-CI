plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
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
val debugSigningConfigured = listOf(
    debugSigningPassword,
    debugKeyAlias,
    debugKeyPassword
).all { !it.isNullOrBlank() } && file(debugKeystoreFile).isFile

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "es.criosrango.app"
    compileSdk = 36
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "es.criosrango.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 167
        versionName = "1.0.167"
    }
    signingConfigs {
        getByName("debug") {
            if (debugSigningConfigured) {
                storeType = "JKS"
                storeFile = file(debugKeystoreFile)
                storePassword = debugSigningPassword
                keyAlias = debugKeyAlias
                keyPassword = debugKeyPassword
            }
        }
        if (releaseSigningConfigured) {
            create("release") {
                storeType = "JKS"
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (debugSigningConfigured) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

gradle.taskGraph.whenReady {
    if (allTasks.any { it.path == ":app:assembleDebug" }) {
        check(debugSigningConfigured) {
            "Android debug tasks require the configured debug keystore and debug signing credentials"
        }
    }
    if (allTasks.any { it.path == ":app:bundleRelease" }) {
        check(releaseSigningConfigured) {
            "Signed :app:bundleRelease requires ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD"
        }
        check(file(releaseKeystorePath!!).isFile) {
            "Signed :app:bundleRelease requires a readable keystore at ANDROID_KEYSTORE_PATH"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.core:core-ktx:1.15.0")
    // Retained for AccountSessionStore legacy-token migration; PendingCardPaymentStore does not use it.
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
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
    implementation("com.google.firebase:firebase-messaging")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.json:json:20250517")

    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
}

// Live audits depend on production services and must not gate deterministic CI.
tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach {
    val runLiveAudits = providers.gradleProperty("runLiveAudits")
        .map(String::toBoolean)
        .getOrElse(false)
    if (!runLiveAudits) {
        exclude("**/LiveImageOptimizationAuditTest.class")
    }
}
