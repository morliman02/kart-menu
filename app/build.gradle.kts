plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fatih.kartmenu"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fatih.kartmenu"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "2.4"
    }

    // Sabit (depoda tutulan) debug anahtarı: her derlemede aynı imza oluşur, böylece
    // yeni sürüm eskisinin ÜSTÜNE güncelleme olarak kurulur (önce silmeye gerek kalmaz).
    // Bu yalnızca bir test/debug anahtarıdır, gizli bir değer değildir.
    signingConfigs {
        create("shared") {
            storeFile = file("debug.keystore")
            storePassword = "kartmenu"
            keyAlias = "kartmenu"
            keyPassword = "kartmenu"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
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

// Sadece Android framework + Kotlin standart kütüphanesi kullanılıyor; ek bağımlılık yok.
