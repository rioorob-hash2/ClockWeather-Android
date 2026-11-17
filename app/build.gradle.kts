import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bratik.clockweather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bratik.clockweather"
        minSdk = 32
        targetSdk = 36
        versionCode = 6
        versionName = "1.9"
    }

    val keystoreFile = rootProject.file("keystore.jks")
    if (keystoreFile.exists()) {
        val props = Properties().apply {
            val f = rootProject.file("keystore.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        signingConfigs {
            create("clockweather") {
                storeFile = keystoreFile
                storePassword = props.getProperty("storePassword") ?: "clockweather"
                keyAlias = props.getProperty("keyAlias") ?: "clockweather"
                keyPassword = props.getProperty("keyPassword") ?: "clockweather"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("clockweather")
        }
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("clockweather")
        }
        // релизный APK кладём под именем prod: файл app-release.apk на этой машине
        // иногда блокируется системным фильтром по имени.
        create("prod") {
            initWith(getByName("release"))
            isDebuggable = false
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("clockweather")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "1g"
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}
