plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lfmlocal.engine"
    compileSdk = 34
    ndkVersion = "28.2.13676358"

    val rebuildNative = project.findProperty("rebuildNative") == "true"
    val hasPrebuiltLibs = file("src/main/jniLibs/arm64-v8a/liblfm_jni.so").exists()

    defaultConfig {
        minSdk = 28

        ndk {
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
