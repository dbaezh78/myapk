plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "com.oukitel.wp56.screenoff"
  compileSdk {
    version = release(36) {
      minorApiLevel = 1
    }
  }
  defaultConfig {
    applicationId = "com.oukitel.wp56.screenoff"
    minSdk = 28
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("debug")
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
