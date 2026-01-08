plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.uka.peopleanalyser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.uka.peopleanalyser"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 如果希望限制 APK 支援的 ABI，可以解除下面註解並調整
        // ndk {
        //     abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        // }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }

    // 如果 AAR 含有 native libs，packagingOptions 可以避免 duplicate 問題
    packagingOptions {
        resources {
            pickFirsts += listOf("META-INF/LICENSE.md", "META-INF/LICENSE", "META-INF/NOTICE")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    // 網路請求
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON解析
    implementation("com.google.code.gson:gson:2.10.1")

    // RTSP串流庫
    implementation("com.github.pedroSG94.rtmp-rtsp-stream-client-java:rtplibrary:2.2.6")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // 相機支援
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")

    // USB攝影機支援：使用 UVCCamera（僅在本地 AAR 存在時加入依賴）
    val uvccameraAar = file("libs/UVCCamera-2.5.6.aar")
    if (uvccameraAar.exists()) {
        implementation(files(uvccameraAar))
    } else {
        // 未找到本地 AAR，為避免自動下載失敗導致 build 中斷，我們不在此自動加入遠端依賴。
        // 要執行真實的 UVCCamera 功能，請手動將 UVCCamera-2.5.6.aar 放到 app/libs/，或在可連網環境下啟用遠端依賴。
        // implementation("com.github.saki4510t:UVCCamera:2.5.6")
    }

    // 測試庫
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}