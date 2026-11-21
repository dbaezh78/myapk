plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "com.oukitel.wp56.screenoff"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.oukitel.wp56.screenoff"
    minSdk = 26
    targetSdk = 34
    versionCode = 2
    versionName = "1.0"
  }

  signingConfigs {
    create("release") {
      storeFile = file("release-key.jks")
      storePassword = "wp56screenoff"
      keyAlias = "wp56key"
      keyPassword = "wp56screenoff"
      enableV1Signing = true
      enableV2Signing = true
      enableV3Signing = true
    }
  }

  buildTypes {
    debug {
      signingConfig = signingConfigs.getByName("release")
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = false
      signingConfig = signingConfigs.getByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = false
    aidl = false
    buildConfig = false
    shaders = false
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  testImplementation(libs.junit)
}
