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
        val versionCodeValue = (project.findProperty("VERSION_CODE") as String).toInt()
        versionCode = versionCodeValue
        // Version format: 1.{incrementalNumber}
        versionName = "1.$versionCodeValue"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Build timestamp for update checking - captured at configuration time
        val buildTime = System.currentTimeMillis()
        buildConfigField("long", "BUILD_TIMESTAMP", "${buildTime}L")
        // Also store human-readable build date for display
        val buildDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(buildTime))
        buildConfigField("String", "BUILD_DATE", "\"$buildDateStr\"")

        // Local-dev-only file logging (writes to /sdcard/apk-logs/HyperWhisper/).
        // Off by default. Enable locally with -PdevLogs=true or `devLogs=true` in
        // ~/.gradle/gradle.properties. Never commit `devLogs=true` to the repo.
        // Combined with BuildConfig.DEBUG at the call site so release builds are no-ops.
        val devLogsProp = (project.findProperty("devLogs") ?: "false").toString()
        buildConfigField("boolean", "DEV_LOGS_ENABLED", devLogsProp)

        // arm64-v8a only on the first whisper.cpp pass — see vendoring notes.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // libwhisper.so is built out-of-band by app/scripts/build-whisper-so.sh
    // using Termux's on-device clang (NDK r29, aarch64 host). The resulting
    // .so is dropped at app/src/main/jniLibs/arm64-v8a/ and AGP just packs it.
    // Why not externalNativeBuild + CMake? Google ships NDK only for x86_64
    // hosts; on this aarch64 device the SDK clang can't run, but Termux clang
    // (same NDK r29, native aarch64 build) targets Android and works fine.

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
        buildConfig = true
        compose = true
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

    // MediaPipe LLM Inference — Gemma on-device.
    // Loads .bin / .task models converted for MediaPipe (e.g.
    // huggingface.co/litert-community/Gemma2-2B-it). GPU acceleration on
    // supported devices, CPU fallback otherwise.
    implementation("com.google.mediapipe:tasks-genai:0.10.27")

    // Accompanist (for permissions)
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
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

    // Single output directory
    val outputDir = file("${rootProject.projectDir}/builds")

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
