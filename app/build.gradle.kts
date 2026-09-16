plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.airgesture.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.airgesture.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 31
        versionName = "0.6.1-rc2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        providers.gradleProperty("targetAbi").orNull?.let { selected ->
            require(selected in setOf("arm64-v8a","armeabi-v7a","x86_64","x86"))
            ndk.abiFilters.add(selected)
        }
    }
    buildFeatures { buildConfig = true }
    // Private human recordings never ship in Git or in a distributable APK.
    sourceSets.getByName("test") {
        resources.setSrcDirs(emptyList<String>())
        if (providers.gradleProperty("withPrivateFixtures").orNull == "true") {
            java.srcDir("src/privateTest/java")
            resources.srcDir("src/privateTest/resources")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled=true
            isShrinkResources=true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
        }
    }
    // The Kotlin API loads JNI + ONNX Runtime only. C/C++ wrapper APIs are unused.
    packaging { jniLibs.excludes += setOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

val verifyRuntimeArtifacts by tasks.registering {
    val manifestFile = rootProject.file("config/artifacts.json")
    inputs.file(manifestFile)
    doLast {
        val paths = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(manifestFile.readText()).map { it.groupValues[1] }.toList()
        check(paths.isNotEmpty()) { "No runtime artifacts declared" }
        val missing = paths.filter { !rootProject.file(it).isFile }
        check(missing.isEmpty()) {
            "Missing runtime artifacts: ${missing.joinToString()}. Read THIRD_PARTY_NOTICES.md and run python3 scripts/fetch-artifacts.py --accept-model-licenses"
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyRuntimeArtifacts) }

dependencies {
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    implementation("androidx.activity:activity-ktx:1.12.3")
    implementation("androidx.camera:camera-core:1.5.3")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")
    implementation("com.google.mediapipe:tasks-vision:0.10.35")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
