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

android {
    namespace = "pl.medidesk.mobile"
    defaultConfig {
        applicationId = "pl.medidesk.mobile"
        versionCode = 1
        versionName = "1.0.0"

        // Read BASE_URL from local.properties or use default
        val baseUrl = project.findProperty("BASE_URL") as String?
            ?: "https://md-order-portal-backend.onrender.com"
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("../assets")
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
