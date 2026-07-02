plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val is_fdroid_build = project.hasProperty("fdroid") ||
    gradle.startParameter.taskNames.any { it.contains("fdroid", ignoreCase = true) }

android {
    namespace = "org.astermail.android"
    compileSdk = 35

    flavorDimensions += "distribution"

    productFlavors {
        create("full") {
            dimension = "distribution"
        }
        create("fdroid") {
            dimension = "distribution"
        }
    }

    defaultConfig {
        applicationId = "org.astermail.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 111
        versionName = "0.6.102"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }

        val hash_email_pepper = System.getenv("ASTER_HASH_EMAIL_PEPPER")
            ?: project.findProperty("aster.hash_email_pepper") as String?
            ?: ""
        buildConfigField("String", "HASH_EMAIL_PEPPER", "\"$hash_email_pepper\"")
    }

    signingConfigs {
        create("release") {
            if (System.getenv("ASTER_UNSIGNED") != "1" && !is_fdroid_build) {
                storeFile = file("${rootProject.projectDir}/keystore/aster-mail-upload-v3.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: error("KEYSTORE_PASSWORD env var is required for release signing")
                keyAlias = "aster-mail"
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: error("KEY_PASSWORD env var is required for release signing")
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (System.getenv("ASTER_UNSIGNED") != "1" && !is_fdroid_build) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            vcsInfo { include = false }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        resources.excludes += "META-INF/versions/**/OSGI-INF/MANIFEST.MF"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    }
}

dependencies {
    implementation(project(":core-crypto"))
    implementation(project(":core-api"))
    implementation(project(":core-design"))
    implementation(project(":core-storage"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.androidx.webkit)
    implementation(libs.zxing.core)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation("net.zetetic:sqlcipher-android:4.16.0")
    implementation("androidx.sqlite:sqlite:2.4.0")

    implementation(libs.work.runtime.ktx)

    implementation(libs.unifiedpush.connector) {
        exclude(group = "com.google.crypto.tink", module = "tink")
    }

    implementation(libs.bouncycastle.provider)
    implementation(libs.bouncycastle.pgp)
    implementation(libs.jsoup)
    "fullImplementation"(platform(libs.firebase.bom))
    "fullImplementation"(libs.firebase.messaging)
    "fullImplementation"(libs.kotlinx.coroutines.play.services)
    "fullImplementation"(libs.unifiedpush.efcmd)
    "fullImplementation"("com.google.android.play:review-ktx:2.0.2")
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.process)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.json)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    "androidTestFullImplementation"("com.google.android.play:review:2.0.2")
    "androidTestFullImplementation"("androidx.test:core-ktx:1.6.1")
}

if (!is_fdroid_build) {
    apply(plugin = "com.google.gms.google-services")
}
