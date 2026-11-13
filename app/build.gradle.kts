import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Reads app/google-services.json (the Firebase project) into resources, for -PpuzzlesBackend=firebase.
    alias(libs.plugins.google.services)
}

// Where the player API lives. Debug builds use the production API unless told otherwise; release
// builds have no server until one is given. Either takes -PpuzzlesApiUrl=https://…/api/v1/ (the path
// carries the API version). To try the API running in Docker on this computer, pass
// -PpuzzlesApiUrl=http://localhost:8080/api/v1/ and run `adb reverse tcp:8080 tcp:8080` (emulator or
// phone over USB): from Android 17 on, an app needs a special permission for the local network, but
// not for localhost, which the debug network config lets through without TLS.
val apiUrl: String? = providers.gradleProperty("puzzlesApiUrl").orNull

// Which server the app talks to (data/Backend.kt): -PpuzzlesBackend=api, firebase or none. Unset, it is
// Firebase, the server of the published app, unless -PpuzzlesApiUrl was given: then the API at that address.
// -PpuzzlesBackend=api alone uses the production API in debug builds; in release ones, with no address, the API
// is no server at all (Backends.create).
val backend: String? = providers.gradleProperty("puzzlesBackend").orNull

val chosenBackend: String = (backend ?: if (apiUrl != null) "api" else "firebase").also { chosen ->
    require(chosen in setOf("none", "api", "firebase")) { "-PpuzzlesBackend=$chosen: los servidores son api, firebase y none" }
}

val debugApiUrl = apiUrl ?: "https://api.puzzles.butaca13.com/api/v1/"
val releaseApiUrl = apiUrl.orEmpty()

// Debug builds buy from a simulated store (FakeStore), to try purchases without Play Console; with
// -PfakeStore=false they use Google Play, which needs the app uploaded there and a tester account.
// Release builds always use Google Play.
val fakeStore: Boolean = providers.gradleProperty("fakeStore").orNull?.toBoolean() ?: true

// Signing secrets stay out of the repository. Without this file, release builds are simply unsigned.
val keystoreProperties = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

android {
    namespace = "com.butaca13.puzzles"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.butaca13.puzzles"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_URL", "\"$debugApiUrl\"")
            buildConfigField("String", "BACKEND", "\"$chosenBackend\"")
            buildConfigField("boolean", "FAKE_STORE", fakeStore.toString())
        }
        release {
            buildConfigField("String", "API_URL", "\"$releaseApiUrl\"")
            buildConfigField("String", "BACKEND", "\"$chosenBackend\"")
            buildConfigField("boolean", "FAKE_STORE", "false")
            if (keystoreProperties != null) signingConfig = signingConfigs.getByName("release")
            // Whole-app R8. Limiting it with packageScope to androidx and kotlin, as the project
            // template did, made the release build crash on launch with an IllegalAccessError.
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.billing.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.functions)
    implementation(libs.firebase.firestore)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
