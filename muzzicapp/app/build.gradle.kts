plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

val fullAudioBackendUrl = providers.gradleProperty("FULL_AUDIO_BACKEND_URL").orNull ?: ""
val zingMp3BackendUrl = providers.gradleProperty("ZINGMP3_BACKEND_URL").orNull ?: ""
val jamendoClientId = providers.gradleProperty("JAMENDO_CLIENT_ID").orNull ?: ""
val audiusAppName = providers.gradleProperty("AUDIUS_APP_NAME").orNull ?: "muzzic"

android {
    namespace = "com.example.muzzicapp"
    buildFeatures {
        buildConfig = true
    }
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.muzzicapp"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FULL_AUDIO_BACKEND_URL", "\"$fullAudioBackendUrl\"")
        buildConfigField("String", "ZINGMP3_BACKEND_URL", "\"$zingMp3BackendUrl\"")
        buildConfigField("String", "JAMENDO_CLIENT_ID", "\"$jamendoClientId\"")
        buildConfigField("String", "AUDIUS_APP_NAME", "\"$audiusAppName\"")
        manifestPlaceholders["redirectSchemeName"] = "muzzicapp"
        manifestPlaceholders["redirectHostName"] = "callback"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.cardview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    
    // External APIs & Media
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.palette:palette:1.0.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media:media:1.7.0")
    implementation("com.github.yausername.youtubedl-android:library:0.14.0")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")

    // Google Firebase (Auth & Cloud Firestore)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}