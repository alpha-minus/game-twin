import java.util.Base64
import java.util.Properties
import java.io.FileInputStream
import org.gradle.api.tasks.PathSensitivity

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    kotlin("kapt")
}

fun Project.readLocalProperties(): Properties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun encodeKey(raw: String, mask: String): String {
    if (raw.isEmpty()) return ""
    require(mask.isNotEmpty()) { "keyObfuscationMask must be non-empty" }
    val rawBytes = raw.toByteArray(Charsets.UTF_8)
    val maskBytes = mask.toByteArray(Charsets.UTF_8)
    val out = ByteArray(rawBytes.size)
    for (i in rawBytes.indices) {
        out[i] = (rawBytes[i].toInt() xor maskBytes[i % maskBytes.size].toInt()).toByte()
    }
    return Base64.getEncoder().encodeToString(out)
}

val localProps = readLocalProperties()
val keyMask: String = localProps.getProperty("keyObfuscationMask")
    ?: "dev-mask-not-for-production"
val steamKeyRaw: String = localProps.getProperty("steamApiKey") ?: ""
val steamGridKeyRaw: String = localProps.getProperty("steamGridDbApiKey") ?: ""
val theGamesDbKeyRaw: String = localProps.getProperty("theGamesDbApiKey") ?: ""
val raApiKeyRaw: String = localProps.getProperty("retroAchievementsApiKey") ?: ""

/**
 * Every bundled key falls back to an empty string, which is what lets a
 * checkout without `local.properties` build and test normally. A release,
 * though, would then ship a blank key: `encodeKey("")` returns "", the
 * BUNDLED_API_KEY is empty, the credential repository rejects it, and the
 * feature silently stops working for every user who has not entered a key of
 * their own. So release packaging fails closed instead.
 *
 * That only applies to a build that bundles keys at all: when none of the
 * properties is set (a fork or a from-source build), the release ships without
 * bundled keys and every feature relies on a key the user enters in Settings.
 *
 * Names only -- never a value.
 */
val bundledKeyPropNames = listOf(
    "keyObfuscationMask", "steamApiKey", "steamGridDbApiKey", "theGamesDbApiKey", "retroAchievementsApiKey",
)
val bundlesKeys: Boolean = bundledKeyPropNames.any { !localProps.getProperty(it).isNullOrBlank() }
val missingBundledKeyProps: List<String> = if (!bundlesKeys) emptyList() else buildList {
    if (localProps.getProperty("keyObfuscationMask").isNullOrBlank()) add("keyObfuscationMask")
    if (steamKeyRaw.isBlank()) add("steamApiKey")
    if (steamGridKeyRaw.isBlank()) add("steamGridDbApiKey")
    if (theGamesDbKeyRaw.isBlank()) add("theGamesDbApiKey")
    if (raApiKeyRaw.isBlank()) add("retroAchievementsApiKey")
}

val verifyReleaseBundledKeys = tasks.register("verifyReleaseBundledKeys") {
    group = "verification"
    description = "Fails a release build when a bundled key property is missing from local.properties."
    val missing = missingBundledKeyProps
    doLast {
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release build refused: missing or blank in local.properties: " +
                    missing.joinToString(", ") +
                    ". Add these properties to local.properties before assembling a release."
            )
        }
    }
}

// Hook the packaging tasks, not `assemble`, so debug builds and unit tests
// (which never reach these) are unaffected on a machine without the properties.
tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }
    .configureEach { dependsOn(verifyReleaseBundledKeys) }

// Load keystore properties
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use {
        keystoreProperties.load(it)
    }
}

android {
    namespace = "com.twinlore.app"
    compileSdk = 35

    sourceSets {
        getByName("main").assets.srcDirs("src/main/assets", "${rootProject.projectDir}/data/retroachievements")
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }

    defaultConfig {
        applicationId = "com.twinlore.app"
        minSdk = 33  // Android 13+ (API 33)
        targetSdk = 35
        versionCode = 26
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        multiDexEnabled = true

        buildConfigField("String", "STEAM_API_KEY_ENCODED",
            "\"${encodeKey(steamKeyRaw, keyMask)}\"")
        buildConfigField("String", "STEAMGRIDDB_API_KEY_ENCODED",
            "\"${encodeKey(steamGridKeyRaw, keyMask)}\"")
        buildConfigField("String", "THEGAMESDB_API_KEY_ENCODED",
            "\"${encodeKey(theGamesDbKeyRaw, keyMask)}\"")
        buildConfigField("String", "RA_API_KEY_ENCODED",
            "\"${encodeKey(raApiKeyRaw, keyMask)}\"")
        buildConfigField("String", "KEY_OBFUSCATION_MASK", "\"$keyMask\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
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
            // Without app/keystore.properties (any checkout but the author's) the
            // release build is left unsigned; sign it with your own key.
            signingConfig = if (keystorePropertiesFile.exists()) signingConfigs.getByName("release") else null
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
        buildConfig = true
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Required for Robolectric-hosted Compose tests: createComposeRule() launches a
            // test Activity from the merged manifest, which needs the merged resources (theme,
            // etc.) to resolve. Applies to every unit test in the module, not just compose ones.
            isIncludeAndroidResources = true
            all { test ->
                // Headroom over Gradle's default 512m worker heap: mock-heavy classes
                // sit close to it, and an OOMed worker reports "N failed" with nothing
                // in the results XML (or wedges on shutdown) instead of a clear error.
                test.maxHeapSize = "2g"
                // A single worker JVM running the whole suite accumulates enough
                // Robolectric/Compose state that the two createComposeRule classes
                // (JourneyPlayerInputTest, JourneyScreenStateTest) time out with
                // AppNotIdleException late in the run while being green in isolation.
                // Recycling the worker bounds that accumulation; parallel forks keep
                // the recycling from stretching wall-clock. 10 classes per JVM, not 40:
                // in a 40-class group, ArtworkThumbnailCacheTest's NATIVE-graphics
                // decode flaked depending on which classmates had initialized graphics
                // first; smaller groups have run the suite green repeatedly.
                test.setForkEvery(10)
                test.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
            }
        }
    }
}

