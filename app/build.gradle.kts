import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi") version "1.76.0"
    id("androidx.baselineprofile") version "1.5.0"
}

val appPackage = "com.catmap.dubai"

/**
 * Optional, for local testing only: put `catmap.emulatorHost=10.0.2.2` in local.properties to make
 * debug builds talk to the Firebase emulators (`firebase emulators:start`) instead of the cloud.
 */
val emulatorHost: String = providers.gradleProperty("catmap.emulatorHost").orNull ?: rootProject.file("local.properties").takeIf { it.exists() }
    ?.readLines()?.firstOrNull { it.startsWith("catmap.emulatorHost=") }
    ?.substringAfter("=")?.trim().orEmpty()
require(emulatorHost.isEmpty() || emulatorHost.matches(Regex("[A-Za-z0-9.:-]+"))) { "Invalid Firebase emulator host" }

/** Reads `key` from a Gradle property (-Pkey=value) or local.properties; empty when unset. */
fun localSetting(key: String): String = providers.gradleProperty(key).orNull ?: rootProject.file("local.properties").takeIf { it.exists() }
    ?.readLines()?.firstOrNull { it.startsWith("$key=") }
    ?.substringAfter("=")?.trim().orEmpty()

/** Optional MapTiler key for the satellite layer; without it the satellite button is hidden. */
val mapTilerKey = localSetting("catmap.maptilerKey")
require(mapTilerKey.matches(Regex("[A-Za-z0-9]*"))) { "Invalid MapTiler key" }

/** owner/repo whose GitHub Releases the in-app updater checks, e.g. `mohammad/catmap`. */
val githubRepo = localSetting("catmap.githubRepo")
require(githubRepo.isEmpty() || githubRepo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Invalid GitHub repo" }

/**
 * Firebase Storage (videos) and FCM push both need the paid Blaze plan, so they are off unless
 * `catmap.blaze=true`. Debug builds against the emulators always have Storage, which is free locally.
 */
val blazeEnabled = localSetting("catmap.blaze") == "true"

/**
 * CI passes the release version from the git tag. Local builds default to the lowest release, so a build
 * installed from this PC still sees (and can install) every published update.
 */
val appVersionName = localSetting("catmap.versionName").ifEmpty { "0.0.1" }
require(appVersionName.matches(Regex("""\d+\.\d+\.\d+"""))) { "versionName must look like 1.2.3" }
val appVersionCode = appVersionName.split(".").map { it.toInt() }.let { (a, b, c) -> a * 10_000 + b * 100 + c }

/** Release signing comes from keystore.properties (git-ignored) or from CI environment variables. */
val signing: Map<String, String> = rootProject.file("keystore.properties").takeIf { it.exists() }?.readLines()
    ?.filter { "=" in it && !it.trimStart().startsWith("#") }
    ?.associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
    ?: listOfNotNull(
        System.getenv("CATMAP_KEYSTORE")?.let { "storeFile" to it },
        System.getenv("CATMAP_KEYSTORE_PASSWORD")?.let { "storePassword" to it },
        System.getenv("CATMAP_KEY_ALIAS")?.let { "keyAlias" to it },
        System.getenv("CATMAP_KEY_PASSWORD")?.let { "keyPassword" to it },
    ).toMap()
val hasSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { !signing[it].isNullOrBlank() }

/**
 * Reads app/google-services.json (if present) and turns it into the string resources Firebase
 * looks for at startup. This is what the google-services Gradle plugin does, without making the
 * build fail when the file is missing: the app then shows a "Firebase setup required" screen.
 */
@Suppress("UNCHECKED_CAST")
fun firebaseResources(): Map<String, String> {
    val file = file("google-services.json")
    if (!file.exists()) return emptyMap()
    val json = JsonSlurper().parse(file) as Map<String, Any?>
    val project = json["project_info"] as Map<String, Any?>
    val client = (json["client"] as List<Map<String, Any?>>).firstOrNull {
        val info = it["client_info"] as Map<String, Any?>
        (info["android_client_info"] as Map<String, Any?>)["package_name"] == appPackage
    } ?: error("google-services.json has no Android app with package name $appPackage")
    val apiKey = (client["api_key"] as List<Map<String, Any?>>).first()["current_key"] as String
    return mapOf(
        "google_app_id" to ((client["client_info"] as Map<String, Any?>)["mobilesdk_app_id"] as String),
        "google_api_key" to apiKey,
        "gcm_defaultSenderId" to project["project_number"].toString(),
        "project_id" to project["project_id"].toString(),
        "google_storage_bucket" to (project["storage_bucket"]?.toString() ?: ""),
    )
}

/**
 * Points a build type at the local Firebase emulators when catmap.emulatorHost is set. Only debug and
 * staging call this, so a release can never inherit the fake demo-project resources.
 */
fun com.android.build.api.dsl.ApplicationBuildType.useEmulators() {
    buildConfigField("String", "EMULATOR_HOST", "\"$emulatorHost\"")
    if (emulatorHost.isNotEmpty()) {
        // Always use the isolated demo project, even if a real config file is present.
        resValue("string", "google_app_id", "1:1234567890:android:0000000000000000")
        resValue("string", "google_api_key", "demo-api-key")
        resValue("string", "project_id", "demo-catmap")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", "true")
        // The Storage emulator is free, so videos work in local demos.
        buildConfigField("boolean", "STORAGE_ENABLED", "true")
        resValue("string", "google_storage_bucket", "demo-catmap.appspot.com")
    }
}

android {
    namespace = appPackage
    compileSdk = 37

    defaultConfig {
        applicationId = appPackage
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        val firebase = firebaseResources()
        firebase.forEach { (name, value) -> resValue("string", name, value) }
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebase.isNotEmpty().toString())
        buildConfigField("String", "EMULATOR_HOST", "\"\"")
        buildConfigField("String", "MAPTILER_KEY", "\"$mapTilerKey\"")
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
        buildConfigField("boolean", "STORAGE_ENABLED", blazeEnabled.toString())
        buildConfigField("boolean", "PUSH_ENABLED", blazeEnabled.toString())
    }

    signingConfigs {
        if (hasSigning) create("release") {
            storeFile = rootProject.file(signing.getValue("storeFile"))
            storePassword = signing.getValue("storePassword")
            keyAlias = signing.getValue("keyAlias")
            keyPassword = signing.getValue("keyPassword")
        }
    }

    buildTypes {
        debug { useEmulators() }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
            // Phones are ARM; the x86 builds of the map engine only serve PC emulators and would add ~50 MB.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
        // Shrunk and optimised exactly like release, but debug-signed and able to use the local emulators,
        // so R8 problems show up before a tag is pushed: ./gradlew :app:installStaging -Pcatmap.emulatorHost=10.0.2.2
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".staging"
            ndk { abiFilters.clear() }
            useEmulators()
        }
    }
    sourceSets.getByName("staging") {
        // Same cleartext allowance for the emulator hosts as debug builds.
        manifest.srcFile("src/debug/AndroidManifest.xml")
        res.directories.add("src/debug/res")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    androidResources {
        generateLocaleConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // The APK is downloaded from GitHub rather than Play, so a compressed download matters more than
        // the small install-time saving of uncompressed native libraries.
        jniLibs.useLegacyPackaging = true
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
}

dependencies {
    baselineProfile(project(":baselineprofile"))
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("com.google.zxing:core:3.5.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.76.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    implementation("com.google.android.gms:play-services-location:21.4.0")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")

    // Vector maps (OpenFreeMap tiles, no key); replaces osmdroid's blurry raster tiles.
    implementation("org.maplibre.gl:android-sdk:13.6.1")
}
