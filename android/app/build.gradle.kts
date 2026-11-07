plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  jacoco
}

base {
  archivesName.set("CacheCleaner")
}

android {
  namespace = "com.droid.cachecleaner"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.droid.cachecleaner"
    minSdk = 26
    targetSdk = 35
    versionCode = 6
    versionName = "2.2.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
    }
  }

  signingConfigs {
    create("upload") {
      storeFile = file(System.getenv("ANDROID_UPLOAD_KEYSTORE_PATH") ?: "keystore/upload-key.jks")
      storePassword = System.getenv("ANDROID_UPLOAD_KEYSTORE_PASSWORD") ?: "cachecleaner123"
      keyAlias = System.getenv("ANDROID_UPLOAD_KEY_ALIAS") ?: "upload-key-alias"
      keyPassword = System.getenv("ANDROID_UPLOAD_KEY_PASSWORD") ?: "cachecleaner123"
      enableV1Signing = true
      enableV2Signing = true
      enableV3Signing = true
    }
  }

  buildTypes {
    debug {
      enableUnitTestCoverage = true
      signingConfig = signingConfigs.getByName("upload")
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("upload")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
      all {
        it.extensions.configure(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class) {
          isIncludeNoLocationClasses = true
          excludes = listOf("jdk.internal.*")
        }
      }
    }
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  testImplementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)

  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.androidx.compose.ui.test.junit4)

  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
