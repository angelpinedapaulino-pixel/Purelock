plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.purelock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.purelock"
        minSdk = 30            // Android 11+ (setUserControlDisabledPackages); tu Xiaomi usa Android 12
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "2").toInt()
        versionName = "2.0"
    }

    // La clave de firma NUNCA va en el repositorio: se inyecta por variables de entorno (secretos de GitHub).
    signingConfigs {
        create("release") {
            val path = System.getenv("KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Sin bloque de metadatos de dependencias dentro del APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
