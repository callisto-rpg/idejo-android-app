// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// The app: Application, MainActivity, navigation host and the flavours (foss: F-Droid and
// GitHub; play: Google Play, which adds the embedded FCM distributor in M3).
plugins {
    id("idejo.android.application")
    id("idejo.android.compose")
    id("idejo.android.hilt")
    id("idejo.licensee")
}

android {
    namespace = "org.idejo.app"

    defaultConfig {
        applicationId = "org.idejo.app"
        versionCode = 1
        versionName = "0.0.1"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
        }
        create("play") {
            dimension = "distribution"
        }
    }
}

// The licence check's report ships in the app, for App settings → Open-source licences (ST-23).
licensee {
    bundleAndroidAsset.set(true)
    androidAssetReportPath.set("licences.json")
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:markdown"))
    listOf(
        "onboarding",
        "shell",
        "browse",
        "topic",
        "composer",
        "inbox",
        "notifications",
        "settings",
        "moderation",
    ).forEach { implementation(project(":feature:$it")) }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
