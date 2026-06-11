plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.github.opscalehub.avacore"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.github.opscalehub.avacore"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // بهینه‌سازی حجم: فقط معماری‌های پرکاربرد را نگه می‌داریم
        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
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
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    @Suppress("UnstableApiUsage")
    kotlinOptions {
        jvmTarget = "17"
        // Kotlin 2.0 compiles lambdas to invokedynamic by default, producing a
        // synthetic lambda that only exposes the erased invoke(Object). Sherpa-ONNX's
        // native generateWithCallback looks up the specialized invoke([F)Integer via
        // JNI, so we force class-based lambdas/SAM conversions to keep that method.
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xlambdas=class",
            "-Xsam-conversions=class"
        )
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes.add("/META-INF/AL2.0")
            excludes.add("/META-INF/LGPL2.1")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Local Sherpa-ONNX Engine
    implementation(files("libs/sherpa-onnx.aar"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