// Load byte-buddy-agent as -javaagent so Mockito-inline doesn't have to
// self-attach to the running JVM. Self-attach fails on Homebrew JDK 17 +
// macOS 26.4 with "Could not self-attach to current VM using external
// process" — see ByteBuddyAgent.installExternal. Loading the agent at JVM
// start bypasses that path entirely.
val mockitoAgent: Configuration = configurations.create("mockitoAgent")
dependencies {
    mockitoAgent("net.bytebuddy:byte-buddy-agent:1.14.6")
}
tasks.withType<Test>().configureEach {
    jvmArgs("-javaagent:${mockitoAgent.singleFile.absolutePath}")

    // These tests read legal text straight off the filesystem via RepoFiles rather than
    // through packaged assets, so Gradle cannot infer the dependency. Without it an
    // asset-only edit leaves the task UP-TO-DATE and prints a green describing the previous
    // content, which is precisely the regression the legal tests exist to catch.
    inputs.dir(layout.projectDirectory.dir("src/main/assets/legal"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("legalTexts")
    inputs.file(rootProject.file("THIRD_PARTY_NOTICES.txt"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("thirdPartyNotices")
}

kapt {
    correctErrorTypes = true
    useBuildCache = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}


dependencies {
    // Core AndroidX (explicit version for compatibility)
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.multidex:multidex:2.0.1")
    
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Pager for carousel with better physics
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    // Note: foundation-pager is managed by BOM, but we're not using HorizontalPager in current implementation
    // Keeping foundation libraries for future use
    
    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-process:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")
    
    // Room (using KSP for better Kotlin support)
    implementation("androidx.room:room-runtime:2.5.2")
    implementation("androidx.room:room-ktx:2.5.2")
    ksp("androidx.room:room-compiler:2.5.2")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // JSON parsing (for RetroArch history)
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    // Animated GIF / WebP decoding (ImageDecoderDecoder) for the app wallpaper.
    implementation("io.coil-kt:coil-gif:2.5.0")
    // SVG decoding — ES-DE theme console logos (downloaded on demand, not bundled).
    implementation("io.coil-kt:coil-svg:2.5.0")
    // Pinch / pan / double-tap / flick-to-dismiss for the in-Guide image viewer.
    // Pinned to 0.7.1 — the last release built against Compose Foundation 1.5
    // (we're on Compose BOM 2023.10.01). Apache-2.0; maintainer: Saket Verma.
    implementation("me.saket.telephoto:zoomable-image-coil:0.7.1")
    
    // Retrofit for HTTP requests
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // DataStore for settings
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Encrypted storage for API keys
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // DocumentFile for SAF (Storage Access Framework)
    implementation("androidx.documentfile:documentfile:1.0.1")
    
    // HTML parsing for offline page saving
    implementation("org.jsoup:jsoup:1.17.2")
    
    // WebView dark mode support
    implementation("androidx.webkit:webkit:1.9.0")
    
    // Palette API for color extraction from images
    implementation("androidx.palette:palette:1.0.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    testImplementation("androidx.lifecycle:lifecycle-runtime-testing:2.6.2")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    
    // Mock Android framework for unit tests
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.room:room-testing:2.5.2")
    testImplementation("androidx.test:core:1.5.0")

    // Robolectric-hosted Compose state-rendering tests (createComposeRule under the JVM).
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.room:room-testing:2.5.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}


/**
 * Compose stability configuration.
 *
 * compose-compiler 1.5.8 predates strong skipping, so a composable taking a `data class` that holds
 * a `List` can never skip - and the Achievement Journey passes several of those down a deep tree at
 * playback frequency. The file this points at names the build-once value objects the compiler
 * cannot prove stable on its own; it changes no behaviour, only what the compiler is allowed to
 * skip. Supported since compiler 1.5.5.
 */
val composeStabilityConfig = layout.projectDirectory.file("compose_compiler_config.conf")

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    // Declared as an input so a change to the list re-runs compilation rather than being cached
    // past: the file is read by the compiler plugin, which Gradle cannot see into.
    inputs.file(composeStabilityConfig).withPathSensitivity(PathSensitivity.RELATIVE)
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=" +
                composeStabilityConfig.asFile.absolutePath,
        )
    }
}
