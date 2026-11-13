import java.util.Properties
import java.io.RandomAccessFile
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.jaredsburrows.license")
}

val releaseSigningProperties = Properties().apply {
    val configFile = rootProject.file("signing.properties")
    if (configFile.exists()) configFile.inputStream().use { load(it) }
}

// Pin both the release and its digest; never consume an unverified "latest" core.
val nativeCoreVersion = "v26.11.9"
val nativeCoreSha256 = "cdfef146218233a31a8b52c8b1016557730d3b63fc121d4c84cacea50e669155"
val nativeCoreFile = rootProject.layout.projectDirectory.file(".gradle/native-core/$nativeCoreVersion/libv2ray.aar")
fun coreSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(65536)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
// IDE sync resolves local AARs without running producer tasks. Prepare it during
// configuration, in a cache outside build/ so clean cannot remove it.
run {
    val destination = nativeCoreFile.asFile
    destination.parentFile.mkdirs()
    RandomAccessFile(File(destination.parentFile, ".lock"), "rw").use { lockFile ->
        lockFile.channel.lock().use {
            if (destination.isFile && coreSha256(destination) == nativeCoreSha256) return@run
            val staging = File(destination.parentFile, "libv2ray.aar.part")
            try {
                val local = file("libs/libv2ray.aar")
                if (local.isFile && coreSha256(local) == nativeCoreSha256) {
                    local.copyTo(staging, overwrite = true)
                } else {
                    check(!gradle.startParameter.isOffline) {
                        "Pinned core is unavailable offline. Build once online or provide verified app/libs/libv2ray.aar."
                    }
                    val connection = URI(
                        "https://github.com/2dust/AndroidLibXrayLite/releases/download/$nativeCoreVersion/libv2ray.aar"
                    ).toURL().openConnection().apply {
                        connectTimeout = 15000
                        readTimeout = 60000
                    }
                    connection.getInputStream().use { input ->
                        staging.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                check(coreSha256(staging) == nativeCoreSha256) {
                    "libv2ray.aar checksum mismatch. Refusing to use the downloaded core."
                }
                staging.copyTo(destination, overwrite = true)
            } finally {
                staging.delete()
            }
        }
    }
}

// Keep the explicit setup command available for scripts and CI.
tasks.register("provisionNativeCore") {
    group = "build setup"
    description = "Ensures the pinned AndroidLibXrayLite AAR is available (prepared during configuration)."
}

android {
    signingConfigs {
        create("release") {
            storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile", ".signing/release.keystore"))
            storePassword = releaseSigningProperties.getProperty("storePassword")
            keyAlias = releaseSigningProperties.getProperty("keyAlias")
            keyPassword = releaseSigningProperties.getProperty("keyPassword")
        }
    }
    namespace = "com.v2ray.ang"
    compileSdk = 37

    defaultConfig {
        applicationId = "pro.tngl"
        minSdk = 24
        targetSdk = 37
        versionCode = 12
        versionName = "0.9.3-test"

        val abiFilterList = (properties["ABI_FILTERS"] as? String)?.split(';')
        splits {
            abi {
                isEnable = true
                reset()
                if (!abiFilterList.isNullOrEmpty()) {
                    include(*abiFilterList.toTypedArray())
                } else {
                    include(
                        "arm64-v8a",
                        "armeabi-v7a",
                        "x86_64",
                        "x86"
                    )
                }
                isUniversalApk = abiFilterList.isNullOrEmpty()
            }
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    flavorDimensions.add("distribution")
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"F-Droid\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"Play Store\"")
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    applicationVariants.all {
        val variant = this
        val versionCodes =
            mapOf("armeabi-v7a" to 4, "arm64-v8a" to 4, "x86" to 4, "x86_64" to 4, "universal" to 4)

        variant.outputs
            .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
            .forEach { output ->
                val abi = output.getFilter("ABI") ?: "universal"
                output.outputFileName = "tngl_${variant.versionName}_${abi}.apk"
                if (versionCodes.containsKey(abi)) {
                    output.versionCodeOverride =
                        (1000000 * versionCodes[abi]!!).plus(variant.versionCode)
                } else {
                    return@forEach
                }
            }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "ru")
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            // Treat Jupiter discovery warnings as failures: JUnit 4 failed on an invalid @Test
            // method, such as one that returns a value, while Jupiter only warns and skips it.
            // The key is @API(status = EXPERIMENTAL) in JUnit 6; after a JUnit upgrade, check
            // that a temporary `@Test fun probe() = 1` still fails the unit test task.
            it.systemProperty("junit.platform.discovery.issue.severity.critical", "WARNING")
        }
    }
}

dependencies {
    // Core Libraries
    implementation(files(nativeCoreFile))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))).matching {
        exclude("libv2ray.aar")
    })

    // AndroidX Core Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Compose Libraries
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Data and Storage Libraries
    implementation(libs.mmkv.static)
    implementation(libs.gson)
    implementation(libs.okhttp)

    // Reactive and Utility Libraries
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // QR Code: CameraX + ZXing
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.compose)
    implementation(libs.core) // zxing core

    // AndroidX Lifecycle and Architecture Components
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // Background Task Libraries
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.multiprocess)

    // Reorderable list
    implementation(libs.reorderable)

    // Testing Libraries
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

// Connected tests may uninstall the target app. Never select personal devices implicitly.
tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }
    .configureEach {
        doFirst {
            check(System.getenv("ANDROID_SERIAL")?.startsWith("emulator-") == true) {
                "Run connected UI tests only on a disposable emulator: set ANDROID_SERIAL=emulator-5554. Target app data may be deleted."
            }
        }
    }
