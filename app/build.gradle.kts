plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lfmlocal.app"
    compileSdk = 34
    ndkVersion = "28.2.13676358"

    val rebuildNative = project.findProperty("rebuildNative") == "true"
    val hasPrebuiltLibs = file("src/main/jniLibs/arm64-v8a/liblfm_jni.so").exists()

    defaultConfig {
        applicationId = "com.lfmlocal.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            // Target 64-bit ARM for physical devices (Dimensity, Snapdragon, Tensor)
            abiFilters += listOf("arm64-v8a")
        }

        if (rebuildNative || !hasPrebuiltLibs) {
            externalNativeBuild {
                cmake {
                    cppFlags += listOf("-O3", "-std=c++17")
                    arguments += listOf(
                        "-DGGML_NATIVE=OFF",
                        "-DLLAMA_BUILD_TESTS=OFF",
                        "-DLLAMA_BUILD_EXAMPLES=OFF",
                        "-DLLAMA_BUILD_SERVER=OFF",
                        "-DBUILD_SHARED_LIBS=OFF",
                        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384",
                        "-DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
                    )
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
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
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += listOf("**/liblfm_jni.so", "**/libomp.so")
        }
    }
    sourceSets {
        getByName("main") {
            if (!rebuildNative) {
                jniLibs.srcDirs("src/main/jniLibs")
            }
        }
    }

    if (rebuildNative || !hasPrebuiltLibs) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1+"
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
