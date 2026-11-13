plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

base { archivesName.set("LiquidGlass3D") }

android {
  namespace = "com.droid.liquidglass3d"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.droid.liquidglass3d"
    minSdk = 33 // RuntimeShader (AGSL) requires API 33
    targetSdk = 37
    versionCode = 5
    versionName = "1.4.0"
    ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
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

  buildFeatures { compose = true }

  // The .tflite depth model must not be compressed so LiteRT can mmap it.
  androidResources { noCompress += listOf("tflite", "agsl") }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.litert)
}
