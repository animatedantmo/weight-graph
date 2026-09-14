plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/*
 * A machine-specific install suffix, read from local.properties (gitignored, per-machine).
 * Setting `installSuffix=laptop` there gives that machine its own applicationId, so its debug
 * build installs alongside the main app instead of colliding with it — the two are separate
 * apps with separate databases, and data moves between them by CSV export and import.
 *
 * Read through providers.fileContents rather than plain file I/O so the configuration cache
 * invalidates when local.properties changes.
 */
val installSuffix: String = providers.fileContents(
    rootProject.layout.projectDirectory.file("local.properties")
).asText.map { text ->
    text.lineSequence()
        .firstOrNull { it.trimStart().startsWith("installSuffix=") }
        ?.substringAfter('=')
        ?.trim()
        .orEmpty()
}.getOrElse("")

// Indigo for the main app, red for a suffixed side-by-side install.
val launcherBackground: String = if (installSuffix.isEmpty()) "#4F46E5" else "#C62828"
val appLabel: String = if (installSuffix.isEmpty()) "Weight Graph" else "Weight Graph ($installSuffix)"

android {
    namespace = "org.animatedantmo.weightgraph"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.animatedantmo.weightgraph"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Only a suffixed debug build becomes a separate app; without the suffix this is
            // the main app, updated in place as usual.
            if (installSuffix.isNotEmpty()) {
                applicationIdSuffix = ".$installSuffix"
                versionNameSuffix = "-$installSuffix"
            }
            resValue("string", "app_name", appLabel)
            resValue("color", "launcher_background", launcherBackground)
        }
        release {
            // A release build is always the real app, whatever machine it was built on.
            resValue("string", "app_name", "Weight Graph")
            resValue("color", "launcher_background", "#4F46E5")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // AGP 9 defaults this off; the app label and launcher colour are set per build type.
        resValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.auth)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
