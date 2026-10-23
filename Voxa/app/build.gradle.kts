import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val demoLicenseKey = Properties().apply {
    val source = rootProject.file("../config/demo-license-public-key.properties")
    if (source.exists()) source.inputStream().use { load(it) }
}
val hostedDemoKey = Properties().apply {
    val source = rootProject.file("../config/hosted-demo-license-public-key.properties")
    if (source.exists()) source.inputStream().use { load(it) }
}
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val webUrl = providers.gradleProperty("VOXA_WEB_URL").orElse("https://voxa.invalid")

android {
    namespace = "com.example.voxa"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.voxa"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.12.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["voxaWebHost"] = URI(webUrl.get()).host
        buildConfigField("String", "VOXA_WEB_URL", quoted(webUrl.get()))
        buildConfigField("String", "HOSTED_DEMO_KEY_ID", quoted(""))
        buildConfigField("String", "HOSTED_DEMO_PUBLIC_KEY", quoted(""))
    }

    signingConfigs {
        if (providers.environmentVariable("VOXA_KEYSTORE_PATH").isPresent) {
            create("production") {
                storeFile = file(providers.environmentVariable("VOXA_KEYSTORE_PATH").get())
                storePassword = providers.environmentVariable("VOXA_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("VOXA_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("VOXA_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "VOXA_WEB_URL", quoted(providers.gradleProperty("VOXA_WEB_URL").orElse("http://127.0.0.1:3000").get()))
            buildConfigField("String", "LICENSE_KEY_ID", quoted(demoLicenseKey.getProperty("kid", "demo-v1")))
            buildConfigField("String", "LICENSE_PUBLIC_KEY", quoted(demoLicenseKey.getProperty("publicKey", "")))
            buildConfigField("boolean", "WEB_UPGRADE_ENABLED", "true")
            buildConfigField("String", "HOSTED_DEMO_KEY_ID", quoted(hostedDemoKey.getProperty("kid", "")))
            buildConfigField("String", "HOSTED_DEMO_PUBLIC_KEY", quoted(hostedDemoKey.getProperty("publicKey", "")))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "LICENSE_KEY_ID", quoted(providers.gradleProperty("VOXA_LICENSE_KEY_ID").orElse("production-v1").get()))
            buildConfigField("String", "LICENSE_PUBLIC_KEY", quoted(providers.gradleProperty("VOXA_LICENSE_PUBLIC_KEY").orElse("").get()))
            // External purchase links are disabled for Play release builds by default.
            buildConfigField("boolean", "WEB_UPGRADE_ENABLED", providers.gradleProperty("VOXA_WEB_UPGRADE_ENABLED").orElse("false").get())
            signingConfig = signingConfigs.findByName("production")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("../config"))
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // KSP annotation processor (generates the actual SQLite database helper code during build)
    ksp(libs.androidx.room.compiler)

    // TensorFlow Lite (LiteRT with 16KB support)
    implementation(libs.litert)

    // Google Play Services Location
    implementation(libs.androidx.appcompat)
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("com.google.zxing:core:3.5.3")
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
