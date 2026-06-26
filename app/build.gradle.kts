plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.calltimer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.calltimer"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            // Giảm dung lượng APK: chỉ build cho CPU phổ biến trên điện thoại.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // VoIP / SIP. Nếu build báo không tìm thấy version, đổi sang "5.3.+".
    implementation("org.linphone:linphone-sdk-android:5.4.+")
}
