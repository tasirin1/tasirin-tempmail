plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.tasirin.tempmail"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.tasirin.tempmail"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "0.7.2"
    }

    signingConfigs {
        create("release") {
            val storeFileProp = project.findProperty("storeFile") as String?
            if (storeFileProp != null) {
                storeFile = file(storeFileProp)
                storePassword = project.findProperty("storePassword") as String?
                keyAlias = project.findProperty("keyAlias") as String?
                keyPassword = project.findProperty("keyPassword") as String?
                // Keystore proyek berformat PKCS12 (dibuat dengan OpenSSL,
                // tersimpan sebagai secret GitHub — tidak pernah di repo).
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Tanda tangan hanya bila properti keystore diberikan (CI rilis).
            // Tanpa itu assembleRelease menghasilkan APK tanpa tanda tangan.
            if (project.findProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
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
    // Hanya yang benar-benar dipakai kode/layout (issue #15): appcompat.
    // core-ktx dan material pernah terdaftar tanpa satu pun pemakaian.
    implementation("androidx.appcompat:appcompat:1.7.0")

    testImplementation("junit:junit:4.13.2")
}
