import java.util.Properties

plugins {
    alias(libs.plugins.md.android.application)
    alias(libs.plugins.md.android.application.compose)
    alias(libs.plugins.md.hilt)
}

// Copy the asset logo to the resources folder during configuration phase
// so it is available to the Android build system automatically
project.copy {
    from("../assets/logo_medidesk.png")
    into("src/main/res/drawable")
    rename { "ic_launcher.png" }
}

// ---------------------------------------------------------------------------
// Load local.properties manually — AGP only auto-reads sdk.dir/ndk.dir from it.
// Custom keys (POSTHOG_API_KEY, BASE_URL, etc.) must be loaded explicitly.
// ---------------------------------------------------------------------------
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.reader().use { reader -> localProperties.load(reader) }
}

android {
    namespace = "pl.medidesk.mobile"
    // Output file names: mEventLab-{debug|release}-{versionName}.{apk|aab}
    base.archivesName.set("mEventLab")
    defaultConfig {
        applicationId = "pl.medidesk.mobile"
        versionCode = 1
        versionName = "1.0.0"

        // Read BASE_URL from local.properties or env var or use default
        val baseUrl = localProperties.getProperty("BASE_URL")
            ?: System.getenv("BASE_URL")
            ?: "https://md-order-portal-backend.onrender.com"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")

        // PostHog — read from local.properties (dev) or env vars (CI/release)
        // Never commit real keys — add POSTHOG_API_KEY=phc_... to local.properties
        val posthogKey = localProperties.getProperty("POSTHOG_API_KEY")
            ?: System.getenv("POSTHOG_API_KEY")
            ?: ""
        val posthogHost = localProperties.getProperty("POSTHOG_HOST")
            ?: System.getenv("POSTHOG_HOST")
            ?: "https://eu.i.posthog.com"
        buildConfigField("String", "POSTHOG_API_KEY", "\"$posthogKey\"")
        buildConfigField("String", "POSTHOG_HOST", "\"$posthogHost\"")
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("../assets")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = localProperties.getProperty("RELEASE_KEYSTORE_PATH")
                ?: System.getenv("RELEASE_KEYSTORE_PATH")
            val keystorePass = localProperties.getProperty("RELEASE_KEYSTORE_PASSWORD")
                ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")
            val keystoreAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                ?: System.getenv("RELEASE_KEY_ALIAS")
            val keystoreKeyPass = localProperties.getProperty("RELEASE_KEY_PASSWORD")
                ?: System.getenv("RELEASE_KEY_PASSWORD")

            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = keystorePass
                keyAlias = keystoreAlias
                keyPassword = keystoreKeyPass
            }
        }
    }

    buildTypes {
        release {
            // Apply release signing only if keystore is configured.
            // Otherwise the build falls back to unsigned (caught by Play Store upload check).
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core modules
    implementation(project(":core:core-model"))
    implementation(project(":core:core-network"))
    implementation(project(":core:core-database"))
    implementation(project(":core:core-datastore"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-sync"))

    // Feature modules (Limited to essential operator functions)
    implementation(project(":features:feature-auth"))
    implementation(project(":features:feature-events"))
    implementation(project(":features:feature-scanner"))
    implementation(project(":features:feature-participants"))
    implementation(project(":features:feature-dashboard"))
    implementation(project(":features:feature-more"))
    implementation(project(":features:feature-add-order"))
    // WO-MOB-015 (2026-05-25): manual speaker check-in (without QR).
    implementation(project(":features:feature-speakers"))

    // Analytics
    implementation(project(":core:core-analytics"))

    // Core Android
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)

    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // WorkManager
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Debug
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
}
