import java.util.Properties
import java.io.FileInputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Import for androidComponents API
import com.android.build.api.variant.FilterConfiguration.FilterType

android {
    namespace = "com.hyperwhisper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hyperwhisper"
        minSdk = 26
        targetSdk = 34
        versionCode = (project.findProperty("VERSION_CODE") as String).toInt()
        versionName = project.findProperty("VERSION_NAME") as String

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // BuildConfig fields for conditional features
        buildConfigField("boolean", "CLOUD_ONLY_BUILD", "false")
        buildConfigField("boolean", "CLOUD_BUILD", "false")
        buildConfigField("boolean", "LOCAL_BUILD", "false")
        buildConfigField("boolean", "INCLUDES_NATIVE_LIBS", "false")
    }

    // Product flavors for different build variants
    // - cloudOnly: Cloud APIs only, no local mode option (built locally on Android)
    // - cloud: Cloud APIs with option to use local mode (built on GitHub)
    // - local: Pre-built native libs included (built on GitHub)
    flavorDimensions += "mode"

    productFlavors {
        create("cloudOnly") {
            dimension = "mode"
            applicationIdSuffix = ".cloudonly"
            versionNameSuffix = "-cloudOnly"

            // BuildConfig: Cloud-only build, no local mode option
            buildConfigField("boolean", "CLOUD_ONLY_BUILD", "true")
            buildConfigField("boolean", "CLOUD_BUILD", "true")
            buildConfigField("boolean", "LOCAL_BUILD", "false")
            buildConfigField("boolean", "INCLUDES_NATIVE_LIBS", "false")
        }

        create("cloud") {
            dimension = "mode"
            applicationIdSuffix = ".cloud"
            versionNameSuffix = "-cloud"

            // BuildConfig: Cloud build with local mode option available
            buildConfigField("boolean", "CLOUD_ONLY_BUILD", "false")
            buildConfigField("boolean", "CLOUD_BUILD", "true")
            buildConfigField("boolean", "LOCAL_BUILD", "false")
            buildConfigField("boolean", "INCLUDES_NATIVE_LIBS", "false")
        }

        create("local") {
            dimension = "mode"
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"

            // BuildConfig: Local build with pre-built native libs
            buildConfigField("boolean", "CLOUD_ONLY_BUILD", "false")
            buildConfigField("boolean", "CLOUD_BUILD", "false")
            buildConfigField("boolean", "LOCAL_BUILD", "true")
            buildConfigField("boolean", "INCLUDES_NATIVE_LIBS", "true")

            // NDK configuration for whisper.cpp (local flavor only)
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }

            // CMake configuration (local flavor only)
            externalNativeBuild {
                cmake {
                    cppFlags += listOf("-std=c++17", "-frtti", "-fexceptions")
                    arguments += listOf(
                        "-DANDROID_STL=c++_shared",
                        "-DANDROID_PLATFORM=android-26"
                    )
                }
            }
        }
    }

    // Note: Native whisper code is included in both flavors but:
    // - Local flavor: Uses real native implementations via FlavorModule
    // - Cloud flavor: Uses stub implementations, ProGuard/R8 removes unused native code
    // - Model assets (75MB) are excluded from cloud via packaging options below

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Signing configuration for consistent signatures across local and CI builds
    signingConfigs {
        create("shared") {
            // Use keystore from environment or fall back to debug keystore
            val keystorePath = System.getenv("KEYSTORE_FILE")
                ?: file("${System.getProperty("user.home")}/.android/debug.keystore").absolutePath
            val keystorePass = System.getenv("KEYSTORE_PASSWORD") ?: "android"
            val keyAliasName = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
            val keyPass = System.getenv("KEY_PASSWORD") ?: "android"

            storeFile = file(keystorePath)
            storePassword = keystorePass
            keyAlias = keyAliasName
            keyPassword = keyPass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use shared signing for consistent signatures
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            // Use shared signing for consistent signatures across local and CI
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xjvm-default=all"
    }

    buildFeatures {
        buildConfig = true  // Enable BuildConfig generation
        compose = true
        prefab = true
    }

    // External native build for whisper.cpp
    // NOTE: This requires NDK and CMake, which are incompatible with Termux on Android
    // For Termux builds: Pre-built .so files should be placed in src/main/jniLibs/
    // For CI/desktop builds: Uncomment the section below

    // Check if pre-built native libs exist
    val hasPreBuiltLibs = file("src/main/jniLibs/arm64-v8a/libhyperwhisper_jni.so").exists() ||
                         file("src/main/jniLibs/armeabi-v7a/libhyperwhisper_jni.so").exists()

    // Check if whisper submodule exists (needed for native build)
    val hasWhisperSubmodule = file("src/main/cpp/whisper/CMakeLists.txt").exists()

    // Only configure CMake if:
    // - Pre-built libs don't exist AND
    // - Whisper submodule is present (cloud flavor skips submodules)
    if (!hasPreBuiltLibs && hasWhisperSubmodule) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    lint {
        disable.add("MutableCollectionMutableState")
        disable.add("AutoboxingStateCreation")
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.compose.runtime:runtime-livedata")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Accompanist (for permissions)
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Auto-increment version code on every build
tasks.register("incrementVersionCode") {
    doLast {
        val propertiesFile = file("../gradle.properties")
        if (propertiesFile.exists()) {
            val properties = Properties()
            FileInputStream(propertiesFile).use { properties.load(it) }

            val currentVersionCode = properties.getProperty("VERSION_CODE")?.toIntOrNull() ?: 1
            val newVersionCode = currentVersionCode + 1

            properties.setProperty("VERSION_CODE", newVersionCode.toString())

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
            FileWriter(propertiesFile).use {
                properties.store(it, "Auto-incremented by Gradle on $timestamp")
            }

            println("✓ Version code incremented: $currentVersionCode → $newVersionCode")
        }
    }
}

// Run incrementVersionCode before assembling debug or release builds
tasks.whenTaskAdded {
    if (name == "assembleDebug" || name == "assembleRelease") {
        dependsOn("incrementVersionCode")
    }
}

// Copy APKs to /builds directory after building
android.applicationVariants.all {
    val variant = this
    val variantName = variant.name
    val flavorName = variant.flavorName ?: "default"

    // Define output directory based on flavor
    val outputDir = when (flavorName) {
        "cloudOnly" -> file("${rootProject.projectDir}/builds/cloudonly")
        "cloud" -> file("${rootProject.projectDir}/builds/cloud")
        "local" -> file("${rootProject.projectDir}/builds/local")
        else -> file("${rootProject.projectDir}/builds/${flavorName}")
    }

    // Create task to copy APK to custom location
    val variantNameCapitalized = variantName.replaceFirstChar { it.uppercase() }
    val copyTask = tasks.register("copy${variantNameCapitalized}Apk", Copy::class.java) {
        group = "build"
        description = "Copy ${variantName} APK to ${outputDir.path}"

        from(variant.outputs.map { it.outputFile })
        into(outputDir)

        dependsOn(variant.assembleProvider)
    }

    // Make assemble task depend on copy task
    tasks.named("assemble${variantNameCapitalized}").configure {
        finalizedBy(copyTask)
    }
}
